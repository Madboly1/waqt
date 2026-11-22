package com.example.glassproductivity

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

class WaqtUpdateClient(
    private val context: Context,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) {
    companion object {
        // The existing channel stays unchanged. Publishing a new release is separate.
        const val MANIFEST_URL = "https://raw.githubusercontent.com/Madboly1/waqt-updates/main/update.json"
        private val REDIRECT_HOSTS = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    }

    fun installedCode(): Long = version(context.packageManager.getPackageInfo(context.packageName, 0))

    /** null means the existing feed does not advertise a newer version. */
    fun check(): WaqtUpdateManifest? {
        val connection = connectionFactory(URL(MANIFEST_URL))
        val text = try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Cache-Control", "no-cache")
            require(connection.responseCode == 200) { "تعذر التحقق من التحديثات مؤقتًا." }
            connection.inputStream.use { input ->
                val bytes = input.readBytesLimited(64 * 1024)
                bytes.toString(Charsets.UTF_8)
            }
        } finally { connection.disconnect() }
        val manifest = WaqtUpdateManifest.parse(text, context.packageName)
        if (manifest.versionCode <= installedCode()) return null
        return manifest.requireDownloadable()
    }

    fun download(
        manifest: WaqtUpdateManifest,
        cancelled: () -> Boolean = { false },
        progress: (Long, Long) -> Unit = { _, _ -> }
    ): File {
        manifest.requireDownloadable()
        require(WaqtUpdateManifest.isTrustedApkUrl(manifest.apkUrl)) { "مصدر التحديث غير موثوق." }
        require(manifest.versionCode > installedCode()) { "التطبيق محدّث بالفعل." }
        var next = manifest.apkUrl
        var connection: HttpURLConnection? = null
        try {
            for (attempt in 0..5) {
                val c = connectionFactory(URL(next))
                connection = c
                c.instanceFollowRedirects = false
                c.connectTimeout = 20_000
                c.readTimeout = 25_000
                val status = c.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val target = c.getHeaderField("Location") ?: throw IOException("رابط التحميل غير صالح.")
                    val uri = URI(next).resolve(target)
                    require(uri.scheme == "https" && uri.host in REDIRECT_HOSTS && uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443)) { "مصدر التحميل غير موثوق." }
                    next = uri.toString()
                    c.disconnect()
                    connection = null
                    continue
                }
                if (status != 200) throw IOException("تعذر تنزيل التحديث مؤقتًا.")
                val dir = File(context.cacheDir, "waqt_updates")
                val file = File(dir, "waqt-${manifest.versionCode}.apk")
                val received = c.inputStream.use {
                    WaqtUpdateFiles.receive(it, file, manifest.sha256!!, c.contentLengthLong, cancelled = cancelled, progress = progress)
                }
                try { verifyPackage(received, manifest) }
                catch (e: Exception) { received.delete(); throw e }
                return received
            }
            throw IOException("تعذر الوصول إلى ملف التحديث.")
        } finally { connection?.disconnect() }
    }

    fun verifyPackage(file: File, manifest: WaqtUpdateManifest) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val pm = context.packageManager
        val installed = pm.getPackageInfo(context.packageName, flags)
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: throw IOException("ملف التحديث غير صالح.")
        val existingCertificates = certificates(installed)
        require(existingCertificates.isNotEmpty() && certificates(archive) == existingCertificates) { "توقيع التحديث لا يطابق التطبيق المثبّت." }
        require(archive.packageName == context.packageName && version(archive) == manifest.versionCode && archive.versionName == manifest.versionName) { "ملف التحديث لا يطابق الإصدار المطلوب." }
        require(manifest.certificateSha256 == null || manifest.certificateSha256 in existingCertificates) { "بصمة توقيع التحديث غير مطابقة." }
        require(manifest.packageName == null || manifest.packageName == archive.packageName) { "التحديث لتطبيق مختلف." }
    }

    private fun certificates(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners
            else @Suppress("DEPRECATION") info.signatures
        return signatures?.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }?.toSet() ?: emptySet()
    }

    private fun version(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = read(buffer)
            if (count < 0) return out.toByteArray()
            if (out.size() + count > limit) throw IOException("بيانات التحديث أكبر من المسموح.")
            out.write(buffer, 0, count)
        }
    }
}
