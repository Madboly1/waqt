package com.example.glassproductivity

import android.app.Activity
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DriveBackupException(message: String, val statusCode: Int? = null) : Exception(message)

/**
 * Drive REST v3 over the hidden app-private folder (appDataFolder), plus the
 * Drive authorization step which is separate from identity sign-in. Backups
 * are immutable: uploads always create a new dated file; nothing is ever
 * overwritten or deleted here.
 */
class DriveBackupManager(private val activity: Activity) {

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val NAME_PREFIX = "waqt-backup-"
        private const val FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"
    }

    data class DriveBackupFile(
        val id: String,
        val name: String,
        val createdTime: String?,
        val sizeBytes: Long
    )

    /** Set by MainActivity to launch the authorization consent resolution. */
    var pendingResolution: ((IntentSender) -> Unit)? = null

    private var resolutionDeferred: kotlinx.coroutines.CompletableDeferred<Int>? = null

    /** Called by MainActivity's IntentSender result launcher. */
    fun onResolutionResult(resultCode: Int) {
        resolutionDeferred?.complete(resultCode)
        resolutionDeferred = null
    }

    /**
     * Returns a Drive access token, or null when the user declines. Identity
     * sign-in stays separate: the first backup asks for Drive permission
     * explicitly.
     */
    suspend fun getAccessToken(interactive: Boolean): String? {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        val result = awaitAuthorization(request)
        if (result.hasResolution()) {
            if (!interactive) return null
            val sender = result.pendingIntent?.intentSender ?: return null
            val launcher = pendingResolution ?: return null
            val deferred = kotlinx.coroutines.CompletableDeferred<Int>()
            resolutionDeferred = deferred
            launcher(sender)
            if (deferred.await() != Activity.RESULT_OK) return null
            return awaitAuthorization(request).accessToken
        }
        return result.accessToken
    }

    private suspend fun awaitAuthorization(request: AuthorizationRequest): AuthorizationResult =
        suspendCancellableCoroutine { cont ->
            Identity.getAuthorizationClient(activity)
                .authorize(request)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    suspend fun listBackups(token: String): List<DriveBackupFile> = withContext(Dispatchers.IO) {
        val query = buildString {
            append("spaces=appDataFolder")
            append("&q=").append(java.net.URLEncoder.encode("name contains '$NAME_PREFIX' and trashed = false", "UTF-8"))
            append("&fields=").append(java.net.URLEncoder.encode("files(id,name,createdTime,size)", "UTF-8"))
            append("&orderBy=").append(java.net.URLEncoder.encode("createdTime desc", "UTF-8"))
            append("&pageSize=20")
        }
        val body = request("GET", "$FILES_ENDPOINT?$query", token)
        val files = JSONObject(body).optJSONArray("files") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until files.length()) {
                val raw = files.optJSONObject(i) ?: continue
                val id = raw.optString("id")
                if (id.isBlank()) continue
                add(
                    DriveBackupFile(
                        id = id,
                        name = raw.optString("name"),
                        createdTime = raw.optString("createdTime").ifBlank { null },
                        sizeBytes = raw.optString("size").toLongOrNull() ?: 0L
                    )
                )
            }
        }
    }

    suspend fun uploadBackup(token: String, name: String, bytes: ByteArray): DriveBackupFile =
        withContext(Dispatchers.IO) {
            val boundary = "waqt_backup_7f3d9a1c"
            val metadata = JSONObject()
                .put("name", name)
                .put("parents", org.json.JSONArray().put("appDataFolder"))
                .toString()
            val head = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
                "--$boundary\r\nContent-Type: application/zip\r\n\r\n").toByteArray(Charsets.UTF_8)
            val tail = "\r\n--$boundary--".toByteArray(Charsets.UTF_8)
            val body = ByteArrayOutputStream(head.size + bytes.size + tail.size).apply {
                write(head); write(bytes); write(tail)
            }.toByteArray()
            val response = request(
                "POST",
                "$UPLOAD_ENDPOINT?uploadType=multipart&fields=id,name,createdTime,size",
                token,
                body = body,
                contentType = "multipart/related; boundary=$boundary"
            )
            val raw = JSONObject(response)
            DriveBackupFile(
                id = raw.optString("id"),
                name = name,
                createdTime = raw.optString("createdTime").ifBlank { null },
                sizeBytes = bytes.size.toLong()
            )
        }

    suspend fun downloadBackup(token: String, id: String): ByteArray = withContext(Dispatchers.IO) {
        requestBytes("GET", "$FILES_ENDPOINT/$id?alt=media", token)
    }

    private fun request(
        method: String,
        url: String,
        token: String,
        body: ByteArray? = null,
        contentType: String? = null
    ): String = String(requestBytes(method, url, token, body, contentType), Charsets.UTF_8)

    private fun requestBytes(
        method: String,
        url: String,
        token: String,
        body: ByteArray? = null,
        contentType: String? = null
    ): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (code !in 200..299) {
                throw DriveBackupException("Drive request failed ($code)", code)
            }
            return bytes
        } finally {
            connection.disconnect()
        }
    }
}
