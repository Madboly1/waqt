package com.example.glassproductivity

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedNumericPreferencesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("waqt_productivity_prefs", Context.MODE_PRIVATE)
    private val settings get() = context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
    private val knownTrace = "java.lang.RuntimeException caused by java.lang.ClassCastException: java.lang.Integer cannot be cast to java.lang.Long at com.example.glassproductivity.ReminderRepository.getActiveFocusState"
    @Before fun reset() { prefs.edit().clear().commit(); settings.edit().clear().commit() }

    @Test fun narrowedFocusIntegersAreReadExactlyWithoutChangingAnyStoredValueOrType() {
        prefs.edit().putInt("active_focus_start_ms", 0).putInt("active_focus_total_ms", 1500000)
            .putInt("active_focus_remaining_pause_ms", 42000).putBoolean("active_focus_running", false)
            .putString("waqt_items_json", "KEEP TASKS").putString("waqt_notes_json", "KEEP NOTES").commit()
        val before = prefs.all.toMap()
        repeat(3) {
            val state = ReminderRepository.getActiveFocusState(context)
            assertEquals(0L, state.getLong("startMs")); assertEquals(1500000L, state.getLong("totalMs"))
            assertEquals(42000L, state.getLong("remainingPauseMs"))
            assertEquals(before, prefs.all)
            assertTrue(prefs.all["active_focus_total_ms"] is Int)
        }
    }
    @Test fun originalLongValuesAndMissingDefaultsRemainExact() {
        assertEquals(1500000L, ReminderRepository.getActiveFocusState(context).getLong("totalMs"))
        prefs.edit().putLong("active_focus_start_ms", Long.MAX_VALUE)
            .putLong("active_focus_total_ms", Long.MIN_VALUE).putLong("active_focus_remaining_pause_ms", 19L).commit()
        val before = prefs.all.toMap(); val state = ReminderRepository.getActiveFocusState(context)
        assertEquals(Long.MAX_VALUE, state.getLong("startMs")); assertEquals(Long.MIN_VALUE, state.getLong("totalMs"))
        assertEquals(before, prefs.all)
    }
    @Test fun narrowedEpochDayDoesNotResetTodaysFocusMinutes() {
        prefs.edit().putInt("waqt_focus_epoch_day", ReminderRepository.currentEpochDay().toInt())
            .putInt("waqt_today_focus_minutes", 37).commit()
        val before = prefs.all.toMap()
        assertEquals(37, ReminderRepository.getTodayFocusMinutes(context))
        assertEquals(before, prefs.all)
    }
    @Test fun railIntegerAndFloatReadWithoutStoredRewrite() {
        settings.edit().putInt("rail_offset", 1).commit()
        val before = settings.all.toMap()
        assertEquals(1f, SavedNumericPreferences.float(settings, "rail_offset", .45f), 0f)
        assertEquals(before, settings.all)
        settings.edit().putFloat("rail_offset", .7f).commit()
        assertEquals(.7f, SavedNumericPreferences.float(settings, "rail_offset", .45f), 0f)
    }
    @Test fun wrongStringsAreNotGuessedOrReplaced() {
        prefs.edit().putString("active_focus_start_ms", "0").commit()
        val before = prefs.all.toMap()
        try { ReminderRepository.getActiveFocusState(context); fail("wrong type must not be silently parsed") }
        catch (_: ClassCastException) { }
        assertEquals(before, prefs.all)
        assertFalse(StartupRecovery.canContinueAfterHandledFocusCrash(context, knownTrace))
    }
    @Test fun onlyDiagnosedHandledTraceCanBypassRecoveryAndTraceStaysStored() {
        settings.edit().putString("last_crash", knownTrace).commit()
        prefs.edit().putInt("active_focus_start_ms", 0).commit()
        assertTrue(StartupRecovery.canContinueAfterHandledFocusCrash(context, knownTrace))
        assertFalse(StartupRecovery.canContinueAfterHandledFocusCrash(context, "other unknown startup failure"))
        assertFalse(StartupRecovery.canContinueAfterHandledFocusCrash(context, knownTrace.replace("getActiveFocusState", "otherFunction")))
        assertEquals(knownTrace, settings.getString("last_crash", null))
    }
}
