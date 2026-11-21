package com.example.glassproductivity

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Code-level verification of the backup export write callback and the
 * restore read/import integration, using the exact statements those
 * callbacks run in MainActivity, with Robolectric's content-resolver shadow
 * standing in for the system documents provider:
 *  - export: contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
 *  - import: contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
 * Proves: the bytes leaving through the export statement equal
 * exportCloudBackupJson output exactly, and bytes read back through the
 * import statement restore correctly, including Arabic/emoji UTF-8
 * fidelity. Does not prove: the SAF picker UI, the Compose launcher wiring,
 * or a real on-device documents provider.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupIntegrationTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private val docUri: Uri = Uri.parse("content://com.example.testdocs/document/waqt-backup-test.json")

    private lateinit var writtenBytes: ByteArrayOutputStream

    private fun prefs() =
        context.getSharedPreferences("waqt_productivity_prefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs().edit().clear().commit()
        context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        writtenBytes = ByteArrayOutputStream()
        val shadow = shadowOf(context.contentResolver)
        shadow.registerOutputStreamSupplier(docUri) { writtenBytes }
        shadow.registerInputStreamSupplier(docUri) { ByteArrayInputStream(writtenBytes.toByteArray()) }
    }

    private fun item(id: String, title: String, hour: Int, minute: Int) = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("isHabit", false)
        put("hour", hour)
        put("minute", minute)
    }

    @Test
    fun `export write callback delivers exact export bytes`() {
        prefs().edit().putString(
            "waqt_items_json",
            JSONArray().apply { put(item("t1", "مهمة", 9, 30)) }.toString()
        ).commit()

        val json = ReminderRepository.exportCloudBackupJson(context)
        // Exact write statement from backupExportLauncher's callback.
        context.contentResolver.openOutputStream(docUri)?.use {
            it.write(json.toByteArray(Charsets.UTF_8))
        }

        assertEquals(json, writtenBytes.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `export then restore through the callback stream paths roundtrips data`() {
        val originalTitle = "مهمة بالعربية 🕌"
        prefs().edit().putString(
            "waqt_items_json",
            JSONArray().apply { put(item("t1", originalTitle, 9, 30)) }.toString()
        ).commit()

        // Export exactly as the app does.
        val json = ReminderRepository.exportCloudBackupJson(context)
        context.contentResolver.openOutputStream(docUri)?.use {
            it.write(json.toByteArray(Charsets.UTF_8))
        }

        // Simulate the user wiping their data (raw key gone; getAllItems
        // would reseed defaults, so check the stored key itself).
        prefs().edit().clear().commit()
        assertNull(prefs().getString("waqt_items_json", null))

        // Exact read statement from backupImportLauncher's callback.
        val backupText = try {
            context.contentResolver.openInputStream(docUri)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
        assertFalse(backupText.isNullOrBlank())

        assertTrue(ReminderRepository.importCloudBackupJson(context, backupText!!))
        val restored = ReminderRepository.getAllItems(context)
        assertEquals(1, restored.size)
        assertEquals("t1", restored[0].id)
        assertEquals(originalTitle, restored[0].title)
        assertEquals(9, restored[0].hour)
        assertEquals(30, restored[0].minute)
    }

    @Test
    fun `restore callback read of a missing document fails closed`() {
        val missing = Uri.parse("content://com.example.testdocs/document/absent.json")
        val backupText = try {
            context.contentResolver.openInputStream(missing)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
        // The callback treats null/blank as "Could not read backup file" and
        // never calls importCloudBackupJson: nothing is touched.
        assertNull(backupText)
        assertNull(prefs().getString("waqt_items_json", null))
    }
}
