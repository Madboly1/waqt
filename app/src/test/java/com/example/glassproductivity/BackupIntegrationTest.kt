package com.example.glassproductivity

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileNotFoundException

/**
 * File-backed fake of the system documents provider, so the exact stream
 * code from MainActivity's SAF export/import callbacks can run against
 * content:// URIs on the JVM. Writes land in real temp files; reads come
 * back out of them. No emulator, no device, no user data.
 */
class MemoryDocumentsProvider : ContentProvider() {

    private val docs = HashMap<String, File>()

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment ?: throw FileNotFoundException("unnamed document")
        if (mode.contains("w")) {
            val f = File.createTempFile("fake-saf-", ".json")
            docs[name] = f
            return ParcelFileDescriptor.open(
                f,
                ParcelFileDescriptor.MODE_WRITE_ONLY or
                    ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE
            )
        }
        val f = docs[name] ?: throw FileNotFoundException(name)
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String = "application/json"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    fun bytes(name: String): ByteArray? = docs[name]?.readBytes()
}

/**
 * Code-level verification of the backup export write callback and the
 * restore read/import integration, using the exact statements those
 * callbacks run in MainActivity:
 *  - export: contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
 *  - import: contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
 * What this proves: the bytes that leave the app are exactly
 * exportCloudBackupJson output, and bytes read back from a picked document
 * import correctly, including Arabic/emoji UTF-8 fidelity. What it does not
 * prove: the Compose launcher wiring and the system picker UI itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupIntegrationTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private val authority = "com.example.testdocs"
    private val docName = "waqt-backup-test.json"
    private val docUri: Uri = Uri.parse("content://$authority/document/$docName")

    private lateinit var provider: MemoryDocumentsProvider

    private fun prefs() =
        context.getSharedPreferences("waqt_productivity_prefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs().edit().clear().commit()
        context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        provider = MemoryDocumentsProvider()
        ShadowContentResolver.registerProviderInternal(authority, provider)
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

        val captured = provider.bytes(docName)
        assertNotNull(captured)
        assertEquals(json, String(captured!!, Charsets.UTF_8))
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
        val missing = Uri.parse("content://$authority/document/absent.json")
        val backupText = try {
            context.contentResolver.openInputStream(missing)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
        // The callback treats null/blank as "Could not read backup file" and
        // never calls importCloudBackupJson: nothing is touched.
        assertNull(backupText)
    }
}
