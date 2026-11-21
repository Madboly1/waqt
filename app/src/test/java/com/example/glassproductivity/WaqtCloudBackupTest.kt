package com.example.glassproductivity

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Non-destructive restore proof with disposable records, fully local: no
 * Google account, no Drive. Covers the backup contract (secrets and signed-in
 * identity never leave the device), a simulated reinstall (wipe then restore),
 * chat merge semantics, and rejection of foreign or future backup files.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WaqtCloudBackupTest {

    private val context get() = RuntimeEnvironment.getApplication()

    // Mirror of the backup contract's store list (kept private in WaqtCloudBackup).
    private val storeNames = listOf(
        "waqt_productivity_prefs", "waqt_prefs", "waqt_daily_journal",
        "waqt_daily_journal_media", "waqt_mentor_memory", "waqt_note_categories",
        "is_arabic", "onboarding_done", "rail_offset", "rail_edge", "ota_auto_check"
    )

    @Before
    fun cleanSlate() {
        for (name in storeNames) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        context.deleteDatabase("waqt_chat_history.db")
    }

    private fun seedDisposableData() {
        context.getSharedPreferences("waqt_productivity_prefs", Context.MODE_PRIVATE).edit()
            .putString("waqt_items_json", JSONArray().apply {
                put(JSONObject().put("text", "مهمة أ").put("done", false))
                put(JSONObject().put("text", "مهمة ب").put("done", true))
            }.toString())
            .putString("waqt_notes_json", JSONArray().apply {
                put(JSONObject().put("text", "ملاحظة تجريبية"))
            }.toString())
            .putInt("max_tokens", 500) // ordinary setting: must be backed up
            .putString("gemini_api_key", "AIza-DISPOSABLE") // secret: never backed up
            .putString("vendor_refresh_token", "rt-DISPOSABLE") // token-like: never backed up
            .putString("waqt_google_email", "signedin@example.com") // identity: never backed up
            .commit()
        context.getSharedPreferences("waqt_daily_journal", Context.MODE_PRIVATE).edit()
            .putString("2026-11-20", "يوم تجريبي")
            .commit()
        File(context.filesDir, "note_photo_test1.jpg").writeBytes(byteArrayOf(1, 2, 3, 4, 5))
    }

    private fun backupJson(zipBytes: ByteArray): JSONObject {
        val entries = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes)).use { zip ->
            val map = mutableMapOf<String, ByteArray>()
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory) map[e.name] = zip.readBytes()
                zip.closeEntry()
                e = zip.nextEntry
            }
            map
        }
        return JSONObject(String(entries.getValue("backup.json"), Charsets.UTF_8))
    }

    @Test
    fun secretsAndIdentityNeverEnterTheBackup() {
        seedDisposableData()
        val zip = WaqtCloudBackup.build(context, "signedin@example.com")
        val prefs = backupJson(zip).getJSONObject("preferences")
            .getJSONObject("waqt_productivity_prefs")

        assertFalse(prefs.has("gemini_api_key"))
        assertFalse(prefs.has("vendor_refresh_token"))
        assertFalse(prefs.has("waqt_google_email"))
        assertTrue(prefs.has("max_tokens"))
        assertTrue(prefs.has("waqt_items_json"))

        val summary = WaqtCloudBackup.summarize(zip)
        assertEquals(2, summary.taskCount)
        assertEquals(1, summary.noteCount)
        assertEquals(1, summary.journalDays)
        assertEquals("signedin@example.com", summary.account)
    }

    @Test
    fun wipeThenRestoreIsNonDestructive() {
        seedDisposableData()
        val zip = WaqtCloudBackup.build(context, null)

        // Simulate reinstall: wipe every store and file, then sign in fresh.
        cleanSlate()
        context.getSharedPreferences("waqt_productivity_prefs", Context.MODE_PRIVATE).edit()
            .putString("waqt_google_email", "fresh@example.com")
            .commit()

        val report = WaqtCloudBackup.restore(context, zip)

        val prefs = context.getSharedPreferences("waqt_productivity_prefs", Context.MODE_PRIVATE)
        val tasks = JSONArray(prefs.getString("waqt_items_json", "[]"))
        assertEquals(2, tasks.length())
        assertEquals("مهمة أ", tasks.getJSONObject(0).getString("text"))
        assertEquals(500, prefs.getInt("max_tokens", 0))
        // Identity signed in after the reinstall is never overwritten.
        assertEquals("fresh@example.com", prefs.getString("waqt_google_email", null))
        // Secrets are not resurrected by a restore.
        assertNull(prefs.getString("gemini_api_key", null))
        assertNull(prefs.getString("vendor_refresh_token", null))
        assertEquals("يوم تجريبي",
            context.getSharedPreferences("waqt_daily_journal", Context.MODE_PRIVATE)
                .getString("2026-11-20", null))
        // Media lands back at its original path and in restored_media.
        assertTrue(File(context.filesDir, "note_photo_test1.jpg").exists())
        assertEquals(1, report.restoredMedia)
        assertEquals(0, report.failedMedia)
    }

    @Test
    fun chatMergeKeepsLocalChatsAndRestoredTitles() {
        val store = ChatHistoryStore(context)
        val backupChatId = store.createChat("محادثة من النسخة")
        store.addMessage(backupChatId, "user", "رسالة أولى")
        store.addMessage(backupChatId, "assistant", "رد أول")
        val zip = WaqtCloudBackup.build(context, null)

        // A local chat created after the backup (new local activity).
        val localChatId = store.createChat("محادثة محلية")
        store.addMessage(localChatId, "user", "سؤال محلي")
        // Remove only the backed-up chat, simulating a partial local state.
        store.deleteChat(backupChatId)

        WaqtCloudBackup.restore(context, zip)

        val after = ChatHistoryStore(context)
        val ids = after.listChats(50).map { it.id }
        assertTrue(ids.contains(localChatId))
        assertTrue(ids.contains(backupChatId)) // original id preserved
        assertEquals(2, after.getMessages(backupChatId).size)
        val restoredTitle = after.listChats(50).first { it.id == backupChatId }.title
        assertEquals("محادثة من النسخة", restoredTitle)
        assertEquals(1, after.getMessages(localChatId).size)
        store.close()
        after.close()
    }

    @Test
    fun restoringTheSameBackupTwiceAddsNothing() {
        val store = ChatHistoryStore(context)
        val chatId = store.createChat("محادثة مكررة")
        store.addMessage(chatId, "user", "مرة واحدة")
        seedDisposableData()
        val zip = WaqtCloudBackup.build(context, null)

        cleanSlate()
        WaqtCloudBackup.restore(context, zip)
        val afterFirst = ChatHistoryStore(context)
        val chatsAfterFirst = afterFirst.listChats(50).size
        val messagesAfterFirst = afterFirst.getMessages(chatId).size

        WaqtCloudBackup.restore(context, zip)
        val afterSecond = ChatHistoryStore(context)
        assertEquals(chatsAfterFirst, afterSecond.listChats(50).size)
        assertEquals(messagesAfterFirst, afterSecond.getMessages(chatId).size)
        store.close()
        afterFirst.close()
        afterSecond.close()
    }

    @Test
    fun rejectsForeignFutureAndCorruptBackups() {
        seedDisposableData()
        val good = WaqtCloudBackup.build(context, null)
        val root = backupJson(good)

        // Foreign app tag.
        val foreign = root.copy().put("app", "OTHER").toString().toByteArray()
        // Future schema.
        val future = root.copy().put("schemaVersion", WaqtCloudBackup.SCHEMA_VERSION + 1)
            .toString().toByteArray()

        for (bad in listOf(foreign, future, byteArrayOf(9, 9, 9), "خربان".toByteArray())) {
            try {
                WaqtCloudBackup.restore(context, repack(bad))
                fail("expected RestoreException")
            } catch (_: WaqtCloudBackup.RestoreException) {
            } catch (_: Exception) {
                // Corrupt zips may fail at unzip time; both paths must refuse.
            }
        }
    }

    /** Wraps a replacement backup.json in a minimal valid zip. */
    private fun repack(backupBytes: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("backup.json"))
            zip.write(backupBytes)
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun JSONObject.copy(): JSONObject = JSONObject(this.toString())
}
