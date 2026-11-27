package com.example.glassproductivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReviewHardeningTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `day gap is 1 across a non-leap year end`() {
        val dec31 = 2026L * 366 + 365
        val jan1 = 2027L * 366 + 1
        assertEquals(1L, ReminderRepository.epochDaysBetween(dec31, jan1))
    }

    @Test
    fun `day gap is 1 across a leap year end`() {
        val dec31 = 2028L * 366 + 366
        val jan1 = 2029L * 366 + 1
        assertEquals(1L, ReminderRepository.epochDaysBetween(dec31, jan1))
    }

    @Test
    fun `day gap inside a year is unchanged`() {
        assertEquals(3L, ReminderRepository.epochDaysBetween(2026L * 366 + 100, 2026L * 366 + 103))
    }

    @Test
    fun `corrupt items json is preserved and does not crash`() {
        val prefs = context.getSharedPreferences("waqt_productivity_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("waqt_items_json", "{not json").commit()
        val items = ReminderRepository.getAllItems(context)
        assertTrue(items.isEmpty())
        assertEquals("{not json", prefs.getString("waqt_items_json_unreadable_backup", null))
    }

    @Test
    fun `one bad item does not hide the good ones`() {
        val prefs = context.getSharedPreferences("waqt_productivity_prefs", android.content.Context.MODE_PRIVATE)
        val raw = """[{"id":"a","title":"ok","hour":9,"minute":0},{"title":"missing id"}]"""
        prefs.edit().putString("waqt_items_json", raw).commit()
        val items = ReminderRepository.getAllItems(context)
        assertEquals(listOf("a"), items.map { it.id })
        assertEquals(raw, prefs.getString("waqt_items_json_unreadable_backup", null))
    }
}
