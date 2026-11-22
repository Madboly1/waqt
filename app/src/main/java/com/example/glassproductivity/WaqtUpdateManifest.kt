package com.example.glassproductivity

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

/** No publishing or feed changes. A newer release must provide its own digest. */
data class WaqtUpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val message: String,
    val apkUrl: String,
    val sha256: String?,
    val packageName: String?,
    val certificateSha256: String?
) {
    fun requireDownloadable(): WaqtUpdateManifest {
        require(sha256 != null) { "بيانات التحقق من التحديث غير جاهزة. جرّب لاحقًا." }
        return this
    }

    companion object {
        private val digestPattern = Regex("[0-9a-fA-F]{64}")
        fun parse(text: String, expectedPackage: String): WaqtUpdateManifest {
            val root = JSONObject(text)
            val rawCode = root.opt("versionCode")
            require(rawCode is Int || rawCode is Long) { "رقم إصدار التحديث غير صالح." }
            val code = (rawCode as Number).toLong()
            require(code in 1..Int.MAX_VALUE.toLong()) { "رقم إصدار التحديث غير صالح." }
            val name = root.opt("versionName")
            require(name is String && name.isNotBlank() && name.length <= 100) { "اسم إصدار التحديث غير صالح." }
            val rawUrl = root.opt("apkDownloadUrl") ?: root.opt("apkUrl")
            require(rawUrl is String && isTrustedApkUrl(rawUrl)) { "مصدر التحديث غير موثوق." }
            val app = root.opt("app")
            require(app == null || app == "WAQT") { "التحديث لتطبيق مختلف." }
            val pkg = optionalString(root, "packageName")
            require(pkg == null || pkg == expectedPackage) { "التحديث لتطبيق مختلف." }
            val hash = optionalDigest(root, "sha256")
            val cert = optionalDigest(root, "certificateSha256")
            val message = optionalString(root, "updateMessage") ?: optionalString(root, "releaseMessage") ?: ""
            require(message.length <= 5000) { "تفاصيل التحديث غير صالحة." }
            return WaqtUpdateManifest(code, name.trim(), message, rawUrl, hash, pkg, cert)
        }

        fun isTrustedApkUrl(raw: String): Boolean = try {
            val u = URI(raw)
            u.scheme == "https" && u.host == "github.com" && u.rawUserInfo == null &&
                (u.port == -1 || u.port == 443) && u.rawQuery == null && u.rawFragment == null &&
                u.rawPath.matches(Regex("/Madboly1/waqt/releases/download/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+\\.apk"))
        } catch (_: Exception) { false }

        private fun optionalString(root: JSONObject, key: String): String? {
            if (!root.has(key)) return null
            val value = root.opt(key)
            require(value is String && value.isNotBlank()) { "بيانات التحديث غير صالحة." }
            return value
        }

        private fun optionalDigest(root: JSONObject, key: String): String? {
            val value = optionalString(root, key) ?: return null
            require(digestPattern.matches(value)) { "بصمة التحديث غير صالحة." }
            return value.lowercase(java.util.Locale.ROOT)
        }
    }
}

/** Streaming, bounded and testable without accessing personal app data. */
object WaqtUpdateFiles {
    const val MAX_BYTES = 100L * 1024 * 1024

    fun receive(
        input: InputStream,
        target: File,
        expectedHash: String,
        declaredLength: Long,
        maxBytes: Long = MAX_BYTES,
        cancelled: () -> Boolean = { false },
        progress: (Long, Long) -> Unit = { _, _ -> }
    ): File {
        require(Regex("[0-9a-f]{64}").matches(expectedHash))
        require(maxBytes > 0)
        if (declaredLength > maxBytes || declaredLength == 0L) throw IOException("حجم التحديث غير صالح.")
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, target.name + ".part")
        val hash = MessageDigest.getInstance("SHA-256")
        var count = 0L
        try {
            partial.outputStream().buffered().use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    if (cancelled()) throw IOException("تم إلغاء تحميل التحديث.")
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    count += n
                    if (count > maxBytes || (declaredLength > 0 && count > declaredLength)) throw IOException("حجم التحديث غير صالح.")
                    output.write(buffer, 0, n)
                    hash.update(buffer, 0, n)
                    progress(count, declaredLength)
                }
            }
            if (count == 0L || (declaredLength > 0 && count != declaredLength)) throw IOException("التنزيل غير مكتمل.")
            val actual = hash.digest().joinToString("") { "%02x".format(it) }
            if (actual != expectedHash) throw IOException("فشل التحقق من سلامة ملف التحديث.")
            if (cancelled()) throw IOException("تم إلغاء تحميل التحديث.")
            // Never overwrite a previously verified candidate with an incomplete download.
            if (target.exists() && !target.delete()) throw IOException("تعذر حفظ ملف التحديث.")
            if (!partial.renameTo(target)) throw IOException("تعذر حفظ ملف التحديث.")
            return target
        } finally {
            if (partial.exists()) partial.delete()
        }
    }
}
