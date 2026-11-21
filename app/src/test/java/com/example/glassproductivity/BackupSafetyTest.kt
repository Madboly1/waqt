package com.example.glassproductivity

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Safety proofs for local backup restore paths, fully local and disposable:
 * malformed or truncated JSON backups are rejected with zero writes, valid
 * backups round-trip exactly, replaced data is snapshotted first, absent
 * sections keep existing data, and ZIP media restore is confined to the
 * app's own directories.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupSafetyTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private fun prefs() =
        context.getSharedPreferences("waqt_productivity_prefs", Context.MODE_PRIVATE)

    @Before
    fun cleanSlate() {
        prefs().edit().clear().commit()
        context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun item(id: String, title: String) = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("isHabit", false)
        put("hour", 9)
        put("minute", 30)
    }

    private fun note(id: String, title: String) = JSONObject().apply {
        put("id", id)
        put("title", title)
    }

    private fun backupJson(build: JSONObject.() -> Unit) = JSONObject().apply {
        put("app", "WAQT")
        put("schemaVersion", 4)
        build()
    }.toString()

    @Test
    fun `truncated backup is rejected and current items are untouched`() {
        val good = JSONArray().apply { put(item("keep-1", "مهمة سليمة")) }.toString()
        prefs().edit().putString("waqt_items_json", good).commit()

        val truncated = "{\"app\":\"WAQT\",\"items\":[{\"id\":\"x\",\"title\":\"y\""
        assertFalse(ReminderRepository.importCloudBackupJson(context, truncated))
        assertEquals(good, prefs().getString("waqt_items_json", null))
    }

    @Test
    fun `malformed section rejects whole file with no partial writes`() {
        val goodItems = JSONArray().apply { put(item("keep-1", "مهمة سليمة")) }.toString()
        val goodNotes = JSONArray().apply { put(note("n1", "ملاحظة")) }.toString()
        prefs().edit()
            .putString("waqt_items_json", goodItems)
            .putString("waqt_notes_json", goodNotes)
            .commit()

        val backup = backupJson {
            put("items", JSONArray().apply { put(item("new-1", "جديدة")) })
            // Missing required id: the notes loader would choke on this later.
            put("notes", JSONArray().apply { put(JSONObject().put("title", "بلا معرف")) })
        }
        assertFalse(ReminderRepository.importCloudBackupJson(context, backup))
        assertEquals(goodItems, prefs().getString("waqt_items_json", null))
        assertEquals(goodNotes, prefs().getString("waqt_notes_json", null))
    }

    @Test
    fun `wrong typed section is treated as malformed not absent`() {
        val good = JSONArray().apply { put(item("keep-1", "مهمة سليمة")) }.toString()
        prefs().edit().putString("waqt_items_json", good).commit()

        val backup = backupJson { put("items", "not an array") }
        assertFalse(ReminderRepository.importCloudBackupJson(context, backup))
        assertEquals(good, prefs().getString("waqt_items_json", null))
    }

    @Test
    fun `valid roundtrip restores exact items`() {
        val original = JSONArray().apply {
            put(item("a-1", "مهمة أ"))
            put(item("b-2", "مهمة ب"))
        }.toString()
        prefs().edit().putString("waqt_items_json", original).commit()

        val exported = ReminderRepository.exportCloudBackupJson(context)
        // Simulate post-backup user changes that the restore must undo.
        prefs().edit().putString("waqt_items_json", JSONArray().toString()).commit()

        assertTrue(ReminderRepository.importCloudBackupJson(context, exported))
        assertEquals(
            JSONArray(original).toString(),
            JSONArray(prefs().getString("waqt_items_json", "[]")).toString()
        )
    }

    @Test
    fun `restore writes a recovery snapshot of replaced data first`() {
        val before = JSONArray().apply { put(item("before-1", "قبل الاستعادة")) }.toString()
        prefs().edit().putString("waqt_items_json", before).commit()

        val backup = backupJson {
            put("items", JSONArray().apply { put(item("after-1", "بعد الاستعادة")) })
        }
        assertTrue(ReminderRepository.importCloudBackupJson(context, backup))
        assertTrue(prefs().getString("waqt_items_json", "")!!.contains("after-1"))

        val dir = File(context.filesDir, "restore_snapshots")
        val snapshots = dir.listFiles()
            ?.filter { it.name.startsWith("waqt-pre-json-restore-") } ?: emptyList()
        assertEquals(1, snapshots.size)
        assertTrue(snapshots[0].readText().contains("before-1"))
    }

    @Test
    fun `absent sections keep existing data`() {
        val notes = JSONArray().apply { put(note("n1", "ملاحظة")) }.toString()
        prefs().edit().putString("waqt_notes_json", notes).commit()

        val backup = backupJson {
            put("items", JSONArray().apply { put(item("i-1", "مهمة")) })
        }
        assertTrue(ReminderRepository.importCloudBackupJson(context, backup))
        assertEquals(notes, prefs().getString("waqt_notes_json", null))
    }

    @Test
    fun `settings section still restores`() {
        val backup = backupJson {
            put("items", JSONArray().apply { put(item("i-1", "مهمة")) })
            put("settings", JSONObject().put(ReminderRepository.KEY_DARK_MODE, false))
        }
        assertTrue(ReminderRepository.importCloudBackupJson(context, backup))
        assertFalse(
            context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
                .getBoolean(ReminderRepository.KEY_DARK_MODE, true)
        )
    }

    @Test
    fun `snapshot failure aborts restore before any write`() {
        val good = JSONArray().apply { put(item("keep-1", "مهمة سليمة")) }.toString()
        prefs().edit().putString("waqt_items_json", good).commit()
        val backup = backupJson {
            put("items", JSONArray().apply { put(item("new-1", "جديدة")) })
        }

        val dir = context.filesDir
        dir.setWritable(false)
        try {
            assertFalse(ReminderRepository.importCloudBackupJson(context, backup))
            assertEquals(good, prefs().getString("waqt_items_json", null))
        } finally {
            dir.setWritable(true)
        }
    }

    private fun zipWith(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun zipBackup(manifest: JSONArray): ByteArray {
        val root = JSONObject().apply {
            put("app", "WAQT")
            put("schemaVersion", WaqtCloudBackup.SCHEMA_VERSION)
            put("preferences", JSONObject())
            put("media", JSONArray())
        }.toString().toByteArray(Charsets.UTF_8)
        return zipWith(
            mapOf(
                "backup.json" to root,
                "media-manifest.json" to manifest.toString().toByteArray(Charsets.UTF_8),
                "media/evil.txt" to byteArrayOf(9, 9, 9),
                "media/ok.jpg" to byteArrayOf(1, 2, 3)
            )
        )
    }

    @Test
    fun `zip media restore refuses paths outside app directories`() {
        val outside = File(context.cacheDir, "evil_outside.txt").absolutePath
        val manifest = JSONArray().apply {
            put(JSONObject().put("original", outside).put("entry", "media/evil.txt"))
        }
        WaqtCloudBackup.restore(context, zipBackup(manifest))

        assertFalse(File(context.cacheDir, "evil_outside.txt").exists())
        // The confined copy still lands in restored_media for reference remapping.
        assertTrue(File(context.filesDir, "restored_media/evil.txt").exists())
    }

    @Test
    fun `zip media restore still writes inside the app files directory`() {
        val inside = File(context.filesDir, "note_media/ok.jpg").absolutePath
        val manifest = JSONArray().apply {
            put(JSONObject().put("original", inside).put("entry", "media/ok.jpg"))
        }
        WaqtCloudBackup.restore(context, zipBackup(manifest))

        assertTrue(File(context.filesDir, "note_media/ok.jpg").exists())
        assertTrue(File(context.filesDir, "restored_media/ok.jpg").exists())
    }
}
