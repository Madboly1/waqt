package com.example.glassproductivity

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full app backup: every SharedPreferences store (minus API keys), the chat
 * history database, mentor memory, and app-owned media files (journal voice
 * notes and images, chat images, note photos). Gallery-referenced images are
 * copied in best-effort and their references rewritten on restore.
 */
object WaqtCloudBackup {

    const val SCHEMA_VERSION = 5

    private val PREFS_FILES = listOf(
        "waqt_productivity_prefs",
        "waqt_prefs",
        "waqt_daily_journal",
        "waqt_daily_journal_media",
        "waqt_mentor_memory",
        "waqt_note_categories",
        "is_arabic",
        "onboarding_done",
        "rail_offset",
        "rail_edge",
        "ota_auto_check"
    )

    /** AI provider keys never leave the device. */
    private val EXCLUDED_KEYS = setOf(
        "gemini_api_key", "groq_api_key", "openrouter_api_key",
        "gemini_model", "groq_model", "openrouter_model"
    )

    /**
     * Any persistent secret- or token-like preference is excluded too,
     * including future auth material. Worded tightly so ordinary settings
     * like max_tokens or pomodoro sessions still back up.
     */
    private val EXCLUDED_KEY_PATTERN = Regex(
        "(?i).*(api[-_]?key|apikey|secret|password|credential|oauth|bearer|(access|refresh|id|auth)[-_]?token).*"
    )

    /** Signed-in Google identity is device state: never backed up, never overwritten by a restore. */
    private val IDENTITY_KEYS = setOf("waqt_google_email", "waqt_google_name")

    private fun isExcludedKey(key: String): Boolean =
        EXCLUDED_KEYS.contains(key) || IDENTITY_KEYS.contains(key) || EXCLUDED_KEY_PATTERN.matches(key)

    data class BackupSummary(
        val exportedAt: String?,
        val account: String?,
        val taskCount: Int,
        val noteCount: Int,
        val journalDays: Int,
        val chatCount: Int,
        val mediaCount: Int
    )

    class RestoreException(message: String) : Exception(message)

    data class BuildOutcome(val zipBytes: ByteArray, val report: BackupIntegrity.BuildReport)

    fun build(context: Context, account: String?): ByteArray = buildWithReport(context, account).zipBytes

    fun buildWithReport(context: Context, account: String?): BuildOutcome {
        val mediaManifest = JSONArray()
        var appMediaCount = 0
        var externalIncluded = 0
        var externalSkipped = 0
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val prefsJson = JSONObject()
            for (name in PREFS_FILES) {
                val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                val store = JSONObject()
                for ((key, value) in prefs.all) {
                    if (isExcludedKey(key)) continue
                    when (value) {
                        is Boolean -> store.put(key, value)
                        is Int -> store.put(key, value)
                        is Long -> store.put(key, value)
                        is Float -> store.put(key, value.toDouble())
                        is String -> store.put(key, value)
                        is Set<*> -> store.put(key, JSONArray(value.filterIsInstance<String>()))
                        else -> {}
                    }
                }
                prefsJson.put(name, store)
            }

            val chatJson = exportChatHistory(context)

            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(JSONObject().apply {
                put("app", "WAQT")
                put("schemaVersion", SCHEMA_VERSION)
                put("exportedAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date()))
                if (!account.isNullOrBlank()) put("account", account)
                put("preferences", prefsJson)
                put("chatHistory", chatJson)
                put("media", mediaManifest)
            }.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            appMediaCount = addAppFiles(context, zip, mediaManifest)
            val external = addExternalMedia(context, zip, mediaManifest)
            externalIncluded = external[0]
            externalSkipped = external[1]
        }

        val report = BackupIntegrity.BuildReport(
            includedAppMedia = appMediaCount,
            includedExternalMedia = externalIncluded,
            skippedExternalMedia = externalSkipped
        )
        // mediaManifest is filled after backup.json is written, so rebuild the
        // manifest entry as a second pass entry for readers.
        val finalOut = ByteArrayOutputStream()
        ZipOutputStream(finalOut).use { zip ->
            ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { input ->
                var entry = input.nextEntry
                while (entry != null) {
                    zip.putNextEntry(ZipEntry(entry.name))
                    input.copyTo(zip)
                    zip.closeEntry()
                    entry = input.nextEntry
                }
            }
            zip.putNextEntry(ZipEntry("media-manifest.json"))
            zip.write(mediaManifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(BackupIntegrity.REPORT_ENTRY))
            zip.write(BackupIntegrity.reportJson(report).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return BuildOutcome(finalOut.toByteArray(), report)
    }

    private fun exportChatHistory(context: Context): JSONObject {
        val root = JSONObject()
        try {
            val store = ChatHistoryStore(context)
            val chats = JSONArray()
            for (chat in store.listChats(500)) {
                val chatObj = JSONObject()
                    .put("id", chat.id)
                    .put("title", chat.title)
                    .put("updatedAt", chat.updatedAt)
                val messages = JSONArray()
                for (message in store.getMessages(chat.id)) {
                    messages.put(JSONObject()
                        .put("role", message.role)
                        .put("content", message.content)
                        .put("createdAt", message.createdAt))
                }
                chatObj.put("messages", messages)
                chats.put(chatObj)
            }
            root.put("chats", chats)
        } catch (_: Exception) {
            root.put("chats", JSONArray())
        }
        return root
    }

    private fun addAppFiles(context: Context, zip: ZipOutputStream, manifest: JSONArray): Int {
        var included = 0
        val dirs = mapOf(
            File(context.filesDir, "journal_audio") to "media/journal_audio",
            File(context.filesDir, "chat_images") to "media/chat_images"
        )
        for ((dir, prefix) in dirs) {
            dir.listFiles()?.filter { it.isFile && it.length() > 0 }?.forEach { file ->
                val entry = "$prefix/${file.name}"
                zip.putNextEntry(ZipEntry(entry))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                manifest.put(JSONObject()
                    .put("original", file.absolutePath)
                    .put("entry", entry))
                manifest.put(JSONObject()
                    .put("original", Uri.fromFile(file).toString())
                    .put("entry", entry))
                included++
            }
        }
        context.filesDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("note_photo_") && it.length() > 0 }
            ?.forEach { file ->
                val entry = "media/note_photos/${file.name}"
                zip.putNextEntry(ZipEntry(entry))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                manifest.put(JSONObject()
                    .put("original", file.absolutePath)
                    .put("entry", entry))
                manifest.put(JSONObject()
                    .put("original", Uri.fromFile(file).toString())
                    .put("entry", entry))
                included++
            }
        return included
    }

    /** Best-effort copy of gallery-referenced media (images/audio by content URI). */
    private fun addExternalMedia(context: Context, zip: ZipOutputStream, manifest: JSONArray): IntArray {
        var included = 0
        var skipped = 0
        val uris = linkedSetOf<String>()

        // Journal media references (images by URI).
        val journalMedia = context.getSharedPreferences("waqt_daily_journal_media", Context.MODE_PRIVATE)
        for ((_, raw) in journalMedia.all) {
            val array = try { JSONArray(raw as? String ?: "[]") } catch (_: Exception) { continue }
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val uri = item.optString("uri")
                if (uri.startsWith("content://")) uris.add(uri)
            }
        }
        // Note images.
        val notes = context.getSharedPreferences("waqt_productivity_prefs", Context.MODE_PRIVATE)
            .getString("waqt_notes_json", "[]") ?: "[]"
        try {
            val array = JSONArray(notes)
            for (i in 0 until array.length()) {
                val uri = array.optJSONObject(i)?.optString("imageUri") ?: continue
                if (uri.startsWith("content://")) uris.add(uri)
            }
        } catch (_: Exception) {}

        var index = 0
        for (uriString in uris) {
            val uri = Uri.parse(uriString)
            val bytes = BackupIntegrity.readExternalOrNull(25 * 1024 * 1024) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytes == null) {
                // Missing or unreadable gallery items must never fail the
                // backup, but they are counted so the user gets a warning.
                skipped++
                continue
            }
            val entry = "media/external/ext_${index++}"
            zip.putNextEntry(ZipEntry(entry))
            zip.write(bytes)
            zip.closeEntry()
            manifest.put(JSONObject().put("original", uriString).put("entry", entry))
            included++
        }
        return intArrayOf(included, skipped)
    }

    fun summarize(zipBytes: ByteArray): BackupSummary {
        val (backup, mediaCount) = readEntries(zipBytes)
        val backupJson = JSONObject(String(backup, Charsets.UTF_8))
        val prefs = backupJson.optJSONObject("preferences")
        val productivity = prefs?.optJSONObject("waqt_productivity_prefs")
        val tasks = productivity?.optString("waqt_items_json")?.let {
            try { JSONArray(it).length() } catch (_: Exception) { 0 }
        } ?: 0
        val notes = productivity?.optString("waqt_notes_json")?.let {
            try { JSONArray(it).length() } catch (_: Exception) { 0 }
        } ?: 0
        val journalDays = prefs?.optJSONObject("waqt_daily_journal")?.length() ?: 0
        val chats = backupJson.optJSONObject("chatHistory")?.optJSONArray("chats")?.length() ?: 0
        return BackupSummary(
            exportedAt = backupJson.optString("exportedAt").ifBlank { null },
            account = backupJson.optString("account").ifBlank { null },
            taskCount = tasks,
            noteCount = notes,
            journalDays = journalDays,
            chatCount = chats,
            mediaCount = mediaCount
        )
    }

    /**
     * Applies a user-confirmed restore: preferences (API keys excluded),
     * chat history, mentor memory, and media files with reference rewriting.
     */
    fun restore(context: Context, zipBytes: ByteArray): BackupIntegrity.RestoreReport {
        val (backup, _) = readEntries(zipBytes)
        val root = try { JSONObject(String(backup, Charsets.UTF_8)) }
        catch (e: Exception) { throw RestoreException("ملف النسخة غير صالح") }
        if (root.optString("app") != "WAQT") throw RestoreException("الملف ده مش نسخة وقت")
        val version = root.optInt("schemaVersion", 0)
        if (version > SCHEMA_VERSION) throw RestoreException("النسخة دي من إصدار أحدث من التطبيق")

        val entries = readAllEntries(zipBytes)

        // 1. Media files first so references can point at restored paths.
        val restoredDir = File(context.filesDir, "restored_media").apply { mkdirs() }
        val replacements = mutableMapOf<String, String>()
        var restoredMedia = 0
        var failedMedia = 0
        val countedEntries = HashSet<String>()
        val manifest = entries["media-manifest.json"]?.let {
            try { JSONArray(String(it, Charsets.UTF_8)) } catch (_: Exception) { null }
        }
        if (manifest != null) {
            for (i in 0 until manifest.length()) {
                val item = manifest.optJSONObject(i) ?: continue
                val original = item.optString("original")
                val entry = item.optString("entry")
                val bytes = entries[entry]
                if (bytes == null) {
                    if (entry.isNotBlank() && countedEntries.add(entry)) failedMedia++
                    continue
                }
                try {
                    val target = File(restoredDir, entry.substringAfterLast('/'))
                    target.writeBytes(bytes)
                    if (countedEntries.add(entry)) restoredMedia++
                    if (original.isNotBlank()) {
                        replacements[original] = when {
                            original.startsWith("content://") -> Uri.fromFile(target).toString()
                            else -> target.absolutePath
                        }
                        // Absolute file paths keep their original layout too.
                        if (original.startsWith("/")) {
                            val originalFile = File(original)
                            originalFile.parentFile?.mkdirs()
                            originalFile.writeBytes(bytes)
                        }
                    }
                } catch (_: Exception) {
                    if (countedEntries.add(entry)) failedMedia++
                }
            }
        }

        // 2. Preferences with reference rewriting.
        val prefs = root.optJSONObject("preferences") ?: JSONObject()
        val names = prefs.keys()
        while (names.hasNext()) {
            val name = names.next()
            val store = prefs.optJSONObject(name) ?: continue
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            val keys = store.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (isExcludedKey(key)) continue
                when (val value = store.get(key)) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is String -> {
                        var rewritten: String = value
                        for ((original, replacement) in replacements) {
                            if (rewritten.contains(original)) rewritten = rewritten.replace(original, replacement)
                        }
                        editor.putString(key, rewritten)
                    }
                    is JSONArray -> editor.putString(key, value.toString())
                    else -> {}
                }
            }
            editor.apply()
        }

        // 3. Chat history (merge: keep local chats, add restored ones).
        val chats = root.optJSONObject("chatHistory")?.optJSONArray("chats")
        if (chats != null && chats.length() > 0) {
            try {
                val store = ChatHistoryStore(context)
                val existing = store.listChats(1000).map { it.id }.toSet()
                for (i in 0 until chats.length()) {
                    val chat = chats.optJSONObject(i) ?: continue
                    val chatId = chat.optString("id")
                    if (chatId.isBlank() || existing.contains(chatId)) continue
                    val messages = chat.optJSONArray("messages") ?: continue
                    var newChatId: String? = null
                    for (m in 0 until messages.length()) {
                        val message = messages.optJSONObject(m) ?: continue
                        if (newChatId == null) {
                            newChatId = store.createChat(chat.optString("title").ifBlank { "محادثة" })
                        }
                        store.addMessage(newChatId, message.optString("role"), message.optString("content"))
                    }
                }
            } catch (_: Exception) {}
        }

        val skippedAtBackup = BackupIntegrity.parseReport(
            entries[BackupIntegrity.REPORT_ENTRY]?.let { String(it, Charsets.UTF_8) }
        ).skippedExternalMedia
        return BackupIntegrity.RestoreReport(
            restoredMedia = restoredMedia,
            failedMedia = failedMedia,
            skippedAtBackup = skippedAtBackup
        )
    }

    private fun readEntries(zipBytes: ByteArray): Pair<ByteArray, Int> {
        val entries = readAllEntries(zipBytes)
        val backup = entries["backup.json"] ?: throw RestoreException("ملف النسخة غير صالح")
        return backup to entries.keys.count { it.startsWith("media/") && it != "media-manifest.json" }
    }

    private fun readAllEntries(zipBytes: ByteArray): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.size <= 30 * 1024 * 1024) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }
}
