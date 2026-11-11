package com.example.glassproductivity

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class ReminderItem(
    val id: String,
    val title: String,
    val isHabit: Boolean,
    val hour: Int,
    val minute: Int,
    val streakCount: Int = 0,
    val streakGoal: Int = 7,
    val priority: String = "medium", // low, medium, high
    val category: String = "work",
    val lastCompletedEpochDay: Long = -1L,
    val isCompletedToday: Boolean = false,
    val description: String = "",
    val dueDateEpochDay: Long = -1L,
    val dueDateString: String = "",
    val expectedMinutes: Int = 25,
    val actualFocusedMinutes: Int = 0,
    val focusSessionsCount: Int = 0,
    val recurrence: String = "daily", // daily, weekly, custom_days
    val customDaysCsv: String = "1,2,3,4,5,6,7",
    val targetValue: Int = 1,
    val currentProgressToday: Int = 0,
    val targetUnit: String = "",
    val longestStreak: Int = 0,
    val isPaused: Boolean = false,
    val hasReminder: Boolean = true,
    val completedDaysHistoryCsv: String = "",
    val completionPercentage: Int = 0 // نسبة إنجاز المهمة (0% إلى 100%)
)

data class NoteItem(
    val id: String,
    val title: String,
    val content: String,
    val category: String = "general",
    val colorHex: String = "#142A2E",
    val isPinned: Boolean = false,
    val timestampMillis: Long = System.currentTimeMillis(),
    val audioPath: String = "",
    val imageUri: String = ""
)

data class HabitCategoryItem(
    val id: String,
    val name: String,
    val icon: String
)

data class FocusSessionRecord(
    val id: String,
    val taskId: String?,
    val taskTitle: String,
    val durationMinutes: Int,
    val timestampMillis: Long,
    val epochDay: Long
)

object ReminderRepository {
    private const val PREFS_NAME = "waqt_productivity_prefs"
    private const val KEY_ITEMS = "waqt_items_json"
    private const val KEY_NOTES = "waqt_notes_json"
    private const val KEY_HABIT_CATEGORIES = "waqt_habit_categories_json"
    private const val KEY_FOCUS_MINUTES = "waqt_today_focus_minutes"
    private const val KEY_FOCUS_EPOCH_DAY = "waqt_focus_epoch_day"
    private const val KEY_FOCUS_HISTORY = "waqt_focus_history_json"
    private const val KEY_GOOGLE_EMAIL = "waqt_google_email"
    private const val KEY_CUSTOM_QUOTES = "waqt_custom_quotes_json"
    private const val KEY_PRE_ALERT_MINUTES = "waqt_pre_alert_minutes"
    private const val KEY_RINGTONE_URI = "waqt_ringtone_uri"

    const val KEY_DARK_MODE = "pref_dark_mode"
    const val KEY_ACCENT_COLOR = "pref_accent_color"
    const val KEY_DEFAULT_FOCUS_MINS = "pref_default_focus_mins"
    const val KEY_DEFAULT_BREAK_MINS = "pref_default_break_mins"
    const val KEY_DAILY_FOCUS_GOAL_MINS = "pref_daily_focus_goal_mins"
    const val KEY_DEFAULT_SOUND_INDEX = "pref_default_sound_index"
    const val KEY_SOUND_VOLUME = "pref_sound_volume"
    const val KEY_TICK_TOCK_ENABLED = "pref_tick_tock_enabled"
    const val KEY_NOTIF_TASKS = "pref_notif_tasks"
    const val KEY_NOTIF_HABITS = "pref_notif_habits"
    const val KEY_NOTIF_SESSION_END = "pref_notif_session_end"
    const val KEY_NOTIF_SUMMARIES = "pref_notif_summaries"
    const val KEY_AUTO_FLOAT = "pref_auto_float"
    const val KEY_QUIET_HOURS = "pref_quiet_hours"

    private const val KEY_ACTIVE_FOCUS_START_MS = "active_focus_start_ms"
    private const val KEY_ACTIVE_FOCUS_TOTAL_MS = "active_focus_total_ms"
    private const val KEY_ACTIVE_FOCUS_REMAINING_PAUSE_MS = "active_focus_remaining_pause_ms"
    private const val KEY_ACTIVE_FOCUS_RUNNING = "active_focus_running"
    private const val KEY_ACTIVE_FOCUS_TASK_ID = "active_focus_task_id"
    private const val KEY_ACTIVE_FOCUS_IS_BREAK = "active_focus_is_break"

    fun currentEpochDay(): Long {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        return (year * 366L) + dayOfYear
    }

    // =========================================================================
    // إدارة المهام والعادات (Tasks & Habits)
    // =========================================================================

    fun getAllItems(context: Context): MutableList<ReminderItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_ITEMS, null) ?: return seedDefaultItems(context)
        val list = mutableListOf<ReminderItem>()
        val todayEpoch = currentEpochDay()

        try {
            val array = JSONArray(rawJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val lastCompleted = obj.optLong("lastCompletedEpochDay", -1L)
                val isHabit = obj.optBoolean("isHabit", false)
                val isPaused = obj.optBoolean("isPaused", false)
                val recurrence = obj.optString("recurrence", if (isHabit) "daily" else "once")
                val customDaysCsv = obj.optString("customDaysCsv", "1,2,3,4,5,6,7")
                var streak = obj.optInt("streakCount", 0)
                val longest = obj.optInt("longestStreak", streak)

                if (isHabit && !isPaused && lastCompleted != -1L && (todayEpoch - lastCompleted) > 1L) {
                    if (recurrence == "daily") {
                        streak = 0
                    }
                }

                val completedToday = (lastCompleted == todayEpoch) || (obj.optBoolean("isCompletedToday", false) && lastCompleted == todayEpoch)
                val progressToday = if (completedToday) obj.optInt("targetValue", 1) else obj.optInt("currentProgressToday", 0)
                val compPct = if (completedToday) 100 else obj.optInt("completionPercentage", 0)

                list.add(
                    ReminderItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        isHabit = isHabit,
                        hour = obj.getInt("hour"),
                        minute = obj.getInt("minute"),
                        streakCount = streak,
                        streakGoal = obj.optInt("streakGoal", 7),
                        priority = obj.optString("priority", "medium"),
                        category = obj.optString("category", "work"),
                        lastCompletedEpochDay = lastCompleted,
                        isCompletedToday = completedToday,
                        description = obj.optString("description", ""),
                        dueDateEpochDay = obj.optLong("dueDateEpochDay", todayEpoch),
                        dueDateString = obj.optString("dueDateString", ""),
                        expectedMinutes = obj.optInt("expectedMinutes", 25),
                        actualFocusedMinutes = obj.optInt("actualFocusedMinutes", 0),
                        focusSessionsCount = obj.optInt("focusSessionsCount", 0),
                        recurrence = recurrence,
                        customDaysCsv = customDaysCsv,
                        targetValue = obj.optInt("targetValue", 1).coerceAtLeast(1),
                        currentProgressToday = progressToday,
                        targetUnit = obj.optString("targetUnit", ""),
                        longestStreak = longest.coerceAtLeast(streak),
                        isPaused = isPaused,
                        hasReminder = obj.optBoolean("hasReminder", true),
                        completedDaysHistoryCsv = obj.optString("completedDaysHistoryCsv", ""),
                        completionPercentage = compPct
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun saveAllItems(context: Context, items: List<ReminderItem>) {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("isHabit", item.isHabit)
                put("hour", item.hour)
                put("minute", item.minute)
                put("streakCount", item.streakCount)
                put("streakGoal", item.streakGoal)
                put("priority", item.priority)
                put("category", item.category)
                put("lastCompletedEpochDay", item.lastCompletedEpochDay)
                put("isCompletedToday", item.isCompletedToday)
                put("description", item.description)
                put("dueDateEpochDay", item.dueDateEpochDay)
                put("dueDateString", item.dueDateString)
                put("expectedMinutes", item.expectedMinutes)
                put("actualFocusedMinutes", item.actualFocusedMinutes)
                put("focusSessionsCount", item.focusSessionsCount)
                put("recurrence", item.recurrence)
                put("customDaysCsv", item.customDaysCsv)
                put("targetValue", item.targetValue)
                put("currentProgressToday", item.currentProgressToday)
                put("targetUnit", item.targetUnit)
                put("longestStreak", item.longestStreak.coerceAtLeast(item.streakCount))
                put("isPaused", item.isPaused)
                put("hasReminder", item.hasReminder)
                put("completedDaysHistoryCsv", item.completedDaysHistoryCsv)
                put("completionPercentage", item.completionPercentage)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }

    fun addOrUpdateItem(context: Context, item: ReminderItem) {
        val items = getAllItems(context)
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            items[index] = item
        } else {
            items.add(0, item)
        }
        saveAllItems(context, items)
    }

    fun deleteItem(context: Context, itemId: String) {
        val items = getAllItems(context).filterNot { it.id == itemId }
        saveAllItems(context, items)
    }

    fun getItemById(context: Context, itemId: String): ReminderItem? {
        return getAllItems(context).firstOrNull { it.id == itemId }
    }

    fun markCompleted(context: Context, itemId: String): ReminderItem? {
        val items = getAllItems(context)
        val index = items.indexOfFirst { it.id == itemId }
        if (index == -1) return null

        val current = items[index]
        val todayEpoch = currentEpochDay()
        val historySet = current.completedDaysHistoryCsv.split(",")
            .filter { it.isNotBlank() }
            .toMutableSet()
        historySet.add(todayEpoch.toString())

        val updated = if (current.isHabit) {
            val newStreak = if (current.lastCompletedEpochDay == todayEpoch) current.streakCount else current.streakCount + 1
            val nextGoal = if (newStreak > current.streakGoal) current.streakGoal + 7 else current.streakGoal
            current.copy(
                streakCount = newStreak,
                longestStreak = maxOf(current.longestStreak, newStreak),
                streakGoal = nextGoal,
                lastCompletedEpochDay = todayEpoch,
                isCompletedToday = true,
                currentProgressToday = current.targetValue,
                completedDaysHistoryCsv = historySet.joinToString(","),
                completionPercentage = 100
            )
        } else {
            current.copy(
                lastCompletedEpochDay = todayEpoch,
                isCompletedToday = true,
                completedDaysHistoryCsv = historySet.joinToString(","),
                completionPercentage = 100
            )
        }

        items[index] = updated
        saveAllItems(context, items)

        if (updated.isHabit && !updated.isPaused && updated.hasReminder) {
            AlarmScheduler.scheduleNextOccurrence(context, updated, forceTomorrow = true)
        }
        return updated
    }

    fun updateTaskPercentage(context: Context, itemId: String, percentage: Int): ReminderItem? {
        val item = getItemById(context, itemId) ?: return null
        val clamped = percentage.coerceIn(0, 100)
        val isDone = clamped == 100
        val todayEpoch = currentEpochDay()
        val updated = item.copy(
            completionPercentage = clamped,
            isCompletedToday = isDone,
            lastCompletedEpochDay = if (isDone) todayEpoch else item.lastCompletedEpochDay
        )
        addOrUpdateItem(context, updated)
        return updated
    }

    fun incrementHabitStep(context: Context, itemId: String): ReminderItem? {
        val item = getItemById(context, itemId) ?: return null
        if (!item.isHabit) return markCompleted(context, itemId)
        val nextStep = item.currentProgressToday + 1
        return if (nextStep >= item.targetValue) {
            markCompleted(context, itemId)
        } else {
            val updated = item.copy(currentProgressToday = nextStep)
            addOrUpdateItem(context, updated)
            updated
        }
    }

    fun toggleHabitPause(context: Context, itemId: String): ReminderItem? {
        val item = getItemById(context, itemId) ?: return null
        val nextPaused = !item.isPaused
        val updated = item.copy(isPaused = nextPaused)
        addOrUpdateItem(context, updated)
        if (nextPaused) {
            AlarmScheduler.cancelAlarm(context, itemId)
        } else if (updated.hasReminder) {
            AlarmScheduler.scheduleNextOccurrence(context, updated)
        }
        return updated
    }

    // =========================================================================
    // إدارة الملاحظات (Notes Tab - Google Keep style)
    // =========================================================================

    fun getAllNotes(context: Context): MutableList<NoteItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_NOTES, null) ?: return seedDefaultNotes(context)
        val list = mutableListOf<NoteItem>()
        try {
            val arr = JSONArray(rawJson)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    NoteItem(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        content = o.optString("content", ""),
                        category = o.optString("category", "general"),
                        colorHex = o.optString("colorHex", "#142A2E"),
                        isPinned = o.optBoolean("isPinned", false),
                        timestampMillis = o.optLong("timestampMillis", System.currentTimeMillis()),
                        audioPath = o.optString("audioPath", ""),
                        imageUri = o.optString("imageUri", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveAllNotes(context: Context, notes: List<NoteItem>) {
        val arr = JSONArray()
        notes.forEach { note ->
            val obj = JSONObject().apply {
                put("id", note.id)
                put("title", note.title)
                put("content", note.content)
                put("category", note.category)
                put("colorHex", note.colorHex)
                put("isPinned", note.isPinned)
                put("timestampMillis", note.timestampMillis)
                put("audioPath", note.audioPath)
                put("imageUri", note.imageUri)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NOTES, arr.toString())
            .apply()
    }

    fun addOrUpdateNote(context: Context, note: NoteItem) {
        val list = getAllNotes(context)
        val idx = list.indexOfFirst { it.id == note.id }
        if (idx >= 0) {
            list[idx] = note
        } else {
            list.add(0, note)
        }
        saveAllNotes(context, list)
    }

    fun deleteNote(context: Context, noteId: String) {
        val list = getAllNotes(context).filterNot { it.id == noteId }
        saveAllNotes(context, list)
    }

    // =========================================================================
    // تصنيفات العادات القابلة للزيادة والتوسيع
    // =========================================================================

    fun getAllHabitCategories(context: Context): MutableList<HabitCategoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HABIT_CATEGORIES, null)
        val list = mutableListOf<HabitCategoryItem>()
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(HabitCategoryItem(o.getString("id"), o.getString("name"), o.getString("icon")))
                }
            } catch (_: Exception) {}
        }
        if (list.isEmpty()) {
            list.addAll(listOf(
                HabitCategoryItem("all", "الكل", "⭕"),
                HabitCategoryItem("health", "صحية", "🥗"),
                HabitCategoryItem("study", "تنموية", "📚"),
                HabitCategoryItem("religion", "دينية", "🕌"),
                HabitCategoryItem("sports", "رياضية", "🏋️")
            ))
            saveAllHabitCategories(context, list)
        }
        return list
    }

    fun saveAllHabitCategories(context: Context, categories: List<HabitCategoryItem>) {
        val arr = JSONArray()
        categories.forEach { c ->
            val o = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("icon", c.icon)
            }
            arr.put(o)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HABIT_CATEGORIES, arr.toString())
            .apply()
    }

    fun addHabitCategory(context: Context, item: HabitCategoryItem) {
        val list = getAllHabitCategories(context)
        if (!list.any { it.id == item.id }) {
            list.add(item)
            saveAllHabitCategories(context, list)
        }
    }

    // =========================================================================
    // جلسات التركيز ومؤقت البومودورو
    // =========================================================================

    fun recordCompletedFocusSession(
        context: Context,
        sessionId: String,
        taskId: String?,
        actualMinutes: Int
    ): Int {
        if (actualMinutes <= 0) return getTodayFocusMinutes(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawHistory = prefs.getString(KEY_FOCUS_HISTORY, "[]") ?: "[]"
        val historyArray = try { JSONArray(rawHistory) } catch (_: Exception) { JSONArray() }

        for (i in 0 until historyArray.length()) {
            if (historyArray.getJSONObject(i).optString("id") == sessionId) {
                return getTodayFocusMinutes(context)
            }
        }

        val todayEpoch = currentEpochDay()
        val linkedTask = if (!taskId.isNullOrBlank()) getItemById(context, taskId) else null

        val recordObj = JSONObject().apply {
            put("id", sessionId)
            put("taskId", taskId ?: "")
            put("taskTitle", linkedTask?.title ?: "General Focus")
            put("durationMinutes", actualMinutes)
            put("timestampMillis", System.currentTimeMillis())
            put("epochDay", todayEpoch)
        }
        historyArray.put(recordObj)
        prefs.edit().putString(KEY_FOCUS_HISTORY, historyArray.toString()).apply()

        if (linkedTask != null) {
            val updatedTask = linkedTask.copy(
                actualFocusedMinutes = linkedTask.actualFocusedMinutes + actualMinutes,
                focusSessionsCount = linkedTask.focusSessionsCount + 1
            )
            addOrUpdateItem(context, updatedTask)
        }

        return addFocusMinutes(context, actualMinutes)
    }

    fun getFocusHistory(context: Context): List<FocusSessionRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_FOCUS_HISTORY, "[]") ?: "[]"
        val list = mutableListOf<FocusSessionRecord>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    FocusSessionRecord(
                        id = o.optString("id"),
                        taskId = o.optString("taskId").takeIf { it.isNotBlank() },
                        taskTitle = o.optString("taskTitle", "General Focus"),
                        durationMinutes = o.optInt("durationMinutes", 0),
                        timestampMillis = o.optLong("timestampMillis", 0L),
                        epochDay = o.optLong("epochDay", currentEpochDay())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun getTodayFocusMinutes(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayEpoch = currentEpochDay()
        val savedDay = prefs.getLong(KEY_FOCUS_EPOCH_DAY, -1L)
        if (savedDay != todayEpoch) {
            prefs.edit()
                .putLong(KEY_FOCUS_EPOCH_DAY, todayEpoch)
                .putInt(KEY_FOCUS_MINUTES, 0)
                .apply()
            return 0
        }
        return prefs.getInt(KEY_FOCUS_MINUTES, 0)
    }

    fun addFocusMinutes(context: Context, minutes: Int): Int {
        val current = getTodayFocusMinutes(context) + minutes
        val todayEpoch = currentEpochDay()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_FOCUS_EPOCH_DAY, todayEpoch)
            .putInt(KEY_FOCUS_MINUTES, current)
            .apply()
        return current
    }

    fun saveActiveFocusState(
        context: Context,
        startEpochMs: Long,
        totalDurationMs: Long,
        remainingOnPauseMs: Long,
        isRunning: Boolean,
        linkedTaskId: String?,
        isBreakMode: Boolean
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putLong(KEY_ACTIVE_FOCUS_START_MS, startEpochMs)
            putLong(KEY_ACTIVE_FOCUS_TOTAL_MS, totalDurationMs)
            putLong(KEY_ACTIVE_FOCUS_REMAINING_PAUSE_MS, remainingOnPauseMs)
            putBoolean(KEY_ACTIVE_FOCUS_RUNNING, isRunning)
            putString(KEY_ACTIVE_FOCUS_TASK_ID, linkedTaskId ?: "")
            putBoolean(KEY_ACTIVE_FOCUS_IS_BREAK, isBreakMode)
            apply()
        }
    }

    fun clearActiveFocusState(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            remove(KEY_ACTIVE_FOCUS_START_MS)
            remove(KEY_ACTIVE_FOCUS_TOTAL_MS)
            remove(KEY_ACTIVE_FOCUS_REMAINING_PAUSE_MS)
            putBoolean(KEY_ACTIVE_FOCUS_RUNNING, false)
            apply()
        }
    }

    fun getActiveFocusState(context: Context): JSONObject {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return JSONObject().apply {
            put("startMs", p.getLong(KEY_ACTIVE_FOCUS_START_MS, 0L))
            put("totalMs", p.getLong(KEY_ACTIVE_FOCUS_TOTAL_MS, 25 * 60 * 1000L))
            put("remainingPauseMs", p.getLong(KEY_ACTIVE_FOCUS_REMAINING_PAUSE_MS, 25 * 60 * 1000L))
            put("isRunning", p.getBoolean(KEY_ACTIVE_FOCUS_RUNNING, false))
            put("taskId", p.getString(KEY_ACTIVE_FOCUS_TASK_ID, "") ?: "")
            put("isBreak", p.getBoolean(KEY_ACTIVE_FOCUS_IS_BREAK, false))
        }
    }

    fun getPreAlertMinutes(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_PRE_ALERT_MINUTES, 10)
    }

    fun setPreAlertMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PRE_ALERT_MINUTES, minutes)
            .apply()
    }

    fun getAlarmRingtoneUri(context: Context): Uri {
        val uriStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RINGTONE_URI, null)
        return if (uriStr != null) Uri.parse(uriStr)
        else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    fun setAlarmRingtoneUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RINGTONE_URI, uri.toString())
            .apply()
    }

    fun getCustomQuotes(context: Context): MutableList<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CUSTOM_QUOTES, null)
        val list = mutableListOf<String>()
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
            } catch (_: Exception) {}
        }
        return list
    }

    fun addCustomQuote(context: Context, quote: String) {
        val list = getCustomQuotes(context)
        if (!list.contains(quote)) {
            list.add(0, quote)
            val arr = JSONArray(list)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CUSTOM_QUOTES, arr.toString())
                .apply()
        }
    }

    fun deleteCustomQuote(context: Context, quote: String) {
        val list = getCustomQuotes(context).filterNot { it == quote }
        val arr = JSONArray(list)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_QUOTES, arr.toString())
            .apply()
    }

    fun getLinkedGoogleEmail(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GOOGLE_EMAIL, null)
    }

    fun setLinkedGoogleEmail(context: Context, email: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GOOGLE_EMAIL, email)
            .apply()
    }

    fun exportCloudBackupJson(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val settingsPrefs = context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        val settingsObj = JSONObject().apply {
            put(KEY_DARK_MODE, settingsPrefs.getBoolean(KEY_DARK_MODE, true))
            put(KEY_ACCENT_COLOR, settingsPrefs.getString(KEY_ACCENT_COLOR, "#FF8A3D"))
            put(KEY_DEFAULT_FOCUS_MINS, settingsPrefs.getInt(KEY_DEFAULT_FOCUS_MINS, 25))
            put(KEY_DEFAULT_BREAK_MINS, settingsPrefs.getInt(KEY_DEFAULT_BREAK_MINS, 5))
            put(KEY_DAILY_FOCUS_GOAL_MINS, settingsPrefs.getInt(KEY_DAILY_FOCUS_GOAL_MINS, 120))
            put(KEY_NOTIF_TASKS, settingsPrefs.getBoolean(KEY_NOTIF_TASKS, true))
            put(KEY_NOTIF_HABITS, settingsPrefs.getBoolean(KEY_NOTIF_HABITS, true))
        }
        val root = JSONObject().apply {
            put("app", "WAQT")
            put("schemaVersion", 4)
            put("timestamp", System.currentTimeMillis())
            put("items", JSONArray(prefs.getString(KEY_ITEMS, "[]")))
            put("notes", JSONArray(prefs.getString(KEY_NOTES, "[]")))
            put("habitCategories", JSONArray(prefs.getString(KEY_HABIT_CATEGORIES, "[]")))
            put("customQuotes", JSONArray(getCustomQuotes(context)))
            put("settings", settingsObj)
        }
        return root.toString(2)
    }

    fun importCloudBackupJson(context: Context, jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)
            if (root.optString("app") != "WAQT" && !root.has("items")) return false
            val itemsArr = root.optJSONArray("items")
            val notesArr = root.optJSONArray("notes")
            val habitCatsArr = root.optJSONArray("habitCategories")
            val quotesArr = root.optJSONArray("customQuotes")

            val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            if (itemsArr != null) editor.putString(KEY_ITEMS, itemsArr.toString())
            if (notesArr != null) editor.putString(KEY_NOTES, notesArr.toString())
            if (habitCatsArr != null) editor.putString(KEY_HABIT_CATEGORIES, habitCatsArr.toString())
            if (quotesArr != null) editor.putString(KEY_CUSTOM_QUOTES, quotesArr.toString())
            editor.apply()

            val settingsObj = root.optJSONObject("settings")
            if (settingsObj != null) {
                val settingsEditor = context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
                if (settingsObj.has(KEY_DARK_MODE)) settingsEditor.putBoolean(KEY_DARK_MODE, settingsObj.optBoolean(KEY_DARK_MODE, true))
                if (settingsObj.has(KEY_ACCENT_COLOR)) settingsEditor.putString(KEY_ACCENT_COLOR, settingsObj.optString(KEY_ACCENT_COLOR, "#FF8A3D"))
                if (settingsObj.has(KEY_DEFAULT_FOCUS_MINS)) settingsEditor.putInt(KEY_DEFAULT_FOCUS_MINS, settingsObj.optInt(KEY_DEFAULT_FOCUS_MINS, 25).coerceIn(1, 240))
                if (settingsObj.has(KEY_DEFAULT_BREAK_MINS)) settingsEditor.putInt(KEY_DEFAULT_BREAK_MINS, settingsObj.optInt(KEY_DEFAULT_BREAK_MINS, 5).coerceIn(1, 240))
                if (settingsObj.has(KEY_DAILY_FOCUS_GOAL_MINS)) settingsEditor.putInt(KEY_DAILY_FOCUS_GOAL_MINS, settingsObj.optInt(KEY_DAILY_FOCUS_GOAL_MINS, 120).coerceAtLeast(1))
                if (settingsObj.has(KEY_NOTIF_TASKS)) settingsEditor.putBoolean(KEY_NOTIF_TASKS, settingsObj.optBoolean(KEY_NOTIF_TASKS, true))
                if (settingsObj.has(KEY_NOTIF_HABITS)) settingsEditor.putBoolean(KEY_NOTIF_HABITS, settingsObj.optBoolean(KEY_NOTIF_HABITS, true))
                settingsEditor.apply()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun seedDefaultItems(context: Context): MutableList<ReminderItem> {
        val today = currentEpochDay()
        val defaults = mutableListOf(
            ReminderItem(
                id = "waqt_habit_1",
                title = "قراءة ١٥ صفحة في بنية البرمجيات",
                isHabit = true,
                hour = 9,
                minute = 0,
                streakCount = 5,
                streakGoal = 7,
                priority = "medium",
                category = "study",
                targetValue = 15,
                currentProgressToday = 0,
                targetUnit = "صفحة",
                longestStreak = 5,
                customDaysCsv = "1,2,3,4,5,6,7"
            ),
            ReminderItem(
                id = "waqt_task_1",
                title = "مراجعة وتسليم المشروع النهائي",
                isHabit = false,
                hour = 14,
                minute = 30,
                streakCount = 0,
                streakGoal = 7,
                priority = "high",
                category = "work",
                dueDateEpochDay = today,
                expectedMinutes = 50,
                actualFocusedMinutes = 25,
                focusSessionsCount = 1,
                completionPercentage = 50
            )
        )
        saveAllItems(context, defaults)
        return defaults
    }

    private fun seedDefaultNotes(context: Context): MutableList<NoteItem> {
        val defaults = mutableListOf(
            NoteItem(
                id = "note_1",
                title = "أفكار تطوير تطبيق وَقْت 💡",
                content = "تصميم عجلة GTA V مع الكاروسيل المتراكب وثيم البترولي والبرتقالي.",
                category = "ideas",
                colorHex = "#142A2E",
                isPinned = true
            ),
            NoteItem(
                id = "note_2",
                title = "قائمة مهام العمل الأسبوعية 💼",
                content = "تسليم تقرير الأداء، مراجعة الكود، وتجهيز ملفات الريندر للمشروع.",
                category = "work",
                colorHex = "#1B383C",
                isPinned = false
            )
        )
        saveAllNotes(context, defaults)
        return defaults
    }
}

object AlarmScheduler {
    const val ACTION_TRIGGER_REMINDER = "com.example.glassproductivity.ACTION_TRIGGER_REMINDER"
    const val ACTION_PRE_NOTIFY = "com.example.glassproductivity.ACTION_PRE_NOTIFY"
    const val ACTION_COMPLETE_FROM_NOTIF = "com.example.glassproductivity.ACTION_COMPLETE_FROM_NOTIF"
    const val ACTION_SNOOZE_10M_FROM_NOTIF = "com.example.glassproductivity.ACTION_SNOOZE_10M_FROM_NOTIF"
    const val ACTION_SNOOZE_1H_FROM_NOTIF = "com.example.glassproductivity.ACTION_SNOOZE_1H_FROM_NOTIF"
    const val ACTION_POMODORO_END = "com.example.glassproductivity.ACTION_POMODORO_END"
    const val EXTRA_POMODORO_BREAK = "extra_pomodoro_break"
    const val EXTRA_POMODORO_SESSION = "extra_pomodoro_session"

    const val EXTRA_ITEM_ID = "extra_item_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_IS_HABIT = "extra_is_habit"
    const val EXTRA_STREAK_COUNT = "extra_streak_count"
    const val EXTRA_STREAK_GOAL = "extra_streak_goal"


    fun schedulePomodoroEnd(context: Context, triggerAtMillis: Long, isBreak: Boolean, sessionId: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_POMODORO_END
            putExtra(EXTRA_POMODORO_BREAK, isBreak)
            putExtra(EXTRA_POMODORO_SESSION, sessionId)
        }
        val requestCode = 73000 + if (isBreak) 1 else 0
        val pending = PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        setAlarmClockSafely(context, manager, triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 1000L), pending)
    }

    fun cancelPomodoroEnd(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (isBreak in listOf(false, true)) {
            val intent = Intent(context, AlarmReceiver::class.java).apply { action = ACTION_POMODORO_END }
            val pending = PendingIntent.getBroadcast(context, 73000 + if (isBreak) 1 else 0, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pending != null) { manager.cancel(pending); pending.cancel() }
        }
    }

    fun scheduleNextOccurrence(
        context: Context,
        item: ReminderItem,
        forceTomorrow: Boolean = false
    ): Long {
        if (item.isPaused || !item.hasReminder) {
            cancelAlarm(context, item.id)
            return -1L
        }

        val settingsPrefs = context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        if (item.isHabit && !settingsPrefs.getBoolean(ReminderRepository.KEY_NOTIF_HABITS, true)) return -1L
        if (!item.isHabit && !settingsPrefs.getBoolean(ReminderRepository.KEY_NOTIF_TASKS, true)) return -1L

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nowMillis = System.currentTimeMillis()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, item.hour)
            set(Calendar.MINUTE, item.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Habit day IDs are stored in Saturday-first order: Sat=1, Sun=2, ... Fri=7.
        // Find the next enabled day instead of blindly scheduling tomorrow.
        if (forceTomorrow || calendar.timeInMillis <= nowMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        if (item.isHabit) {
            val allowedDays = item.customDaysCsv.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..7 }
                .toSet()
            if (allowedDays.isEmpty()) {
                cancelAlarm(context, item.id)
                return -1L
            }
            var attempts = 0
            while (attempts < 7) {
                val calendarDay = calendar.get(Calendar.DAY_OF_WEEK)
                val saturdayFirstDayId = ((calendarDay + 5) % 7) + 1
                if (saturdayFirstDayId in allowedDays) break
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                attempts++
            }
            if (attempts >= 7) {
                cancelAlarm(context, item.id)
                return -1L
            }
        }

        val pendingIntent = buildAlarmPendingIntent(context, item, ACTION_TRIGGER_REMINDER)
        setAlarmClockSafely(context, alarmManager, calendar.timeInMillis, pendingIntent)

        val preAlertMinutes = ReminderRepository.getPreAlertMinutes(context)
        val preNotifyMillis = calendar.timeInMillis - (preAlertMinutes * 60 * 1000L)
        if (preNotifyMillis > nowMillis + 5000L && settingsPrefs.getBoolean(ReminderRepository.KEY_NOTIF_SUMMARIES, true)) {
            val prePendingIntent = buildAlarmPendingIntent(context, item, ACTION_PRE_NOTIFY)
            setAlarmClockSafely(context, alarmManager, preNotifyMillis, prePendingIntent)
        }

        return calendar.timeInMillis
    }

    fun scheduleExactDelayMillis(context: Context, item: ReminderItem, delayMillis: Long): Long {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMillis = System.currentTimeMillis() + delayMillis
        val pendingIntent = buildAlarmPendingIntent(context, item, ACTION_TRIGGER_REMINDER)
        setAlarmClockSafely(context, alarmManager, triggerAtMillis, pendingIntent)
        return triggerAtMillis
    }

    fun scheduleSnooze(context: Context, item: ReminderItem, snoozeMinutes: Int = 10): Long {
        return scheduleExactDelayMillis(context, item, snoozeMinutes * 60 * 1000L)
    }

    fun cancelAlarm(context: Context, itemId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun buildAlarmPendingIntent(context: Context, item: ReminderItem, actionString: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = actionString
            putExtra(EXTRA_ITEM_ID, item.id)
            putExtra(EXTRA_TITLE, item.title)
            putExtra(EXTRA_IS_HABIT, item.isHabit)
            putExtra(EXTRA_STREAK_COUNT, item.streakCount)
            putExtra(EXTRA_STREAK_GOAL, item.streakGoal)
        }
        return PendingIntent.getBroadcast(
            context,
            item.id.hashCode() + if (actionString == ACTION_PRE_NOTIFY) 1 else 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setAlarmClockSafely(
        context: Context,
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            val showIntent = Intent(context, MainActivity::class.java)
            val showPendingIntent = PendingIntent.getActivity(
                context,
                0,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }
}