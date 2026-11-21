package com.example.glassproductivity

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Production-wiring verification for the SAF backup launchers.
 *
 * Unlike BackupIntegrationTest (which re-executes copies of the callback
 * statements), this test instantiates the real MainActivity, launches the
 * real registered ActivityResultLaunchers, and delivers SAF results through
 * the real ActivityResultRegistry via ShadowActivity.receiveResult, so the
 * code under test is the actual production callback lambdas registered in
 * MainActivity - not copies.
 *
 * Proves:
 *  - export: backupExportLauncher's registered callback consumes
 *    pendingBackupJson and writes those exact bytes to the picked URI.
 *  - import: backupImportLauncher's registered callback reads the picked
 *    URI, shows the restore confirmation dialog, and its positive button
 *    runs the real import, replacing current items with the backup.
 *
 * Does not prove: the SAF/DocumentsUI picker itself, a real documents
 * provider, Drive backup, or any on-device behavior outside Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupWiringTest {

    private lateinit var activity: MainActivity

    private fun prefs() =
        activity.getSharedPreferences("waqt_productivity_prefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        prefs().edit().clear().commit()
        activity.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        activity.filesDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <I> launcherOf(name: String): ActivityResultLauncher<I> {
        val f = MainActivity::class.java.getDeclaredField(name)
        f.isAccessible = true
        return f.get(activity) as ActivityResultLauncher<I>
    }

    private fun pendingJson(): String? {
        val f = MainActivity::class.java.getDeclaredField("pendingBackupJson")
        f.isAccessible = true
        return f.get(activity) as String?
    }

    private fun setPendingJson(value: String?) {
        val f = MainActivity::class.java.getDeclaredField("pendingBackupJson")
        f.isAccessible = true
        f.set(activity, value)
    }

    private fun markerItem() = JSONObject().apply {
        put("id", "wiring_marker")
        put("title", "WIRING_MARKER_TASK")
        put("isHabit", false)
        put("hour", 8)
        put("minute", 0)
    }

    @Test
    fun `export launcher production callback writes exact backup json to picked uri`() {
        val uri = Uri.parse("content://com.example.testdocs/document/export-target.json")
        val out = ByteArrayOutputStream()
        shadowOf(activity.contentResolver).registerOutputStreamSupplier(uri) { out }

        val json = ReminderRepository.exportCloudBackupJson(activity)
        setPendingJson(json)

        launcherOf<String>("backupExportLauncher").launch("waqt-backup-test.json")

        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("export launcher did not start an SAF CreateDocument intent", started)
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(uri))

        assertEquals(
            "bytes written by the production export callback differ from pending backup json",
            json, out.toString(Charsets.UTF_8.name())
        )
        assertNull("pendingBackupJson must be consumed by the callback", pendingJson())
    }

    @Test
    fun `export launcher production callback ignores cancelled picker`() {
        val uri = Uri.parse("content://com.example.testdocs/document/export-cancelled.json")
        val out = ByteArrayOutputStream()
        shadowOf(activity.contentResolver).registerOutputStreamSupplier(uri) { out }

        val json = ReminderRepository.exportCloudBackupJson(activity)
        setPendingJson(json)

        launcherOf<String>("backupExportLauncher").launch("waqt-backup-test.json")
        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("export launcher did not start an SAF CreateDocument intent", started)
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_CANCELED, null)

        assertEquals("nothing must be written on picker cancel", 0, out.size())
        assertNull("pendingBackupJson must still be consumed", pendingJson())
    }

    @Test
    fun `import launcher production callback restores backup through confirm dialog`() {
        val baseline = ReminderRepository.exportCloudBackupJson(activity)

        val items = JSONArray(prefs().getString("waqt_items_json", "[]"))
        items.put(markerItem())
        prefs().edit().putString("waqt_items_json", items.toString()).commit()
        assertTrue(ReminderRepository.getAllItems(activity).any { it.title == "WIRING_MARKER_TASK" })

        val uri = Uri.parse("content://com.example.testdocs/document/import-source.json")
        shadowOf(activity.contentResolver).registerInputStreamSupplier(uri) {
            ByteArrayInputStream(baseline.toByteArray(Charsets.UTF_8))
        }

        launcherOf<Array<String>>("backupImportLauncher").launch(arrayOf("application/json"))

        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("import launcher did not start an SAF OpenDocument intent", started)
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(uri))

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("production import callback did not show the restore confirmation dialog", dialog)
        assertTrue(
            "marker survived before confirmation - unexpected",
            ReminderRepository.getAllItems(activity).any { it.title == "WIRING_MARKER_TASK" }
        )

        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()

        assertFalse(
            "marker survived import - production callback did not run the real import",
            ReminderRepository.getAllItems(activity).any { it.title == "WIRING_MARKER_TASK" }
        )
    }

    @Test
    fun `import launcher production callback keeps data when dialog cancelled`() {
        val items = JSONArray(prefs().getString("waqt_items_json", "[]"))
        items.put(markerItem())
        prefs().edit().putString("waqt_items_json", items.toString()).commit()

        val baseline = ReminderRepository.exportCloudBackupJson(activity)
        val uri = Uri.parse("content://com.example.testdocs/document/import-cancel.json")
        shadowOf(activity.contentResolver).registerInputStreamSupplier(uri) {
            ByteArrayInputStream(baseline.toByteArray(Charsets.UTF_8))
        }

        launcherOf<Array<String>>("backupImportLauncher").launch(arrayOf("application/json"))
        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("import launcher did not start an SAF OpenDocument intent", started)
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(uri))

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("production import callback did not show the restore confirmation dialog", dialog)
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()

        assertTrue(
            "marker lost after dialog cancel - import ran without confirmation",
            ReminderRepository.getAllItems(activity).any { it.title == "WIRING_MARKER_TASK" }
        )
    }
}
