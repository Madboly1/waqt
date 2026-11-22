package com.example.glassproductivity

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WaqtUpdateSafetyTest {
    private val pkg = "com.example.glassproductivity"
    private val goodUrl = "https://github.com/Madboly1/waqt/releases/download/v5.6.80/app-release.apk"
    private val content = "disposable-update-test".toByteArray()
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun json() = JSONObject().put("app", "WAQT").put("versionCode", 106).put("versionName", "5.6.80").put("apkDownloadUrl", goodUrl).put("sha256", digest(content))
    private fun reject(change: (JSONObject) -> Unit) {
        val root=json();change(root)
        try { WaqtUpdateManifest.parse(root.toString(),pkg); fail("Must reject malformed update") } catch (_: IllegalArgumentException) { }
    }
    private fun file() = File(Files.createTempDirectory("waqt-update-test").toFile(), "update.apk")
    private fun expectDownloadFailure(file: File, action: () -> Unit) {
        try { action();fail("Must reject download") } catch (_: IOException) { }
        assertFalse(File(file.parentFile,file.name+".part").exists())
    }
    @Test fun validOwnRepositoryManifestParses() { assertEquals(106L,WaqtUpdateManifest.parse(json().toString(),pkg).requireDownloadable().versionCode) }
    @Test fun oldFeedWithoutDigestCanBeInspectedButNotDownloaded() {
        val root=json();root.remove("sha256");root.put("versionCode",105)
        val result=WaqtUpdateManifest.parse(root.toString(),pkg);assertEquals(105L,result.versionCode)
        try { result.requireDownloadable();fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun fractionalVersionRejected() = reject { it.put("versionCode",106.5) }
    @Test fun stringVersionRejected() = reject { it.put("versionCode","106") }
    @Test fun overflowVersionRejected() = reject { it.put("versionCode",Long.MAX_VALUE) }
    @Test fun zeroVersionRejected() = reject { it.put("versionCode",0) }
    @Test fun foreignAppRejected() = reject { it.put("app","BOUGA") }
    @Test fun foreignPackageRejected() = reject { it.put("packageName","another.app") }
    @Test fun malformedDigestRejected() = reject { it.put("sha256","bad") }
    @Test fun nullDigestRejected() = reject { it.put("sha256",JSONObject.NULL) }
    @Test fun malformedCertificateRejected() = reject { it.put("certificateSha256","bad") }
    @Test fun insecureDownloadRejected() = reject { it.put("apkDownloadUrl",goodUrl.replace("https:","http:")) }
    @Test fun foreignRepositoryRejected() = reject { it.put("apkDownloadUrl",goodUrl.replace("/waqt/","/Smart-hafeza/")) }
    @Test fun lookalikeHostRejected() = reject { it.put("apkDownloadUrl",goodUrl.replace("github.com","github.com.evil.invalid")) }
    @Test fun userInfoRejected() = reject { it.put("apkDownloadUrl",goodUrl.replace("github.com","attacker@github.com")) }
    @Test fun encodedTraversalRejected() = reject { it.put("apkDownloadUrl",goodUrl.replace("v5.6.80","%2e%2e")) }
    @Test fun queryAndFragmentRejected() {
        for (suffix in listOf("?x=1","#fragment")) assertFalse(WaqtUpdateManifest.isTrustedApkUrl(goodUrl+suffix))
    }
    @Test fun uppercaseDigestNormalized() { val root=json();root.put("sha256",digest(content).uppercase());assertEquals(digest(content),WaqtUpdateManifest.parse(root.toString(),pkg).sha256) }
    @Test fun successfulStreamIsExactAndProgressReportsBytes() {
        val target=file();val events=mutableListOf<Long>()
        WaqtUpdateFiles.receive(ByteArrayInputStream(content),target,digest(content),content.size.toLong(),progress={n,_->events.add(n)})
        assertArrayEquals(content,target.readBytes());assertEquals(content.size.toLong(),events.last());assertFalse(File(target.parentFile,"update.apk.part").exists())
    }
    @Test fun badHashKeepsPreviousVerifiedFile() {
        val target=file();target.writeText("previous")
        expectDownloadFailure(target) { WaqtUpdateFiles.receive(ByteArrayInputStream(content),target,"0".repeat(64),content.size.toLong()) }
        assertEquals("previous",target.readText())
    }
    @Test fun truncatedStreamRejected() { val t=file();expectDownloadFailure(t) { WaqtUpdateFiles.receive(ByteArrayInputStream(content),t,digest(content),content.size.toLong()+1) };assertFalse(t.exists()) }
    @Test fun longerThanDeclaredRejected() { val t=file();expectDownloadFailure(t) { WaqtUpdateFiles.receive(ByteArrayInputStream(content),t,digest(content),content.size.toLong()-1) } }
    @Test fun oversizedResponseRejectedBeforeWriting() { val t=file();expectDownloadFailure(t) { WaqtUpdateFiles.receive(ByteArrayInputStream(content),t,digest(content),200,maxBytes=100) };assertFalse(t.exists()) }
    @Test fun unknownLengthStillBounded() { val t=file();expectDownloadFailure(t) { WaqtUpdateFiles.receive(ByteArrayInputStream(content),t,digest(content),-1,maxBytes=2) } }
    @Test fun unknownLengthCanCompleteWithExactDigest() { val t=file();WaqtUpdateFiles.receive(ByteArrayInputStream(content),t,digest(content),-1);assertArrayEquals(content,t.readBytes()) }
    @Test fun emptyStreamRejected() { val t=file();expectDownloadFailure(t) { WaqtUpdateFiles.receive(ByteArrayInputStream(byteArrayOf()),t,digest(byteArrayOf()),-1) } }
    @Test fun cancellationDeletesPartialAndKeepsPrevious() { val t=file();t.writeText("previous");expectDownloadFailure(t) { WaqtUpdateFiles.receive(ByteArrayInputStream(content),t,digest(content),content.size.toLong(),cancelled={true}) };assertEquals("previous",t.readText()) }
}
