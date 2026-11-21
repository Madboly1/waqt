package com.example.glassproductivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar

/**
 * Regression for the reviewed reminder-timing defect: a task carrying a future
 * due date (dueDateEpochDay) must ring on that date at its set time, not
 * today/tomorrow. Habits and undated tasks keep their existing behavior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmSchedulerFutureDueDateTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private fun taskItem(hour: Int, minute: Int, dueEpochDay: Long, isHabit: Boolean = false) = ReminderItem(
        id = "test-${System.nanoTime()}",
        title = "Test task",
        isHabit = isHabit,
        hour = hour,
        minute = minute,
        dueDateEpochDay = dueEpochDay
    )

    /** A minute-truncated calendar [plusMinutes] from now. */
    private fun nearFutureCalendar(plusMinutes: Int): Calendar =
        Calendar.getInstance().apply {
            add(Calendar.MINUTE, plusMinutes)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    @Test
    fun `task with future due date rings on that date not tomorrow`() {
        val dueDay = AlarmScheduler.currentEpochDay() + 3
        val item = taskItem(hour = 9, minute = 30, dueEpochDay = dueDay)

        val scheduled = AlarmScheduler.scheduleNextOccurrence(context, item)

        val expected = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 3)
        }
        assertEquals("alarm must land on the task's due date", expected.timeInMillis, scheduled)
    }

    @Test
    fun `task with next-day due date rings tomorrow not today`() {
        val dueDay = AlarmScheduler.currentEpochDay() + 1
        val item = taskItem(hour = 9, minute = 30, dueEpochDay = dueDay)

        val scheduled = AlarmScheduler.scheduleNextOccurrence(context, item)

        val expected = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        assertEquals("alarm must land on the next-day due date", expected.timeInMillis, scheduled)
    }

    @Test
    fun `undated task still rings today or tomorrow`() {
        val near = nearFutureCalendar(2)
        val item = taskItem(
            hour = near.get(Calendar.HOUR_OF_DAY),
            minute = near.get(Calendar.MINUTE),
            dueEpochDay = -1L
        )

        val scheduled = AlarmScheduler.scheduleNextOccurrence(context, item)

        val delta = scheduled - near.timeInMillis
        assertTrue("undated task should keep today/tomorrow behavior, delta=$delta", delta in -60_000..60_000)
    }

    @Test
    fun `habit ignores future due date field`() {
        val near = nearFutureCalendar(2)
        val item = taskItem(
            hour = near.get(Calendar.HOUR_OF_DAY),
            minute = near.get(Calendar.MINUTE),
            dueEpochDay = AlarmScheduler.currentEpochDay() + 3,
            isHabit = true
        )

        val scheduled = AlarmScheduler.scheduleNextOccurrence(context, item)

        val delta = scheduled - near.timeInMillis
        assertTrue("habit must not jump to the due-date field, delta=$delta", delta in -60_000..60_000)
    }
}
