package com.example.glassproductivity

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ALARM_CHANNEL_ID = "waqt_alarm_critical_channel"
        const val MOTIVATION_CHANNEL_ID = "waqt_motivation_channel"
        const val NOTIF_ID_ALARM = 9091
        const val NOTIF_ID_MOTIVATION = 8082
        const val POMODORO_CHANNEL_ID = "waqt_pomodoro_channel"
        const val NOTIF_ID_POMODORO = 7077
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val items = ReminderRepository.getAllItems(context)
                items.forEach { item ->
                    if (!item.isPaused && item.hasReminder && (item.isHabit || !item.isCompletedToday)) {
                        AlarmScheduler.scheduleNextOccurrence(context, item)
                    }
                }
            }

            AlarmScheduler.ACTION_POMODORO_END -> {
                showPomodoroEndNotification(context, intent)
            }

            AlarmScheduler.ACTION_COMPLETE_FROM_NOTIF -> {
                val itemId = intent.getStringExtra(AlarmScheduler.EXTRA_ITEM_ID) ?: return
                ReminderRepository.markCompleted(context, itemId)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(itemId.hashCode())
                nm.cancel(NOTIF_ID_ALARM)
                context.sendBroadcast(Intent(FloatingCardService.ACTION_DATA_UPDATED).setPackage(context.packageName))
                val isAr = context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).getBoolean("is_arabic", true)
                Toast.makeText(context, if (isAr) "✓ تم إتمام المهمة بنجاح" else "✓ Task marked as completed", Toast.LENGTH_SHORT).show()
            }

            AlarmScheduler.ACTION_SNOOZE_10M_FROM_NOTIF -> {
                handleSnoozeFromNotification(context, intent, 10)
            }

            AlarmScheduler.ACTION_SNOOZE_1H_FROM_NOTIF -> {
                handleSnoozeFromNotification(context, intent, 60)
            }

            AlarmScheduler.ACTION_PRE_NOTIFY -> {
                if (!isInQuietHours(context)) {
                    sendPreAlarmMotivation(context, intent)
                }
            }

            AlarmScheduler.ACTION_TRIGGER_REMINDER -> {
                // Re-arm recurring habits immediately so the next reminder survives
                // even when the user dismisses the notification without completing it.
                val itemId = intent.getStringExtra(AlarmScheduler.EXTRA_ITEM_ID).orEmpty()
                val item = ReminderRepository.getItemById(context, itemId)
                if (item != null && item.isHabit && !item.isPaused && item.hasReminder) {
                    AlarmScheduler.scheduleNextOccurrence(context, item, forceTomorrow = true)
                }
                triggerFullSystemAlarm(context, intent)
            }
        }
    }


    private fun showPomodoroEndNotification(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        val isArabic = prefs.getBoolean("is_arabic", Locale.getDefault().language == "ar")
        val isBreak = intent.getBooleanExtra(AlarmScheduler.EXTRA_POMODORO_BREAK, false)
        val sessionId = intent.getStringExtra(AlarmScheduler.EXTRA_POMODORO_SESSION).orEmpty()
        val active = ReminderRepository.getActiveFocusState(context)
        // Ignore alarms from a paused/reset timer or a different phase.
        if (!active.optBoolean("isRunning", false) || active.optBoolean("isBreak", false) != isBreak) return
        val title = if (isBreak) {
            if (isArabic) "☕ انتهت الراحة — وقت التركيز" else "☕ Break finished — time to focus"
        } else {
            if (isArabic) "🎉 انتهى وقت التركيز — خذ استراحة" else "🎉 Focus finished — take a break"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            manager.createNotificationChannel(NotificationChannel(POMODORO_CHANNEL_ID,
                if (isArabic) "تنبيهات التركيز والاستراحة" else "Focus and break alerts",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = if (isArabic) "تنبيه عند انتهاء التركيز أو الاستراحة" else "Alerts when focus or break ends"
                setSound(sound, attrs)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
            })
        }
        val open = PendingIntent.getActivity(context, NOTIF_ID_POMODORO,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, POMODORO_CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_waqt)
            .setContentTitle(title)
            .setContentText(if (isArabic) "افتح وَقْت للانتقال للمرحلة التالية." else "Open WAQT to continue to the next phase.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .build()
        manager.notify(NOTIF_ID_POMODORO, notification)
    }

    private fun handleSnoozeFromNotification(context: Context, intent: Intent, minutes: Int) {
        val itemId = intent.getStringExtra(AlarmScheduler.EXTRA_ITEM_ID) ?: return
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "WAQT"
        val item = ReminderRepository.getItemById(context, itemId) ?: ReminderItem(
            id = itemId,
            title = title,
            isHabit = false,
            hour = 9,
            minute = 0
        )
        AlarmScheduler.scheduleSnooze(context, item, snoozeMinutes = minutes)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(itemId.hashCode())
        nm.cancel(NOTIF_ID_ALARM)
        val isAr = context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).getBoolean("is_arabic", true)
        Toast.makeText(
            context,
            if (isAr) "⏰ تم تأجيل التذكير لمدة $minutes دقيقة" else "⏰ Reminder snoozed for $minutes mins",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun isInQuietHours(context: Context): Boolean {
        val prefs = context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ReminderRepository.KEY_QUIET_HOURS, false)) return false
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return (hour >= 23 || hour < 7)
    }

    private fun triggerFullSystemAlarm(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        val isArabic = prefs.getBoolean("is_arabic", Locale.getDefault().language == "ar")
        val itemId = intent.getStringExtra(AlarmScheduler.EXTRA_ITEM_ID) ?: ""
        val storedItem = ReminderRepository.getItemById(context, itemId)
        val isHabit = storedItem?.isHabit ?: intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_HABIT, false)

        if (isHabit && !prefs.getBoolean(ReminderRepository.KEY_NOTIF_HABITS, true)) return
        if (!isHabit && !prefs.getBoolean(ReminderRepository.KEY_NOTIF_TASKS, true)) return

        val title = storedItem?.title ?: intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: if (isArabic) "تذكير وقت" else "WAQT Reminder"

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "WAQT::FullAlarmWakeLock"
        )
        wakeLock?.acquire(12000L)

        val alarmSoundUri: Uri = ReminderRepository.getAlarmRingtoneUri(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                if (isArabic) "تنبيهات منبه وَقْت" else "WAQT Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical task and habit alarms"
                setSound(alarmSoundUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            itemId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // أزرار الإجراءات المباشرة من الإشعار (تم • بعد 10 دقائق • بعد ساعة)
        val completeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_COMPLETE_FROM_NOTIF
            putExtra(AlarmScheduler.EXTRA_ITEM_ID, itemId)
        }
        val completePending = PendingIntent.getBroadcast(
            context,
            itemId.hashCode() + 10,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze10Intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_SNOOZE_10M_FROM_NOTIF
            putExtra(AlarmScheduler.EXTRA_ITEM_ID, itemId)
            putExtra(AlarmScheduler.EXTRA_TITLE, title)
        }
        val snooze10Pending = PendingIntent.getBroadcast(
            context,
            itemId.hashCode() + 20,
            snooze10Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze1hIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_SNOOZE_1H_FROM_NOTIF
            putExtra(AlarmScheduler.EXTRA_ITEM_ID, itemId)
            putExtra(AlarmScheduler.EXTRA_TITLE, title)
        }
        val snooze1hPending = PendingIntent.getBroadcast(
            context,
            itemId.hashCode() + 30,
            snooze1hIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_waqt)
            .setContentTitle(if (isArabic) "⏰ حان موعد: $title" else "⏰ Due now: $title")
            .setContentText(if (isArabic) "اختر إجراءً سريعاً أو افتح التطبيق" else "Choose a quick action or open WAQT")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSoundUri)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .addAction(0, if (isArabic) "تم ✓" else "Done ✓", completePending)
            .addAction(0, if (isArabic) "بعد 10 د" else "+10m", snooze10Pending)
            .addAction(0, if (isArabic) "بعد ساعة" else "+1h", snooze1hPending)
            .build()

        notificationManager.notify(NOTIF_ID_ALARM, notification)

        val autoFloat = prefs.getBoolean(ReminderRepository.KEY_AUTO_FLOAT, true)
        if (autoFloat && Settings.canDrawOverlays(context)) {
            val serviceIntent = Intent(context, FloatingCardService::class.java).apply {
                putExtra(FloatingCardService.EXTRA_ITEM_ID, storedItem?.id ?: itemId)
                putExtra(FloatingCardService.EXTRA_TITLE, title)
                putExtra(FloatingCardService.EXTRA_IS_HABIT, isHabit)
                putExtra(FloatingCardService.EXTRA_STREAK_COUNT, storedItem?.streakCount ?: intent.getIntExtra(AlarmScheduler.EXTRA_STREAK_COUNT, 0))
                putExtra(FloatingCardService.EXTRA_STREAK_GOAL, storedItem?.streakGoal ?: intent.getIntExtra(AlarmScheduler.EXTRA_STREAK_GOAL, 7))
                putExtra(FloatingCardService.EXTRA_IS_ARABIC, isArabic)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }

    private fun sendPreAlarmMotivation(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ReminderRepository.KEY_NOTIF_SUMMARIES, true)) return

        val isArabic = prefs.getBoolean("is_arabic", Locale.getDefault().language == "ar")
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Focus"
        val preMinutes = ReminderRepository.getPreAlertMinutes(context)

        val customQuotes = ReminderRepository.getCustomQuotes(context)
        val defaultQuotes = if (isArabic) listOf(
            "مهمتك التالية في انتظارك: «$title» بعد $preMinutes دقائق.",
            "ابدأ بخطوة صغيرة • موعد «$title» يقترب.",
            "الوقت هو أغلى مواردك، فلا تضيعه."
        ) else listOf(
            "Next up: \"$title\" in $preMinutes minutes.",
            "Start with a small step • \"$title\" begins soon.",
            "Time is your most valuable resource, don't waste it."
        )

        val allQuotes = if (customQuotes.isNotEmpty()) customQuotes + defaultQuotes else defaultQuotes
        val message = allQuotes.random()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MOTIVATION_CHANNEL_ID,
                if (isArabic) "تذكيرات وَقْت التمهيدية" else "WAQT Pre-Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MOTIVATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_waqt)
            .setContentTitle(if (isArabic) "وَقْت • تذكير تمهيدي ⏳" else "WAQT • Upcoming Task ⏳")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(NOTIF_ID_MOTIVATION, notification)
    }
}