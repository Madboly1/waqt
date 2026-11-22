package com.example.glassproductivity

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupDiagnosticScopeTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val taskJson = """[{"id":"startup-keep-task","title":"KEEP STARTUP TASK","hour":9,"minute":30,"isHabit":false,"hasReminder":false}]"""
    private val noteJson = """[{"id":"startup-keep-note","title":"KEEP STARTUP NOTE","content":"Existing note content"}]"""

    @Before
    fun seedExistingLibrary() {
        context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("onboarding_done", true).putBoolean("ota_auto_check", false).commit()
        context.getSharedPreferences("waqt_productivity_prefs", Context.MODE_PRIVATE).edit().clear()
            .putString("waqt_items_json", taskJson).putString("waqt_notes_json", noteJson).commit()
    }

    private fun activity(diagnostic: Boolean = false): MainActivity {
        val intent = Intent(context, MainActivity::class.java)
        if (diagnostic) intent.putExtra("waqt_startup_diagnostic", true)
        return Robolectric.buildActivity(MainActivity::class.java, intent).create().start().resume().get()
    }

    private fun texts(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(texts(view.getChildAt(i)))
    }

    private fun assertNormalLibrary(activity: MainActivity) {
        assertFalse("normal startup must not enter diagnostics", StartupRecovery.isActive(activity))
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        assertNotNull(root)
        assertTrue("actual task library was not built", texts(root).any { it.contains("KEEP STARTUP TASK") })
        assertFalse(texts(root).any { it.contains("فتح آمن للتشخيص") })
        assertLibraryUnchanged()
    }

    private fun assertLibraryUnchanged() {
        val prefs = context.getSharedPreferences("waqt_productivity_prefs", Context.MODE_PRIVATE)
        assertEquals(taskJson, prefs.getString("waqt_items_json", null))
        assertEquals(noteJson, prefs.getString("waqt_notes_json", null))
    }

    @Test
    fun `ordinary startup still opens the real library`() {
        assertNormalLibrary(activity())
    }

    @Test
    fun `stale unrelated crash never automatically traps ordinary startup`() {
        context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
            .putString("last_crash", "java.lang.IllegalStateException: historical unrelated crash").commit()
        assertNormalLibrary(activity())
    }

    @Test
    fun `explicit diagnostic intent keeps inspection available without editing library or trace`() {
        val crash = "java.lang.IllegalStateException: diagnostic inspection fixture"
        context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit().putString("last_crash", crash).commit()
        val activity = activity(diagnostic = true)
        assertTrue(StartupRecovery.isActive(activity))
        assertTrue(texts(activity.findViewById(android.R.id.content)).any { it.contains("فتح آمن للتشخيص") })
        assertEquals(crash, context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).getString("last_crash", null))
        assertLibraryUnchanged()
    }

    @Test
    fun `repaired legacy focus integer crash still opens library even with diagnostic request`() {
        val crash = "java.lang.ClassCastException: java.lang.Integer cannot be cast to java.lang.Long at ReminderRepository.getActiveFocusState"
        context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit().putString("last_crash", crash).commit()
        context.getSharedPreferences("waqt_productivity_prefs", Context.MODE_PRIVATE).edit()
            .putInt("active_focus_start_ms", 0).putInt("active_focus_total_ms", 1500000)
            .putInt("active_focus_remaining_pause_ms", 1500000).putBoolean("active_focus_running", false).commit()
        assertNormalLibrary(activity(diagnostic = true))
    }
}
