package com.example.glassproductivity

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.Ringtone
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private var isArabic: Boolean = true
    private var isDarkMode: Boolean = true
    private var currentTab: Int = 0 // 0: المهام, 1: العادات, 2: التركيز, 3: مدونتي

    // ألوان البترولي والبرتقالي
    private val COLOR_BG_DARK = "#07111F"
    private val COLOR_CARD_DARK = "#B31B3048"
    private val COLOR_ELEVATED_DARK = "#CC243D59"
    private val COLOR_ORANGE = "#FF9DCEFF"
    private val COLOR_ORANGE_LIGHT = "#FFD7F0FF"
    private val COLOR_TEAL = "#FF65D9F2"
    private val COLOR_TEAL_DEEP = "#FF9BEAFF"
    private val COLOR_TEXT_PRIMARY = "#FFF5FAFF"
    private val COLOR_TEXT_MUTED = "#FFB8C9DC"

    private fun bgPrimaryColor(): Int = Color.parseColor(if (isDarkMode) COLOR_BG_DARK else "#F0F6F4")
    private fun bgCardColor(): Int = Color.parseColor(if (isDarkMode) COLOR_CARD_DARK else "#EFFFFFFF")
    private fun bgElevatedColor(): Int = Color.parseColor(if (isDarkMode) COLOR_ELEVATED_DARK else "#DFFFFFFF")
    private fun textPrimaryColor(): Int = Color.parseColor(if (isDarkMode) COLOR_TEXT_PRIMARY else "#0B1417")
    private fun textMutedColor(): Int = Color.parseColor(if (isDarkMode) COLOR_TEXT_MUTED else "#526C6C")
    private fun accentOrangeColor(): Int = Color.parseColor(COLOR_ORANGE)
    private fun accentTealColor(): Int = Color.parseColor(COLOR_TEAL)

    private var taskFilter: String = "all"
    private var taskSearchQuery: String = ""
    private var taskPriorityFilter: String = "all"
    private var isTasksLeftDockOpen: Boolean = false

    private var habitSelectedCategory: String = "all"
    private var isHabitsLeftDockOpen: Boolean = false

    private var noteSelectedCategory: String = "all"
    private var noteSearchQuery: String = ""
    private var isNotesLeftDockOpen: Boolean = false
    private var notesSubTab: Int = 0 // 0 = Notes, 1 = Daily journal
    private var diarySelectedDate: String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    private var diaryDisplayedMonth: Int = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
    private var diaryDisplayedYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    private var currentTaskCarouselIndex: Int = 0
    private var currentHabitCarouselIndex: Int = 0
    private var currentNoteCarouselIndex: Int = 0

    private var isPomodoroLinkedToTask: Boolean = true
    private var selectedFocusTaskId: String? = null
    private var selectedPomodoroMinutes: Int = 25
    private var selectedBreakMinutes: Int = 5
    private var isBreakSession: Boolean = false
    private var pomodoroTotalMillis: Long = 25 * 60 * 1000L
    private var pomodoroRemainingMillis: Long = 25 * 60 * 1000L
    private var pomodoroStartWallClockMs: Long = 0L
    private var activeFocusSessionId: String = UUID.randomUUID().toString()
    private var isPomodoroRunning: Boolean = false
    private var inAppCountDownTimer: CountDownTimer? = null
    private var circularTimerView: CircularTimerRingView? = null
    private var isTickTockSoundActive: Boolean = false
    private var isPomodoroLeftDockOpen: Boolean = false

    private var isFlipClockModeActive: Boolean = false
    private var fullscreenClockType: String = "flip"

    private val soundEngine = SoundEngine()
    private var focusCycleCount: Int = 0
    private var isAmbientPlaying: Boolean = false
    private var selectedAmbientIndex: Int = 0
    private var ambientVolume: Float = 0.7f
    private var customAudioUri: Uri? = null
    private var customAudioName: String? = null
    private var customAudioPlayer: MediaPlayer? = null
    private var isPickingAudioFromConsole: Boolean = false
    private var audioManager: AudioManager? = null
    private var previewRingtone: Ringtone? = null
    private var noteAudioPath: String = ""
    private var noteImageUri: String = ""
    private var noteAudioRecorder: MediaRecorder? = null
    private var isRecordingNoteAudio: Boolean = false
    private var activeChatMessagesBox: LinearLayout? = null
    private var activeChatScroll: ScrollView? = null
    private var chatRecorder: MediaRecorder? = null
    private var chatRecordingFile: java.io.File? = null
    private var chatIsRecording: Boolean = false
    private var chatRecordButton: TextView? = null
    private var chatTts: TextToSpeech? = null
    private var lastAssistantReply: String = ""
    private var autoSpeakAssistantReply: Boolean = false
    private val chatHistoryStore by lazy { ChatHistoryStore(this) }
    private var activeChatId: String = ""
    private var activeAIChatDialog: Dialog? = null
    private var activeAssistantAvatarView: ImageView? = null
    private var activeAssistantNameView: TextView? = null

    private lateinit var rootFrameLayout: FrameLayout
    private lateinit var mainContentContainer: LinearLayout
    private lateinit var headerBarLayout: LinearLayout
    private lateinit var tabContentHost: FrameLayout
    private lateinit var tvBrandTitle: TextView
    private lateinit var tvBrandSubtitle: TextView

    private lateinit var gtaRadialTriggerButton: FrameLayout
    private lateinit var gtaRadialOverlayView: GTARadialWheelView
    private lateinit var floatingAIChatBubble: FrameLayout

    private var activeJournalMediaDateKey: String = ""
    private var journalAudioRecorder: MediaRecorder? = null
    private var journalAudioFile: java.io.File? = null
    private var isRecordingJournalAudio: Boolean = false

    private val journalImagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && activeJournalMediaDateKey.isNotBlank()) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val prefs = getSharedPreferences("waqt_daily_journal_media", Context.MODE_PRIVATE)
                val current = try { org.json.JSONArray(prefs.getString(activeJournalMediaDateKey, "[]") ?: "[]") } catch (_: Exception) { org.json.JSONArray() }
                current.put(org.json.JSONObject().put("type", "image").put("uri", uri.toString()).put("name", getFileNameFromUri(uri)))
                prefs.edit().putString(activeJournalMediaDateKey, current.toString()).apply()
                showDailyJournalEntryPage(activeJournalMediaDateKey)
            } catch (e: Exception) {
                Toast.makeText(this, if (isArabic) "تعذر إرفاق الصورة" else "Could not attach image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val journalAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startJournalAudioRecording()
        else Toast.makeText(this, if (isArabic) "لازم تسمح بالميكروفون لتسجيل الصوت" else "Microphone permission is needed", Toast.LENGTH_SHORT).show()
    }

    private val noteImagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            noteImageUri = uri.toString()
            Toast.makeText(this, if (isArabic) "تم إرفاق الصورة بالملاحظة" else "Image attached", Toast.LENGTH_SHORT).show()
        }
    }

    private val chatImagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("تعذر قراءة الصورة")
                if (bytes.size > 12 * 1024 * 1024) throw IllegalArgumentException("حجم الصورة أكبر من 12 ميجابايت")
                val mime = contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
                val box = activeChatMessagesBox
                val scroll = activeChatScroll
                if (box != null && scroll != null) {
                    box.addView(createUserMessageBubble("🖼 ${if (isArabic) "تم إرفاق صورة" else "Image attached"}"))
                    sendToAssistant(if (isArabic) "حلّل الصورة المرفقة واشرح لي أهم ما يظهر فيها." else "Analyze the attached image and describe what matters.", box, scroll,
                        GeminiClient.MediaInput(bytes, mime, "image"))
                }
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: if (isArabic) "تعذر إرفاق الصورة" else "Could not attach image", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val assistantAvatarPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
                .putString("assistant_avatar_uri", uri.toString()).apply()
            activeAssistantAvatarView?.let {
                it.setImageURI(uri)
                it.visibility = View.VISIBLE
            }
            Toast.makeText(this, if (isArabic) "تم تحديث صورة المساعد" else "Assistant photo updated", Toast.LENGTH_SHORT).show()
        }
    }

    private val chatMicPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startChatVoiceRecording()
        else Toast.makeText(this, if (isArabic) "الميكروفون مطلوب لتسجيل الرسالة الصوتية فقط" else "Microphone permission is needed only for voice messages", Toast.LENGTH_LONG).show()
    }

    private val noteCameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val file = java.io.File(filesDir, "note_photo_${System.currentTimeMillis()}.jpg")
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                noteImageUri = Uri.fromFile(file).toString()
                Toast.makeText(this, if (isArabic) "تم التقاط الصورة وإرفاقها" else "Photo captured and attached", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, if (isArabic) "تعذر حفظ الصورة" else "Could not save photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                ReminderRepository.setAlarmRingtoneUri(this, uri)
                Toast.makeText(this, if (isArabic) "✓ تم حفظ نغمة المنبه بنجاح" else "✓ Ringtone saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var pendingBackupJson: String? = null
    private val backupExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val json = pendingBackupJson
        pendingBackupJson = null
        if (uri != null && json != null) {
            val ok = try {
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                true
            } catch (_: Exception) { false }
            Toast.makeText(this, if (ok) (if (isArabic) "✅ اتحفظت النسخة الاحتياطية" else "Backup saved") else (if (isArabic) "تعذّر الحفظ" else "Could not save"), Toast.LENGTH_LONG).show()
        }
    }

    private val backupImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val backupText = try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (_: Exception) { null }
            if (backupText.isNullOrBlank()) {
                Toast.makeText(this, if (isArabic) "تعذر قراءة ملف النسخة الاحتياطية" else "Could not read backup file", Toast.LENGTH_LONG).show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle(if (isArabic) "استعادة النسخة الاحتياطية؟" else "Restore backup?")
                    .setMessage(if (isArabic) "سيتم استبدال المهام والعادات ومدونتي الحالية بالبيانات الموجودة في الملف." else "Current tasks, habits and journal entries will be replaced by the backup data.")
                    .setNegativeButton(if (isArabic) "إلغاء" else "Cancel", null)
                    .setPositiveButton(if (isArabic) "استعادة" else "Restore") { _, _ ->
                        val ok = ReminderRepository.importCloudBackupJson(this, backupText)
                        Toast.makeText(this, if (ok) {
                            if (isArabic) "تمت استعادة النسخة الاحتياطية" else "Backup restored"
                        } else {
                            if (isArabic) "الملف غير صالح أو غير مدعوم" else "Invalid or unsupported backup"
                        }, Toast.LENGTH_LONG).show()
                        if (ok) renderCurrentTab()
                    }.show()
            }
        }
    }

    private val localAudioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            customAudioUri = uri
            customAudioName = getFileNameFromUri(uri)
            selectedAmbientIndex = -1
            getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
                .putString("custom_audio_uri", uri.toString())
                .putString("custom_audio_name", customAudioName)
                .putInt(ReminderRepository.KEY_DEFAULT_SOUND_INDEX, -1)
                .apply()
            Toast.makeText(this, if (isArabic) "🎵 تم اختيار: $customAudioName" else "🎵 Audio loaded: $customAudioName", Toast.LENGTH_SHORT).show()
            if (isPickingAudioFromConsole) playCustomAudio()
            isPickingAudioFromConsole = false
            renderCurrentTab()
        } else {
            isPickingAudioFromConsole = false
        }
    }

    private val dataUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            syncActiveFocusTimerFromStorage()
            renderCurrentTab()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashReporter()
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        loadSavedPreferences()
        applySystemBarTheme()
        buildMainLayoutStructure()
        syncActiveFocusTimerFromStorage()
        renderCurrentTab()
    }

    private fun applySystemBarTheme() {
        window.statusBarColor = bgPrimaryColor()
        window.navigationBarColor = bgPrimaryColor()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (isDarkMode) 0 else (
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        )
    }

    private fun loadSavedPreferences() {
        val prefs = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        isArabic = prefs.getBoolean("is_arabic", true)
        isDarkMode = prefs.getBoolean(ReminderRepository.KEY_DARK_MODE, true)
        selectedPomodoroMinutes = prefs.getInt(ReminderRepository.KEY_DEFAULT_FOCUS_MINS, 25)
        selectedBreakMinutes = prefs.getInt(ReminderRepository.KEY_DEFAULT_BREAK_MINS, 5)
        selectedAmbientIndex = prefs.getInt(ReminderRepository.KEY_DEFAULT_SOUND_INDEX, 0)
        ambientVolume = (prefs.getInt(ReminderRepository.KEY_SOUND_VOLUME, 70) / 100f).coerceIn(0.05f, 1f)
        isTickTockSoundActive = prefs.getBoolean(ReminderRepository.KEY_TICK_TOCK_ENABLED, false)
        customAudioUri = prefs.getString("custom_audio_uri", null)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        customAudioName = prefs.getString("custom_audio_name", null)

        pomodoroTotalMillis = selectedPomodoroMinutes * 60 * 1000L
        pomodoroRemainingMillis = pomodoroTotalMillis
        window.decorView.layoutDirection = if (isArabic) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(FloatingCardService.ACTION_DATA_UPDATED)
        ContextCompat.registerReceiver(this, dataUpdateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        syncActiveFocusTimerFromStorage()
        renderCurrentTab()
        onboardingRefresh?.invoke()
        if (!startupChecksDone) {
            startupChecksDone = true
            if (getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).getBoolean("ota_auto_check", false)) {
                checkForOnlineUpdates(manual = false)
            }
            window.decorView.post {
                val crashed = showLastCrashIfAny()
                if (!crashed && !getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).getBoolean("onboarding_done", false)) {
                    showOnboarding(false)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isPomodoroRunning) {
            ReminderRepository.saveActiveFocusState(
                this,
                startEpochMs = pomodoroStartWallClockMs,
                totalDurationMs = pomodoroTotalMillis,
                remainingOnPauseMs = pomodoroRemainingMillis,
                isRunning = true,
                linkedTaskId = selectedFocusTaskId,
                isBreakMode = isBreakSession
            )
        }
    }

    override fun onDestroy() {
        if (isRecordingJournalAudio) {
            try { stopJournalAudioRecording() } catch (_: Exception) {
                try { journalAudioRecorder?.release() } catch (_: Exception) {}
                journalAudioRecorder = null
                journalAudioFile = null
                isRecordingJournalAudio = false
            }
        }
        super.onDestroy()
        try { unregisterReceiver(dataUpdateReceiver) } catch (_: Exception) {}
        inAppCountDownTimer?.cancel()
        stopAmbientSound()
        stopCustomAudio()
        soundEngine.release()
        previewRingtone?.stop()
        try { chatRecorder?.release() } catch (_: Exception) {}
        chatRecorder = null
        chatTts?.stop()
        chatTts?.shutdown()
        chatTts = null
    }

    private fun syncActiveFocusTimerFromStorage() {
        val state = ReminderRepository.getActiveFocusState(this)
        val wasRunning = state.optBoolean("isRunning", false)
        val totalMs = state.optLong("totalMs", selectedPomodoroMinutes * 60 * 1000L)
        val startMs = state.optLong("startMs", 0L)
        val savedTaskId = state.optString("taskId", "").takeIf { it.isNotBlank() }
        val wasBreak = state.optBoolean("isBreak", false)

        if (wasRunning && startMs > 0L) {
            val elapsed = System.currentTimeMillis() - startMs
            val remaining = totalMs - elapsed
            selectedFocusTaskId = savedTaskId
            isBreakSession = wasBreak
            pomodoroTotalMillis = totalMs

            if (remaining <= 0L) {
                inAppCountDownTimer?.cancel()
                isPomodoroRunning = false
                ReminderRepository.clearActiveFocusState(this)
                if (!isBreakSession) {
                    val mins = (totalMs / 60000L).toInt().coerceAtLeast(1)
                    ReminderRepository.recordCompletedFocusSession(
                        this,
                        sessionId = activeFocusSessionId,
                        taskId = selectedFocusTaskId,
                        actualMinutes = mins
                    )
                }
                pomodoroRemainingMillis = pomodoroTotalMillis
            } else {
                pomodoroRemainingMillis = remaining
                pomodoroStartWallClockMs = startMs
                if (!isPomodoroRunning) {
                    startWallClockCountDown(remaining)
                }
            }
        }
    }

    private fun buildMainLayoutStructure() {
        rootFrameLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(bgPrimaryColor())
        }
        // Liquid-glass atmosphere: layered cool light blooms behind translucent surfaces.
        rootFrameLayout.addView(AtmosphereBackgroundView(this), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        mainContentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        headerBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(10))
            background = createGlassCardDrawable(false, false)
            elevation = dp(8).toFloat()
        }

        val logoIv = ImageView(this).apply {
            setImageResource(R.drawable.logo_waqt)
            val s = dp(34)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = dp(10) }
        }
        headerBarLayout.addView(logoIv)

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvBrandTitle = TextView(this).apply {
            text = if (isArabic) "وَقْت" else "WAQT"
            setTextColor(textPrimaryColor())
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setShadowLayer(dp(10).toFloat(), 0f, 0f, Color.argb(160, 255, 138, 61))
        }
        tvBrandSubtitle = TextView(this).apply {
            text = getTabSubtitleText()
            setTextColor(accentOrangeColor())
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
        }
        titleCol.addView(tvBrandTitle)
        titleCol.addView(tvBrandSubtitle)
        headerBarLayout.addView(titleCol)

        val btnLang = TextView(this).apply {
            text = if (isArabic) "EN" else "عربي"
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(accentTealColor())
            background = createGlass3DDrawable(dp(14)); elevation = dp(6).toFloat()
            val h = dp(36)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h).apply { marginEnd = dp(8) }
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { toggleLanguage() }
        }
        headerBarLayout.addView(btnLang)

        val btnSettings = TextView(this).apply {
            text = "⚙️"
            textSize = 18f
            gravity = Gravity.CENTER
            background = createGlass3DDrawable(dp(14)); elevation = dp(6).toFloat()
            val s = dp(36)
            layoutParams = LinearLayout.LayoutParams(s, s)
            setOnClickListener { showAccordionSettingsDialog() }
        }
        headerBarLayout.addView(btnSettings)

        mainContentContainer.addView(headerBarLayout)

        tabContentHost = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        mainContentContainer.addView(tabContentHost)

        rootFrameLayout.addView(mainContentContainer)

        // عجلة GTA V Radial Wheel
        gtaRadialOverlayView = GTARadialWheelView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            visibility = View.GONE
        }

        gtaRadialTriggerButton = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)).apply {
                gravity = Gravity.BOTTOM
                leftMargin = dp(14)
                rightMargin = dp(14)
                bottomMargin = dp(12)
            }
            elevation = dp(18).toFloat()
        }
        rootFrameLayout.addView(gtaRadialOverlayView)
        refreshTabBar()
        rootFrameLayout.addView(gtaRadialTriggerButton)

        setupFloatingAIChatHeadBubble()
        setContentView(rootFrameLayout)
    }

    private fun refreshTabBar() {
        if (!::gtaRadialTriggerButton.isInitialized) return
        val bar = gtaRadialTriggerButton
        bar.removeAllViews()
        bar.background = createGlass3DDrawable(dp(33))
        val tabs = listOf(
            Triple(0, "🎯", if (isArabic) "المهام" else "Tasks"),
            Triple(1, "🌱", if (isArabic) "العادات" else "Habits"),
            Triple(2, "🍅", if (isArabic) "التركيز" else "Focus"),
            Triple(3, "📝", if (isArabic) "مدونتي" else "My Journal")
        )
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        tabs.forEach { (idx, icon, label) ->
            val sel = idx == currentTab
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(2); marginEnd = dp(2) }
                background = if (sel) createOrange3DDrawable(dp(26)) else null
                addView(TextView(this@MainActivity).apply {
                    text = icon
                    textSize = if (sel) 21f else 19f
                    gravity = Gravity.CENTER
                })
                addView(TextView(this@MainActivity).apply {
                    text = label
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, if (sel) Typeface.BOLD else Typeface.NORMAL)
                    setTextColor(if (sel) Color.parseColor("#0B1417") else textMutedColor())
                })
                setOnClickListener {
                    if (idx != currentTab) performLiquidWaveTransitionToTab(idx)
                }
            })
        }
        bar.addView(row)

        // Swipe left/right on the bar to move between tabs.
        var downX = 0f
        var done = false
        bar.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; done = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    if (!done && abs(dx) > dp(48)) {
                        done = true
                        val dir = if (dx > 0) -1 else 1
                        val rtlFlip = if (isArabic) -1 else 1
                        val next = (currentTab + dir * rtlFlip).coerceIn(0, 3)
                        if (next != currentTab) performLiquidWaveTransitionToTab(next)
                    }
                }
            }
            done
        }
    }

    private fun getTabSubtitleText(): String {
        return when (currentTab) {
            0 -> if (isArabic) "🎯 المهام اليومية" else "🎯 Tasks"
            1 -> if (isArabic) "🌱 سلاسل العادات" else "🌱 Habits"
            2 -> if (isArabic) "🍅 ستوديو التركيز" else "🍅 Focus"
            3 -> if (isArabic) "📝 مدونتي" else "📝 My Journal"
            else -> ""
        }
    }

    private fun toggleLanguage() {
        isArabic = !isArabic
        getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit().putBoolean("is_arabic", isArabic).apply()
        window.decorView.layoutDirection = if (isArabic) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        renderCurrentTab()
    }

    private fun renderCurrentTab() {
        syncTickTock()
        refreshTabBar()
        tvBrandTitle.text = if (isArabic) "وَقْت" else "WAQT"
        tvBrandSubtitle.text = getTabSubtitleText()

        tabContentHost.removeAllViews()
        val view = when (currentTab) {
            0 -> buildTasksScreenView()
            1 -> buildHabitsScreenView()
            2 -> buildPomodoroScreenView()
            3 -> buildNotesScreenView()
            else -> buildTasksScreenView()
        }
        tabContentHost.addView(view)
    }

    private var radialTouchStartX = 0f
    private var radialTouchStartY = 0f
    private var selectedRadialSector = -1

    private fun handleGTARadialTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                radialTouchStartX = event.rawX
                radialTouchStartY = event.rawY
                selectedRadialSector = currentTab
                gtaRadialOverlayView.activeSector = selectedRadialSector
                gtaRadialOverlayView.visibility = View.VISIBLE
                triggerGentleHaptic()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - radialTouchStartX
                val dy = event.rawY - radialTouchStartY
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > dp(36)) {
                    var angleDeg = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())).toFloat()
                    if (angleDeg < 0) angleDeg += 360f

                    // قوس نصف دائري من اليسار إلى اليمين: مدونتي، عادات، تركيز، مهام.
                    val arcAngle = angleDeg.coerceIn(0f, 180f)
                    val newSector = when (arcAngle) {
                        in 0f..45f -> 0       // يمين: المهام
                        in 45.001f..90f -> 2  // أعلى اليمين: التركيز
                        in 90.001f..135f -> 1 // أعلى اليسار: العادات
                        else -> 3             // يسار: الملاحظات
                    }
                    if (newSector != selectedRadialSector) {
                        selectedRadialSector = newSector
                        gtaRadialOverlayView.activeSector = selectedRadialSector
                        gtaRadialOverlayView.invalidate()
                        triggerGentleHaptic()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                gtaRadialOverlayView.visibility = View.GONE
                if (selectedRadialSector != -1 && selectedRadialSector != currentTab) {
                    performLiquidWaveTransitionToTab(selectedRadialSector)
                }
            }
        }
    }

    private fun performLiquidWaveTransitionToTab(targetTab: Int) {
        currentTab = targetTab
        triggerGentleHaptic()

        tabContentHost.animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .alpha(0.3f)
            .setDuration(120)
            .withEndAction {
                renderCurrentTab()
                tabContentHost.scaleX = 1.04f
                tabContentHost.scaleY = 1.04f
                tabContentHost.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(180)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun setupFloatingAIChatHeadBubble() {
        floatingAIChatBubble = FrameLayout(this).apply {
            val size = dp(54)
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                leftMargin = dp(16)
                topMargin = dp(140)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(178, 46, 116, 160))
                setStroke(dp(1), Color.argb(220, 190, 238, 255))
            }
            elevation = dp(16).toFloat()

            val tvIcon = TextView(context).apply {
                text = "✦"
                textSize = 22f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            addView(tvIcon)

            setupDraggableChatHeadTouch(this)
            setOnClickListener {
                triggerGentleHaptic()
                val minimizedDialog = activeAIChatDialog
                if (minimizedDialog != null) {
                    visibility = View.GONE
                    minimizedDialog.show()
                } else {
                    showAIChatCopilotModal()
                }
            }
        }
        rootFrameLayout.addView(floatingAIChatBubble)
    }

    private fun setupDraggableChatHeadTouch(view: View) {
        var startX = 0f
        var startY = 0f
        var rawStartX = 0f
        var rawStartY = 0f
        var isDrag = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        view.setOnTouchListener { v, event ->
            val lp = v.layoutParams as FrameLayout.LayoutParams
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.leftMargin.toFloat()
                    startY = lp.topMargin.toFloat()
                    rawStartX = event.rawX
                    rawStartY = event.rawY
                    isDrag = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - rawStartX
                    val dy = event.rawY - rawStartY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        isDrag = true
                        lp.leftMargin = (startX + dx).toInt().coerceIn(dp(6), resources.displayMetrics.widthPixels - dp(60))
                        lp.topMargin = (startY + dy).toInt().coerceIn(dp(60), resources.displayMetrics.heightPixels - dp(120))
                        v.layoutParams = lp
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDrag) {
                        val screenW = resources.displayMetrics.widthPixels
                        val targetMargin = if (lp.leftMargin + dp(27) < screenW / 2) dp(12) else screenW - dp(66)
                        lp.leftMargin = targetMargin
                        v.layoutParams = lp
                    } else {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showAIChatCopilotModal(chatId: String? = null) {
        activeChatId = chatHistoryStore.ensureChat(chatId)
        geminiHistory.clear()
        chatHistoryStore.getMessages(activeChatId).forEach { msg ->
            if (msg.role == "user" || msg.role == "model") geminiHistory.add(msg.role to msg.content)
        }
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlass3DDrawable(dp(24))
            setPadding(dp(18), dp(16), dp(18), dp(18))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        header.addView(createAssistantAvatarView())
        header.addView(TextView(this).apply {
            text = assistantName()
            setTextColor(textPrimaryColor())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            activeAssistantNameView = this
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showAssistantProfileDialog() }
        })
        header.addView(TextView(this).apply {
            text = "👤"
            textSize = 17f
            setPadding(dp(7), 0, dp(7), 0)
            contentDescription = if (isArabic) "تخصيص شخصية المساعد" else "Customize assistant"
            setOnClickListener { showAssistantProfileDialog() }
        })
        header.addView(TextView(this).apply {
            text = "🕘"
            textSize = 18f
            setPadding(dp(8), 0, dp(8), 0)
            contentDescription = if (isArabic) "المحادثات السابقة" else "Chat history"
            setOnClickListener { showChatHistoryDialog(dialog) }
        })
        header.addView(TextView(this).apply {
            text = "＋"
            textSize = 21f
            setPadding(dp(8), 0, dp(8), 0)
            contentDescription = if (isArabic) "محادثة جديدة" else "New chat"
            setOnClickListener {
                dialog.dismiss()
                showAIChatCopilotModal()
            }
        })
        header.addView(TextView(this).apply {
            text = "🔑"
            textSize = 18f
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { showGeminiKeyDialog() }
        })
        header.addView(TextView(this).apply {
            text = "—"
            textSize = 20f
            setPadding(dp(8), 0, dp(8), 0)
            contentDescription = if (isArabic) "تصغير المحادثة" else "Minimize chat"
            setOnClickListener {
                if (chatIsRecording) stopChatVoiceRecording(discard = true)
                dialog.hide()
                floatingAIChatBubble.visibility = View.VISIBLE
            }
        })
        header.addView(TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(textMutedColor())
            setPadding(dp(8), 0, dp(4), 0)
            setOnClickListener { dialog.dismiss() }
        })
        root.addView(header)

        val chatLogContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(330))
        }
        val chatScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            isFillViewport = true
        }

        val messagesBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val savedMessages = chatHistoryStore.getMessages(activeChatId)
        if (!hasAnyAssistantKey() && savedMessages.isEmpty()) {
        messagesBox.addView(createAIMessageBubble(
            if (isArabic) "أهلاً بيك! أنا WAQT Mentor. احكيلي عن هدف عايز توصله، مشكلة محتاج تفكر فيها، أو مهارة عايز تتعلمها. هنفهم وضعك ونبني طريق عملي يناسبك خطوة بخطوة."
            else "Hi! I’m your WAQT Mentor. Tell me what you’re trying to achieve, what’s getting in your way, or what you want to learn. We’ll think it through and build a realistic next step together."
        ))
            messagesBox.addView(createAIMessageBubble(if (isArabic) "🔑 عشان تشغّل WAQT Mentor، افتح إعدادات 🔑 واربط Gemini أو Groq أو OpenRouter." else "🔑 Connect Gemini, Groq, or OpenRouter from the key icon."))
        }

        // Restore the selected conversation from the local database.
        savedMessages.forEach { saved ->
            if (saved.role == "user") messagesBox.addView(createUserMessageBubble(saved.content))
            else if (saved.role == "model") messagesBox.addView(createAIMessageBubble(saved.content))
        }
        if (savedMessages.isNotEmpty()) lastAssistantReply = savedMessages.lastOrNull { it.role == "model" }?.content.orEmpty()

        chatScroll.addView(messagesBox)
        chatLogContainer.addView(chatScroll)
        root.addView(chatLogContainer)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        val etMsg = EditText(this).apply {
            hint = if (isArabic) "فضفض أو اكتب مهامك هنا..." else "Type ideas or tasks..."
            setHintTextColor(textMutedColor())
            setTextColor(textPrimaryColor())
            textSize = 13f
            background = createPillDrawable(bgElevatedColor(), dp(14), null)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) }
        }
        inputRow.addView(etMsg)

        val btnImage = TextView(this).apply {
            text = "🖼"
            textSize = 18f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(46)).apply { marginEnd = dp(4) }
            setOnClickListener {
                if (geminiKey().isBlank()) {
                    showGeminiKeyDialog()
                    return@setOnClickListener
                }
                activeChatMessagesBox = messagesBox
                activeChatScroll = chatScroll
                chatImagePickerLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp", "image/*"))
            }
        }
        inputRow.addView(btnImage)

        val btnMic = TextView(this).apply {
            text = "🎙"
            textSize = 18f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(46)).apply { marginEnd = dp(4) }
            chatRecordButton = this
            setOnClickListener {
                if (geminiKey().isBlank()) {
                    showGeminiKeyDialog()
                    return@setOnClickListener
                }
                activeChatMessagesBox = messagesBox
                activeChatScroll = chatScroll
                if (chatIsRecording) stopChatVoiceRecording() else {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) startChatVoiceRecording()
                    else chatMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        inputRow.addView(btnMic)

        val btnSpeak = TextView(this).apply {
            text = "🔊"
            textSize = 17f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(46)).apply { marginEnd = dp(4) }
            setOnClickListener { speakLastAssistantReply() }
        }
        inputRow.addView(btnSpeak)

        val btnSend = TextView(this).apply {
            text = "🚀"
            textSize = 18f
            gravity = Gravity.CENTER
            background = createPillDrawable(accentOrangeColor(), dp(14), null)
            val s = dp(46)
            layoutParams = LinearLayout.LayoutParams(s, s)
            setOnClickListener {
                val userText = etMsg.text.toString().trim()
                if (userText.isNotBlank()) {
                    messagesBox.addView(createUserMessageBubble(userText))
                    etMsg.text.clear()
                    chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
                    sendToAssistant(userText, messagesBox, chatScroll)
                }
            }
        }
        inputRow.addView(btnSend)
        root.addView(inputRow)

        activeChatMessagesBox = messagesBox
        activeChatScroll = chatScroll
        dialog.setOnDismissListener {
            if (chatIsRecording) stopChatVoiceRecording(discard = true)
            activeChatMessagesBox = null
            activeChatScroll = null
            if (activeAIChatDialog === dialog) activeAIChatDialog = null
            floatingAIChatBubble.visibility = View.VISIBLE
        }
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.94).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
        activeAIChatDialog = dialog
        floatingAIChatBubble.visibility = View.GONE
        if (geminiKey().isNotBlank() && savedMessages.isEmpty()) sendToAssistant(null, messagesBox, chatScroll)
    }


    private fun assistantName(): String =
        getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
            .getString("assistant_name", if (isArabic) "صاحبي" else "My WAQT Buddy")
            ?.trim()?.takeIf { it.isNotBlank() } ?: if (isArabic) "صاحبي" else "My WAQT Buddy"

    private fun createAssistantAvatarView(): FrameLayout {
        val size = dp(42)
        return FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(10) }
            background = createGlass3DDrawable(dp(21))
            clipToOutline = true
            val fallback = TextView(this@MainActivity).apply {
                text = "✦"
                textSize = 21f
                gravity = Gravity.CENTER
                setTextColor(accentTealColor())
            }
            addView(fallback, FrameLayout.LayoutParams(size, size))
            val image = ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
            }
            addView(image, FrameLayout.LayoutParams(size, size))
            val savedUri = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
                .getString("assistant_avatar_uri", null)
            if (!savedUri.isNullOrBlank()) {
                try {
                    image.setImageURI(Uri.parse(savedUri))
                    if (image.drawable != null) {
                        image.visibility = View.VISIBLE
                        fallback.visibility = View.GONE
                    }
                } catch (_: Exception) { }
            }
            activeAssistantAvatarView = image
            setOnClickListener { showAssistantProfileDialog() }
        }
    }

    private fun buildAssistantSystemPrompt(): String {
        val name = assistantName().take(30)
        val tone = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
            .getString("assistant_personality", "friendly") ?: "friendly"
        val style = when (tone) {
            "playful" -> "استخدم روحًا مرحة وخفة ظل طبيعية باعتدال، من غير مبالغة أو تهريج وقت الجد."
            "direct" -> "كن مباشرًا وحاسمًا وعمليًا، مع الحفاظ على الود والاحترام."
            "calm" -> "كن هادئًا ومطمئنًا ومتزنًا، ولا تستعجل المستخدم."
            else -> "كن ودودًا وقريبًا من المستخدم، كصديق ذكي يعرف متى يستمع ومتى ينصح."
        }
        return geminiSystemPrompt + "\n\nهوية المستخدم المخصصة: اسمك الذي اختاره المستخدم هو «$name». عرّف نفسك بهذا الاسم عند الحاجة فقط، ولا تكرر الاسم في كل رد. أسلوبك المفضل: $style"
    }

    private fun showAssistantProfileDialog() {
        val prefs = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        panel.addView(TextView(this).apply {
            text = if (isArabic) "اسم المساعد" else "Assistant name"
            textSize = 13f
            setTextColor(textPrimaryColor())
        })
        val nameInput = EditText(this).apply {
            setText(assistantName())
            hint = if (isArabic) "اكتب الاسم اللي تحبه" else "Choose a name"
            setSingleLine(true)
            setTextColor(textPrimaryColor())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = createPillDrawable(bgElevatedColor(), dp(12), null)
        }
        panel.addView(nameInput)
        val avatarButton = TextView(this).apply {
            text = if (isArabic) "🖼  تغيير صورة المساعد" else "🖼  Change assistant photo"
            textSize = 14f
            setTextColor(accentTealColor())
            setPadding(0, dp(16), 0, dp(10))
            setOnClickListener { assistantAvatarPickerLauncher.launch(arrayOf("image/*")) }
        }
        panel.addView(avatarButton)

        panel.addView(TextView(this).apply {
            text = if (isArabic) "الصوت" else "Voice"
            textSize = 13f
            setTextColor(textPrimaryColor())
            setPadding(0, dp(10), 0, dp(4))
        })
        val voiceOptions = if (isArabic) listOf("صوت نسائي", "صوت رجالي") else listOf("Female voice", "Male voice")
        val voiceSpinner = Spinner(this)
        voiceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voiceOptions)
        voiceSpinner.setSelection(if ((prefs.getString("assistant_voice_gender", "female") ?: "female") == "male") 1 else 0)
        panel.addView(voiceSpinner)

        panel.addView(TextView(this).apply {
            text = if (isArabic) "أسلوب الشخصية" else "Personality style"
            textSize = 13f
            setTextColor(textPrimaryColor())
            setPadding(0, dp(12), 0, dp(4))
        })
        val toneOptions = if (isArabic) listOf("ودود وقريب", "مرح وخفيف الظل", "مباشر وعملي", "هادئ ومتزن")
            else listOf("Friendly", "Playful", "Direct and practical", "Calm")
        val toneValues = listOf("friendly", "playful", "direct", "calm")
        val toneSpinner = Spinner(this)
        toneSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, toneOptions)
        toneSpinner.setSelection(toneValues.indexOf(prefs.getString("assistant_personality", "friendly")).coerceAtLeast(0))
        panel.addView(toneSpinner)

        AlertDialog.Builder(this)
            .setTitle(if (isArabic) "تخصيص شخصية المساعد" else "Customize assistant")
            .setView(panel)
            .setPositiveButton(if (isArabic) "حفظ" else "Save") { _, _ ->
                val gender = if (voiceSpinner.selectedItemPosition == 1) "male" else "female"
                prefs.edit()
                    .putString("assistant_name", nameInput.text.toString().trim().take(30).ifBlank { if (isArabic) "صاحبي" else "My WAQT Buddy" })
                    .putString("assistant_voice_gender", gender)
                    .putString("assistant_personality", toneValues[toneSpinner.selectedItemPosition.coerceIn(0, toneValues.lastIndex)])
                    .apply()
                activeAssistantNameView?.text = assistantName()
                chatTts?.stop()
                chatTts?.shutdown()
                chatTts = null
                Toast.makeText(this, if (isArabic) "تم حفظ شخصية المساعد" else "Assistant profile saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(if (isArabic) "إلغاء" else "Cancel", null)
            .show()
    }

    private fun showChatHistoryDialog(parentDialog: Dialog) {
        val chats = chatHistoryStore.listChats()
        if (chats.isEmpty()) {
            Toast.makeText(this, if (isArabic) "لسه مفيش محادثات محفوظة" else "No saved conversations yet", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = chats.map { chat ->
            val date = java.text.SimpleDateFormat("dd/MM  hh:mm a", if (isArabic) Locale("ar", "EG") else Locale.US).format(java.util.Date(chat.updatedAt))
            "${chat.title}  ·  $date"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(if (isArabic) "المحادثات السابقة" else "Previous chats")
            .setItems(labels) { _, which ->
                val selected = chats[which]
                parentDialog.dismiss()
                showAIChatCopilotModal(selected.id)
            }
            .setNegativeButton(if (isArabic) "إغلاق" else "Close", null)
            .setNeutralButton(if (isArabic) "حذف المحادثة الحالية" else "Delete current chat") { _, _ ->
                chatHistoryStore.deleteChat(activeChatId)
                parentDialog.dismiss()
                showAIChatCopilotModal()
            }
            .show()
    }

    // ------------------------------------------------------------------
    // Gemini-powered planner
    // ------------------------------------------------------------------
    private val geminiHistory = mutableListOf<Pair<String, String>>()

    private fun geminiKey(): String = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        .getString("gemini_api_key", "") ?: ""

    private fun groqKey(): String = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        .getString("groq_api_key", "") ?: ""

    private fun openRouterKey(): String = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        .getString("openrouter_api_key", "") ?: ""

    private fun hasAnyAssistantKey(): Boolean =
        geminiKey().isNotBlank() || groqKey().isNotBlank() || openRouterKey().isNotBlank()

    private fun geminiModel(): String = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        .getString("gemini_model", GeminiClient.DEFAULT_MODEL) ?: GeminiClient.DEFAULT_MODEL

    private fun showGeminiKeyDialog(onSaved: (() -> Unit)? = null) {
        val prefs = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }

        fun addProviderField(title: String, hint: String, value: String, helpUrl: String): EditText {
            panel.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(4))
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 13f
                    setTextColor(textPrimaryColor())
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(TextView(this@MainActivity).apply {
                    text = "↗"
                    textSize = 18f
                    setTextColor(accentTealColor())
                    setPadding(dp(10), dp(2), dp(4), dp(2))
                    contentDescription = if (isArabic) "الحصول على مفتاح API" else "Get API key"
                    setOnClickListener {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl)))
                        } catch (_: Exception) {
                            Toast.makeText(this@MainActivity, if (isArabic) "تعذر فتح صفحة المفاتيح" else "Could not open API key page", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            })
            return EditText(this).apply {
                this.hint = hint
                setText(value)
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setPadding(dp(14), dp(12), dp(14), dp(12))
                panel.addView(this)
            }
        }

        val geminiInput = addProviderField("Gemini API key", if (isArabic) "مفتاح Gemini" else "Paste Gemini API key", geminiKey(), "https://aistudio.google.com/app/apikey")
        val groqInput = addProviderField("Groq API key — fallback 1", if (isArabic) "مفتاح Groq المجاني" else "Paste Groq API key", groqKey(), "https://console.groq.com/keys")
        val routerInput = addProviderField("OpenRouter API key — fallback 2", if (isArabic) "مفتاح OpenRouter المجاني" else "Paste OpenRouter API key", openRouterKey(), "https://openrouter.ai/settings/keys")

        panel.addView(TextView(this).apply {
            text = if (isArabic) "موديل Gemini الأساسي" else "Primary Gemini model"
            textSize = 13f
            setTextColor(textPrimaryColor())
            setPadding(0, dp(14), 0, dp(4))
        })
        val models = listOf(GeminiClient.DEFAULT_MODEL, GeminiClient.FALLBACK_MODEL)
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, models)
        spinner.setSelection(models.indexOf(geminiModel()).coerceAtLeast(0))
        panel.addView(spinner)

        val scrollPanel = ScrollView(this).apply {
            addView(panel)
            isFillViewport = true
        }
        AlertDialog.Builder(this)
            .setTitle(if (isArabic) "إعدادات مزودي الذكاء الاصطناعي" else "AI provider settings")
            .setMessage(
                if (isArabic) "WAQT هيجرب Gemini الأول، ولو الخدمة وقفت أو وصلت للحد ينتقل تلقائيًا إلى Groq ثم OpenRouter. أضف المفاتيح المتاحة عندك فقط."
                else "WAQT tries Gemini first, then automatically falls back to Groq and OpenRouter when a provider is unavailable or rate-limited. Add only the keys you have."
            )
            .setView(scrollPanel)
            .setPositiveButton(if (isArabic) "حفظ" else "Save") { _, _ ->
                prefs.edit()
                    .putString("gemini_api_key", geminiInput.text.toString().trim())
                    .putString("groq_api_key", groqInput.text.toString().trim())
                    .putString("openrouter_api_key", routerInput.text.toString().trim())
                    .putString("gemini_model", models[spinner.selectedItemPosition.coerceIn(0, models.lastIndex)])
                    .apply()
                Toast.makeText(this, if (isArabic) "✅ اتحفظت إعدادات مزودي الذكاء الاصطناعي" else "AI provider settings saved", Toast.LENGTH_SHORT).show()
                onSaved?.invoke()
            }
            .setNegativeButton(if (isArabic) "إلغاء" else "Cancel", null)
            .show()
    }

    private fun buildDaySnapshot(): String {
        val today = ReminderRepository.currentEpochDay()
        val now = Calendar.getInstance()
        val sb = StringBuilder()
        sb.append("الوقت الآن: ${String.format(Locale.US, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))}، اليوم: ${(weekDayNamesAr)[now.get(Calendar.DAY_OF_WEEK) - 1]}\n")
        val items = ReminderRepository.getAllItems(this)
        val tasks = items.filter { !it.isHabit }
        sb.append("المهام (${tasks.size}):\n")
        tasks.take(15).forEach {
            val done = it.isCompletedToday || it.completionPercentage >= 100
            val whenTxt = when {
                it.dueDateEpochDay < 0 -> "بدون موعد"
                it.dueDateEpochDay < today -> "متأخرة"
                it.dueDateEpochDay == today -> "اليوم ${String.format(Locale.US, "%02d:%02d", it.hour, it.minute)}"
                else -> "قادمة"
            }
            sb.append("- ${it.title} | أهمية: ${it.priority} | $whenTxt | ${if (done) "منجزة" else "غير منجزة (${it.completionPercentage}%)"}\n")
        }
        val habits = items.filter { it.isHabit }
        sb.append("العادات (${habits.size}):\n")
        habits.take(10).forEach { sb.append("- ${it.title.take(70)} | سلسلة: ${it.streakCount} يوم | ${if (it.isCompletedToday) "اتعملت النهارده" else "لسه النهارده"}\n") }
        val goal = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).getInt(ReminderRepository.KEY_DAILY_FOCUS_GOAL_MINS, 120)
        val hist = ReminderRepository.getFocusHistory(this)
        sb.append("التركيز النهارده: ${ReminderRepository.getTodayFocusMinutes(this)} دقيقة من هدف $goal دقيقة، جلسات اليوم: ${hist.count { it.epochDay == today }}\n")
        val notes = ReminderRepository.getAllNotes(this)
        sb.append("المدونة والملاحظات (${notes.size}):\n")
        notes.take(8).forEach { sb.append("- ${it.title.take(60)}: ${it.content.take(100).replace('\n', ' ')}\n") }
        val journal = getDiaryPreferences()
        val journalKeys = journal.all.keys.filter { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }.sortedDescending().take(3)
        if (journalKeys.isNotEmpty()) {
            sb.append("آخر اليوميات المحفوظة:\n")
            journalKeys.forEach { key -> sb.append("- $key: ${journal.getString(key, "").orEmpty().take(180).replace('\n', ' ')}\n") }
        }
        return sb.toString()
    }

    private val geminiSystemPrompt = """
أنت WAQT Mentor — مدرب وموجّه شخصي ذكي، وليس مجرد بوت لتحويل الكلام إلى مهام.
تحدث بالعربية المصرية الطبيعية، بود ووضوح وصراحة واحترام. كن عمليًا، اسأل بذكاء، ولا تجامل المستخدم على حساب الحقيقة.

هويتك وطريقة تفكيرك:
- تعامل مع المستخدم كشخص تريد مساعدته على التفكير والتعلم وتحقيق أهدافه، وليس كقائمة مهام.
- افهم المقصود والسياق قبل إعطاء الحل. لا تفترض مستوى المستخدم أو وقته أو موارده.
- إذا كانت معلومة أساسية ناقصة، اسأل سؤالًا واحدًا أو سؤالين كحد أقصى في الرسالة، ثم ابنِ على الإجابة. لا تحول الحوار إلى استبيان طويل.
- إذا كان الهدف واضحًا بما يكفي، ابدأ بخطة عملية بدل الإكثار من الأسئلة.
- كن صريحًا إذا كان الهدف أو الموعد غير واقعي، واشرح السبب واقترح بديلًا قابلًا للتنفيذ.
- لا تقدم نصائح عامة محفوظة. اربط اقتراحاتك بظروف المستخدم وبياناته وسياق المحادثة.

عند التعامل مع هدف أو مشروع:
1) وضّح النتيجة النهائية وكيف سنعرف أنها تحققت.
2) حدّد الفجوة بين الوضع الحالي والنتيجة المطلوبة، واسأل عن المستوى الحالي إذا لم يكن معروفًا.
3) قسّم الطريق إلى مراحل متسلسلة، ولكل مرحلة نتيجة واضحة.
4) حوّل المرحلة الحالية إلى خطوات عملية صغيرة، مع ترتيب الأولويات والوقت أو المدة التقديرية عندما تتوفر معلومات كافية.
5) اذكر العقبات المحتملة وكيفية التعامل معها، ولا تملأ الخطة بتفاصيل غير ضرورية.
6) اختم بأفضل خطوة تالية واحدة، أو بسؤال محدد يساعد على التقدم.

في التعليم والتوجيه:
- اشرح من مستوى المستخدم، بمثال عملي، ثم اقترح تمرينًا أو طريقة لاختبار الفهم عند ملاءمة ذلك.
- ساعد المستخدم على اكتشاف الخطأ بنفسه، ولا تكتفِ بإعطاء الإجابة النهائية.
- لا تدّعِ خبرة مهنية أو يقينًا في موضوع لا تعرفه. في الطب والقانون والمال والسلامة، وضّح حدودك وشجّع الرجوع لمختص عند الحاجة.

استخدام بيانات WAQT:
- لقطة اليوم قد تحتوي على المهام والعادات والتركيز. استخدمها فقط عندما تفيد السؤال، ولا تجعل كل محادثة عن تنظيم اليوم.
- لا تكرر مهام المستخدم ولا تفترض أن كل هدف يجب أن يتحول إلى مهام يومية.
- فرّق بين النقاش، والتعلّم، والتخطيط، وطلب تنفيذ إجراء داخل التطبيق.

قواعد تنفيذ الإجراءات (مهمة جدًا):
- لا تضف أو تكمل أو تحذف أو تعدّل أي مهمة لمجرد أن المستخدم ذكرها أو ناقشها.
- عند اقتراح خطة، اعرضها أولًا للمراجعة. لا تنشئ مهامًا إلا إذا طلب المستخدم صراحة إضافة الخطوات إلى WAQT أو وافق بوضوح على خطة سبق عرضها.
- طلب خطة أو مناقشة هدف لا يعني الموافقة على إنشاء المهام.
- لا تنفذ إجراءً غير متاح لك، ولا تدّعِ أنك نفذته.
- لا تكرر إضافة مهمة موجودة بالفعل.
- عند إكمال أو حذف مهمة أو عادة أو ملاحظة، لا تختَر أول نتيجة عشوائيًا. استخدم تطابق الاسم الكامل، أو نتيجة جزئية وحيدة فقط؛ إذا تعددت النتائج اطلب من المستخدم تحديد الاسم.

- التزم حرفيًا بمخطط الإجراءات والأسماء والقيم المسموح بها أدناه؛ لا تخترع أنواع إجراءات أو أسماء حقول أو قيمًا غير مذكورة.
- add_task: title مطلوب؛ الحقول الاختيارية الوحيدة هي priority (high/medium/low) وhour (0–23) وminute (0–59) وday_offset (0–30). لا ترسل description مع المهمة.
- add_habit: title مطلوب؛ category اختيارية. استخدم health/study/religion/sports للفئات الافتراضية، أو اسم/معرّف فئة مخصصة موجودة بالفعل في التطبيق إذا ذكرها المستخدم. لا تخترع فئة مخصصة. وpriority واحدة من high/medium/low، وhour من 0–23، وminute من 0–59، وtarget_value عدد صحيح من 1–1000، وtarget_unit نص غير فارغ، وhas_reminder قيمة true/false. لا ترسل أي حقل اختياري بقيمة فارغة أو بنوع خاطئ.
- update_task: title يحدد المهمة الحالية، ويجب إرسال حقل تغيير واحد على الأقل من new_title أو priority أو hour أو minute أو day_offset. أرسل فقط الحقول التي يريد المستخدم تغييرها، مع الالتزام بنفس الحدود أعلاه.
- update_habit: title يحدد العادة الحالية، ويجب إرسال حقل تغيير واحد على الأقل من new_title أو priority أو hour أو minute أو category أو target_value أو target_unit أو has_reminder. category من القيم الأربع المذكورة، وtarget_value من 1–1000.
- add_note يتطلب content غير فارغ. update_note يتطلب title للملاحظة الحالية، ومعه new_title غير فارغ أو content غير فارغ على الأقل. لا ترسل محتوى فارغًا.
- set_task_progress يستخدم percentage من 0 إلى 100 فقط؛ set_habit_progress يستخدم progress من 0 إلى 1000 فقط. لا تخلط بين الحقلين أو النطاقين.
- save_journal_entry يتطلب content غير فارغ، ويمكنه استخدام date بصيغة yyyy-MM-dd؛ update_journal_entry يتطلب date صالحًا وcontent غير فارغ؛ delete_journal_entry يتطلب date صالحًا. لا تخمّن تاريخًا غير معروف.
- set_ambient_volume من 0 إلى 100؛ set_focus_duration وset_break_duration من 1 إلى 240 دقيقة؛ start_focus من 1 إلى 180 دقيقة؛ set_goal من 15 إلى 720 دقيقة.
- كل حقل اختياري إذا أُرسل يجب أن يكون من النوع الصحيح وغير فارغ للنصوص. لا تضع أمثلة القيم الوصفية مثل "high|medium|low" كنص فعلي؛ اختر قيمة واحدة فقط.
- عند عدم معرفة معلومة مطلوبة، اسأل المستخدم بدل اختراعها أو إصدار action ناقص. لا ترسل action لمجرد شرح ما ستفعله؛ أرسلها فقط عند طلب التنفيذ الصريح أو الموافقة الواضحة.

تنسيق الرد:
أخرج JSON صالحًا فقط دون Markdown fences أو أي نص خارجه، بالشكل:
{"reply":"الرد الطبيعي للمستخدم","actions":[]}
اكتب داخل reply إجابة مفيدة ومنظمة. عند بناء خطة استخدم عناوين ومراحل ونقاط قصيرة داخل النص، ولا تختصر الخطة في ٤ سطور إذا كانت تحتاج تفصيلًا.
الـ actions اختيارية، ولا تستخدمها إلا بعد طلب صريح أو موافقة واضحة:
{"type":"add_task","title":"عنوان واضح وقابل للتنفيذ","priority":"high|medium|low","hour":0-23,"minute":0-59,"day_offset":0}
{"type":"add_habit","title":"اسم العادة","category":"health|study|religion|sports","priority":"high|medium|low","hour":0-23,"minute":0-59,"has_reminder":true,"target_value":1,"target_unit":"صفحة أو دقيقة أو كوب"}
{"type":"complete_task","title":"جزء مميز من عنوان مهمة موجودة"}
{"type":"reopen_task","title":"عنوان المهمة لإعادة فتحها"}
{"type":"set_task_progress","title":"عنوان المهمة","percentage":0-100}
{"type":"update_task","title":"عنوان المهمة الحالية","new_title":"العنوان الجديد اختياري","priority":"high|medium|low","hour":0-23,"minute":0-59,"day_offset":0}
{"type":"update_habit","title":"اسم العادة الحالية","new_title":"الاسم الجديد اختياري","priority":"high|medium|low","hour":0-23,"minute":0-59,"category":"health|study|religion|sports","target_value":1,"target_unit":"صفحة أو دقيقة أو كوب","has_reminder":true}
{"type":"update_note","title":"عنوان الملاحظة الحالية","new_title":"العنوان الجديد اختياري","content":"المحتوى الجديد"}
{"type":"pin_note","title":"عنوان الملاحظة لتثبيتها"}
{"type":"unpin_note","title":"عنوان الملاحظة لإلغاء تثبيتها"}
{"type":"delete_task","title":"جزء مميز من عنوان المهمة — يتطلب تأكيد المستخدم"}
{"type":"delete_habit","title":"جزء مميز من اسم العادة — يتطلب تأكيد المستخدم"}
{"type":"delete_note","title":"عنوان الملاحظة — يتطلب تأكيد المستخدم"}
{"type":"add_note","title":"عنوان","content":"المحتوى"}
{"type":"save_journal_entry","date":"yyyy-MM-dd","content":"نص اليوميات"}
{"type":"update_journal_entry","date":"yyyy-MM-dd","content":"النص الكامل الجديد للتدوينة الموجودة"}
{"type":"delete_journal_entry","date":"yyyy-MM-dd — يتطلب تأكيد المستخدم"}
{"type":"complete_habit","title":"جزء مميز من اسم العادة"}
{"type":"set_habit_progress","title":"اسم العادة","progress":0-1000}
{"type":"start_focus","minutes":25}
{"type":"pause_focus"}
{"type":"resume_focus"}
{"type":"stop_focus"}
{"type":"set_focus_duration","minutes":25}
{"type":"set_break_duration","minutes":5}
{"type":"set_tick_tock","enabled":true}
{"type":"choose_alarm_ringtone"}
{"type":"choose_custom_audio"}
{"type":"preview_custom_audio"}
{"type":"stop_custom_audio"}
{"type":"set_goal","minutes":120}
{"type":"set_ambient_sound","sound":"off|rain|waves|fire|forest|water|library|wind|night"}
{"type":"set_ambient_volume","volume":0-100}
{"type":"set_theme","mode":"dark|light|toggle"}
{"type":"set_language","language":"ar|en"}
{"type":"open_app_settings"}
{"type":"open_app_tour"}
{"type":"export_backup"}
{"type":"open_notification_settings"}
{"type":"set_auto_update_check","enabled":true}
{"type":"check_for_updates"}
{"type":"navigate_tab","tab":"tasks|habits|focus|notes"}
{"type":"open_notes_section","section":"notes|journal"}
لا تستخدم navigate_tab أو open_notes_section لمجرد ذكر قسم؛ استخدمهما عندما يطلب المستخدم فتحه أو الانتقال إليه صراحة.
لو لا يوجد إجراء مطلوب، أرسل actions كمصفوفة فارغة.

    """.trimIndent()

    /**
     * Finds a valid WAQT response object, tolerating Markdown fences and
     * surrounding prose while rejecting unrelated or malformed JSON objects.
     */
    private fun extractAssistantResponseObject(raw: String): org.json.JSONObject? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        fun isValidResponse(candidate: org.json.JSONObject): Boolean {
            val reply = candidate.opt("reply")
            if (reply !is String || reply.isBlank()) return false
            if (candidate.has("actions") && candidate.opt("actions") !is org.json.JSONArray) return false
            return true
        }

        try {
            val whole = org.json.JSONObject(
                text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            )
            if (isValidResponse(whole)) return whole
        } catch (_: Exception) { }

        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        for (index in text.indices) {
            val ch = text[index]
            if (inString) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') inString = false
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) start = index
                    depth++
                }
                '}' -> {
                    if (depth > 0) depth--
                    if (depth == 0 && start >= 0) {
                        val candidateText = text.substring(start, index + 1)
                        try {
                            val candidate = org.json.JSONObject(candidateText)
                            if (isValidResponse(candidate)) return candidate
                        } catch (_: Exception) { }
                        start = -1
                    }
                }
            }
        }
        return null
    }

    private fun sendToAssistant(userText: String?, box: LinearLayout, scroll: ScrollView, media: GeminiClient.MediaInput? = null) {
        if (media != null && geminiKey().isBlank()) {
            box.addView(createAIMessageBubble(
                if (isArabic) "الصور والرسائل الصوتية محتاجة مفتاح Gemini؛ اربطه من زر 🔑. المحادثة النصية تقدر تشتغل بـ Groq أو OpenRouter."
                else "Images and voice messages currently require a Gemini key. Text chat can use Groq or OpenRouter."
            ))
            return
        }
        if (!hasAnyAssistantKey()) {
            if (userText != null) {
                chatHistoryStore.addMessage(activeChatId, "user", userText)
                processUserMessageWithLocalAI(userText, box, scroll)
            }
            return
        }
        val typing = createAIMessageBubble("…")
        box.addView(typing)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

        val snapshot = buildDaySnapshot()
        val cleanUserText = userText?.trim().orEmpty()
        val persistedUserText = when {
            cleanUserText.isNotBlank() -> cleanUserText
            media?.label == "image" -> "🖼 ${if (isArabic) "صورة مرفقة" else "Image attached"}"
            media?.label == "voice" -> "🎙 ${if (isArabic) "رسالة صوتية" else "Voice message"}"
            else -> ""
        }
        if (persistedUserText.isNotBlank()) chatHistoryStore.addMessage(activeChatId, "user", persistedUserText)
        val prompt = if (userText == null)
            "[لقطة اليوم الحالية]\n$snapshot\nابدأ كـ WAQT Mentor: رحّب بالمستخدم باختصار، ووضّح أنك تساعده في التفكير والتعلم وتحقيق الأهداف، ثم اسأله سؤالًا واحدًا مفتوحًا: إيه أهم حاجة حابب نشتغل عليها دلوقتي؟ لا تنشئ مهامًا."
        else
            "[لقطة اليوم الحالية]\n$snapshot\n[رسالة المستخدم]\n$cleanUserText"
        // Do not resend old day snapshots: they make every turn slower and inflate the request.
        // Keep only the last three exchanges, then append the latest snapshot and message.
        val requestHistory = geminiHistory.takeLast(6).toMutableList().apply { add("user" to prompt) }
        val previousAssistantReply = lastAssistantReply

        GeminiClient.generate(geminiKey(), geminiModel(), buildAssistantSystemPrompt(), requestHistory, media, groqKey(), openRouterKey()) { result ->
            box.removeView(typing)
            result.onSuccess { raw ->
                var replyText = raw
                var actions: org.json.JSONArray? = null
                var malformedActionCount = 0
                val structuredResponse = extractAssistantResponseObject(raw)
                if (structuredResponse != null) {
                    replyText = structuredResponse.optString("reply")
                    val returnedActions = structuredResponse.optJSONArray("actions")
                    if (returnedActions != null) {
                        actions = org.json.JSONArray()
                        for (actionIndex in 0 until returnedActions.length()) {
                            val action = returnedActions.optJSONObject(actionIndex)
                            if (action != null && action.optString("type").isNotBlank()) {
                                actions.put(action)
                            } else {
                                malformedActionCount++
                            }
                        }
                    }
                } else if (raw.trimStart().startsWith("{") || raw.trimStart().startsWith("```")) {
                    replyText = if (isArabic) {
                        "⚠️ وصلني رد بتنسيق غير سليم من Gemini، فمقدرتش أعرضه أو أنفذ أي أوامر منه. جرّب تبعت رسالتك تاني."
                    } else {
                        "⚠️ Gemini returned an invalid response format, so I couldn't display it or execute any actions. Please try again."
                    }
                }
                geminiHistory.add("user" to (cleanUserText.ifBlank { "ابدأ بتقييم يومي الحالي." }))
                geminiHistory.add("model" to replyText)
                while (geminiHistory.size > 6) geminiHistory.removeAt(0)
                lastAssistantReply = replyText
                chatHistoryStore.addMessage(activeChatId, "model", replyText)
                box.addView(createAIMessageBubble(replyText))
                // A submitted voice recording is itself the user's message. The transcription is inside
                // Gemini's media input, so cleanUserText only contains the generic audio instruction.
                // Allow actions returned from that voice request while keeping the normal text-intent
                // and explicit-approval gate for ordinary chat turns.
                val isExplicitVoiceRequest = media?.label == "voice"
                val actionExecutionAllowed = isExplicitVoiceRequest ||
                    shouldAllowAssistantActions(cleanUserText, previousAssistantReply)
                if (actions != null && actions.length() > 0) {
                    if (actionExecutionAllowed) {
                        applyAssistantActions(actions, box)
                    } else {
                        box.addView(createAIMessageBubble(
                            if (isArabic) {
                                "🔒 فهمت إن فيه إجراء ممكن يتنفذ، لكن مش هغيّر بياناتك من غير طلب واضح منك. اكتب الأمر صراحة، زي: «ضيف المهمة» أو «حدّث العادة»."
                            } else {
                                "🔒 I detected a possible app action, but I won't change your data without a clear request. Please state the action explicitly."
                            }
                        ))
                    }
                }
                if (malformedActionCount > 0) {
                    box.addView(createAIMessageBubble(
                        if (isArabic) "⚠️ تجاهلت $malformedActionCount أمر غير مكتمل من رد المساعد، ولم أنفذه."
                        else "⚠️ Ignored $malformedActionCount malformed action(s); nothing was executed for them."
                    ))
                }
                if (autoSpeakAssistantReply) {
                    autoSpeakAssistantReply = false
                    speakLastAssistantReply()
                }
            }.onFailure { e ->
                autoSpeakAssistantReply = false
                val m = e.message ?: ""
                val friendly = when {
                    m.contains("HTTP 400") || m.contains("HTTP 403") -> if (isArabic) "المفتاح مش شغال. اضغطي 🔑 فوق وحطي مفتاح صحيح." else "Invalid key."
                    m.contains("HTTP 429") -> if (isArabic) "المزودين المتاحين وصلوا للحد المجاني أو مشغولين حاليًا. جرّب بعد شوية أو أضف مفتاح مزود تاني." else "Available providers are rate-limited or busy. Try again later or add another provider key."
                    m.contains("HTTP 503") || m.contains("HTTP 500") || m.contains("HTTP 502") || m.contains("HTTP 504") -> if (isArabic) "خدمة Gemini مشغولة مؤقتًا. جرّبي تاني بعد لحظة." else "Gemini is temporarily busy. Please try again shortly."
                    m.contains("HTTP 404") -> if (isArabic) "موديل Gemini المختار مش متاح للمفتاح ده. افتحي 🔑 ثم اختاري موديل متاح." else "The selected Gemini model is unavailable for this key. Choose another model."
                    m.contains("HTTP 400") || m.contains("HTTP 401") || m.contains("HTTP 403") || m.contains("API key") -> if (isArabic) "في مشكلة في مفاتيح مزودي الذكاء الاصطناعي. راجع مفاتيح Gemini وGroq وOpenRouter من زر 🔑." else "One or more AI provider keys are invalid. Check your Gemini, Groq, and OpenRouter keys."
                    m.contains("SocketTimeoutException") || m.contains("timeout", ignoreCase = true) -> if (isArabic) "الرد اتأخر بسبب الاتصال. جرّبي تاني مع إنترنت أسرع." else "The request timed out. Please try again on a better connection."
                    else -> if (isArabic) "حصلت مشكلة: ${m.take(140)}" else "Request failed: ${m.take(140)}"
                }
                box.addView(createAIMessageBubble("⚠️ $friendly"))
            }
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun startChatVoiceRecording() {
        if (chatIsRecording) return
        try {
            val file = java.io.File(cacheDir, "waqt_chat_${System.currentTimeMillis()}.m4a")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(64000)
            recorder.setAudioSamplingRate(22050)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            chatRecorder = recorder
            chatRecordingFile = file
            chatIsRecording = true
            chatRecordButton?.text = "⏹"
            Toast.makeText(this, if (isArabic) "جاري تسجيل الرسالة... اضغط ⏹ للإرسال" else "Recording... tap ⏹ to send", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            chatRecorder?.release()
            chatRecorder = null
            chatIsRecording = false
            chatRecordButton?.text = "🎙"
            Toast.makeText(this, if (isArabic) "تعذر بدء التسجيل: ${e.message}" else "Could not start recording: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopChatVoiceRecording(discard: Boolean = false) {
        if (!chatIsRecording) return
        val file = chatRecordingFile
        try { chatRecorder?.stop() } catch (_: Exception) { }
        try { chatRecorder?.reset(); chatRecorder?.release() } catch (_: Exception) { }
        chatRecorder = null
        chatIsRecording = false
        chatRecordButton?.text = "🎙"
        chatRecordingFile = null
        if (discard || file == null || !file.exists() || file.length() < 512) {
            file?.delete()
            return
        }
        try {
            val bytes = file.readBytes()
            val box = activeChatMessagesBox
            val scroll = activeChatScroll
            if (box != null && scroll != null) {
                box.addView(createUserMessageBubble("🎙 ${if (isArabic) "رسالة صوتية" else "Voice message"}"))
                if (bytes.size > 18 * 1024 * 1024) throw IllegalArgumentException("التسجيل طويل جدًا؛ جرّب رسالة أقصر")
                autoSpeakAssistantReply = true
                sendToAssistant(if (isArabic) "استمع للرسالة الصوتية وافهم مقصدي ورد عليّ بالمصري. لو فيها طلب لتنظيم يومي تعامل معه كرسالة شات عادية." else "Listen to this voice message, understand it, and respond conversationally.",
                    box, scroll, GeminiClient.MediaInput(bytes, "audio/mp4", "voice"))
            }
        } catch (e: Exception) {
            Toast.makeText(this, if (isArabic) "تعذر قراءة التسجيل" else "Could not read recording", Toast.LENGTH_SHORT).show()
        } finally {
            file.delete()
        }
    }

    private fun speakLastAssistantReply() {
        if (lastAssistantReply.isBlank()) {
            Toast.makeText(this, if (isArabic) "لسه مفيش رد أقراه" else "No reply to read yet", Toast.LENGTH_SHORT).show()
            return
        }
        if (chatTts == null) {
            chatTts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) speakWithSelectedAssistantVoice(lastAssistantReply)
                else Toast.makeText(this, if (isArabic) "تحويل النص لصوت غير متاح على الجهاز" else "Text-to-speech is unavailable", Toast.LENGTH_SHORT).show()
            }
        } else {
            speakWithSelectedAssistantVoice(lastAssistantReply)
        }
    }

    private fun speakWithSelectedAssistantVoice(text: String) {
        val tts = chatTts ?: return
        val locale = if (isArabic) Locale("ar", "EG") else Locale.US
        val gender = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
            .getString("assistant_voice_gender", "female") ?: "female"
        val candidates = tts.voices.orEmpty().filter { it.locale.language == locale.language }
        val matchingVoice = candidates.firstOrNull { voice ->
            val voiceName = voice.name.lowercase(Locale.ROOT)
            if (gender == "male") voiceName.contains("male") && !voiceName.contains("female")
            else voiceName.contains("female")
        }
        if (matchingVoice != null) {
            tts.voice = matchingVoice
        } else {
            tts.language = locale
        }
        tts.setSpeechRate(0.96f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "waqt_ai_reply")
    }

    private fun shouldAllowAssistantActions(userText: String, previousReply: String): Boolean {
        val message = userText.trim().lowercase(Locale.ROOT)
        if (message.isBlank()) return false
        val directIntent = listOf(
            "ضيف", "اضيف", "أضيف", "أضف", "اضف", "سجل", "نفذ", "نفّذ", "احذف", "امسح", "الغ", "ألغ", "عدّل", "عدل", "غيّر", "غير", "حوّلها لمهام", "حولها لمهام",
            "اعمل مهام", "أنشئ مهام", "انشئ مهام", "ضيفها للمهام", "أضفها للمهام", "ابدأ جلسة تركيز",
            "شغل صوت", "شغّل صوت", "شغل المطر", "شغل صوت المطر", "شغل الأمواج", "شغل صوت البحر", "شغل صوت النار", "شغل الغابة", "شغل أصوات الليل", "وقف الصوت", "اقفل الصوت", "اطفي الصوت", "غير مستوى الصوت", "قلل الصوت", "علي الصوت", "زوّد الصوت", "زود الصوت", "غير المظهر", "غير الثيم", "الوضع الداكن", "الوضع الفاتح", "غير اللغة", "خلي التطبيق عربي", "خلي التطبيق انجليزي", "حوّل للعربي", "حوّل للإنجليزي", "غير مدة التركيز", "مدة التركيز", "غير مدة الراحة", "مدة الراحة", "شغل صوت الساعة", "شغل تيك تاك", "وقف تيك تاك", "صوت تيك تاك", "غير نغمة المنبه", "غيّر نغمة المنبه", "اختار نغمة المنبه", "اختار صوت المنبه", "change alarm ringtone", "choose alarm sound", "اختار صوت من الجهاز", "اختار ملف صوتي", "ضيف صوت من موبايلي", "اختيار صوت للتركيز", "choose local audio", "pick audio from device", "شغل الصوت المخصص", "شغل الصوت اللي اخترته", "عاين الصوت المختار", "معاينة الصوت", "وقف الصوت المخصص", "وقف صوت التركيز", "preview custom audio", "play selected audio", "stop custom audio",
            "play rain", "play ocean", "play ambient sound", "stop ambient sound", "change volume",
            "ضيف عادة", "أضف عادة", "اعمل عادة", "سجل عادة", "ضيف العادة", "أضف العادة", "سجل العادة",
            "عدّل اليوميات", "عدل اليوميات", "حدّث اليوميات", "حدث اليوميات", "عدّل التدوينة", "عدل التدوينة", "update journal", "edit journal",
            "ابدأ التركيز", "ابدأ جلسة", "شغل التركيز", "شغّل التركيز", "وقف التركيز", "أوقف التركيز", "وقف الجلسة", "استأنف التركيز", "كمل التركيز", "كمّل التركيز", "الغ جلسة التركيز", "ألغي جلسة التركيز",
            "فعّل الفحص التلقائي للتحديثات", "شغل الفحص التلقائي للتحديثات", "وقف الفحص التلقائي للتحديثات", "عطّل الفحص التلقائي للتحديثات", "افحص التحديثات", "افحص وجود تحديث", "check for updates", "enable automatic updates check", "disable automatic updates check", "turn on update checks", "turn off update checks", "افتح", "افتحلي", "افتح الإعدادات", "افتح الاعدادات", "روح للإعدادات", "روح للاعدادات", "settings", "open settings", "show settings", "انتقل", "روح لقسم", "روح على", "وديني", "اعرض قسم", "open ", "go to ", "navigate to ", "show me ", "افتح الجولة التعريفية", "افتح شرح التطبيق", "اعرض الجولة", "صدّر نسخة احتياطية", "صدر نسخة احتياطية", "اعمل نسخة احتياطية", "export backup", "open app tour",
            // Task and habit lifecycle commands
            "add habit", "create a habit", "add this habit", "save as habit",
            "كمل المهمة", "كمّل المهمة", "خلص المهمة", "انه المهمة", "أنهِ المهمة", "علّم المهمة", "علم المهمة", "اعتبر المهمة خلصت",
            "كمل العادة", "كمّل العادة", "خلص العادة", "انه العادة", "أنهِ العادة", "سجل إنجاز العادة", "سجل انجاز العادة",
            "رجع المهمة", "رجّع المهمة", "افتح المهمة تاني", "إعادة فتح المهمة", "اعادة فتح المهمة",
            "حدث تقدم المهمة", "حدّث تقدم المهمة", "نسبة إنجاز المهمة", "نسبة انجاز المهمة", "تقدم العادة", "حدّث تقدم العادة", "حدث تقدم العادة",
            "عدل المهمة", "عدّل المهمة", "عدل العادة", "عدّل العادة", "غير اسم المهمة", "غير اسم العادة",
            "احذف المهمة", "امسح المهمة", "احذف العادة", "امسح العادة", "احذف الملاحظة", "امسح الملاحظة",
            "ضيف ملاحظة", "أضف ملاحظة", "احفظ ملاحظة", "ثبت الملاحظة", "ثبّت الملاحظة", "الغ تثبيت الملاحظة", "ألغ تثبيت الملاحظة", "فك تثبيت الملاحظة",
            "احفظ يومياتي", "سجل يومياتي", "ضيف تدوينة", "أضف تدوينة", "احفظ تدوينة", "احذف التدوينة", "امسح التدوينة",
            "حدد هدف التركيز", "ظبط هدف التركيز", "اضبط هدف التركيز", "غير هدف التركيز", "غيّر هدف التركيز",
            "add these tasks", "add them to my tasks", "create the tasks", "add to my tasks", "do it", "execute the plan",
            "complete task", "finish task", "mark task complete", "complete habit", "finish habit", "mark habit complete",
            "reopen task", "update task", "edit task", "update habit", "edit habit", "set task progress", "set habit progress",
            "add note", "create note", "update note", "edit note", "pin note", "unpin note", "delete note",
            "save journal", "save journal entry", "update journal entry", "delete journal entry", "set daily focus goal"
        ).any { message.contains(it) }
        if (directIntent) return true
        val approval = listOf("موافق", "موافقة", "تمام", "اعتمد", "نفذها", "يلا", "yes", "okay", "ok", "approved", "go ahead")
            .any { message == it || message.startsWith("$it ") }
        val previousAssistantAskedForApproval = listOf("أحولها لمهام", "أضيفها للمهام", "أضيف الخطوات", "أضيف الخطة", "تحب أضيف", "shall i add", "add these to your tasks")
            .any { previousReply.contains(it, ignoreCase = true) }
        return approval && previousAssistantAskedForApproval
    }

    private fun isAssistantActionWellFormed(action: org.json.JSONObject): Boolean {
        fun hasText(key: String): Boolean =
            action.has(key) && !action.isNull(key) && action.optString(key).trim().isNotBlank()
        fun hasNumber(key: String): Boolean = action.has(key) && action.opt(key) is Number
        fun hasBoolean(key: String): Boolean = action.has(key) && action.opt(key) is Boolean
        fun optionalText(key: String): Boolean = !action.has(key) || hasText(key)
        fun optionalBoolean(key: String): Boolean = !action.has(key) || hasBoolean(key)
        fun numberInRange(key: String, range: IntRange): Boolean =
            !action.has(key) || (hasNumber(key) && action.optDouble(key).let { it.isFinite() && it % 1.0 == 0.0 && it.toInt() in range })
        fun valueIn(key: String, allowed: Set<String>): Boolean =
            !action.has(key) || (hasText(key) && action.optString(key).trim() in allowed)
        fun validDate(key: String): Boolean {
            if (!hasText(key)) return false
            val value = action.optString(key).trim()
            if (!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return false
            return try {
                val format = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
                val parsed = format.parse(value) ?: return false
                format.format(parsed) == value
            } catch (_: Exception) { false }
        }

        val type = action.optString("type").trim()
        if (type.isBlank()) return false

        val supportedTypes = setOf(
            "add_task", "add_habit", "reopen_task", "set_task_progress", "complete_task",
            "update_task", "update_habit", "delete_task", "delete_habit", "delete_note",
            "add_note", "update_note", "pin_note", "unpin_note", "update_journal_entry",
            "delete_journal_entry", "save_journal_entry", "set_habit_progress", "complete_habit",
            "start_focus", "pause_focus", "resume_focus", "stop_focus", "set_ambient_sound",
            "set_ambient_volume", "set_theme", "set_language", "set_focus_duration",
            "set_break_duration", "choose_custom_audio", "preview_custom_audio",
            "stop_custom_audio", "choose_alarm_ringtone", "set_tick_tock", "set_goal",
            "set_auto_update_check", "check_for_updates", "open_app_settings", "open_app_tour",
            "export_backup", "open_notification_settings", "navigate_tab", "open_notes_section"
        )
        if (type !in supportedTypes) return false

        val requiredFieldsValid = when (type) {
            "add_task", "add_habit", "reopen_task", "complete_task",
            "delete_task", "delete_habit", "delete_note", "pin_note",
            "unpin_note", "complete_habit" -> hasText("title")
            "update_task" -> hasText("title") &&
                listOf("new_title", "priority", "hour", "minute", "day_offset").any { action.has(it) }
            "update_habit" -> hasText("title") &&
                listOf("new_title", "priority", "hour", "minute", "category", "target_value", "target_unit", "has_reminder").any { action.has(it) }
            "add_note" -> hasText("content")
            "update_note" -> hasText("title") && (hasText("new_title") || hasText("content"))
            "update_journal_entry" -> validDate("date") && hasText("content")
            "delete_journal_entry" -> validDate("date")
            "save_journal_entry" -> hasText("content") && (!action.has("date") || validDate("date"))
            "set_ambient_sound" -> hasText("sound")
            "set_ambient_volume" -> hasNumber("volume")
            "set_language" -> hasText("language")
            "set_focus_duration", "set_break_duration" -> hasNumber("minutes")
            "set_tick_tock", "set_auto_update_check" -> hasBoolean("enabled")
            "navigate_tab" -> hasText("tab")
            "open_notes_section" -> hasText("section")
            "set_task_progress" -> hasText("title") && hasNumber("percentage")
            "set_habit_progress" -> hasText("title") && hasNumber("progress")
            else -> true
        }
        if (!requiredFieldsValid) return false

        return when (type) {
            "add_task", "update_task" ->
                numberInRange("hour", 0..23) && numberInRange("minute", 0..59) &&
                    numberInRange("day_offset", 0..30)
            "add_habit", "update_habit" ->
                numberInRange("hour", 0..23) && numberInRange("minute", 0..59) &&
                    numberInRange("target_value", 1..1000)
            "set_task_progress" -> numberInRange("percentage", 0..100)
            "set_habit_progress" -> numberInRange("progress", 0..1000)
            "set_ambient_volume" -> numberInRange("volume", 0..100)
            "set_focus_duration", "set_break_duration" -> numberInRange("minutes", 1..240)
            "start_focus" -> numberInRange("minutes", 1..180)
            "set_goal" -> numberInRange("minutes", 15..720)
            else -> true
        } &&
            optionalText("new_title") && optionalText("target_unit") &&
            optionalText("description") && optionalText("content") &&
            optionalBoolean("has_reminder") &&
            valueIn("priority", setOf("high", "medium", "low")) &&
            optionalText("category") &&
            valueIn("sound", setOf("off", "rain", "waves", "fire", "forest", "water", "library", "wind", "night")) &&
            valueIn("mode", setOf("dark", "light", "toggle")) &&
            valueIn("language", setOf("ar", "en")) &&
            valueIn("tab", setOf("tasks", "habits", "focus", "notes")) &&
            valueIn("section", setOf("notes", "journal"))
    }

    private fun applyAssistantActions(actions: org.json.JSONArray, box: LinearLayout) {
        val today = ReminderRepository.currentEpochDay()
        for (i in 0 until actions.length()) {
            val a = actions.optJSONObject(i)
            if (a == null || !isAssistantActionWellFormed(a)) {
                val warning = if (isArabic) "⚠️ تجاهلت أمرًا غير مكتمل أو غير مدعوم من المساعد." else "⚠️ Ignored an incomplete or unsupported assistant action."
                box.addView(createAIMessageBubble(warning))
                continue
            }
            var note: String? = null
            when (a.optString("type")) {
                "add_task" -> {
                    val title = a.optString("title").trim()
                    if (title.isNotBlank()) {
                        val nowH = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                        val offset = a.optInt("day_offset", 0).coerceIn(0, 30)
                        val item = ReminderItem(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            isHabit = false,
                            hour = a.optInt("hour", (nowH + 1) % 24).coerceIn(0, 23),
                            minute = a.optInt("minute", 0).coerceIn(0, 59),
                            priority = a.optString("priority", "medium").let { if (it in listOf("high", "medium", "low")) it else "medium" },
                            dueDateEpochDay = shiftEpoch(today, offset)
                        )
                        ReminderRepository.addOrUpdateItem(this, item)
                        try { AlarmScheduler.scheduleNextOccurrence(this, item) } catch (_: Exception) {}
                        note = "➕ ضفت مهمة: $title"
                    }
                }
                "add_habit" -> {
                    val title = a.optString("title").trim().take(100)
                    if (title.isNotBlank()) {
                        val existing = ReminderRepository.getAllItems(this).firstOrNull {
                            it.isHabit && it.title.trim().equals(title, ignoreCase = true)
                        }
                        if (existing != null) {
                            note = "ℹ️ العادة موجودة بالفعل: ${existing.title}"
                        } else {
                            val requestedCategory = a.optString("category", "study").trim()
                            val categories = ReminderRepository.getAllHabitCategories(this)
                            val category = categories.firstOrNull {
                                it.id.equals(requestedCategory, ignoreCase = true) || it.name.equals(requestedCategory, ignoreCase = true)
                            }?.id ?: "study"
                            val hasReminder = a.optBoolean("has_reminder", true)
                            val habit = ReminderItem(
                                id = UUID.randomUUID().toString(), title = title, isHabit = true,
                                hour = a.optInt("hour", 9).coerceIn(0, 23),
                                minute = a.optInt("minute", 0).coerceIn(0, 59),
                                priority = a.optString("priority", "medium").let { if (it in listOf("high", "medium", "low")) it else "medium" },
                                category = category, description = a.optString("description", "").take(240),
                                recurrence = "daily", customDaysCsv = "1,2,3,4,5,6,7",
                                targetValue = a.optInt("target_value", 1).coerceIn(1, 1000),
                                targetUnit = a.optString("target_unit", "").take(20), hasReminder = hasReminder
                            )
                            ReminderRepository.addOrUpdateItem(this, habit)
                            if (hasReminder) try { AlarmScheduler.scheduleNextOccurrence(this, habit) } catch (_: Exception) {}
                            note = "🌱 ضفت عادة: $title${if (hasReminder) " — تذكير ${String.format(Locale.US, "%02d:%02d", habit.hour, habit.minute)}" else " — بدون تذكير"}"
                        }
                    }
                }
                "reopen_task" -> {
                    val q = a.optString("title").trim()
                    val matches = ReminderRepository.getAllItems(this).filter { !it.isHabit && q.isNotBlank() && it.title.contains(q, true) }
                    val hit = matches.firstOrNull { it.title.equals(q, true) } ?: matches.singleOrNull()
                    if (hit == null) {
                        note = if (matches.size > 1) "⚠️ لقيت أكتر من مهمة مطابقة؛ اكتب اسمًا أدق." else "ℹ️ ملقتش المهمة المطلوبة."
                    } else {
                        ReminderRepository.addOrUpdateItem(this, hit.copy(isCompletedToday = false, completionPercentage = 0, lastCompletedEpochDay = -1))
                        note = "↩️ رجّعت المهمة لقائمة غير المنجز: ${hit.title}"
                    }
                }
                "set_task_progress" -> {
                    val q = a.optString("title").trim()
                    val matches = ReminderRepository.getAllItems(this).filter { !it.isHabit && q.isNotBlank() && it.title.contains(q, true) }
                    val hit = matches.firstOrNull { it.title.equals(q, true) } ?: matches.singleOrNull()
                    if (hit == null) {
                        note = if (matches.size > 1) "⚠️ فيه أكتر من مهمة مطابقة؛ حدد اسمها بدقة." else "ℹ️ ملقتش المهمة."
                    } else {
                        val percentage = a.optInt("percentage", hit.completionPercentage).coerceIn(0, 100)
                        ReminderRepository.addOrUpdateItem(this, hit.copy(
                            completionPercentage = percentage,
                            isCompletedToday = percentage >= 100,
                            lastCompletedEpochDay = if (percentage >= 100) today else hit.lastCompletedEpochDay
                        ))
                        note = "📊 حدّثت تقدم «${hit.title}» إلى $percentage٪."
                    }
                }
                "complete_task" -> {
                    val q = a.optString("title").trim()
                    val matches = ReminderRepository.getAllItems(this).filter {
                        !it.isHabit && q.isNotBlank() && it.title.contains(q, ignoreCase = true)
                    }
                    val hit = matches.firstOrNull { it.title.equals(q, ignoreCase = true) } ?: matches.singleOrNull()
                    if (hit == null) {
                        note = if (matches.size > 1) "⚠️ لقيت أكتر من مهمة مطابقة؛ اكتب اسم المهمة بدقة." else "ℹ️ ملقتش المهمة المطلوبة."
                    } else {
                        ReminderRepository.addOrUpdateItem(this, hit.copy(isCompletedToday = true, completionPercentage = 100, lastCompletedEpochDay = today))
                        note = "✅ خلّصت: ${hit.title}"
                    }
                }
                "update_task", "update_habit" -> {
                    val wantHabit = a.optString("type") == "update_habit"
                    val query = a.optString("title").trim()
                    val matches = ReminderRepository.getAllItems(this).filter {
                        it.isHabit == wantHabit && query.isNotBlank() && it.title.contains(query, ignoreCase = true)
                    }
                    val hit = matches.firstOrNull { it.title.equals(query, ignoreCase = true) }
                        ?: matches.singleOrNull()
                    if (hit == null) {
                        note = if (matches.size > 1) "⚠️ لقيت أكتر من عنصر مطابق؛ اكتب اسمًا أدق." else "ℹ️ ملقتش العنصر المطلوب تعديله."
                    } else {
                        val newTitle = a.optString("new_title").trim().ifBlank { hit.title }.take(100)
                        val requestedPriority = a.optString("priority").trim().lowercase(Locale.ROOT)
                        val priority = if (requestedPriority in listOf("high", "medium", "low")) requestedPriority else hit.priority
                        val updated = if (wantHabit) {
                            val requestedCategory = a.optString("category").trim()
                            val categories = ReminderRepository.getAllHabitCategories(this)
                            val category = categories.firstOrNull {
                                requestedCategory.isNotBlank() && (it.id.equals(requestedCategory, true) || it.name.equals(requestedCategory, true))
                            }?.id ?: hit.category
                            hit.copy(
                                title = newTitle,
                                priority = priority,
                                hour = if (a.has("hour")) a.optInt("hour", hit.hour).coerceIn(0, 23) else hit.hour,
                                minute = if (a.has("minute")) a.optInt("minute", hit.minute).coerceIn(0, 59) else hit.minute,
                                category = category,
                                targetValue = if (a.has("target_value")) a.optInt("target_value", hit.targetValue).coerceIn(1, 1000) else hit.targetValue,
                                targetUnit = a.optString("target_unit").trim().takeIf { it.isNotBlank() }?.take(20) ?: hit.targetUnit,
                                hasReminder = if (a.has("has_reminder")) a.optBoolean("has_reminder", hit.hasReminder) else hit.hasReminder
                            )
                        } else {
                            val offset = if (a.has("day_offset")) a.optInt("day_offset", 0).coerceIn(0, 30) else -1
                            hit.copy(
                                title = newTitle,
                                priority = priority,
                                hour = if (a.has("hour")) a.optInt("hour", hit.hour).coerceIn(0, 23) else hit.hour,
                                minute = if (a.has("minute")) a.optInt("minute", hit.minute).coerceIn(0, 59) else hit.minute,
                                dueDateEpochDay = if (offset >= 0) shiftEpoch(today, offset) else hit.dueDateEpochDay
                            )
                        }
                        ReminderRepository.addOrUpdateItem(this, updated)
                        AlarmScheduler.cancelAlarm(this, hit.id)
                        if (!wantHabit || updated.hasReminder) {
                            try { AlarmScheduler.scheduleNextOccurrence(this, updated) } catch (_: Exception) {}
                        }
                        note = if (wantHabit) "🌱 عدّلت العادة: ${updated.title}" else "✏️ عدّلت المهمة: ${updated.title}"
                    }
                }
                "delete_task", "delete_habit" -> {
                    val actionType = a.optString("type")
                    val q = a.optString("title").trim()
                    val wantHabit = actionType == "delete_habit"
                    val matches = ReminderRepository.getAllItems(this).filter {
                        it.isHabit == wantHabit && q.isNotBlank() && it.title.contains(q, ignoreCase = true)
                    }
                    val hit = matches.firstOrNull { it.title.equals(q, ignoreCase = true) } ?: matches.singleOrNull()
                    if (hit != null) {
                        AlertDialog.Builder(this)
                            .setTitle(if (isArabic) "تأكيد الحذف" else "Confirm deletion")
                            .setMessage(if (isArabic) "المساعد بيطلب حذف «${hit.title}». متأكد؟" else "The assistant wants to delete “${hit.title}”. Continue?")
                            .setNegativeButton(if (isArabic) "إلغاء" else "Cancel", null)
                            .setPositiveButton(if (isArabic) "حذف" else "Delete") { _, _ ->
                                ReminderRepository.deleteItem(this, hit.id)
                                AlarmScheduler.cancelAlarm(this, hit.id)
                                Toast.makeText(this, if (isArabic) "تم حذف ${hit.title}" else "Deleted ${hit.title}", Toast.LENGTH_SHORT).show()
                                renderCurrentTab()
                            }.show()
                        note = "⚠️ طلبت تأكيد حذف: ${hit.title}"
                    } else {
                        note = when {
                            matches.size > 1 -> "⚠️ لقيت أكتر من عنصر مطابق؛ اكتب الاسم كاملًا قبل الحذف."
                            else -> if (isArabic) "ملقتش عنصر بالاسم ده." else "I couldn't find that item."
                        }
                    }
                }
                "delete_note" -> {
                    val q = a.optString("title").trim()
                    val matches = ReminderRepository.getAllNotes(this).filter {
                        q.isNotBlank() && (it.title.contains(q, true) || it.content.contains(q, true))
                    }
                    val hit = matches.firstOrNull { it.title.equals(q, true) } ?: matches.singleOrNull()
                    if (hit != null) {
                        AlertDialog.Builder(this)
                            .setTitle(if (isArabic) "تأكيد حذف الملاحظة" else "Confirm note deletion")
                            .setMessage(if (isArabic) "تحذف «${hit.title}»؟" else "Delete “${hit.title}”?")
                            .setNegativeButton(if (isArabic) "إلغاء" else "Cancel", null)
                            .setPositiveButton(if (isArabic) "حذف" else "Delete") { _, _ ->
                                ReminderRepository.deleteNote(this, hit.id)
                                renderCurrentTab()
                            }.show()
                        note = "⚠️ طلبت تأكيد حذف الملاحظة: ${hit.title}"
                    } else {
                        note = if (matches.size > 1) "⚠️ لقيت أكتر من ملاحظة مطابقة؛ اكتب عنوانًا أدق قبل الحذف." else "ℹ️ ملقتش الملاحظة المطلوبة."
                    }
                }
                "add_note" -> {
                    val t = a.optString("title").ifBlank { "ملاحظة" }
                    ReminderRepository.addOrUpdateNote(this, NoteItem(id = UUID.randomUUID().toString(), title = t, content = a.optString("content")))
                    note = "📝 حفظت ملاحظة: $t"
                }
                "update_note" -> {
                    val query = a.optString("title").trim()
                    val matches = ReminderRepository.getAllNotes(this).filter {
                        query.isNotBlank() && (it.title.contains(query, true) || it.content.contains(query, true))
                    }
                    val hit = matches.firstOrNull { it.title.equals(query, true) } ?: matches.singleOrNull()
                    if (hit == null) {
                        note = if (matches.size > 1) "⚠️ لقيت أكتر من ملاحظة مطابقة؛ اكتب عنوانًا أدق." else "ℹ️ ملقتش الملاحظة المطلوبة."
                    } else {
                        val updatedTitle = a.optString("new_title").trim().ifBlank { hit.title }.take(120)
                        val updatedContent = if (a.has("content")) a.optString("content") else hit.content
                        ReminderRepository.addOrUpdateNote(this, hit.copy(title = updatedTitle, content = updatedContent))
                        note = "📝 عدّلت الملاحظة: $updatedTitle"
                    }
                }
                "pin_note", "unpin_note" -> {
                    val q = a.optString("title").trim()
                    val matches = ReminderRepository.getAllNotes(this).filter { q.isNotBlank() && (it.title.contains(q, true) || it.content.contains(q, true)) }
                    val hit = matches.firstOrNull { it.title.equals(q, true) } ?: matches.singleOrNull()
                    if (hit == null) {
                        note = if (matches.size > 1) "⚠️ لقيت أكتر من ملاحظة مطابقة؛ اكتب عنوانًا أدق." else "ℹ️ ملقتش الملاحظة."
                    } else {
                        val pinned = a.optString("type") == "pin_note"
                        ReminderRepository.addOrUpdateNote(this, hit.copy(isPinned = pinned))
                        note = if (pinned) "📌 ثبّت الملاحظة: ${hit.title}" else "📍 ألغيت تثبيت الملاحظة: ${hit.title}"
                    }
                }
                "update_journal_entry" -> {
                    val dateKey = a.optString("date").trim()
                    val value = a.optString("content").trim()
                    val diary = getDiaryPreferences()
                    val validDate = dateKey.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
                    if (!validDate || value.isBlank()) {
                        note = "⚠️ لازم تحدد تاريخًا صحيحًا ومحتوى غير فارغ للتدوينة."
                    } else if (!diary.contains(dateKey)) {
                        note = "ℹ️ مفيش تدوينة محفوظة بتاريخ $dateKey عشان أعدّلها."
                    } else {
                        diary.edit().putString(dateKey, value).apply()
                        note = "✏️ حدّثت تدوينة يوم $dateKey."
                    }
                }
                "delete_journal_entry" -> {
                    val dateKey = a.optString("date").trim()
                    val diary = getDiaryPreferences()
                    if (!dateKey.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                        note = "⚠️ لازم تحدد تاريخًا صحيحًا للتدوينة."
                    } else if (!diary.contains(dateKey)) {
                        note = "ℹ️ مفيش تدوينة محفوظة بتاريخ $dateKey."
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle(if (isArabic) "تأكيد حذف التدوينة" else "Confirm journal deletion")
                            .setMessage(if (isArabic) "المساعد بيطلب حذف تدوينة يوم $dateKey نهائيًا. متأكد؟" else "The assistant wants to permanently delete the journal entry for $dateKey. Continue?")
                            .setNegativeButton(if (isArabic) "إلغاء" else "Cancel", null)
                            .setPositiveButton(if (isArabic) "حذف التدوينة" else "Delete entry") { _, _ ->
                                diary.edit().remove(dateKey).apply()
                                Toast.makeText(this, if (isArabic) "تم حذف تدوينة $dateKey" else "Journal entry deleted", Toast.LENGTH_SHORT).show()
                                renderCurrentTab()
                            }
                            .show()
                        note = "⚠️ طلبت تأكيد حذف تدوينة يوم $dateKey."
                    }
                }
                "save_journal_entry" -> {
                    val dateKey = a.optString("date", java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date()))
                    val value = a.optString("content").trim()
                    if (dateKey.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && value.isNotBlank()) {
                        getDiaryPreferences().edit().putString(dateKey, value).apply()
                        note = "📔 حفظت تدوينة يوم $dateKey"
                    }
                }
                "set_habit_progress" -> {
                    val q = a.optString("title").trim()
                    val matches = ReminderRepository.getAllItems(this).filter { it.isHabit && q.isNotBlank() && it.title.contains(q, true) }
                    val habit = matches.firstOrNull { it.title.equals(q, true) } ?: matches.singleOrNull()
                    if (habit == null) {
                        note = if (matches.size > 1) "⚠️ لقيت أكتر من عادة مطابقة؛ اكتب اسمًا أدق." else "ℹ️ ملقتش العادة."
                    } else {
                        val progress = a.optInt("progress", habit.currentProgressToday).coerceIn(0, 1000)
                        val percentage = ((progress.toFloat() / habit.targetValue.coerceAtLeast(1)) * 100f).toInt().coerceIn(0, 100)
                        ReminderRepository.addOrUpdateItem(this, habit.copy(
                            currentProgressToday = progress,
                            completionPercentage = percentage,
                            isCompletedToday = percentage >= 100,
                            lastCompletedEpochDay = if (percentage >= 100) today else habit.lastCompletedEpochDay
                        ))
                        note = "🌱 حدّثت تقدم عادة «${habit.title}»: $progress ${habit.targetUnit} ($percentage٪)."
                    }
                }
                "complete_habit" -> {
                    val q = a.optString("title").trim()
                    val matches = ReminderRepository.getAllItems(this).filter {
                        it.isHabit && q.isNotBlank() && it.title.contains(q, ignoreCase = true)
                    }
                    val habit = matches.firstOrNull { it.title.equals(q, ignoreCase = true) } ?: matches.singleOrNull()
                    if (habit == null) {
                        note = if (matches.size > 1) "⚠️ لقيت أكتر من عادة مطابقة؛ اكتب اسم العادة بدقة." else "ℹ️ ملقتش العادة المطلوبة."
                    } else {
                        ReminderRepository.addOrUpdateItem(this, habit.copy(isCompletedToday = true, lastCompletedEpochDay = today, currentProgressToday = habit.targetValue, completionPercentage = 100))
                        note = "🌱 سجلت إنجاز العادة: ${habit.title}"
                    }
                }
                "start_focus" -> {
                    val m = a.optInt("minutes", 25).coerceIn(1, 180)
                    currentTab = 2
                    if (isPomodoroRunning) {
                        note = "ℹ️ جلسة التركيز شغالة بالفعل."
                    } else {
                        isBreakSession = false
                        selectedPomodoroMinutes = m
                        pomodoroTotalMillis = m * 60 * 1000L
                        pomodoroRemainingMillis = pomodoroTotalMillis
                        activeFocusSessionId = UUID.randomUUID().toString()
                        renderCurrentTab()
                        startWallClockCountDown(pomodoroRemainingMillis)
                        note = "🍅 بدأت جلسة تركيز لمدة $m دقيقة."
                    }
                }
                "pause_focus" -> {
                    if (isPomodoroRunning) {
                        toggleInAppPomodoro()
                        note = "⏸️ أوقفت جلسة التركيز مؤقتًا."
                    } else {
                        note = "ℹ️ مفيش جلسة تركيز شغالة حاليًا."
                    }
                }
                "resume_focus" -> {
                    if (!isPomodoroRunning && pomodoroRemainingMillis > 0L) {
                        currentTab = 2
                        toggleInAppPomodoro()
                        note = "▶️ استأنفت جلسة التركيز."
                    } else if (isPomodoroRunning) {
                        note = "ℹ️ جلسة التركيز شغالة بالفعل."
                    } else {
                        note = "ℹ️ مفيش جلسة متوقفة لاستئنافها."
                    }
                }
                "stop_focus" -> {
                    if (isPomodoroRunning || pomodoroRemainingMillis < pomodoroTotalMillis) {
                        setPomodoroDuration(selectedPomodoroMinutes)
                        note = "⏹️ أنهيت جلسة التركيز وأعدت ضبط المؤقت."
                    } else {
                        note = "ℹ️ مفيش جلسة نشطة لإيقافها."
                    }
                }
                "set_ambient_sound" -> {
                    val requested = a.optString("sound").trim().lowercase(Locale.ROOT)
                    val soundIndex = when (requested) {
                        "off", "stop", "إيقاف", "توقف" -> 0
                        "rain", "مطر", "المطر" -> 1
                        "waves", "ocean", "أمواج", "الأمواج", "البحر" -> 2
                        "fire", "campfire", "نار", "النار" -> 3
                        "forest", "غابة", "الغابة" -> 4
                        "water", "stream", "جدول الماء", "الماء" -> 5
                        "library", "مكتبة", "المكتبة" -> 6
                        "wind", "air", "هواء", "الهواء" -> 7
                        "night", "ليل", "الليل" -> 8
                        else -> -1
                    }
                    if (soundIndex < 0) {
                        note = "⚠️ مش قادر أحدد الصوت المطلوب. الأصوات المتاحة: المطر، الأمواج، النار، الغابة، جدول الماء، المكتبة، الهواء، الليل، أو إيقاف."
                    } else {
                        selectedAmbientIndex = soundIndex
                        getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
                            .putInt(ReminderRepository.KEY_DEFAULT_SOUND_INDEX, soundIndex).apply()
                        if (soundIndex == 0) stopAmbientSound() else startRichAmbientSound(soundIndex)
                        note = if (soundIndex == 0) "🔇 أوقفت الصوت المحيطي." else "🎧 شغّلت الصوت المحيطي: ${listOf("", "المطر", "أمواج البحر", "نار المخيم", "الغابة", "جدول الماء", "المكتبة", "الهواء", "أصوات الليل")[soundIndex]}."
                    }
                }
                "set_ambient_volume" -> {
                    val volume = a.optInt("volume", -1)
                    if (volume !in 0..100) {
                        note = "⚠️ مستوى الصوت لازم يكون من 0 إلى 100."
                    } else {
                        ambientVolume = (volume / 100f).coerceIn(0f, 1f)
                        soundEngine.setAmbientVolume(ambientVolume)
                        customAudioPlayer?.setVolume(ambientVolume, ambientVolume)
                        getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
                            .putInt(ReminderRepository.KEY_SOUND_VOLUME, volume).apply()
                        note = "🔊 ضبطت مستوى الصوت المحيطي على $volume٪."
                    }
                }
                "set_theme" -> {
                    val requested = a.optString("mode", "toggle").trim().lowercase(Locale.ROOT)
                    val targetDark = when (requested) {
                        "dark", "داكن", "ليل" -> true
                        "light", "فاتح", "نهاري" -> false
                        "toggle", "بدّل", "تبديل" -> !isDarkMode
                        else -> isDarkMode
                    }
                    if (requested !in listOf("dark", "داكن", "ليل", "light", "فاتح", "نهاري", "toggle", "بدّل", "تبديل")) {
                        note = "⚠️ اختار الوضع الداكن أو الفاتح."
                    } else if (targetDark == isDarkMode) {
                        note = if (isArabic) "المظهر المطلوب مفعّل بالفعل." else "That appearance is already active."
                    } else {
                        isDarkMode = targetDark
                        getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
                            .putBoolean(ReminderRepository.KEY_DARK_MODE, isDarkMode).apply()
                        applySystemBarTheme()
                        recreate()
                        note = if (isDarkMode) "🌙 تم تفعيل الوضع الداكن." else "☀️ تم تفعيل الوضع الفاتح."
                    }
                }
                "set_language" -> {
                    val requested = a.optString("language").trim().lowercase(Locale.ROOT)
                    val targetArabic = when (requested) {
                        "ar", "arabic", "عربي", "العربية" -> true
                        "en", "english", "انجليزي", "إنجليزي", "الإنجليزية" -> false
                        else -> isArabic
                    }
                    if (requested !in listOf("ar", "arabic", "عربي", "العربية", "en", "english", "انجليزي", "إنجليزي", "الإنجليزية")) {
                        note = "⚠️ اختار العربية أو الإنجليزية."
                    } else if (targetArabic == isArabic) {
                        note = if (isArabic) "لغة التطبيق عربية بالفعل." else "The app is already in English."
                    } else {
                        isArabic = targetArabic
                        getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
                            .putBoolean("is_arabic", isArabic).apply()
                        window.decorView.layoutDirection = if (isArabic) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                        renderCurrentTab()
                        note = if (isArabic) "🌐 تم تحويل التطبيق للعربية." else "🌐 App language changed to English."
                    }
                }
                "set_focus_duration" -> {
                    val minutes = a.optInt("minutes", -1)
                    if (minutes !in 1..240) {
                        note = "⚠️ مدة التركيز لازم تكون من 1 إلى 240 دقيقة."
                    } else {
                        selectedPomodoroMinutes = minutes
                        getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
                            .putInt(ReminderRepository.KEY_DEFAULT_FOCUS_MINS, minutes).apply()
                        if (!isPomodoroRunning && !isBreakSession) {
                            pomodoroTotalMillis = minutes * 60 * 1000L
                            pomodoroRemainingMillis = pomodoroTotalMillis
                        }
                        note = "🍅 تم ضبط مدة التركيز الافتراضية على $minutes دقيقة."
                    }
                }
                "set_break_duration" -> {
                    val minutes = a.optInt("minutes", -1)
                    if (minutes !in 1..240) {
                        note = "⚠️ مدة الراحة لازم تكون من 1 إلى 240 دقيقة."
                    } else {
                        selectedBreakMinutes = minutes
                        getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
                            .putInt(ReminderRepository.KEY_DEFAULT_BREAK_MINS, minutes).apply()
                        if (!isPomodoroRunning && isBreakSession) {
                            pomodoroTotalMillis = minutes * 60 * 1000L
                            pomodoroRemainingMillis = pomodoroTotalMillis
                        }
                        note = "☕ تم ضبط مدة الراحة الافتراضية على $minutes دقيقة."
                    }
                }
                "choose_custom_audio" -> {
                    try {
                        isPickingAudioFromConsole = false
                        localAudioPickerLauncher.launch(arrayOf("audio/*"))
                        note = if (isArabic) "🎵 فتحت ملفات الصوت على جهازك؛ اختار الملف اللي تحب تستخدمه في جلسات التركيز." else "🎵 Opened your device audio files. Choose the sound you want for focus sessions."
                    } catch (_: Exception) {
                        isPickingAudioFromConsole = false
                        note = if (isArabic) "⚠️ مش قادر أفتح ملفات الصوت على الجهاز." else "⚠️ Could not open audio files on this device."
                    }
                }
                "preview_custom_audio" -> {
                    if (customAudioUri == null) {
                        note = if (isArabic) "⚠️ اختار ملف صوتي الأول، وبعدها أقدر أشغّله لك." else "⚠️ Choose an audio file first, then I can play it for you."
                    } else {
                        playCustomAudio()
                        note = if (isArabic) "▶️ شغّلت معاينة الصوت المخصص." else "▶️ Playing your selected custom audio."
                    }
                }
                "stop_custom_audio" -> {
                    stopCustomAudio()
                    note = if (isArabic) "⏹️ وقفت الصوت المخصص." else "⏹️ Stopped the custom audio."
                }
                "choose_alarm_ringtone" -> {
                    try {
                        val picker = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, if (isArabic) "اختار نغمة المنبه" else "Choose alarm ringtone")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        }
                        ringtonePickerLauncher.launch(picker)
                        note = if (isArabic) "🔔 افتحت لك اختيار نغمة المنبه؛ اختار النغمة واحفظها من النافذة." else "🔔 Opened the alarm ringtone picker. Choose and save a tone there."
                    } catch (_: Exception) {
                        note = if (isArabic) "⚠️ مش قادر أفتح اختيار نغمة المنبه على الجهاز." else "⚠️ Could not open the alarm ringtone picker."
                    }
                }
                "set_tick_tock" -> {
                    if (!a.has("enabled")) {
                        note = "⚠️ حدد تشغيل أو إيقاف صوت تيك-تاك."
                    } else {
                        isTickTockSoundActive = a.optBoolean("enabled")
                        getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
                            .putBoolean(ReminderRepository.KEY_TICK_TOCK_ENABLED, isTickTockSoundActive).apply()
                        syncTickTock()
                        note = if (isTickTockSoundActive) "⏱️ تم تشغيل صوت تيك-تاك أثناء التركيز." else "🔕 تم إيقاف صوت تيك-تاك أثناء التركيز."
                    }
                }
                "set_goal" -> {
                    val m = a.optInt("minutes", 120).coerceIn(15, 720)
                    getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit().putInt(ReminderRepository.KEY_DAILY_FOCUS_GOAL_MINS, m).apply()
                    note = "🎯 هدف التركيز: $m دقيقة"
                }
                "set_auto_update_check" -> {
                    if (!a.has("enabled")) {
                        note = if (isArabic) "⚠️ حدد تشغيل أو إيقاف الفحص التلقائي للتحديثات." else "⚠️ Specify whether automatic update checks should be enabled or disabled."
                    } else {
                        val enabled = a.optBoolean("enabled")
                        getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
                            .putBoolean("ota_auto_check", enabled).apply()
                        note = if (enabled) {
                            if (isArabic) "🔄 فعّلت الفحص التلقائي عن تحديثات وَقْت." else "🔄 Automatic WAQT update checks are enabled."
                        } else {
                            if (isArabic) "🔕 أوقفت الفحص التلقائي عن التحديثات." else "🔕 Automatic update checks are disabled."
                        }
                    }
                }
                "check_for_updates" -> {
                    triggerGentleHaptic()
                    Toast.makeText(this, if (isArabic) "جاري فحص التحديث من GitHub..." else "Checking GitHub for updates...", Toast.LENGTH_SHORT).show()
                    checkForOnlineUpdates(manual = true)
                    note = if (isArabic) "🔄 بدأت فحص التحديثات أونلاين." else "🔄 Started checking for online updates."
                }
                "open_app_settings" -> {
                    showAccordionSettingsDialog()
                    note = if (isArabic) "⚙️ فتحت لك إعدادات وَقْت." else "⚙️ Opened WAQT settings."
                }
                "open_app_tour" -> {
                    showOnboarding(false)
                    note = if (isArabic) "🧭 فتحت لك الجولة التعريفية في وَقْت." else "🧭 Opened the WAQT app tour."
                }
                "export_backup" -> {
                    try {
                        pendingBackupJson = ReminderRepository.exportCloudBackupJson(this)
                        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        backupExportLauncher.launch("waqt-backup-$stamp.json")
                        note = if (isArabic) "💾 جهزت النسخة الاحتياطية. اختار مكان حفظ الملف من النافذة." else "💾 Backup is ready. Choose where to save the file."
                    } catch (_: Exception) {
                        pendingBackupJson = null
                        note = if (isArabic) "⚠️ تعذر تجهيز النسخة الاحتياطية." else "⚠️ Could not prepare the backup."
                    }
                }
                "open_notification_settings" -> {
                    try {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        }
                        startActivity(intent)
                        note = if (isArabic) "🔔 فتحت إعدادات إشعارات وَقْت على جهازك. تقدر تراجع الأذونات وتغيّرها بنفسك." else "🔔 Opened WAQT notification settings. You can review and change permissions there."
                    } catch (_: Exception) {
                        try {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                            note = if (isArabic) "🔔 فتحت صفحة معلومات التطبيق؛ ادخل منها إلى الإشعارات." else "🔔 Opened app info. Go to Notifications from there."
                        } catch (_: Exception) {
                            note = if (isArabic) "⚠️ مش قادر أفتح إعدادات الإشعارات على الجهاز." else "⚠️ Could not open notification settings on this device."
                        }
                    }
                }
                "navigate_tab" -> {
                    val requested = a.optString("tab").trim().lowercase(Locale.ROOT)
                    val target = when (requested) {
                        "tasks", "task", "المهام" -> 0
                        "habits", "habit", "العادات" -> 1
                        "focus", "pomodoro", "التركيز" -> 2
                        "notes", "note", "journal", "المدونة", "مدونتي", "الملاحظات" -> 3
                        else -> -1
                    }
                    if (target >= 0) {
                        if (target != currentTab) performLiquidWaveTransitionToTab(target)
                        note = "↗️ فتحت قسم ${when (target) { 0 -> "المهام"; 1 -> "العادات"; 2 -> "التركيز"; else -> "مدونتي" }}"
                    } else {
                        note = "⚠️ مش قادر أحدد القسم المطلوب فتحه."
                    }
                }
                "open_notes_section" -> {
                    val section = a.optString("section").trim().lowercase(Locale.ROOT)
                    if (section in listOf("notes", "note", "الملاحظات")) {
                        notesSubTab = 0
                        currentTab = 3
                        renderCurrentTab()
                        note = "📝 فتحت الملاحظات."
                    } else if (section in listOf("journal", "diary", "اليوميات", "مدونتي")) {
                        notesSubTab = 1
                        currentTab = 3
                        renderCurrentTab()
                        note = "📔 فتحت اليوميات."
                    } else {
                        note = "⚠️ اختار الملاحظات أو اليوميات."
                    }
                }
                else -> {
                    note = if (isArabic) "⚠️ المساعد أرسل أمرًا غير مدعوم: ${a.optString("type", "غير معروف")}" else "⚠️ Unsupported assistant action: ${a.optString("type", "unknown")}"
                }
            }
            if (note != null) {
                box.addView(TextView(this).apply {
                    text = note
                    textSize = 12f
                    setTextColor(accentTealColor())
                    setPadding(dp(8), dp(2), dp(8), dp(6))
                })
            }
        }
        renderCurrentTab()
    }

    // ------------------------------------------------------------------
    // First-run tour + reliable-reminders permissions + crash reporter
    // ------------------------------------------------------------------
    private var onboardingRefresh: (() -> Unit)? = null
    private var startupChecksDone = false

    private fun installCrashReporter() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
                    .putString("last_crash", android.util.Log.getStackTraceString(e).take(2500)).commit()
            } catch (_: Exception) {}
            prev?.uncaughtException(t, e)
        }
    }

    private fun showLastCrashIfAny(): Boolean {
        val prefs = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        val crash = prefs.getString("last_crash", null) ?: return false
        prefs.edit().remove("last_crash").apply()
        val tv = TextView(this).apply {
            text = crash.lines().take(14).joinToString("\n")
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle(if (isArabic) "⚠️ التطبيق اتقفل آخر مرة" else "⚠️ The app closed last time")
            .setMessage(if (isArabic) "صوّري الشاشة دي وابعتيها للمطوّر عشان يصلّح السبب." else "Send a screenshot of this to the developer.")
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton(if (isArabic) "نسخ" else "Copy") { _, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("crash", crash))
            }
            .setNegativeButton(if (isArabic) "إغلاق" else "Close", null)
            .show()
        return true
    }

    private fun isNotifOk() = (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).areNotificationsEnabled()
    private fun isBatteryOk() = (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
    private fun isExactOk() = if (android.os.Build.VERSION.SDK_INT >= 31) (getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms() else true
    private fun isOverlayOk() = Settings.canDrawOverlays(this)

    private fun openSettingsSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (_: Exception) {}
        }
    }

    private fun askNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 701)
        } else {
            openSettingsSafely(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun askBattery() = openSettingsSafely(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:$packageName") }
    )

    private fun askExactAlarm() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            openSettingsSafely(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply { data = Uri.parse("package:$packageName") })
        }
    }

    private fun askOverlay() = openSettingsSafely(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { data = Uri.parse("package:$packageName") }
    )


    private fun styleGlassDialog(dialog: Dialog) {
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.48f)
            decorView.background = createGlassCardDrawable(false, false)
            decorView.elevation = dp(20).toFloat()
        }
    }

    private fun showOnboarding(jumpToPermissions: Boolean) {
        data class Slide(val icon: String, val ar: String, val en: String, val bodyAr: String, val bodyEn: String, val targetTab: Int, val openSideDock: Boolean = false)
        val slides = listOf(
            Slide("⏳", "أهلاً بيك في وقت", "Welcome to WAQT", "تطبيق واحد لمهامك وعاداتك وتركيزك وملاحظاتك، هنتنقل معاك دلوقتي في كل تاب ونعرفك كل ميزة!", "One app for tasks, habits, focus and notes — let us walk through each tab live!", 0),
            Slide("🎯", "تاب المهام", "Tasks Tab", "كل مهامك هنا مترتبة بالأهمية (عالية/متوسطة/منخفضة). اضغط (+) لإضافة مهمة وتحديد أهميتها، أو اضغط على شارة الأهمية بالكارت لتبديلها!", "Add tasks with the orange +, pick or tap priorities to filter, swipe cards, and earn ranks.", 0),
            Slide("🌱", "تاب العادات", "Habits Tab", "ابني عاداتك يوم بيوم وتابع سلسلتك! اضغط (+) لإضافة عادة جديدة وتحديد أهميتها (مهمة/متوسطة/عادية) وتصنيفها وأيام تكرارها.", "Build streaks, choose habit priority & category, and track daily progress.", 1),
            Slide("🍅", "تاب التركيز والأصوات", "Focus & Ambient Sounds", "مؤقت بومودورو بالساعة الرملية والتيك-تاك الهادي، وأصوات محيطية طبيعية بنقاء عالي ولوب ناعم، مع استراحة تلقائية وإحصائيات لهدفك اليومي.", "Pomodoro timer with seamless ambient sounds, auto breaks and daily goals.", 2),
            Slide("📝", "تاب مدونتي", "My Journal", "اعمل تصنيفاتك بنفسك، ودون تدويناتك أو افتح تدوينة عائمة فوق أي تطبيق!", "Create your own categories and floating quick notes over any app.", 3),
            Slide("✨", "المساعد الذكي", "Smart Assistant", "بيبص على يومك ويساعدك تحقق أهدافك. اربط Gemini أو Groq أو OpenRouter من أيقونة 🔑، وWAQT هيتنقل بينهم تلقائيًا.", "Understands your day, helps with goals, and can switch between Gemini, Groq, and OpenRouter.", 0),
            Slide("🧭", "التنقل والشريط الجانبي", "Navigation & Side Rail", "الشريط السفلي للتنقل بين التابات، والشريط الجانبي فتحتهولك أهو: فيه بحث وإحصائيات وتذكيرات سريعة (واسحبه لأي حافة).", "Bottom bar to switch tabs. The side rail gives quick access to stats, search, and presets.", 0, openSideDock = true),
            Slide("🔔", "الأذونات (اختيارية)", "Optional permissions", "تقدر تستخدم التطبيق من غير ما تفعّل أي إذن دلوقتي. فعّل الإذن وقت ما تحتاج الميزة المرتبطة بيه فقط؛ وتقدر تتخطى الخطوة دي عادي.", "Permissions are optional. WAQT works without enabling them now. Grant each permission only when you use the feature that needs it, or skip this step.", 0)
        )
        val dlg = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(12), dp(24), dp(12), dp(86))
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = createGlassCardDrawable(false, false).mutate().also {
                // Glass panel is intentionally translucent over the dimmed app backdrop.
            }
            elevation = dp(24).toFloat()
            setPadding(dp(18), dp(14), dp(18), dp(16))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
            }
        }
        root.addView(column)

        val skip = TextView(this).apply {
            text = if (isArabic) "تخطي ✕" else "Skip ✕"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#FFB9D8F2"))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.END }
        }
        column.addView(skip)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        column.addView(ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(body)
        })
        val dots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)).apply { topMargin = dp(6) }
        }
        column.addView(dots)
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(8) }
        }
        column.addView(btnRow)

        var cur = if (jumpToPermissions) slides.lastIndex else 0
        fun closeTour() {
            isTasksLeftDockOpen = false
            getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit().putBoolean("onboarding_done", true).apply()
            onboardingRefresh = null
            dlg.dismiss()
            renderCurrentTab()
        }
        fun permRow(label: String, ok: Boolean, action: () -> Unit): View = TextView(this).apply {
            text = (if (ok) "✅  " else "⚠️  ") + label
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (ok) Color.parseColor("#FF42C98A") else Color.parseColor("#FFE8F7F5"))
            gravity = Gravity.CENTER_VERTICAL
            background = createGlass3DDrawable(dp(14))
            setPadding(dp(14), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(6) }
            setOnClickListener { triggerGentleHaptic(); action() }
        }

        fun render() {
            val sl = slides[cur]
            currentTab = sl.targetTab
            isTasksLeftDockOpen = sl.openSideDock
            renderCurrentTab()

            body.removeAllViews()
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            headerRow.addView(TextView(this).apply {
                text = sl.icon
                textSize = 26f
                gravity = Gravity.CENTER
                background = createGlass3DDrawable(dp(26)).also { }
                clipToOutline = true
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(12) }
            })
            headerRow.addView(TextView(this).apply {
                text = if (isArabic) sl.ar else sl.en
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#FFEAF6FF"))
            })
            body.addView(headerRow)
            body.addView(TextView(this).apply {
                text = if (isArabic) sl.bodyAr else sl.bodyEn
                textSize = 14f
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.2f)
                setTextColor(Color.parseColor("#FFEAF4FF"))
                setPadding(dp(6), dp(8), dp(6), dp(4))
            })
            if (cur == slides.lastIndex) {
                body.addView(permRow(if (isArabic) "السماح بالإشعارات" else "Allow notifications", isNotifOk()) { askNotifications() })
                body.addView(permRow(if (isArabic) "تشغيل التطبيق في الخلفية (البطارية)" else "Run in background (battery)", isBatteryOk()) { askBattery() })
                body.addView(permRow(if (isArabic) "المنبهات الدقيقة (التذكير في الميعاد بالظبط)" else "Exact alarms", isExactOk()) { askExactAlarm() })
                body.addView(permRow(if (isArabic) "العرض فوق التطبيقات (للنوافذ العايمة)" else "Draw over apps (floating windows)", isOverlayOk()) { askOverlay() })
                body.addView(TextView(this).apply {
                    text = if (isArabic) "اختياري تمامًا: بعض الأجهزة قد تحتاج إعدادات البطارية لتحسين وصول التذكيرات في الخلفية."
                    else "On Xiaomi/Oppo/Samsung also enable Autostart and set battery to Unrestricted in App info."
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#FFB9D8F2"))
                    setPadding(dp(6), dp(12), dp(6), dp(4))
                })
                body.addView(permRow(if (isArabic) "فتح إعدادات التطبيق" else "Open app settings", false) {
                    openSettingsSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                })
            }
            body.alpha = 0f
            body.translationY = dp(22).toFloat()
            body.animate().alpha(1f).translationY(0f).setDuration(260).start()

            dots.removeAllViews()
            for (i in slides.indices) {
                dots.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(5).toFloat()
                        setColor(if (i == cur) Color.parseColor("#FF8BD5FF") else Color.parseColor("#55FFFFFF"))
                    }
                    layoutParams = LinearLayout.LayoutParams(if (i == cur) dp(22) else dp(8), dp(8)).apply { marginStart = dp(4); marginEnd = dp(4) }
                    setOnClickListener { cur = i; render() }
                })
            }
            btnRow.removeAllViews()
            if (cur > 0) {
                btnRow.addView(TextView(this).apply {
                    text = if (isArabic) "رجوع" else "Back"
                    textSize = 15f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#FFEAF4FF"))
                    background = createGlass3DDrawable(dp(27))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(10) }
                    setOnClickListener { cur--; render() }
                })
            }
            btnRow.addView(TextView(this).apply {
                text = if (cur == slides.lastIndex) (if (isArabic) "يلا نبدأ 🚀" else "Let's go 🚀") else (if (isArabic) "التالي" else "Next")
                textSize = 17f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#FFF3FAFF"))
                background = createGlass3DDrawable(dp(27))
                elevation = dp(10).toFloat()
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f)
                setOnClickListener { if (cur == slides.lastIndex) closeTour() else { cur++; render() } }
            })
        }
        skip.setOnClickListener { if (cur < slides.lastIndex) { cur = slides.lastIndex; render() } else closeTour() }
        onboardingRefresh = { if (cur == slides.lastIndex && dlg.isShowing) render() }
        dlg.setOnDismissListener { onboardingRefresh = null }
        dlg.setCancelable(false)
        dlg.setCanceledOnTouchOutside(false)
        dlg.setContentView(root)
        dlg.show()
        dlg.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.62f)
        }
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onboardingRefresh?.invoke()
    }

    private fun createAIMessageBubble(text: String): View {
        return LinearLayout(this).apply {
            gravity = Gravity.START
            val bubble = TextView(this@MainActivity).apply {
                this.text = text
                setTextColor(textPrimaryColor())
                textSize = 14f
                setLineSpacing(dp(2).toFloat(), 1.02f)
                background = createPillDrawable(bgElevatedColor(), dp(17), COLOR_TEAL_DEEP)
                setPadding(dp(14), dp(11), dp(14), dp(11))
                maxWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
            }
            addView(bubble)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(9)
            }
        }
    }

    private fun createUserMessageBubble(text: String): View {
        return LinearLayout(this).apply {
            gravity = Gravity.END
            val bubble = TextView(this@MainActivity).apply {
                this.text = text
                setTextColor(Color.parseColor("#0B1417"))
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setLineSpacing(dp(2).toFloat(), 1.02f)
                background = createPillDrawable(accentOrangeColor(), dp(17), null)
                setPadding(dp(14), dp(11), dp(14), dp(11))
                maxWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
            }
            addView(bubble)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(9)
            }
        }
    }

    private fun processUserMessageWithLocalAI(input: String, container: LinearLayout, scroll: ScrollView) {
        Handler(Looper.getMainLooper()).postDelayed({
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createPillDrawable(bgElevatedColor(), dp(14), COLOR_ORANGE)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }

                addView(TextView(this@MainActivity).apply {
                    text = if (isArabic) "💡 رتبت لك الخطة التالية:" else "💡 Action Plan:"
                    setTextColor(accentOrangeColor())
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                })

                val taskTitle = if (input.length > 30) input.substring(0, 30) + "..." else input
                addView(TextView(this@MainActivity).apply {
                    text = "• $taskTitle"
                    setTextColor(textPrimaryColor())
                    textSize = 14f
                    setPadding(0, dp(4), 0, dp(8))
                })

                val btnRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }

                val btnAddTask = TextView(this@MainActivity).apply {
                    text = if (isArabic) "+ إضافة للمهام 🎯" else "+ Add Task"
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.parseColor("#0B1417"))
                    background = createPillDrawable(accentOrangeColor(), dp(10), null)
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(6) }
                    setOnClickListener {
                        val newItem = ReminderItem(
                            id = UUID.randomUUID().toString(),
                            title = taskTitle,
                            isHabit = false,
                            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1,
                            minute = 0,
                            priority = "high",
                            dueDateEpochDay = ReminderRepository.currentEpochDay()
                        )
                        ReminderRepository.addOrUpdateItem(this@MainActivity, newItem)
                        AlarmScheduler.scheduleNextOccurrence(this@MainActivity, newItem)
                        Toast.makeText(this@MainActivity, if (isArabic) "✓ أضيفت للمهام بنجاح!" else "Task added!", Toast.LENGTH_SHORT).show()
                        renderCurrentTab()
                    }
                }

                val btnAddNote = TextView(this@MainActivity).apply {
                    text = if (isArabic) "حفظ كملاحظة 📝" else "Save Note"
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(textPrimaryColor())
                    background = createPillDrawable(bgCardColor(), dp(10), COLOR_TEAL)
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f)
                    setOnClickListener {
                        val note = NoteItem(
                            id = UUID.randomUUID().toString(),
                            title = if (isArabic) "فكرة ذكية" else "AI Thought",
                            content = input,
                            category = "ideas",
                            colorHex = "#142A2E"
                        )
                        ReminderRepository.addOrUpdateNote(this@MainActivity, note)
                        Toast.makeText(this@MainActivity, if (isArabic) "✓ حفظت في مدونتي!" else "Note saved!", Toast.LENGTH_SHORT).show()
                        renderCurrentTab()
                    }
                }

                val btnAddHabit = TextView(this@MainActivity).apply {
                    text = if (isArabic) "+ عادة 🌱" else "+ Habit"
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(textPrimaryColor())
                    background = createPillDrawable(bgCardColor(), dp(10), COLOR_TEAL)
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                    layoutParams = LinearLayout.LayoutParams(0, dp(34), 0.8f).apply { marginEnd = dp(5) }
                    setOnClickListener {
                        val title = taskTitle.take(100)
                        val existing = ReminderRepository.getAllItems(this@MainActivity).firstOrNull { it.isHabit && it.title.equals(title, ignoreCase = true) }
                        if (existing != null) {
                            Toast.makeText(this@MainActivity, if (isArabic) "العادة موجودة بالفعل" else "Habit already exists", Toast.LENGTH_SHORT).show()
                        } else {
                            val habit = ReminderItem(id = UUID.randomUUID().toString(), title = title, isHabit = true, hour = 9, minute = 0, priority = "medium", category = "study")
                            ReminderRepository.addOrUpdateItem(this@MainActivity, habit)
                            try { AlarmScheduler.scheduleNextOccurrence(this@MainActivity, habit) } catch (_: Exception) {}
                            Toast.makeText(this@MainActivity, if (isArabic) "🌱 اتضافت العادة" else "Habit added", Toast.LENGTH_SHORT).show()
                            renderCurrentTab()
                        }
                    }
                }
                btnAddTask.layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(5) }
                btnAddNote.layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(5) }
                btnRow.addView(btnAddTask)
                btnRow.addView(btnAddHabit)
                btnRow.addView(btnAddNote)
                addView(btnRow)
            }
            container.addView(card)
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }, 300)
    }

    private fun buildTasksScreenView(): View {
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(dp(16), dp(14), dp(16), dp(96))
        }

        val allItems = ReminderRepository.getAllItems(this)
        val todayEpoch = ReminderRepository.currentEpochDay()

        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(18))
        }

        val circles = listOf(
            "all" to (if (isArabic) "الكل" else "All"),
            "today" to (if (isArabic) "اليوم" else "Today"),
            "overdue" to (if (isArabic) "متأخرة" else "Late"),
            "habits" to (if (isArabic) "عادات" else "Habits"),
            "done" to (if (isArabic) "مكتملة" else "Done")
        )

        circles.forEach { (key, label) ->
            val isSel = (taskFilter == key)
            val circleLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                val circleView = TextView(this@MainActivity).apply {
                    text = label
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    val s = dp(50)
                    layoutParams = LinearLayout.LayoutParams(s, s)
                    setTextColor(if (isSel) Color.parseColor("#0B1417") else textPrimaryColor())
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (isSel) (if (key == "overdue") Color.parseColor("#F16D68") else accentOrangeColor()) else bgCardColor())
                        setStroke(dp(1), Color.parseColor(if (key == "overdue") "#F16D68" else "#2635B6AD"))
                    }
                    elevation = if (isSel) dp(6).toFloat() else dp(2).toFloat()
                }
                addView(circleView)
                setOnClickListener {
                    triggerGentleHaptic()
                    taskFilter = key
                    currentTaskCarouselIndex = 0
                    renderCurrentTab()
                }
            }
            filterRow.addView(circleLayout)
        }
        content.addView(buildRankPill("tasks"))
        content.addView(filterRow)
        content.addView(buildPriorityFilterRow())

        val filteredList = allItems.filter { item ->
            val isLate = !item.isCompletedToday && (item.dueDateEpochDay < todayEpoch || (item.dueDateEpochDay == todayEpoch && isHourPassed(item.hour, item.minute)))
            when (taskFilter) {
                "habits" -> item.isHabit
                "today" -> !item.isHabit && !item.isCompletedToday && item.dueDateEpochDay <= todayEpoch
                "overdue" -> !item.isHabit && isLate
                "done" -> item.isCompletedToday
                else -> true
            }
        }.filter {
            taskSearchQuery.isBlank() || it.title.contains(taskSearchQuery, ignoreCase = true)
        }.filter {
            taskPriorityFilter == "all" || it.priority == taskPriorityFilter
        }.sortedWith(
            compareBy<ReminderItem>(
                { if (it.isCompletedToday || it.completionPercentage >= 100) 1 else 0 },
                { when (it.priority) { "high" -> 0; "medium" -> 1; else -> 2 } },
                { if (it.isHabit) Long.MAX_VALUE / 2 else it.dueDateEpochDay },
                { it.hour * 60 + it.minute }
            )
        )

        if (filteredList.isEmpty()) {
            content.addView(buildEmptyCardPlaceholder(
                if (isArabic) "مساحتك جاهزة لأول إنجاز" else "Ready for your first win",
                if (isArabic) "أضف مهمة جديدة من الشريط الجانبي، ورتّب يومك خطوة بخطوة." else "Add a task from the side rail and shape your day."
            ))
        } else {
            content.addView(buildStackedTasksCarousel(filteredList))
        }
        root.addView(content)

        val leftDock = buildTasksLeftSlideOutDock()
        root.addView(leftDock)
        root.addView(buildAddFab { showAddOrEditTaskModal(null) })
        return root
    }

    private fun isHourPassed(hour: Int, min: Int): Boolean {
        val now = Calendar.getInstance()
        val nowM = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return (hour * 60 + min) < nowM
    }

    private fun buildStackedTasksCarousel(tasks: List<ReminderItem>): View {
        val total = tasks.size
        val activeIdx = currentTaskCarouselIndex.coerceIn(0, total - 1)
        val stackContainer = FrameLayout(this).apply {
            // The carousel must wrap its content; MATCH_PARENT height made each
            // note card stretch to the full available screen height.
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(330))
            clipChildren = false
            clipToPadding = false
            cameraDistance = dp(12000).toFloat()
        }

        val navCounter = TextView(this).apply {
            text = if (isArabic) "${activeIdx + 1} من $total" else "${activeIdx + 1} of $total"
            setTextColor(Color.parseColor("#D9F5F0"))
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#CC142A2E"))
                setStroke(dp(1), Color.parseColor("#6650C9C0"))
            }
            elevation = dp(4).toFloat()
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
        }
        stackContainer.addView(navCounter)

        // Keep the active card narrower than the viewport so both neighboring
        // cards remain visibly present, matching the layered reference layout.
        val cardWidth = (resources.displayMetrics.widthPixels * 0.72f).toInt()
        fun prepareStackCard(card: View) {
            val lp = (card.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.width = cardWidth
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            card.layoutParams = lp
        }

        fun styleRearCard(card: View, side: Int) {
            prepareStackCard(card)
            setCardTreeEnabled(card, false)
            card.scaleX = 0.94f
            card.scaleY = 0.94f
            card.translationX = (side * dp(104)).toFloat()
            card.translationY = dp(42).toFloat()
            card.rotationY = side * -10f
            card.alpha = 0.70f
            card.elevation = dp(4).toFloat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                card.setRenderEffect(RenderEffect.createBlurEffect(dp(2).toFloat(), dp(2).toFloat(), Shader.TileMode.CLAMP))
            }
        }

        if (activeIdx > 0) {
            val previous = buildSingleTaskCardView(tasks[activeIdx - 1], isForeground = false).apply {
                styleRearCard(this, -1)
            }
            stackContainer.addView(previous)
        }
        if (activeIdx + 1 < total) {
            val next = buildSingleTaskCardView(tasks[activeIdx + 1], isForeground = false).apply {
                styleRearCard(this, 1)
            }
            stackContainer.addView(next)
        }

        val frontCard = buildSingleTaskCardView(tasks[activeIdx], isForeground = true).apply {
            prepareStackCard(this)
            scaleX = 1f
            scaleY = 1f
            translationY = dp(24).toFloat()
            translationX = 0f
            rotationY = 0f
            alpha = 1f
            elevation = dp(22).toFloat()
        }
        setupCardSwipeNavigation(frontCard) { direction ->
            if (direction > 0 && currentTaskCarouselIndex < total - 1) {
                animateCarouselExit(frontCard, direction)
                currentTaskCarouselIndex++
                triggerGentleHaptic()
                stackContainer.postDelayed({ renderCurrentTab() }, 90)
            } else if (direction < 0 && currentTaskCarouselIndex > 0) {
                animateCarouselExit(frontCard, direction)
                currentTaskCarouselIndex--
                triggerGentleHaptic()
                stackContainer.postDelayed({ renderCurrentTab() }, 90)
            }
        }
        stackContainer.addView(frontCard)
        return stackContainer
    }

    private fun buildSingleTaskCardView(item: ReminderItem, isForeground: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassCardDrawable(isForeground, item.priority == "high")
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(22)
                rightMargin = dp(22)
                topMargin = dp(28)
            }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnDelete = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.parseColor("#F16D68"))
            setTypeface(typeface, Typeface.BOLD)
            val s = dp(32)
            layoutParams = LinearLayout.LayoutParams(s, s)
            gravity = Gravity.CENTER
            background = createPillDrawable(bgElevatedColor(), dp(16), null)
            setOnClickListener {
                if (isForeground) {
                    ReminderRepository.deleteItem(this@MainActivity, item.id)
                    AlarmScheduler.cancelAlarm(this@MainActivity, item.id)
                    Toast.makeText(this@MainActivity, if (isArabic) "تم حذف المهمة" else "Task deleted", Toast.LENGTH_SHORT).show()
                    renderCurrentTab()
                }
            }
        }
        topRow.addView(btnDelete)

        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f) }
        topRow.addView(spacer)

        val isDone = item.isCompletedToday || item.completionPercentage >= 100
        val prioColor = when (item.priority) { "high" -> "#FFF16D68"; "medium" -> "#FFFFC93C"; else -> "#FF42C98A" }
        val prioText = when (item.priority) {
            "high" -> if (isArabic) "🔴 عالية" else "🔴 High"
            "medium" -> if (isArabic) "🟡 متوسطة" else "🟡 Medium"
            else -> if (isArabic) "🟢 منخفضة" else "🟢 Low"
        }
        topRow.addView(TextView(this).apply {
            val habitPrefix = if (item.isHabit) "🌱 " else ""
            text = if (isDone) (if (isArabic) "✅ مكتملة" else "✅ Done") else (habitPrefix + prioText)
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (isDone) Color.parseColor("#FF42C98A") else Color.parseColor(prioColor))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.argb(40, 255, 255, 255))
                setStroke(dp(1), if (isDone) Color.parseColor("#FF42C98A") else Color.parseColor(prioColor))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8); marginEnd = dp(8) }
            setOnClickListener {
                if (isForeground) {
                    val nextPrio = when (item.priority) {
                        "high" -> "medium"
                        "medium" -> "low"
                        else -> "high"
                    }
                    triggerGentleHaptic()
                    ReminderRepository.addOrUpdateItem(this@MainActivity, item.copy(priority = nextPrio))
                    renderCurrentTab()
                }
            }
        })
        val dateStr = if (item.dueDateString.isNotBlank()) item.dueDateString else "اليوم"
        val tvDateBadge = TextView(this).apply {
            text = "📅 $dateStr"
            textSize = 12f
            setTextColor(textMutedColor())
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = createPillDrawable(bgElevatedColor(), dp(10), null)
        }
        topRow.addView(tvDateBadge)
        card.addView(topRow)

        val tvTitle = TextView(this).apply {
            text = if (isDone) "✅  ${item.title}" else item.title
            setTextColor(if (isDone) Color.parseColor("#FF42C98A") else textPrimaryColor())
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            if (isDone) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            setPadding(0, dp(12), 0, dp(4))
        }
        card.addView(tvTitle)

        val pctRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(10))
        }

        val pBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = item.completionPercentage
            progressTintList = ColorStateList.valueOf(if (item.completionPercentage == 100) Color.parseColor("#42C98A") else accentOrangeColor())
            progressBackgroundTintList = ColorStateList.valueOf(bgElevatedColor())
            layoutParams = LinearLayout.LayoutParams(0, dp(8), 1f).apply { marginEnd = dp(8) }
        }
        pctRow.addView(pBar)

        val tvPctNum = TextView(this).apply {
            text = "${item.completionPercentage}%"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(accentOrangeColor())
        }
        pctRow.addView(tvPctNum)
        card.addView(pctRow)

        val iconsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
        }

        if (item.isHabit) {
            iconsRow.addView(createCardActionCircle("✏️", "Edit") { showAddOrEditHabitModal(item) })
            iconsRow.addView(createCardActionCircle("🔥", "Streak") {
                Toast.makeText(this, if (isArabic) "🔥 سلسلة مستمرة: ${item.streakCount} أيام!" else "Streak: ${item.streakCount} days", Toast.LENGTH_SHORT).show()
            })
        } else {
            iconsRow.addView(createCardActionCircle("✏️", "Edit") {
                showAddOrEditTaskModal(item)
            })
        }

        iconsRow.addView(createCardActionCircle("⏰", "Alarm") {
            TimePickerDialog(this, { _, h, m ->
                val updated = item.copy(hour = h, minute = m)
                ReminderRepository.addOrUpdateItem(this, updated)
                AlarmScheduler.scheduleNextOccurrence(this, updated)
                renderCurrentTab()
            }, item.hour, item.minute, false).show()
        })

        iconsRow.addView(createCardActionCircle("💤", "Snooze") {
            AlarmScheduler.scheduleSnooze(this, item, 10)
            Toast.makeText(this, if (isArabic) "تم التأجيل 10 دقائق" else "Snoozed 10m", Toast.LENGTH_SHORT).show()
        })

        iconsRow.addView(createCardActionCircle("🍅", "Focus") {
            selectedFocusTaskId = item.id
            isPomodoroLinkedToTask = true
            currentTab = 2
            renderCurrentTab()
        })

        if (item.isHabit) {
            iconsRow.addView(createCardActionCircle("🎯", "+1") {
                ReminderRepository.incrementHabitStep(this, item.id)
                renderCurrentTab()
            })
        } else {
            iconsRow.addView(createCardActionCircle("％", "Progress") {
                showTaskPercentageDialog(item)
            })
        }

        if (!item.isHabit && (isDone || item.completionPercentage > 0)) {
            iconsRow.addView(createCardActionCircle("🔄", "Restart") {
                val restarted = item.copy(isCompletedToday = false, completionPercentage = 0, lastCompletedEpochDay = -1L)
                ReminderRepository.addOrUpdateItem(this, restarted)
                Toast.makeText(this, if (isArabic) "🔄 اتعادت المهمة من الأول" else "🔄 Task restarted", Toast.LENGTH_SHORT).show()
                renderCurrentTab()
            })
        }

        card.addView(iconsRow)
        return card
    }

    private fun createCardActionCircle(icon: String, contentDesc: String, onClick: () -> Unit): View {
        return TextView(this).apply {
            text = icon
            textSize = 15f
            gravity = Gravity.CENTER
            val s = dp(42)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginStart = dp(6); marginEnd = dp(6) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgElevatedColor())
                setStroke(dp(1), Color.parseColor("#3335B6AD"))
            }
            setOnClickListener {
                triggerGentleHaptic()
                onClick()
            }
        }
    }

    private fun showTaskPercentageDialog(item: ReminderItem) {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createPillDrawable(bgCardColor(), dp(20), COLOR_ORANGE)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        v.addView(TextView(this).apply {
            text = if (isArabic) "حدد نسبة إنجاز المهمة:" else "Set Completion Percentage:"
            setTextColor(textPrimaryColor())
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        })

        val tvVal = TextView(this).apply {
            text = "${item.completionPercentage}%"
            textSize = 24f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(accentOrangeColor())
            setPadding(0, dp(10), 0, dp(6))
        }
        v.addView(tvVal)

        var selectedPct = item.completionPercentage
        val seek = SeekBar(this).apply {
            max = 100
            progress = selectedPct
            progressTintList = ColorStateList.valueOf(accentOrangeColor())
            thumbTintList = ColorStateList.valueOf(accentOrangeColor())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                    selectedPct = prog
                    tvVal.text = "$selectedPct%"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        v.addView(seek)

        val btnSave = Button(this).apply {
            text = if (isArabic) "حفظ النسبة" else "Save"
            isAllCaps = false
            setTextColor(Color.parseColor("#0B1417"))
            backgroundTintList = ColorStateList.valueOf(accentOrangeColor())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(12) }
            setOnClickListener {
                ReminderRepository.updateTaskPercentage(this@MainActivity, item.id, selectedPct)
                d.dismiss()
                renderCurrentTab()
            }
        }
        v.addView(btnSave)

        d.setContentView(v)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.85).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
        styleGlassDialog(d)
    }

    private fun buildTasksLeftSlideOutDock(): View {
        val dockLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = createRailDrawable()
            setPadding(dp(8), dp(16), dp(8), dp(16))
            val w = if (isTasksLeftDockOpen) dp(60) else dp(28)
            layoutParams = FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
            }
            elevation = dp(16).toFloat()
        }

        if (!isTasksLeftDockOpen) {
            val tvHandle = TextView(this).apply {
                text = "▸"
                textSize = 22f
                setTextColor(Color.parseColor("#FF4FD6C9"))
                gravity = Gravity.CENTER
            }
            dockLayout.addView(tvHandle)
            dockLayout.setOnClickListener {
                isTasksLeftDockOpen = true
                renderCurrentTab()
            }
        } else {
            dockLayout.addView(createDockIcon("⚡") {
                showQuickReminderDialog()
            })

            dockLayout.addView(createDockIcon("📊") {
                showStatsDialog("tasks")
            })

            dockLayout.addView(createDockIcon("🔍") {
                showSearchTasksInputDialog()
            })

            val tvClose = TextView(this).apply {
                text = "◂"
                textSize = 16f
                setTextColor(textMutedColor())
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
                setOnClickListener {
                    isTasksLeftDockOpen = false
                    renderCurrentTab()
                }
            }
            dockLayout.addView(tvClose)
        }
        finishRail(dockLayout, isTasksLeftDockOpen) { open -> isTasksLeftDockOpen = open; renderCurrentTab() }
        return dockLayout
    }

    private fun createDockIcon(icon: String, onClick: () -> Unit): View {
        return TextView(this).apply {
            text = icon
            textSize = 18f
            gravity = Gravity.CENTER
            val s = dp(42)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { bottomMargin = dp(10) }
            val tint = railTint(icon)
            background = GradientDrawable().apply {
                cornerRadius = dp(13).toFloat()
                setColor(Color.argb(if (isDarkMode) 70 else 90, Color.red(tint), Color.green(tint), Color.blue(tint)))
                setStroke(dp(2), tint)
            }
            elevation = dp(4).toFloat()
            setOnClickListener {
                triggerGentleHaptic()
                onClick()
            }
        }
    }

    private fun showSearchTasksInputDialog() {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createPillDrawable(bgCardColor(), dp(18), COLOR_TEAL)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val et = EditText(this).apply {
            hint = if (isArabic) "ابحث عن مهمة..." else "Search task..."
            setHintTextColor(textMutedColor())
            setTextColor(textPrimaryColor())
            background = createPillDrawable(bgElevatedColor(), dp(12), null)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        v.addView(et)

        val btn = Button(this).apply {
            text = if (isArabic) "بحث" else "Search"
            isAllCaps = false
            setTextColor(Color.parseColor("#0B1417"))
            backgroundTintList = ColorStateList.valueOf(accentTealColor())
            setOnClickListener {
                taskSearchQuery = et.text.toString().trim()
                d.dismiss()
                renderCurrentTab()
            }
        }
        v.addView(btn)
        d.setContentView(v)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.85).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
        styleGlassDialog(d)
    }

    private fun showAddOrEditTaskModal(existingItem: ReminderItem?) {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val scroll = ScrollView(this).apply {
            background = createPillDrawable(bgCardColor(), dp(22), COLOR_ORANGE)
        }
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        v.addView(TextView(this).apply {
            text = if (existingItem == null) (if (isArabic) "➕ إضافة مهمة جديدة" else "➕ New Task") else (if (isArabic) "✏️ تعديل المهمة" else "✏️ Edit Task")
            setTextColor(textPrimaryColor())
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        })

        val etTitle = EditText(this).apply {
            hint = if (isArabic) "اسم المهمة..." else "Task Title..."
            setText(existingItem?.title ?: "")
            setHintTextColor(textMutedColor())
            setTextColor(textPrimaryColor())
            background = createPillDrawable(bgElevatedColor(), dp(12), null)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        v.addView(etTitle)

        var chosenEpochDay = existingItem?.dueDateEpochDay ?: ReminderRepository.currentEpochDay()
        var chosenDateString = existingItem?.dueDateString ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val btnPickDate = TextView(this).apply {
            text = "📅 " + (if (isArabic) "تاريخ المهمة: " else "Date: ") + chosenDateString
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textPrimaryColor())
            background = createPillDrawable(bgElevatedColor(), dp(12), COLOR_TEAL)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(10) }
            setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(this@MainActivity, { _, year, month, day ->
                    val selCal = Calendar.getInstance().apply { set(year, month, day) }
                    chosenEpochDay = (year * 366L) + selCal.get(Calendar.DAY_OF_YEAR)
                    chosenDateString = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                    text = "📅 " + (if (isArabic) "تاريخ المهمة: " else "Date: ") + chosenDateString
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        v.addView(btnPickDate)

        var chosenHour = existingItem?.hour ?: 9
        var chosenMinute = existingItem?.minute ?: 0
        val btnPickTime = TextView(this).apply {
            val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, chosenHour); set(Calendar.MINUTE, chosenMinute) }
            text = "⏰ " + (if (isArabic) "وقت التنبيه: " else "Alarm: ") + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(c.time)
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textPrimaryColor())
            background = createPillDrawable(bgElevatedColor(), dp(12), COLOR_ORANGE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(8) }
            setOnClickListener {
                TimePickerDialog(this@MainActivity, { _, h, m ->
                    chosenHour = h
                    chosenMinute = m
                    val tCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }
                    text = "⏰ " + (if (isArabic) "وقت التنبيه: " else "Alarm: ") + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(tCal.time)
                }, chosenHour, chosenMinute, false).show()
            }
        }
        v.addView(btnPickTime)

        var chosenPriority = existingItem?.priority ?: "medium"
        v.addView(TextView(this).apply {
            text = if (isArabic) "أهمية المهمة:" else "Task Priority:"
            setTextColor(accentTealColor())
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(6))
        })
        val prioRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val prioOptions = listOf(
            Triple("high", if (isArabic) "🔴 عالية" else "🔴 High", "#FFF16D68"),
            Triple("medium", if (isArabic) "🟡 متوسطة" else "🟡 Medium", "#FFFFC93C"),
            Triple("low", if (isArabic) "🟢 منخفضة" else "🟢 Low", "#FF42C98A")
        )
        fun paintTaskPrioChips() {
            prioRow.removeAllViews()
            prioOptions.forEach { (key, label, hex) ->
                val sel = (chosenPriority == key)
                prioRow.addView(TextView(this).apply {
                    text = label
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (sel) Color.parseColor("#0B1417") else Color.parseColor(hex))
                    background = createPillDrawable(if (sel) Color.parseColor(hex) else bgElevatedColor(), dp(12), if (sel) null else hex)
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) }
                    setOnClickListener {
                        triggerGentleHaptic()
                        chosenPriority = key
                        paintTaskPrioChips()
                    }
                })
            }
        }
        paintTaskPrioChips()
        v.addView(prioRow)

        val btnSave = Button(this).apply {
            text = if (isArabic) "حفظ وضبط المنبه" else "Save Task"
            isAllCaps = false
            setTextColor(Color.parseColor("#0B1417"))
            backgroundTintList = ColorStateList.valueOf(accentOrangeColor())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(16) }
            setOnClickListener {
                val t = etTitle.text.toString().trim()
                if (t.isNotBlank()) {
                    val item = (existingItem ?: ReminderItem(
                        id = UUID.randomUUID().toString(),
                        title = t,
                        isHabit = false,
                        hour = chosenHour,
                        minute = chosenMinute,
                        priority = chosenPriority
                    )).copy(
                        title = t,
                        hour = chosenHour,
                        minute = chosenMinute,
                        priority = chosenPriority,
                        dueDateEpochDay = chosenEpochDay,
                        dueDateString = chosenDateString
                    )
                    ReminderRepository.addOrUpdateItem(this@MainActivity, item)
                    AlarmScheduler.scheduleNextOccurrence(this@MainActivity, item)
                    d.dismiss()
                    renderCurrentTab()
                }
            }
        }
        v.addView(btnSave)

        scroll.addView(v)
        d.setContentView(scroll)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
        styleGlassDialog(d)
    }

    private fun buildHabitsScreenView(): View {
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(dp(16), dp(14), dp(16), dp(96))
        }

        content.addView(buildRankPill("habits"))
        val categories = ReminderRepository.getAllHabitCategories(this)
        val catHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }

        val catScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val catRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        categories.forEach { c ->
            val isSel = (habitSelectedCategory == c.id)
            val chip = TextView(this).apply {
                text = "${c.icon} ${c.name}"
                textSize = 13f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (isSel) Color.parseColor("#0B1417") else textPrimaryColor())
                background = createPillDrawable(if (isSel) accentOrangeColor() else bgCardColor(), dp(14), if (isSel) null else "#2635B6AD")
                setPadding(dp(14), dp(8), dp(14), dp(8))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply { marginEnd = dp(6) }
                setOnClickListener {
                    triggerGentleHaptic()
                    habitSelectedCategory = c.id
                    currentHabitCarouselIndex = 0
                    renderCurrentTab()
                }
            }
            catRow.addView(chip)
        }
        catScroll.addView(catRow)
        catHeaderRow.addView(catScroll)

        val btnAddCat = TextView(this).apply {
            text = "➕"
            textSize = 14f
            gravity = Gravity.CENTER
            background = createPillDrawable(bgElevatedColor(), dp(12), null)
            val s = dp(38)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginStart = dp(6) }
            setOnClickListener { showAddHabitCategoryDialog() }
        }
        catHeaderRow.addView(btnAddCat)
        content.addView(catHeaderRow)

        val habits = ReminderRepository.getAllItems(this).filter { it.isHabit }.filter {
            habitSelectedCategory == "all" || it.category == habitSelectedCategory
        }

        if (habits.isEmpty()) {
            content.addView(buildEmptyCardPlaceholder(
                if (isArabic) "ابدأ بعادة صغيرة" else "Start with one small habit",
                if (isArabic) "أضف عادة وحدد أيامها وتذكيرها، وتابع تقدمك بهدوء." else "Add a habit, choose its days and reminder, then track it gently."
            ))
        } else {
            content.addView(buildStackedHabitsCarousel(habits))
        }
        root.addView(content)

        root.addView(buildHabitsLeftSlideOutDock())
        root.addView(buildAddFab { showAddOrEditHabitModal(null) })
        return root
    }

    private fun showAddHabitCategoryDialog() {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createPillDrawable(bgCardColor(), dp(18), COLOR_ORANGE)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val et = EditText(this).apply {
            hint = if (isArabic) "اسم التصنيف الجديد (مثال: مالية)..." else "New category..."
            setHintTextColor(textMutedColor())
            setTextColor(textPrimaryColor())
            background = createPillDrawable(bgElevatedColor(), dp(12), null)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        v.addView(et)
        val btn = Button(this).apply {
            text = if (isArabic) "+ إضافة التصنيف" else "+ Add"
            isAllCaps = false
            setTextColor(Color.parseColor("#0B1417"))
            backgroundTintList = ColorStateList.valueOf(accentOrangeColor())
            setOnClickListener {
                val name = et.text.toString().trim()
                if (name.isNotBlank()) {
                    ReminderRepository.addHabitCategory(this@MainActivity, HabitCategoryItem(UUID.randomUUID().toString(), name, "⭐"))
                    d.dismiss()
                    renderCurrentTab()
                }
            }
        }
        v.addView(btn)
        d.setContentView(v)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.85).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
        styleGlassDialog(d)
    }

    private fun buildStackedHabitsCarousel(habits: List<ReminderItem>): View {
        val total = habits.size
        val activeIdx = currentHabitCarouselIndex.coerceIn(0, total - 1)
        val stackContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(330))
            clipChildren = false
            clipToPadding = false
            cameraDistance = dp(12000).toFloat()
        }
        val navCounter = TextView(this).apply {
            text = if (isArabic) "${activeIdx + 1} من $total" else "${activeIdx + 1} of $total"
            setTextColor(Color.parseColor("#D9F5F0"))
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#CC142A2E"))
                setStroke(dp(1), Color.parseColor("#6650C9C0"))
            }
            elevation = dp(4).toFloat()
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
        }
        stackContainer.addView(navCounter)

        // Keep the active card narrower than the viewport so both neighboring
        // cards remain visibly present, matching the layered reference layout.
        val cardWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
        fun prepareStackCard(card: View) {
            val lp = (card.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.width = cardWidth
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            card.layoutParams = lp
        }

        fun styleRearCard(card: View, side: Int) {
            prepareStackCard(card)
            setCardTreeEnabled(card, false)
            card.scaleX = 0.94f
            card.scaleY = 0.94f
            card.translationX = (side * dp(104)).toFloat()
            card.translationY = dp(42).toFloat()
            card.rotationY = side * -10f
            card.alpha = 0.70f
            card.elevation = dp(4).toFloat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                card.setRenderEffect(RenderEffect.createBlurEffect(dp(2).toFloat(), dp(2).toFloat(), Shader.TileMode.CLAMP))
            }
        }
        if (activeIdx > 0) {
            stackContainer.addView(buildSingleHabitCardView(habits[activeIdx - 1], isForeground = false).apply { styleRearCard(this, -1) })
        }
        if (activeIdx + 1 < total) {
            stackContainer.addView(buildSingleHabitCardView(habits[activeIdx + 1], isForeground = false).apply { styleRearCard(this, 1) })
        }
        val frontCard = buildSingleHabitCardView(habits[activeIdx], isForeground = true).apply {
            prepareStackCard(this)
            scaleX = 1f
            scaleY = 1f
            translationY = dp(24).toFloat()
            translationX = 0f
            rotationY = 0f
            alpha = 1f
            elevation = dp(22).toFloat()
        }
        setupCardSwipeNavigation(frontCard) { direction ->
            if (direction > 0 && currentHabitCarouselIndex < total - 1) {
                animateCarouselExit(frontCard, direction)
                currentHabitCarouselIndex++
                triggerGentleHaptic()
                stackContainer.postDelayed({ renderCurrentTab() }, 90)
            } else if (direction < 0 && currentHabitCarouselIndex > 0) {
                animateCarouselExit(frontCard, direction)
                currentHabitCarouselIndex--
                triggerGentleHaptic()
                stackContainer.postDelayed({ renderCurrentTab() }, 90)
            }
        }
        stackContainer.addView(frontCard)
        return stackContainer
    }

    private fun setCardTreeEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) setCardTreeEnabled(view.getChildAt(i), enabled)
        }
    }

    private fun animateCarouselExit(card: View, direction: Int) {
        card.animate()
            .translationX((direction * -dp(70)).toFloat())
            .rotationY(direction * 7f)
            .alpha(0.25f)
            .setDuration(90L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun buildSingleHabitCardView(habit: ReminderItem, isForeground: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassCardDrawable(isForeground, habit.isPaused)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(22)
                rightMargin = dp(22)
                topMargin = dp(28)
            }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnDel = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.parseColor("#F16D68"))
            setTypeface(typeface, Typeface.BOLD)
            val s = dp(32)
            layoutParams = LinearLayout.LayoutParams(s, s)
            gravity = Gravity.CENTER
            background = createPillDrawable(bgElevatedColor(), dp(16), null)
            setOnClickListener {
                if (isForeground) {
                    ReminderRepository.deleteItem(this@MainActivity, habit.id)
                    AlarmScheduler.cancelAlarm(this@MainActivity, habit.id)
                    renderCurrentTab()
                }
            }
        }
        topRow.addView(btnDel)

        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f) }
        topRow.addView(spacer)

        val habitPrioColor = when (habit.priority) { "high" -> "#FFF16D68"; "medium" -> "#FFFFC93C"; else -> "#FF42C98A" }
        val habitPrioText = when (habit.priority) {
            "high" -> if (isArabic) "🔴 مهمة" else "🔴 High"
            "medium" -> if (isArabic) "🟡 متوسطة" else "🟡 Medium"
            else -> if (isArabic) "🟢 عادية" else "🟢 Normal"
        }
        val tvHabitPrio = TextView(this).apply {
            text = habitPrioText
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(habitPrioColor))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = createPillDrawable(bgElevatedColor(), dp(10), habitPrioColor)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) }
            setOnClickListener {
                if (isForeground) {
                    val nextPrio = when (habit.priority) {
                        "high" -> "medium"
                        "medium" -> "low"
                        else -> "high"
                    }
                    triggerGentleHaptic()
                    ReminderRepository.addOrUpdateItem(this@MainActivity, habit.copy(priority = nextPrio))
                    renderCurrentTab()
                }
            }
        }
        topRow.addView(tvHabitPrio)

        val tvStatus = TextView(this).apply {
            text = if (habit.isPaused) "⏸ متوقفة" else "🔥 ${habit.streakCount} أيام"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(accentOrangeColor())
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = createPillDrawable(bgElevatedColor(), dp(10), null)
        }
        topRow.addView(tvStatus)
        card.addView(topRow)

        val tvTitle = TextView(this).apply {
            text = habit.title
            setTextColor(textPrimaryColor())
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(4))
        }
        card.addView(tvTitle)

        val progressText = "${habit.currentProgressToday} / ${habit.targetValue} ${habit.targetUnit}"
        card.addView(TextView(this).apply {
            text = "🎯 $progressText  •  ⏰ ${String.format(Locale.US, "%02d:%02d", habit.hour, habit.minute)}"
            setTextColor(textMutedColor())
            textSize = 13f
            setPadding(0, 0, 0, dp(10))
        })

        val iconsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(4))
        }

        iconsRow.addView(createCardActionCircle("🔥", "Streak") {
            Toast.makeText(this, if (isArabic) "🔥 سلسلة مستمرة: ${habit.streakCount} أيام!" else "Streak: ${habit.streakCount} days", Toast.LENGTH_SHORT).show()
        })

        iconsRow.addView(createCardActionCircle("🎯", "+1") {
            ReminderRepository.incrementHabitStep(this, habit.id)
            renderCurrentTab()
        })

        iconsRow.addView(createCardActionCircle("📅", "Schedule") {
            showEditHabitDaysDialog(habit)
        })

        iconsRow.addView(createCardActionCircle(if (habit.isPaused) "▶" else "❄️", "Freeze") {
            ReminderRepository.toggleHabitPause(this, habit.id)
            renderCurrentTab()
        })

        iconsRow.addView(createCardActionCircle("⏰", "Alarm") {
            TimePickerDialog(this, { _, h, m ->
                val u = habit.copy(hour = h, minute = m)
                ReminderRepository.addOrUpdateItem(this, u)
                AlarmScheduler.scheduleNextOccurrence(this, u)
                renderCurrentTab()
            }, habit.hour, habit.minute, false).show()
        })

        iconsRow.addView(createCardActionCircle("✏️", "Edit") {
            showAddOrEditHabitModal(habit)
        })

        card.addView(iconsRow)
        return card
    }

    private fun showEditHabitDaysDialog(habit: ReminderItem) {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createPillDrawable(bgCardColor(), dp(20), COLOR_ORANGE)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        v.addView(TextView(this).apply {
            text = if (isArabic) "حدد أيام تكرار العادة:" else "Select Active Days:"
            setTextColor(textPrimaryColor())
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        })

        val currentDays = habit.customDaysCsv.split(",").toMutableSet()
        val dayNames = if (isArabic) listOf("سبت", "حد", "اتنين", "تلات", "أربع", "خميس", "جمعة")
        else listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri")

        val daysRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        for (i in 1..7) {
            val dayId = i.toString()
            val isSelected = currentDays.contains(dayId)
            val dayChip = TextView(this).apply {
                text = dayNames[i - 1]
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (isSelected) Color.parseColor("#0B1417") else textPrimaryColor())
                background = createPillDrawable(if (isSelected) accentOrangeColor() else bgElevatedColor(), dp(12), null)
                val s = dp(40)
                layoutParams = LinearLayout.LayoutParams(0, s, 1f).apply { marginEnd = dp(4) }
                setOnClickListener {
                    if (currentDays.contains(dayId)) currentDays.remove(dayId) else currentDays.add(dayId)
                    val active = currentDays.contains(dayId)
                    setTextColor(if (active) Color.parseColor("#0B1417") else textPrimaryColor())
                    background = createPillDrawable(if (active) accentOrangeColor() else bgElevatedColor(), dp(12), null)
                }
            }
            daysRow.addView(dayChip)
        }
        v.addView(daysRow)

        val btnSave = Button(this).apply {
            text = if (isArabic) "حفظ الأيام" else "Save"
            isAllCaps = false
            setTextColor(Color.parseColor("#0B1417"))
            backgroundTintList = ColorStateList.valueOf(accentOrangeColor())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(16) }
            setOnClickListener {
                val csv = currentDays.sorted().joinToString(",")
                val u = habit.copy(customDaysCsv = csv)
                ReminderRepository.addOrUpdateItem(this@MainActivity, u)
                if (!u.isPaused && u.hasReminder) {
                    AlarmScheduler.scheduleNextOccurrence(this@MainActivity, u)
                } else {
                    AlarmScheduler.cancelAlarm(this@MainActivity, u.id)
                }
                d.dismiss()
                renderCurrentTab()
            }
        }
        v.addView(btnSave)

        d.setContentView(v)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
        styleGlassDialog(d)
    }

    private fun buildHabitsLeftSlideOutDock(): View {
        val dockLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = createRailDrawable()
            setPadding(dp(8), dp(16), dp(8), dp(16))
            val w = if (isHabitsLeftDockOpen) dp(60) else dp(28)
            layoutParams = FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
            }
            elevation = dp(16).toFloat()
        }

        if (!isHabitsLeftDockOpen) {
            val tvHandle = TextView(this).apply {
                text = "▸"
                textSize = 22f
                setTextColor(Color.parseColor("#FF4FD6C9"))
                gravity = Gravity.CENTER
            }
            dockLayout.addView(tvHandle)
            dockLayout.setOnClickListener {
                isHabitsLeftDockOpen = true
                renderCurrentTab()
            }
        } else {
            dockLayout.addView(createDockIcon("📊") {
                showStatsDialog("habits")
            })

            dockLayout.addView(createDockIcon("🏆") {
                showRankDialog("habits")
            })

            val tvClose = TextView(this).apply {
                text = "◂"
                textSize = 16f
                setTextColor(textMutedColor())
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
                setOnClickListener {
                    isHabitsLeftDockOpen = false
                    renderCurrentTab()
                }
            }
            dockLayout.addView(tvClose)
        }
        finishRail(dockLayout, isHabitsLeftDockOpen) { open -> isHabitsLeftDockOpen = open; renderCurrentTab() }
        return dockLayout
    }

    private fun showAddOrEditHabitModal(existingHabit: ReminderItem?) {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val scroll = ScrollView(this).apply {
            background = createPillDrawable(bgCardColor(), dp(22), COLOR_ORANGE)
        }
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        v.addView(TextView(this).apply {
            text = if (existingHabit == null) (if (isArabic) "🌱 إضافة عادة جديدة" else "🌱 New Habit") else (if (isArabic) "✏️ تعديل العادة" else "✏️ Edit Habit")
            setTextColor(textPrimaryColor())
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        })

        val etTitle = EditText(this).apply {
            hint = if (isArabic) "اسم العادة (مثال: قراءة 15 صفحة)..." else "Habit Name..."
            setText(existingHabit?.title ?: "")
            setHintTextColor(textMutedColor())
            setTextColor(textPrimaryColor())
            background = createPillDrawable(bgElevatedColor(), dp(12), null)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        v.addView(etTitle)

        v.addView(TextView(this).apply {
            text = if (isArabic) "اختر أيام التكرار الأسبوعية:" else "Select Active Days:"
            setTextColor(accentTealColor())
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(6))
        })

        val selectedDaysSet = (existingHabit?.customDaysCsv?.split(",")?.toMutableSet() ?: mutableSetOf("1", "2", "3", "4", "5", "6", "7"))
        val dayNames = if (isArabic) listOf("سبت", "حد", "اتنين", "تلات", "أربع", "خميس", "جمعة")
        else listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri")

        val daysRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        for (i in 1..7) {
            val dId = i.toString()
            val isSel = selectedDaysSet.contains(dId)
            val dChip = TextView(this).apply {
                text = dayNames[i - 1]
                textSize = 11f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (isSel) Color.parseColor("#0B1417") else textPrimaryColor())
                background = createPillDrawable(if (isSel) accentOrangeColor() else bgElevatedColor(), dp(12), null)
                val s = dp(38)
                layoutParams = LinearLayout.LayoutParams(0, s, 1f).apply { marginEnd = dp(4) }
                setOnClickListener {
                    if (selectedDaysSet.contains(dId)) selectedDaysSet.remove(dId) else selectedDaysSet.add(dId)
                    val active = selectedDaysSet.contains(dId)
                    setTextColor(if (active) Color.parseColor("#0B1417") else textPrimaryColor())
                    background = createPillDrawable(if (active) accentOrangeColor() else bgElevatedColor(), dp(12), null)
                }
            }
            daysRow.addView(dChip)
        }
        v.addView(daysRow)

        val habitCats = ReminderRepository.getAllHabitCategories(this).filter { it.id != "all" }
        var selHabitCat: String = existingHabit?.category?.takeIf { c -> habitCats.any { it.id == c } }
            ?: habitSelectedCategory.takeIf { c -> c != "all" && habitCats.any { it.id == c } }
            ?: (habitCats.firstOrNull()?.id ?: "general")
        v.addView(TextView(this).apply {
            text = if (isArabic) "التصنيف:" else "Category:"
            setTextColor(accentTealColor())
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(6))
        })
        val catChipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun paintHabitCats() {
            catChipRow.removeAllViews()
            habitCats.forEach { c ->
                val sel = c.id == selHabitCat
                catChipRow.addView(TextView(this).apply {
                    text = "${c.icon} ${c.name}"
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (sel) Color.parseColor("#0B1417") else textPrimaryColor())
                    background = if (sel) createOrange3DDrawable(dp(17)) else createGlass3DDrawable(dp(17))
                    setPadding(dp(14), 0, dp(14), 0)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginEnd = dp(8) }
                    setOnClickListener { selHabitCat = c.id; paintHabitCats() }
                })
            }
            catChipRow.addView(TextView(this).apply {
                text = if (isArabic) "＋ جديد" else "＋ New"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(accentTealColor())
                background = createGlass3DDrawable(dp(17))
                setPadding(dp(14), 0, dp(14), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36))
                setOnClickListener { d.dismiss(); showAddHabitCategoryDialog() }
            })
        }
        paintHabitCats()
        v.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(catChipRow)
        })

        var habitHour = existingHabit?.hour ?: 9
        var habitMinute = existingHabit?.minute ?: 0
        val btnPickTime = TextView(this).apply {
            val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, habitHour); set(Calendar.MINUTE, habitMinute) }
            text = "⏰ " + (if (isArabic) "وقت التذكير: " else "Time: ") + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(c.time)
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textPrimaryColor())
            background = createPillDrawable(bgElevatedColor(), dp(12), COLOR_ORANGE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(12) }
            setOnClickListener {
                TimePickerDialog(this@MainActivity, { _, h, m ->
                    habitHour = h
                    habitMinute = m
                    val tCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }
                    text = "⏰ " + (if (isArabic) "وقت التذكير: " else "Time: ") + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(tCal.time)
                }, habitHour, habitMinute, false).show()
            }
        }
        v.addView(btnPickTime)

        var chosenHabitPriority = existingHabit?.priority ?: "medium"
        v.addView(TextView(this).apply {
            text = if (isArabic) "أهمية العادة (مهمة / متوسطة / عادية):" else "Habit Priority:"
            setTextColor(accentTealColor())
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(6))
        })
        val habitPrioRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val habitPrioOptions = listOf(
            Triple("high", if (isArabic) "🔴 مهمة" else "🔴 High", "#FFF16D68"),
            Triple("medium", if (isArabic) "🟡 متوسطة" else "🟡 Medium", "#FFFFC93C"),
            Triple("low", if (isArabic) "🟢 عادية" else "🟢 Normal", "#FF42C98A")
        )
        fun paintHabitPrioChips() {
            habitPrioRow.removeAllViews()
            habitPrioOptions.forEach { (key, label, hex) ->
                val sel = (chosenHabitPriority == key)
                habitPrioRow.addView(TextView(this).apply {
                    text = label
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (sel) Color.parseColor("#0B1417") else Color.parseColor(hex))
                    background = createPillDrawable(if (sel) Color.parseColor(hex) else bgElevatedColor(), dp(12), if (sel) null else hex)
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) }
                    setOnClickListener {
                        triggerGentleHaptic()
                        chosenHabitPriority = key
                        paintHabitPrioChips()
                    }
                })
            }
        }
        paintHabitPrioChips()
        v.addView(habitPrioRow)

        val btnSave = Button(this).apply {
            text = if (isArabic) "حفظ العادة وربطها بالمهام" else "Save Habit"
            isAllCaps = false
            setTextColor(Color.parseColor("#0B1417"))
            backgroundTintList = ColorStateList.valueOf(accentOrangeColor())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(16) }
            setOnClickListener {
                val t = etTitle.text.toString().trim()
                if (t.isNotBlank()) {
                    val habit = (existingHabit ?: ReminderItem(
                        id = UUID.randomUUID().toString(),
                        title = t,
                        isHabit = true,
                        hour = habitHour,
                        minute = habitMinute,
                        priority = chosenHabitPriority
                    )).copy(
                        title = t,
                        hour = habitHour,
                        minute = habitMinute,
                        priority = chosenHabitPriority,
                        customDaysCsv = selectedDaysSet.sorted().joinToString(","),
                        category = selHabitCat
                    )
                    ReminderRepository.addOrUpdateItem(this@MainActivity, habit)
                    AlarmScheduler.scheduleNextOccurrence(this@MainActivity, habit)
                    d.dismiss()
                    renderCurrentTab()
                }
            }
        }
        v.addView(btnSave)

        scroll.addView(v)
        d.setContentView(scroll)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
        styleGlassDialog(d)
    }

    private fun buildNotesScreenView(): View {
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setPadding(dp(16), dp(12), dp(16), dp(88))
        }

        // Two independent experiences: saved notes and date-based daily journal.
        val switcher = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = createGlassCardDrawable(true, false)
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(12) }
        }
        listOf(if (isArabic) "📝 ملاحظاتي" else "📝 My notes", if (isArabic) "📅 يومياتي" else "📅 Daily journal").forEachIndexed { index, label ->
            switcher.addView(TextView(this).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                setTypeface(typeface, if (notesSubTab == index) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (notesSubTab == index) Color.parseColor("#0B1417") else textPrimaryColor())
                background = if (notesSubTab == index) createOrange3DDrawable(dp(18)) else null
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = dp(2); marginEnd = dp(2)
                }
                setOnClickListener { notesSubTab = index; renderCurrentTab() }
            })
        }
        content.addView(switcher)

        if (notesSubTab == 0) {
            content.addView(buildNoteCategoryRow())
            val notes = ReminderRepository.getAllNotes(this).filter {
                noteSelectedCategory == "all" || it.category == noteSelectedCategory
            }.filter {
                noteSearchQuery.isBlank() || it.title.contains(noteSearchQuery, ignoreCase = true) || it.content.contains(noteSearchQuery, ignoreCase = true)
            }
            if (notes.isEmpty()) {
                content.addView(buildEmptyCardPlaceholder(
                    if (isArabic) "لا توجد ملاحظات مسجلة" else "No saved notes",
                    if (isArabic) "اضغط + لإنشاء ملاحظة جديدة، وهتظهر هنا على شكل كارت مستقل."
                    else "Tap + to create a note. Your notes will appear here as cards."
                ))
            } else {
                currentNoteCarouselIndex = currentNoteCarouselIndex.coerceIn(0, notes.lastIndex)
                content.addView(buildStackedNotesCarousel(notes))
            }
            root.addView(content)
            root.addView(buildNotesLeftSlideOutDock())
            root.addView(buildAddFab { showAddOrEditNoteModal(null) })
        } else {
            content.addView(buildDailyJournalCalendar())
            root.addView(content)
            root.addView(buildNotesLeftSlideOutDock())
        }
        return root
    }

    private fun getDiaryPreferences() = getSharedPreferences("waqt_daily_journal", Context.MODE_PRIVATE)

    private fun diaryDateKey(year: Int, month: Int, day: Int): String =
        String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month + 1, day)

    private fun buildDailyJournalCalendar(): View {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val monthBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createGlassCardDrawable(true, false)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        fun monthButton(label: String, move: Int) = TextView(this).apply {
            text = label; textSize = 22f; gravity = Gravity.CENTER
            setTextColor(textPrimaryColor())
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            setOnClickListener {
                val c = java.util.Calendar.getInstance().apply { set(diaryDisplayedYear, diaryDisplayedMonth, 1); add(java.util.Calendar.MONTH, move) }
                diaryDisplayedYear = c.get(java.util.Calendar.YEAR)
                diaryDisplayedMonth = c.get(java.util.Calendar.MONTH)
                renderCurrentTab()
            }
        }
        monthBar.addView(monthButton("‹", -1))
        monthBar.addView(TextView(this).apply {
            val locale = if (isArabic) java.util.Locale("ar") else java.util.Locale.getDefault()
            val c = java.util.Calendar.getInstance().apply { set(diaryDisplayedYear, diaryDisplayedMonth, 1) }
            text = java.text.SimpleDateFormat("MMMM yyyy", locale).format(c.time)
            textSize = 17f; gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD); setTextColor(textPrimaryColor())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        monthBar.addView(monthButton("›", 1))
        outer.addView(monthBar)

        val grid = android.widget.GridLayout(this).apply {
            columnCount = 7
            rowCount = 7
            useDefaultMargins = false
            setPadding(dp(4), dp(10), dp(4), dp(10))
        }
        val locale = if (isArabic) java.util.Locale("ar") else java.util.Locale.getDefault()
        val dayFormat = java.text.SimpleDateFormat("EEEEE", locale)
        val weekdays = (0..6).map { offset ->
            val c = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
                add(java.util.Calendar.DAY_OF_YEAR, offset)
            }
            dayFormat.format(c.time)
        }
        weekdays.forEach { dayName ->
            grid.addView(TextView(this).apply {
                text = dayName; textSize = 11f; gravity = Gravity.CENTER
                setTextColor(textMutedColor())
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 0; height = dp(34); columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                }
            })
        }
        val month = java.util.Calendar.getInstance().apply { set(diaryDisplayedYear, diaryDisplayedMonth, 1) }
        val daysInMonth = month.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val mondayOffset = (month.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
        repeat(mondayOffset) {
            grid.addView(View(this).apply {
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 0; height = dp(54); columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                }
            })
        }
        val prefs = getDiaryPreferences()
        for (day in 1..daysInMonth) {
            val key = diaryDateKey(diaryDisplayedYear, diaryDisplayedMonth, day)
            val hasEntry = prefs.contains(key)
            val selected = key == diarySelectedDate
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = if (selected) createOrange3DDrawable(dp(14)) else if (hasEntry) createGlass3DDrawable(dp(14)) else null
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 0; height = dp(54)
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
                addView(TextView(this@MainActivity).apply {
                    text = day.toString(); textSize = 14f; gravity = Gravity.CENTER
                    setTextColor(if (selected) Color.parseColor("#0B1417") else textPrimaryColor())
                    setTypeface(typeface, if (selected || hasEntry) Typeface.BOLD else Typeface.NORMAL)
                })
                addView(View(this@MainActivity).apply {
                    visibility = if (hasEntry) View.VISIBLE else View.INVISIBLE
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (selected) Color.parseColor("#0B1417") else accentTealColor())
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(5), dp(5)).apply { topMargin = dp(2) }
                })
                setOnClickListener { diarySelectedDate = key; showDailyJournalEntryPage(key) }
            }
            grid.addView(cell)
        }
        outer.addView(grid)
        val savedCount = prefs.all.keys.count { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        outer.addView(TextView(this).apply {
            text = if (isArabic) "اضغط على أي يوم لكتابة يومياته أو قراءة ما سجلته سابقًا.  •  $savedCount يوم مسجل"
                else "Tap any day to write or revisit its entry.  •  $savedCount days saved"
            textSize = 12f; setTextColor(textMutedColor())
            setPadding(dp(8), dp(4), dp(8), dp(10))
        })
        return outer
    }

    private fun journalMediaPrefs() = getSharedPreferences("waqt_daily_journal_media", Context.MODE_PRIVATE)

    private fun readJournalMedia(dateKey: String): org.json.JSONArray =
        try { org.json.JSONArray(journalMediaPrefs().getString(dateKey, "[]") ?: "[]") } catch (_: Exception) { org.json.JSONArray() }

    private fun startJournalAudioRecording() {
        if (isRecordingJournalAudio) return
        try {
            val dir = java.io.File(filesDir, "journal_audio").apply { mkdirs() }
            val file = java.io.File(dir, "journal_${activeJournalMediaDateKey}_${System.currentTimeMillis()}.m4a")
            val recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(96000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            journalAudioRecorder = recorder
            journalAudioFile = file
            isRecordingJournalAudio = true
            showDailyJournalEntryPage(activeJournalMediaDateKey)
        } catch (e: Exception) {
            try { journalAudioRecorder?.release() } catch (_: Exception) {}
            journalAudioRecorder = null
            journalAudioFile?.delete()
            journalAudioFile = null
            isRecordingJournalAudio = false
            Toast.makeText(this, if (isArabic) "فشل بدء التسجيل الصوتي" else "Could not start recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopJournalAudioRecording() {
        val recorder = journalAudioRecorder ?: return
        try {
            recorder.stop()
            val file = journalAudioFile
            if (file != null && file.exists() && file.length() > 0L) {
                val media = readJournalMedia(activeJournalMediaDateKey)
                media.put(org.json.JSONObject().put("type", "audio").put("path", file.absolutePath)
                    .put("name", file.name).put("duration", ""))
                journalMediaPrefs().edit().putString(activeJournalMediaDateKey, media.toString()).apply()
                Toast.makeText(this, if (isArabic) "تم حفظ التسجيل الصوتي في يومياتك" else "Voice recording saved", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            journalAudioFile?.delete()
            Toast.makeText(this, if (isArabic) "التسجيل لم يكتمل، حاول مرة تانية" else "Recording did not complete", Toast.LENGTH_SHORT).show()
        } finally {
            try { recorder.reset(); recorder.release() } catch (_: Exception) {}
            journalAudioRecorder = null
            journalAudioFile = null
            isRecordingJournalAudio = false
            showDailyJournalEntryPage(activeJournalMediaDateKey)
        }
    }

    private fun showDailyJournalEntryPage(dateKey: String) {
        activeJournalMediaDateKey = dateKey
        val prefs = getDiaryPreferences()
        val existingText = prefs.getString(dateKey, "") ?: ""
        val date = try {
            val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(dateKey)
            java.text.SimpleDateFormat(if (isArabic) "EEEE، d MMMM yyyy" else "EEEE, d MMMM yyyy",
                if (isArabic) java.util.Locale("ar") else java.util.Locale.getDefault()).format(parsed!!)
        } catch (_: Exception) { dateKey }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassCardDrawable(true, false)
            setPadding(dp(16), dp(12), dp(16), dp(88))
            clipToPadding = false
        }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(TextView(this).apply {
            text = "‹"; textSize = 30f; gravity = Gravity.CENTER
            setTextColor(textPrimaryColor()); layoutParams = LinearLayout.LayoutParams(dp(44), dp(48))
            setOnClickListener {
                if (isRecordingJournalAudio) stopJournalAudioRecording()
                notesSubTab = 1; renderCurrentTab()
            }
        })
        toolbar.addView(TextView(this).apply {
            text = date; textSize = 16f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(textPrimaryColor()); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        page.addView(toolbar)
        val editor = EditText(this).apply {
            hint = if (isArabic) "اكتب يومياتك هنا...\n\nاحكي عن يومك، أفكارك، أو أي لحظة حابب تفتكرها." else "Write about your day...\n\nCapture your thoughts, moments and memories."
            setText(existingText)
            setTextColor(textPrimaryColor()); setHintTextColor(textMutedColor())
            textSize = 16f; gravity = Gravity.TOP or Gravity.START
            minLines = 7
            background = createGlass3DDrawable(dp(18))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        page.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(12); bottomMargin = dp(8)
        })
        editor.isVerticalScrollBarEnabled = true
        editor.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

        val mediaHeader = TextView(this).apply {
            text = if (isArabic) "المرفقات" else "Attachments"
            textSize = 13f; setTypeface(typeface, Typeface.BOLD); setTextColor(textPrimaryColor())
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }
        page.addView(mediaHeader)
        val mediaList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun refreshMediaList() {
            mediaList.removeAllViews()
            val items = readJournalMedia(dateKey)
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val isImage = item.optString("type") == "image"
                val path = item.optString("path")
                val uriText = item.optString("uri")
                val label = item.optString("name").ifBlank { if (isImage) "صورة" else "تسجيل صوتي" }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    background = createPillDrawable(bgElevatedColor(), dp(12), null)
                    setPadding(dp(10), dp(7), dp(10), dp(7))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(5) }
                }
                row.addView(TextView(this).apply {
                    text = (if (isImage) "🖼  " else "🎙  ") + label
                    textSize = 12f; setTextColor(textPrimaryColor()); maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                if (isImage || path.isNotBlank()) row.addView(TextView(this).apply {
                    text = if (isImage) "فتح" else "▶"
                    textSize = 13f; setTextColor(accentTealColor()); setPadding(dp(8), dp(4), dp(8), dp(4))
                    setOnClickListener {
                        try {
                            val uri = if (isImage) Uri.parse(uriText) else androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", java.io.File(path))
                            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, if (isImage) "image/*" else "audio/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        } catch (_: Exception) {
                            if (!isImage && path.isNotBlank()) {
                                try { MediaPlayer().apply { setDataSource(path); setOnCompletionListener { it.release() }; prepare(); start() } } catch (_: Exception) {}
                            } else Toast.makeText(this@MainActivity, "تعذر فتح المرفق", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
                row.addView(TextView(this).apply {
                    text = "×"; textSize = 19f; setTextColor(Color.parseColor("#FFFF8A8A")); setPadding(dp(8), 0, dp(4), 0)
                    setOnClickListener {
                        val updated = org.json.JSONArray()
                        for (j in 0 until items.length()) if (j != i) updated.put(items.getJSONObject(j))
                        journalMediaPrefs().edit().putString(dateKey, updated.toString()).apply()
                        if (!isImage && path.isNotBlank()) try { java.io.File(path).delete() } catch (_: Exception) {}
                        refreshMediaList()
                    }
                })
                mediaList.addView(row)
            }
            if (items.length() == 0) mediaList.addView(TextView(this).apply {
                text = if (isArabic) "لسه مفيش مرفقات لليوم ده" else "No attachments yet"
                textSize = 11f; setTextColor(textMutedColor())
            })
        }
        page.addView(mediaList)
        refreshMediaList()

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        actions.addView(createSettingsActionButton(if (isArabic) "إضافة صورة" else "Add image", "🖼") {
            journalImagePickerLauncher.launch(arrayOf("image/*"))
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        actions.addView(createSettingsActionButton(
            if (isRecordingJournalAudio) (if (isArabic) "إيقاف التسجيل" else "Stop recording") else (if (isArabic) "تسجيل صوت" else "Record voice"),
            if (isRecordingJournalAudio) "■" else "🎙"
        ) {
            if (isRecordingJournalAudio) stopJournalAudioRecording()
            else {
                activeJournalMediaDateKey = dateKey
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) startJournalAudioRecording()
                else journalAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(6) })
        page.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(6); bottomMargin = dp(6) })

        page.addView(Button(this).apply {
            text = if (isArabic) "حفظ يومياتي" else "Save journal"
            isAllCaps = false; setTextColor(Color.parseColor("#0B1417"))
            backgroundTintList = ColorStateList.valueOf(accentTealColor())
            setOnClickListener {
                val value = editor.text.toString().trim()
                if (value.isBlank()) prefs.edit().remove(dateKey).apply()
                else prefs.edit().putString(dateKey, value).apply()
                Toast.makeText(this@MainActivity, if (isArabic) "تم حفظ يوميات اليوم" else "Daily entry saved", Toast.LENGTH_SHORT).show()
                notesSubTab = 1
                renderCurrentTab()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        tabContentHost.removeAllViews()
        tabContentHost.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun buildStackedNotesCarousel(notes: List<NoteItem>): View {
        val total = notes.size
        val activeIdx = currentNoteCarouselIndex.coerceIn(0, total - 1)
        val stackContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(330))
            clipChildren = false
            clipToPadding = false
            cameraDistance = dp(12000).toFloat()
        }
        stackContainer.addView(TextView(this).apply {
            text = if (isArabic) "${activeIdx + 1} من $total" else "${activeIdx + 1} of $total"
            setTextColor(Color.parseColor("#D9F5F0"))
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#CC142A2E"))
                setStroke(dp(1), Color.parseColor("#6650C9C0"))
            }
            elevation = dp(4).toFloat()
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
        })
        // Keep the active card narrower than the viewport so both neighboring
        // cards remain visibly present, matching the layered reference layout.
        val cardWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
        fun prepareStackCard(card: View) {
            val lp = (card.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.width = cardWidth
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            card.layoutParams = lp
        }

        fun styleRearCard(card: View, side: Int) {
            prepareStackCard(card)
            setCardTreeEnabled(card, false)
            card.scaleX = 0.94f
            card.scaleY = 0.94f
            card.translationX = (side * dp(104)).toFloat()
            card.translationY = dp(42).toFloat()
            card.rotationY = side * -10f
            card.alpha = 0.70f
            card.elevation = dp(4).toFloat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                card.setRenderEffect(RenderEffect.createBlurEffect(dp(2).toFloat(), dp(2).toFloat(), Shader.TileMode.CLAMP))
            }
        }
        if (activeIdx > 0) stackContainer.addView(buildSingleNoteCardView(notes[activeIdx - 1], false).apply { styleRearCard(this, -1) })
        if (activeIdx + 1 < total) stackContainer.addView(buildSingleNoteCardView(notes[activeIdx + 1], false).apply { styleRearCard(this, 1) })
        val frontCard = buildSingleNoteCardView(notes[activeIdx], true).apply {
            prepareStackCard(this)
            scaleX = 1f; scaleY = 1f; translationY = dp(24).toFloat(); translationX = 0f
            rotationY = 0f; alpha = 1f; elevation = dp(22).toFloat()
        }
        setupCardSwipeNavigation(frontCard) { direction ->
            if (direction > 0 && currentNoteCarouselIndex < total - 1) {
                animateCarouselExit(frontCard, direction)
                currentNoteCarouselIndex++
                triggerGentleHaptic()
                stackContainer.postDelayed({ renderCurrentTab() }, 90)
            } else if (direction < 0 && currentNoteCarouselIndex > 0) {
                animateCarouselExit(frontCard, direction)
                currentNoteCarouselIndex--
                triggerGentleHaptic()
                stackContainer.postDelayed({ renderCurrentTab() }, 90)
            }
        }
        stackContainer.addView(frontCard)
        return stackContainer
    }

    private fun buildSingleNoteCardView(note: NoteItem, isForeground: Boolean): View {
        val displayTitle = note.title.takeIf { it.isNotBlank() && it != "ملاحظة" }
            ?: note.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(64)
            ?: if (isArabic) "ملاحظة بدون عنوان" else "Untitled note"
        val preview = note.content.trim()
        val pinned = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
            .getStringSet("waqt_pinned_notes", emptySet())?.contains(note.id) == true
        val cardColor = if (isDarkMode) Color.parseColor("#B8203B50") else Color.parseColor("#C9FFFFFF")
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = dp(22).toFloat()
                setColor(cardColor)
                setStroke(dp(1), if (isDarkMode) Color.parseColor("#665DD5E8") else Color.parseColor("#6680B8C4"))
            }
            setPadding(dp(13), dp(12), dp(13), dp(13))
            isClickable = true; isFocusable = true; elevation = dp(1).toFloat()
            setOnClickListener { showNoteDetailsPage(note) }
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (note.category.isNotBlank() && note.category != "general") {
            top.addView(TextView(this).apply {
                text = note.category; textSize = 10f
                setTextColor(if (isDarkMode) Color.parseColor("#FFD3E3EF") else Color.parseColor("#FF52616C"))
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        } else top.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
        top.addView(TextView(this).apply {
            text = if (pinned) "📌" else "📍"; textSize = 15f; gravity = Gravity.CENTER
            contentDescription = if (isArabic) "تثبيت الملاحظة" else "Pin note"
            setPadding(dp(5), 0, dp(2), 0)
            setOnClickListener {
                val prefs = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
                val ids = prefs.getStringSet("waqt_pinned_notes", emptySet())?.toMutableSet() ?: mutableSetOf()
                if (pinned) ids.remove(note.id) else ids.add(note.id)
                prefs.edit().putStringSet("waqt_pinned_notes", ids).apply()
                renderCurrentTab()
            }
        })
        card.addView(top)
        card.addView(TextView(this).apply {
            text = displayTitle
            setTextColor(if (isDarkMode) Color.parseColor("#FFF4F7FA") else Color.parseColor("#FF20262C"))
            textSize = 16f; setTypeface(typeface, Typeface.BOLD); maxLines = 4
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(8), 0, dp(5))
        })
        if (preview.isNotBlank()) card.addView(TextView(this).apply {
            text = preview
            setTextColor(if (isDarkMode) Color.parseColor("#FFD5E0E8") else Color.parseColor("#FF414B54"))
            textSize = 13f; maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(dp(3).toFloat(), 1.02f)
        })
        if (note.imageUri.isNotBlank()) {
            runCatching {
                card.addView(ImageView(this).apply {
                    setImageURI(Uri.parse(note.imageUri)); scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(118)).apply { topMargin = dp(9) }
                    background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(if (isDarkMode) Color.parseColor("#FF18232D") else Color.WHITE) }
                    clipToOutline = true
                })
            }
        }
        if (note.audioPath.isNotBlank() && java.io.File(note.audioPath).exists()) {
            card.addView(TextView(this).apply {
                text = "🎙  ${if (isArabic) "تسجيل صوتي" else "Voice note"}"
                textSize = 11f
                setTextColor(if (isDarkMode) Color.parseColor("#FFB9E7E3") else Color.parseColor("#FF236C67"))
                setPadding(0, dp(10), 0, 0)
            })
        }
        return card
    }

    private fun showNoteDetailsPage(note: NoteItem) {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassCardDrawable(true, false)
            setPadding(dp(18), dp(12), dp(18), dp(16))
        }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(TextView(this).apply {
            text = "‹"; textSize = 30f; gravity = Gravity.CENTER
            setTextColor(textPrimaryColor()); layoutParams = LinearLayout.LayoutParams(dp(44), dp(48))
            setOnClickListener { renderCurrentTab() }
        })
        toolbar.addView(TextView(this).apply {
            text = if (isArabic) "التدوينة" else "Journal entry"
            textSize = 18f; setTypeface(typeface, Typeface.BOLD); setTextColor(textPrimaryColor())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        toolbar.addView(TextView(this).apply {
            text = "✏️"; textSize = 20f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(48))
            setOnClickListener { showAddOrEditNoteModal(note) }
        })
        toolbar.addView(TextView(this).apply {
            text = "🗑"; textSize = 18f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(48))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(if (isArabic) "تحذف التدوينة دي؟" else "Delete this entry?")
                    .setNegativeButton(if (isArabic) "إلغاء" else "Cancel", null)
                    .setPositiveButton(if (isArabic) "حذف" else "Delete") { _, _ ->
                        ReminderRepository.deleteNote(this@MainActivity, note.id)
                        renderCurrentTab()
                    }.show()
            }
        })
        page.addView(toolbar)

        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(14), dp(4), dp(30))
        }
        body.addView(TextView(this).apply {
            text = note.title.takeIf { it.isNotBlank() && it != "ملاحظة" }
                ?: note.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                ?: if (isArabic) "ملاحظة بدون عنوان" else "Untitled note"
            textSize = 25f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(textPrimaryColor()); setPadding(0, 0, 0, dp(10))
        })
        if (note.category.isNotBlank() && note.category != "general") {
            body.addView(TextView(this).apply {
                text = "🏷 ${note.category}"; textSize = 12f
                setTextColor(accentTealColor()); setPadding(0, 0, 0, dp(18))
            })
        }
        if (note.content.isNotBlank()) {
            body.addView(TextView(this).apply {
                text = note.content; textSize = 16f; setTextColor(textPrimaryColor())
                gravity = Gravity.TOP or Gravity.START
                setLineSpacing(dp(7).toFloat(), 1.06f)
                setPadding(0, 0, 0, dp(20))
            })
        }
        if (note.imageUri.isNotBlank()) {
            runCatching {
                body.addView(ImageView(this).apply {
                    setImageURI(Uri.parse(note.imageUri))
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams(dp(190), dp(150)).apply { bottomMargin = dp(18) }
                    background = createGlass3DDrawable(dp(14)); clipToOutline = true
                    contentDescription = if (isArabic) "صورة مرفقة - اضغط للتكبير" else "Attached image - tap to enlarge"
                    setOnClickListener {
                        val preview = Dialog(this@MainActivity)
                        val full = ImageView(this@MainActivity).apply {
                            setImageURI(Uri.parse(note.imageUri)); scaleType = ImageView.ScaleType.FIT_CENTER
                            setBackgroundColor(Color.BLACK)
                            setOnClickListener { preview.dismiss() }
                        }
                        preview.setContentView(full)
                        preview.window?.setBackgroundDrawableResource(android.R.color.black)
                        preview.show()
                        preview.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                })
            }
        }
        if (note.audioPath.isNotBlank()) {
            val audioFile = java.io.File(note.audioPath)
            if (audioFile.exists()) {
                val audioRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    background = createGlass3DDrawable(dp(18))
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                val play = TextView(this).apply {
                    text = "▶"; textSize = 20f; gravity = Gravity.CENTER
                    setTextColor(accentTealColor()); layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                }
                val waveform = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
                }
                val heights = listOf(9,17,25,14,30,20,12,27,35,18,11,24,31,15,22,10,28,19,13,26,16,32,12,21)
                heights.forEachIndexed { i, h ->
                    waveform.addView(View(this).apply {
                        background = GradientDrawable().apply { cornerRadius = dp(4).toFloat(); setColor(accentTealColor()) }
                        layoutParams = LinearLayout.LayoutParams(dp(3), dp(h)).apply { marginEnd = dp(3) }
                        alpha = if (i % 4 == 0) 0.55f else 0.95f
                    })
                }
                audioRow.addView(play); audioRow.addView(waveform)
                var prepared = false
                play.setOnClickListener {
                    try {
                        val player = customAudioPlayer
                        if (player != null && player.isPlaying) {
                            player.pause(); play.text = "▶"
                        } else if (player != null && prepared) {
                            player.start(); play.text = "Ⅱ"
                        } else {
                            stopCustomAudio()
                            prepared = false
                            customAudioPlayer = MediaPlayer().apply {
                                setDataSource(note.audioPath)
                                setOnPreparedListener { prepared = true; it.start(); play.text = "Ⅱ" }
                                setOnCompletionListener { play.text = "▶"; it.release(); if (customAudioPlayer === it) customAudioPlayer = null; prepared = false }
                                prepareAsync()
                            }
                        }
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, if (isArabic) "تعذر تشغيل التسجيل" else "Could not play recording", Toast.LENGTH_SHORT).show()
                    }
                }
                body.addView(audioRow)
            }
        }
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        tabContentHost.removeAllViews()
        tabContentHost.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun buildNotesLeftSlideOutDock(): View {
        val dockLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = createRailDrawable()
            setPadding(dp(8), dp(16), dp(8), dp(16))
            val w = if (isNotesLeftDockOpen) dp(60) else dp(28)
            layoutParams = FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
            }
            elevation = dp(16).toFloat()
        }

        if (!isNotesLeftDockOpen) {
            val tvHandle = TextView(this).apply {
                text = "▸"
                textSize = 16f
                setTextColor(accentTealColor())
                gravity = Gravity.CENTER
            }
            dockLayout.addView(tvHandle)
            dockLayout.setOnClickListener {
                isNotesLeftDockOpen = true
                renderCurrentTab()
            }
        } else {
            dockLayout.addView(createDockIcon("🔍") {
                showSearchNotesDialog()
            })

            dockLayout.addView(createDockIcon("🪟") {
                launchFloatingQuickNoteOverlay()
            })

            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(1)).apply {
                    topMargin = dp(6)
                    bottomMargin = dp(10)
                }
                setBackgroundColor(Color.parseColor("#4DA5B4B4"))
            }
            dockLayout.addView(divider)

            dockLayout.addView(createDockIcon("🏷") {
                showNoteCategoriesDialog()
            })

            val tvClose = TextView(this).apply {
                text = "◂"
                textSize = 16f
                setTextColor(textMutedColor())
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
                setOnClickListener {
                    isNotesLeftDockOpen = false
                    renderCurrentTab()
                }
            }
            dockLayout.addView(tvClose)
        }
        finishRail(dockLayout, isNotesLeftDockOpen) { open -> isNotesLeftDockOpen = open; renderCurrentTab() }
        return dockLayout
    }

    private fun showSearchNotesDialog() {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createPillDrawable(bgCardColor(), dp(18), COLOR_TEAL)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val et = EditText(this).apply {
            hint = if (isArabic) "ابحث في مدونتي..." else "Search notes..."
            setHintTextColor(textMutedColor())
            setTextColor(textPrimaryColor())
            background = createPillDrawable(bgElevatedColor(), dp(12), null)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        v.addView(et)
        val btn = Button(this).apply {
            text = if (isArabic) "بحث" else "Search"
            isAllCaps = false
            setTextColor(Color.parseColor("#0B1417"))
            backgroundTintList = ColorStateList.valueOf(accentTealColor())
            setOnClickListener {
                noteSearchQuery = et.text.toString().trim()
                d.dismiss()
                renderCurrentTab()
            }
        }
        v.addView(btn)
        d.setContentView(v)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.85).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
        styleGlassDialog(d)
    }

    private fun showAddOrEditNoteModal(existingNote: NoteItem?) {
        noteAudioPath = existingNote?.audioPath ?: ""
        noteImageUri = existingNote?.imageUri ?: ""
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlassCardDrawable(true, false)
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(TextView(this).apply {
            text = "‹"; textSize = 30f; gravity = Gravity.CENTER
            setTextColor(textPrimaryColor()); layoutParams = LinearLayout.LayoutParams(dp(44), dp(48))
            setOnClickListener { renderCurrentTab() }
        })
        toolbar.addView(TextView(this).apply {
            text = if (existingNote == null) (if (isArabic) "تدوينة جديدة" else "New journal entry") else (if (isArabic) "تعديل التدوينة" else "Edit entry")
            textSize = 18f; setTypeface(typeface, Typeface.BOLD); setTextColor(textPrimaryColor())
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        page.addView(toolbar)
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createPillDrawable(bgCardColor(), dp(22), COLOR_TEAL)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        v.addView(TextView(this).apply {
            text = if (existingNote == null) (if (isArabic) "📔 تدوينة جديدة" else "📔 New journal entry") else (if (isArabic) "✏️ تعديل التدوينة" else "✏️ Edit entry")
            setTextColor(textPrimaryColor())
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        })

        val etTitle = EditText(this).apply {
            hint = if (isArabic) "عنوان الملاحظة..." else "Title..."
            setText(existingNote?.title ?: "")
            setHintTextColor(textMutedColor())
            setTextColor(textPrimaryColor())
            background = createPillDrawable(bgElevatedColor(), dp(12), null)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        v.addView(etTitle)

        val etContent = EditText(this).apply {
            hint = if (isArabic) "اكتب محتوى الملاحظة..." else "Content..."
            setText(existingNote?.content ?: "")
            setHintTextColor(textMutedColor())
            setTextColor(textPrimaryColor())
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            background = createPillDrawable(bgElevatedColor(), dp(12), null)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
        }
        v.addView(etContent)

        var selNoteCat: String? = existingNote?.category?.takeIf { it in getNoteCategories() }
        val noteCats = getNoteCategories()
        if (noteCats.isEmpty()) {
            v.addView(TextView(this).apply {
                text = if (isArabic) "🏷 تقدر تعمل تصنيفاتك بنفسك من أيقونة 🏷 في الشريط الجانبي" else "🏷 Create your own categories from the 🏷 rail icon"
                textSize = 12f
                setTextColor(textMutedColor())
                setPadding(0, dp(10), 0, 0)
            })
        } else {
            val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun paintChips() {
                chipRow.removeAllViews()
                (listOf<String?>(null) + noteCats).forEach { c ->
                    val sel = (c == selNoteCat)
                    chipRow.addView(TextView(this).apply {
                        text = c ?: (if (isArabic) "بدون" else "None")
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setTextColor(if (sel) Color.parseColor("#0B1417") else textPrimaryColor())
                        background = if (sel) createOrange3DDrawable(dp(16)) else createGlass3DDrawable(dp(16))
                        setPadding(dp(12), 0, dp(12), 0)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply { marginEnd = dp(6) }
                        setOnClickListener { selNoteCat = c; paintChips() }
                    })
                }
            }
            paintChips()
            v.addView(HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                setPadding(0, dp(12), 0, 0)
                addView(chipRow)
            })
        }

        val attachmentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        fun attachmentButton(label: String, action: () -> Unit) {
            attachmentRow.addView(TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(textPrimaryColor())
                background = createGlass3DDrawable(dp(12))
                setPadding(dp(10), dp(10), dp(10), dp(10))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
                setOnClickListener { action() }
            })
        }
        attachmentButton("🎙 ${if (isArabic) "تسجيل صوت" else "Record audio"}") { showNoteAudioRecorderDialog() }
        attachmentButton("📷 ${if (isArabic) "تصوير" else "Camera"}") { noteCameraLauncher.launch(null) }
        attachmentButton("🖼 ${if (isArabic) "اختيار صورة" else "Choose image"}") { noteImagePickerLauncher.launch(arrayOf("image/*")) }
        v.addView(attachmentRow)
        v.addView(TextView(this).apply {
            text = buildString {
                if (noteAudioPath.isNotBlank()) append("🎙 ${if (isArabic) "تم إرفاق تسجيل صوتي" else "Audio attached"}  ")
                if (noteImageUri.isNotBlank()) append("🖼 ${if (isArabic) "تم إرفاق صورة" else "Image attached"}")
            }
            setTextColor(accentTealColor())
            textSize = 11f
            setPadding(0, dp(6), 0, 0)
        })

        val btnSave = Button(this).apply {
            text = if (isArabic) "حفظ التدوينة" else "Save entry"
            isAllCaps = false
            setTextColor(Color.parseColor("#0B1417"))
            backgroundTintList = ColorStateList.valueOf(accentTealColor())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(16) }
            setOnClickListener {
                val t = etTitle.text.toString().trim()
                val c = etContent.text.toString().trim()
                if (t.isNotBlank() || c.isNotBlank()) {
                    val note = (existingNote ?: NoteItem(
                        id = UUID.randomUUID().toString(),
                        title = t.ifBlank { "ملاحظة" },
                        content = c
                    )).copy(
                        title = t.ifBlank { "ملاحظة" },
                        content = c,
                        category = selNoteCat ?: "general",
                        audioPath = noteAudioPath,
                        imageUri = noteImageUri
                    )
                    ReminderRepository.addOrUpdateNote(this@MainActivity, note)
                    renderCurrentTab()
                }
            }
        }
        v.addView(btnSave)

        val editorScroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(v)
        }
        page.addView(editorScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        tabContentHost.removeAllViews()
        tabContentHost.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun launchFloatingQuickNoteOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        val intent = Intent(this, FloatingCardService::class.java).apply {
            action = FloatingCardService.ACTION_OPEN_FLOATING_NOTE
            putExtra(FloatingCardService.EXTRA_IS_FLOATING_NOTE, true)
            putExtra(FloatingCardService.EXTRA_IS_ARABIC, isArabic)
        }
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, if (isArabic) "📝 ظهرت الملاحظة العائمة فوق التطبيقات!" else "Floating note active!", Toast.LENGTH_SHORT).show()
    }

    private fun showNoteAudioRecorderDialog() {
        val dialog = Dialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = createGlassCardDrawable(false, false)
            setPadding(dp(22), dp(22), dp(22), dp(20))
        }
        panel.addView(TextView(this).apply {
            text = if (isArabic) "🎙️ تسجيل ملاحظة صوتية" else "🎙️ Voice note recorder"
            textSize = 20f; setTypeface(typeface, Typeface.BOLD); setTextColor(textPrimaryColor()); gravity = Gravity.CENTER
        })
        val timer = TextView(this).apply {
            text = "00:00"; textSize = 34f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(accentTealColor()); gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(8))
        }
        panel.addView(timer)
        val status = TextView(this).apply {
            text = if (isArabic) "جاهز للتسجيل" else "Ready to record"
            textSize = 13f; setTextColor(textMutedColor()); gravity = Gravity.CENTER
        }
        panel.addView(status)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, 0)
        }
        val startBtn = TextView(this).apply {
            text = if (isArabic) "● بدء" else "● Start"; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); background = createPillDrawable(Color.parseColor("#FF248B72"), dp(16), null)
            setPadding(dp(22), dp(13), dp(22), dp(13))
        }
        val stopBtn = TextView(this).apply {
            text = if (isArabic) "✓ إيقاف وحفظ" else "✓ Stop & Save"; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); background = createPillDrawable(Color.parseColor("#FF3679B8"), dp(16), null)
            setPadding(dp(18), dp(13), dp(18), dp(13)); alpha = 0.45f; isEnabled = false
        }
        actions.addView(startBtn); actions.addView(stopBtn)
        panel.addView(actions)
        val cancel = TextView(this).apply {
            text = if (isArabic) "إلغاء" else "Cancel"; gravity = Gravity.CENTER
            setTextColor(textMutedColor()); setPadding(dp(12), dp(16), dp(12), 0)
        }
        panel.addView(cancel)
        dialog.setContentView(panel)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        styleGlassDialog(dialog)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        var startedAt = 0L
        var elapsedRunnable: Runnable? = null
        var outputFile: java.io.File? = null
        fun stopTicker() { elapsedRunnable?.let { timer.removeCallbacks(it) }; elapsedRunnable = null }
        fun discardRecording() {
            stopTicker()
            try { noteAudioRecorder?.stop() } catch (_: Exception) {}
            try { noteAudioRecorder?.release() } catch (_: Exception) {}
            noteAudioRecorder = null; isRecordingNoteAudio = false
            outputFile?.delete(); outputFile = null
        }
        startBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 702)
                Toast.makeText(this, if (isArabic) "إذن الميكروفون مطلوب للتسجيل فقط" else "Microphone permission is only needed to record", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            try {
                val file = java.io.File(filesDir, "note_audio_${System.currentTimeMillis()}.m4a")
                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setOutputFile(file.absolutePath); recorder.prepare(); recorder.start()
                outputFile = file; noteAudioRecorder = recorder; isRecordingNoteAudio = true; startedAt = System.currentTimeMillis()
                status.text = if (isArabic) "🔴 جاري التسجيل..." else "🔴 Recording..."
                startBtn.isEnabled = false; startBtn.alpha = 0.45f; stopBtn.isEnabled = true; stopBtn.alpha = 1f
                val tick = object : Runnable {
                    override fun run() {
                        val sec = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0)
                        timer.text = String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
                        timer.postDelayed(this, 500)
                    }
                }
                elapsedRunnable = tick; timer.post(tick)
            } catch (_: Exception) {
                Toast.makeText(this, if (isArabic) "تعذر بدء التسجيل" else "Could not start recording", Toast.LENGTH_LONG).show()
                discardRecording()
            }
        }
        stopBtn.setOnClickListener {
            try {
                noteAudioRecorder?.stop()
                // outputFile is nullable while the recorder is being prepared.
                // At this point recording has started, so require a valid path before saving.
                noteAudioPath = outputFile?.absolutePath
                    ?: throw IllegalStateException("Recording output file is unavailable")
                Toast.makeText(this, if (isArabic) "تم حفظ التسجيل. اضغط حفظ التدوينة لإرفاقه." else "Recording saved. Save the journal entry to attach it.", Toast.LENGTH_LONG).show()
                stopTicker(); noteAudioRecorder?.release(); noteAudioRecorder = null; isRecordingNoteAudio = false
                dialog.dismiss()
            } catch (_: Exception) {
                Toast.makeText(this, if (isArabic) "التسجيل قصير جدًا أو تعذر حفظه" else "Recording was too short or could not be saved", Toast.LENGTH_LONG).show()
                discardRecording()
            }
        }
        cancel.setOnClickListener { if (isRecordingNoteAudio) discardRecording(); dialog.dismiss() }
        dialog.setOnCancelListener { if (isRecordingNoteAudio) discardRecording() }
    }

    private fun buildPomodoroScreenView(): View {
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        if (isFlipClockModeActive) {
            return buildFullscreenClockView()
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dp(16), dp(12), dp(16), dp(120))
        }

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(14))
        }

        val btnTaskLinked = TextView(this).apply {
            text = if (isArabic) "🎯 بومودورو بمهمة" else "🎯 Linked to Task"
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (isPomodoroLinkedToTask) Color.parseColor("#0B1417") else textPrimaryColor())
            background = if (isPomodoroLinkedToTask) createOrange3DDrawable(dp(14)) else createGlass3DDrawable(dp(14))
            elevation = dp(6).toFloat()
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
            setOnClickListener {
                triggerGentleHaptic()
                isPomodoroLinkedToTask = true
                showTaskPickerSortedByTime()
            }
        }
        modeRow.addView(btnTaskLinked)

        val btnFree = TextView(this).apply {
            text = if (isArabic) "🍅 بومودورو حر" else "🍅 Free Session"
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (!isPomodoroLinkedToTask) Color.parseColor("#0B1417") else textPrimaryColor())
            background = if (!isPomodoroLinkedToTask) createOrange3DDrawable(dp(14)) else createGlass3DDrawable(dp(14))
            elevation = dp(6).toFloat()
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            setOnClickListener {
                triggerGentleHaptic()
                isPomodoroLinkedToTask = false
                selectedFocusTaskId = null
                renderCurrentTab()
            }
        }
        modeRow.addView(btnFree)
        content.addView(modeRow)

        if (isPomodoroLinkedToTask && selectedFocusTaskId != null) {
            val task = ReminderRepository.getItemById(this, selectedFocusTaskId ?: "")
            content.addView(TextView(this).apply {
                text = "🎯 المهمة المختارة: ${task?.title ?: ""}"
                setTextColor(accentTealColor())
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, 0, dp(8))
            })
        }

        val ringView = CircularTimerRingView(this).apply {
            val s = dp(230)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                topMargin = dp(6)
                bottomMargin = dp(14)
            }
            updateTimerDisplay(pomodoroRemainingMillis, pomodoroTotalMillis)
            setOnClickListener {
                if (!isPomodoroRunning) {
                    showSetTimeInsideDialDialog()
                }
            }
        }
        circularTimerView = ringView
        content.addView(ringView)

        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(14))
        }

        val btnStart = TextView(this).apply {
            text = if (isPomodoroRunning) (if (isArabic) "⏸ إيقاف مؤقت" else "⏸ Pause") else (if (isArabic) "▶ بدء التركيز" else "▶ Start")
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0B1417"))
            background = createOrange3DDrawable(dp(24))
            elevation = dp(12).toFloat()
            layoutParams = LinearLayout.LayoutParams(dp(148), dp(48)).apply { marginEnd = dp(10) }
            setOnClickListener {
                triggerGentleHaptic()
                toggleInAppPomodoro()
            }
        }
        controlsRow.addView(btnStart)

        val btnTickTock = TextView(this).apply {
            text = if (isTickTockSoundActive) "⏱️ تيك-تاك ✓" else "⏱️ تيك-تاك"
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (isTickTockSoundActive) Color.parseColor("#0B1417") else textPrimaryColor())
            background = if (isTickTockSoundActive) create3DButtonDrawable(Color.parseColor("#FF4FD6C9"), Color.parseColor("#FF168A84"), dp(24), Color.parseColor("#FF9CFFF0")) else createGlass3DDrawable(dp(24))
            elevation = dp(8).toFloat()
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(48))
            setOnClickListener {
                triggerGentleHaptic()
                isTickTockSoundActive = !isTickTockSoundActive
                getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit().putBoolean(ReminderRepository.KEY_TICK_TOCK_ENABLED, isTickTockSoundActive).apply()
                renderCurrentTab()
            }
        }
        controlsRow.addView(btnTickTock)
        content.addView(controlsRow)

        val btnModeSwitch = TextView(this).apply {
            text = if (isBreakSession) (if (isArabic) "🎯 رجوع للتركيز" else "🎯 Back to focus") else (if (isArabic) "☕ ابدأ استراحة" else "☕ Start a break")
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textPrimaryColor())
            background = createGlass3DDrawable(dp(20))
            elevation = dp(6).toFloat()
            setPadding(dp(18), 0, dp(18), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { bottomMargin = dp(12) }
            setOnClickListener {
                if (isPomodoroRunning) {
                    Toast.makeText(this@MainActivity, if (isArabic) "أوقف الجلسة الأول" else "Pause the session first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                triggerGentleHaptic()
                isBreakSession = !isBreakSession
                pomodoroTotalMillis = (if (isBreakSession) selectedBreakMinutes else selectedPomodoroMinutes) * 60 * 1000L
                pomodoroRemainingMillis = pomodoroTotalMillis
                renderCurrentTab()
            }
        }
        content.addView(btnModeSwitch)

        content.addView(buildFocusDashboard())

        root.addView(ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            addView(content)
        })
        root.addView(buildPomodoroLeftSlideOutDock())
        return root
    }

    private fun showTaskPickerSortedByTime() {
        val tasks = ReminderRepository.getAllItems(this).filter { !it.isHabit && !it.isCompletedToday }
            .sortedWith(compareBy({ it.hour }, { it.minute }))

        val names = tasks.map { "⏰ ${String.format(Locale.US, "%02d:%02d", it.hour, it.minute)} • ${it.title}" }

        AlertDialog.Builder(this)
            .setTitle(if (isArabic) "اختر المهمة الأقرب لوقتك:" else "Select Task by Due Time:")
            .setItems(names.toTypedArray()) { _, which ->
                selectedFocusTaskId = tasks[which].id
                renderCurrentTab()
            }
            .show()
    }

    private fun showSetTimeInsideDialDialog() {
        val options = arrayOf("15 دقيقة", "25 دقيقة", "45 دقيقة", "50 دقيقة", "60 دقيقة", "تحديد مخصص...")
        AlertDialog.Builder(this)
            .setTitle(if (isArabic) "تحديد وقت الجلسة من قلب الساعة:" else "Set Duration:")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setPomodoroDuration(15)
                    1 -> setPomodoroDuration(25)
                    2 -> setPomodoroDuration(45)
                    3 -> setPomodoroDuration(50)
                    4 -> setPomodoroDuration(60)
                    5 -> showCustomMinutesDialog()
                }
            }
            .show()
    }

    private fun buildPomodoroLeftSlideOutDock(): View {
        val dockLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = createRailDrawable()
            setPadding(dp(8), dp(16), dp(8), dp(16))
            val w = if (isPomodoroLeftDockOpen) dp(60) else dp(28)
            layoutParams = FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
            }
            elevation = dp(16).toFloat()
        }

        if (!isPomodoroLeftDockOpen) {
            val tvHandle = TextView(this).apply {
                text = "▸"
                textSize = 22f
                setTextColor(Color.parseColor("#FF4FD6C9"))
                gravity = Gravity.CENTER
            }
            dockLayout.addView(tvHandle)
            dockLayout.setOnClickListener {
                isPomodoroLeftDockOpen = true
                renderCurrentTab()
            }
        } else {
            dockLayout.addView(createDockIcon("🔇") {
                stopAmbientSound()
                selectedAmbientIndex = 0
                getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit().putInt(ReminderRepository.KEY_DEFAULT_SOUND_INDEX, 0).apply()
                Toast.makeText(this, if (isArabic) "🔇 اتوقف الصوت المحيط" else "Ambient sound stopped", Toast.LENGTH_SHORT).show()
                renderCurrentTab()
            })

            dockLayout.addView(createDockIcon("📊") {
                showStatsDialog("focus")
            })

            dockLayout.addView(createDockIcon("🏆") {
                showRankDialog("focus")
            })

            dockLayout.addView(createDockIcon("🎧") {
                showWhiteNoiseConsoleModal()
            })

            dockLayout.addView(createDockIcon("⛶") {
                isFlipClockModeActive = true
                renderCurrentTab()
            })

            dockLayout.addView(createDockIcon("🪟") {
                launchFloatingFocusWindow(null)
            })

            val tvClose = TextView(this).apply {
                text = "◂"
                textSize = 16f
                setTextColor(textMutedColor())
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
                setOnClickListener {
                    isPomodoroLeftDockOpen = false
                    renderCurrentTab()
                }
            }
            dockLayout.addView(tvClose)
        }
        finishRail(dockLayout, isPomodoroLeftDockOpen) { open -> isPomodoroLeftDockOpen = open; renderCurrentTab() }
        return dockLayout
    }

    private fun showWhiteNoiseConsoleModal() {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val scroll = ScrollView(this).apply {
            background = createPillDrawable(bgCardColor(), dp(24), COLOR_TEAL)
        }
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(TextView(this).apply {
            text = if (isArabic) "ضوضاء بيضاء" else "White Noise"
            setTextColor(textPrimaryColor())
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        headerRow.addView(TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(textMutedColor())
            setOnClickListener { d.dismiss() }
        })
        v.addView(headerRow)

        val volCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(14))
        }
        volCol.addView(TextView(this).apply {
            text = if (isArabic) "مستوي الصوت" else "Volume Level"
            setTextColor(textPrimaryColor())
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        })
        val sb = SeekBar(this).apply {
            max = 100
            progress = (ambientVolume * 100).toInt()
            progressTintList = ColorStateList.valueOf(Color.parseColor("#F16D68"))
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#F16D68"))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, prog: Int, fromUser: Boolean) {
                    ambientVolume = (prog / 100f).coerceIn(0.05f, 1f)
                    soundEngine.setAmbientVolume(ambientVolume)
                    customAudioPlayer?.setVolume(ambientVolume, ambientVolume)
                    getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
                        .putInt(ReminderRepository.KEY_SOUND_VOLUME, (ambientVolume * 100).toInt()).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        volCol.addView(sb)
        v.addView(volCol)

        val btnAddLocal = Button(this).apply {
            text = if (customAudioName != null) "🎵 $customAudioName" else (if (isArabic) "📁 اختيار ملف صوتي أو موسيقى من جهازي" else "📁 Pick Audio/MP3 from Device")
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.parseColor("#0B1417"))
            backgroundTintList = ColorStateList.valueOf(accentOrangeColor())
            setOnClickListener {
                isPickingAudioFromConsole = true
                localAudioPickerLauncher.launch(arrayOf("audio/*"))
                d.dismiss()
            }
        }
        v.addView(btnAddLocal)

        val soundTitles = if (isArabic) listOf(
            "إيقاف", "المطر", "أمواج البحر", "نار المخيم", "الغابة",
            "جدول الماء", "المكتبة", "الهواء", "أصوات الليل"
        ) else listOf(
            "Off", "Rain", "Ocean Waves", "Campfire", "Forest",
            "Water Stream", "Library", "Wind", "Night Ambience"
        )

        soundTitles.forEachIndexed { idx, name ->
            val isSel = (selectedAmbientIndex == idx)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))

                val tvRadio = TextView(this@MainActivity).apply {
                    text = if (isSel) "🔘" else "⚪"
                    textSize = 16f
                    setPadding(0, 0, dp(12), 0)
                }
                val tvName = TextView(this@MainActivity).apply {
                    text = name
                    textSize = 15f
                    setTextColor(if (isSel) accentOrangeColor() else textPrimaryColor())
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                addView(tvRadio)
                addView(tvName)

                setOnClickListener {
                    selectedAmbientIndex = idx
                    getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
                        .putInt(ReminderRepository.KEY_DEFAULT_SOUND_INDEX, idx).apply()
                    if (idx == 0) {
                        stopAmbientSound()
                        stopCustomAudio()
                    } else startRichAmbientSound(idx)
                    d.dismiss()
                    renderCurrentTab()
                }
            }
            v.addView(row)
        }

        scroll.addView(v)
        d.setContentView(scroll)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), (resources.displayMetrics.heightPixels * 0.85).toInt())
        d.show()
        styleGlassDialog(d)
    }

    private fun buildFullscreenClockView(): View {
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.BLACK)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(10))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val btnSwitchType = TextView(this).apply {
            text = if (fullscreenClockType == "flip") "⏳ ساعة رملية" else "⏲️ ساعة فليب"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = createPillDrawable(Color.parseColor("#1B383C"), dp(12), null)
            setOnClickListener {
                fullscreenClockType = if (fullscreenClockType == "flip") "hourglass" else "flip"
                renderCurrentTab()
            }
        }
        topBar.addView(btnSwitchType)

        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f) }
        topBar.addView(spacer)

        val btnClose = TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                isFlipClockModeActive = false
                renderCurrentTab()
            }
        }
        topBar.addView(btnClose)
        root.addView(topBar)

        val centerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val totalSec = (pomodoroRemainingMillis / 1000L).coerceAtLeast(0L)
        val mStr = String.format(Locale.US, "%02d", totalSec / 60L)
        val sStr = String.format(Locale.US, "%02d", totalSec % 60L)

        if (fullscreenClockType == "flip") {
            val rowTopDigits = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            rowTopDigits.addView(createFlipDigitCard(mStr[0]))
            rowTopDigits.addView(createFlipDigitCard(mStr[1]))
            centerBox.addView(rowTopDigits)

            val rowBottomDigits = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(14), 0, 0) }
            rowBottomDigits.addView(createFlipDigitCard(sStr[0]))
            rowBottomDigits.addView(createFlipDigitCard(sStr[1]))
            centerBox.addView(rowBottomDigits)
        } else {
            centerBox.addView(HourglassAnimatedView(this).apply {
                val s = dp(240)
                layoutParams = LinearLayout.LayoutParams(s, s)
                progressFraction = if (pomodoroTotalMillis > 0) (pomodoroRemainingMillis.toFloat() / pomodoroTotalMillis.toFloat()) else 0f
            })
            centerBox.addView(TextView(this).apply {
                text = "$mStr:$sStr"
                textSize = 34f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(16), 0, 0)
            })
        }

        val btnPlayPause = TextView(this).apply {
            text = if (isPomodoroRunning) "⏸" else "▶"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1B383C"))
            }
            val s = dp(64)
            layoutParams = FrameLayout.LayoutParams(s, s).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(40)
            }
            setOnClickListener {
                triggerGentleHaptic()
                toggleInAppPomodoro()
                renderCurrentTab()
            }
        }
        root.addView(centerBox)
        root.addView(btnPlayPause)
        return root
    }

    private fun createFlipDigitCard(ch: Char): View {
        return FrameLayout(this).apply {
            val w = dp(140)
            val h = dp(160)
            layoutParams = LinearLayout.LayoutParams(w, h).apply { marginStart = dp(6); marginEnd = dp(6) }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121820"))
                cornerRadius = dp(16).toFloat()
            }

            val tv = TextView(context).apply {
                text = ch.toString()
                textSize = 88f
                setTextColor(Color.parseColor("#D0D7DE"))
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            addView(tv)

            val divider = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(2), Gravity.CENTER)
                setBackgroundColor(Color.BLACK)
            }
            addView(divider)
        }
    }

    private fun showAccordionSettingsDialog() {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            background = GradientDrawable().apply {
                setColor(bgPrimaryColor())
                cornerRadius = dp(26).toFloat()
                setStroke(dp(1), Color.parseColor("#5535B6AD"))
            }
            clipToOutline = true
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(22))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleColumn.addView(TextView(this).apply {
            text = if (isArabic) "إعدادات وَقْت" else "WAQT Settings"
            setTextColor(textPrimaryColor())
            textSize = 23f
            setTypeface(typeface, Typeface.BOLD)
        })
        titleColumn.addView(TextView(this).apply {
            text = if (isArabic) "خلّي تجربتك مناسبة ليك." else "Make your experience yours."
            setTextColor(textMutedColor())
            textSize = 13f
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(titleColumn)
        header.addView(TextView(this).apply {
            text = "✕"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(textMutedColor())
            background = createPillDrawable(bgElevatedColor(), dp(14), null)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setOnClickListener { d.dismiss() }
        })
        body.addView(header)

        val brandLine = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(accentTealColor(), accentOrangeColor())
            ).apply { cornerRadius = dp(3).toFloat() }
            layoutParams = LinearLayout.LayoutParams(dp(74), dp(4)).apply {
                topMargin = dp(4)
                bottomMargin = dp(16)
            }
        }
        body.addView(brandLine)

        // Quick appearance card
        val appearanceCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(bgCardColor())
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.parseColor("#3335B6AD"))
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        appearanceCard.addView(TextView(this).apply {
            text = if (isArabic) "المظهر" else "Appearance"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textPrimaryColor())
        })
        val appearanceActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        appearanceActions.addView(TextView(this).apply {
            text = if (isDarkMode) (if (isArabic) "🌙 الوضع الداكن" else "🌙 Dark mode")
                   else (if (isArabic) "☀️ الوضع الفاتح" else "☀️ Light mode")
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(textPrimaryColor())
            background = createPillDrawable(bgElevatedColor(), dp(14), "#5535B6AD")
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                isDarkMode = !isDarkMode
                getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
                    .putBoolean(ReminderRepository.KEY_DARK_MODE, isDarkMode).apply()
                d.dismiss()
                recreate()
            }
        })
        appearanceActions.addView(TextView(this).apply {
            text = if (isArabic) "🌐 اللغة" else "🌐 Language"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(accentOrangeColor())
            background = createPillDrawable(bgElevatedColor(), dp(14), "#55FF8A3D")
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener {
                d.dismiss()
                toggleLanguage()
            }
        })
        appearanceCard.addView(appearanceActions)
        body.addView(appearanceCard)

        body.addView(createAccordionSection(if (isArabic) "✨ إعدادات المساعد الذكي" else "✨ AI Assistant") { container ->
            container.addView(TextView(this@MainActivity).apply {
                text = if (isArabic) "إعدادات المساعد مستقلة عن باقي التطبيق. اربط Gemini وGroq وOpenRouter، وWAQT هينتقل تلقائيًا بين المزودين عند تعطل الخدمة أو انتهاء الحد." else "Connect Gemini, Groq, and OpenRouter here. WAQT automatically switches providers when a service is unavailable or rate-limited."
                textSize = 12f; setTextColor(textMutedColor()); setPadding(dp(8), dp(4), dp(8), dp(10))
            })
            container.addView(createSettingsActionButton(if (isArabic) "مفتاح API واختيار الموديل" else "API key & model", "🔑") {
                showGeminiKeyDialog()
            })
            container.addView(TextView(this@MainActivity).apply {
                text = if (isArabic) "الترتيب: Gemini ← Groq ← OpenRouter | موديل Gemini: ${geminiModel()}" else "Fallback order: Gemini → Groq → OpenRouter | Gemini model: ${geminiModel()}"
                textSize = 11f; setTextColor(textMutedColor()); setPadding(dp(12), dp(8), dp(12), dp(4))
            })
        })

        body.addView(createAccordionSection(if (isArabic) "🍅 التركيز والاستراحة" else "🍅 Focus & Breaks") { container ->
            fun addDurationRow(label: String, value: Int, onChoose: () -> Unit) {
                val row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = createPillDrawable(bgCardColor(), dp(12), null)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                }
                row.addView(TextView(this@MainActivity).apply {
                    text = label
                    textSize = 14f
                    setTextColor(textPrimaryColor())
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(TextView(this@MainActivity).apply {
                    text = "$value ${if (isArabic) "دقيقة" else "min"}  ›"
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(accentOrangeColor())
                })
                row.setOnClickListener { onChoose() }
                container.addView(row)
            }

            addDurationRow(if (isArabic) "مدة التركيز الافتراضية" else "Default focus duration", selectedPomodoroMinutes) {
                showDurationSettingDialog(
                    if (isArabic) "مدة التركيز بالدقائق" else "Focus duration (minutes)",
                    selectedPomodoroMinutes, true, d
                )
            }
            addDurationRow(if (isArabic) "مدة الراحة الافتراضية" else "Default break duration", selectedBreakMinutes) {
                showDurationSettingDialog(
                    if (isArabic) "مدة الراحة بالدقائق" else "Break duration (minutes)",
                    selectedBreakMinutes, false, d
                )
            }

            val tickRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(4), dp(2), dp(4))
            }
            tickRow.addView(TextView(this@MainActivity).apply {
                text = if (isArabic) "صوت تيك-تاك أثناء التركيز" else "Tick-tock sound during focus"
                textSize = 14f
                setTextColor(textPrimaryColor())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            val tickSwitch = Switch(this@MainActivity).apply {
                isChecked = isTickTockSoundActive
                buttonTintList = ColorStateList.valueOf(accentOrangeColor())
                setOnCheckedChangeListener { _, checked ->
                    isTickTockSoundActive = checked
                    getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
                        .putBoolean(ReminderRepository.KEY_TICK_TOCK_ENABLED, checked).apply()
                    syncTickTock()
                }
            }
            tickRow.addView(tickSwitch)
            container.addView(tickRow)
        })

        body.addView(createAccordionSection(if (isArabic) "🔔 التنبيهات والصوت" else "🔔 Alarms & Audio") { container ->
            val ringtoneButton = createSettingsActionButton(
                if (isArabic) "اختيار نغمة المنبه" else "Choose alarm ringtone",
                "♫"
            ) {
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                }
                ringtonePickerLauncher.launch(intent)
            }
            container.addView(ringtoneButton)

            val customAudioButton = createSettingsActionButton(
                if (isArabic) "اختيار صوت أو موسيقى من الجهاز" else "Choose audio from device",
                "♫"
            ) {
                localAudioPickerLauncher.launch(arrayOf("audio/*"))
            }
            container.addView(customAudioButton)
            container.addView(TextView(this@MainActivity).apply {
                text = customAudioName?.let {
                    if (isArabic) "المحدد حاليًا: $it" else "Selected: $it"
                } ?: if (isArabic) "يمكنك اختيار ملف صوتي من جهازك من قائمة أصوات التركيز." else "Choose a local audio file for focus sessions."
                setTextColor(textMutedColor())
                textSize = 12f
                setPadding(0, dp(7), 0, 0)
            })
            container.addView(createSettingsActionButton(
                if (isArabic) "▶ معاينة / إيقاف الصوت المختار" else "▶ Preview / stop selected audio",
                "♫"
            ) {
                if (customAudioPlayer?.isPlaying == true) {
                    stopCustomAudio()
                } else {
                    if (customAudioUri == null) {
                        Toast.makeText(this@MainActivity, if (isArabic) "اختار ملف صوتي الأول" else "Choose an audio file first", Toast.LENGTH_SHORT).show()
                    } else playCustomAudio()
                }
            })
        })

        body.addView(createAccordionSection(if (isArabic) "💾 بياناتك والنسخ الاحتياطي" else "💾 Your Data & Backup") { container ->
            container.addView(createSettingsActionButton(
                if (isArabic) "حفظ نسخة في ملف / جوجل درايف" else "Save backup to file / Google Drive",
                "☁"
            ) {
                pendingBackupJson = ReminderRepository.exportCloudBackupJson(this@MainActivity)
                val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                backupExportLauncher.launch("waqt-backup-$stamp.json")
            })
            container.addView(TextView(this@MainActivity).apply {
                text = if (isArabic) "إعدادات المساعد الذكي موجودة في قسم مستقل أعلى هذه الصفحة." else "AI assistant settings are in their own section above."
                setTextColor(textMutedColor()); textSize = 12f
                setPadding(dp(12), dp(10), dp(12), dp(10))
            })
            container.addView(createSettingsActionButton(
                if (isArabic) "🔔 أذونات التنبيهات والخلفية" else "🔔 Reminder permissions",
                "🔔"
            ) {
                showOnboarding(true)
            })
            container.addView(createSettingsActionButton(
                if (isArabic) "🎓 شرح التطبيق" else "🎓 App tour",
                "🎓"
            ) {
                showOnboarding(false)
            })
            container.addView(createSettingsActionButton(
                if (isArabic) "مشاركة نسخة احتياطية (نص)" else "Share backup (text)",
                "↗"
            ) {
                val json = ReminderRepository.exportCloudBackupJson(this@MainActivity)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, json)
                }
                startActivity(Intent.createChooser(shareIntent, if (isArabic) "نسخة وقت الاحتياطية" else "WAQT Backup"))
            })
            container.addView(createSettingsActionButton(
                if (isArabic) "استعادة نسخة احتياطية من ملف" else "Restore backup from file",
                "↙"
            ) {
                backupImportLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
            })
            container.addView(TextView(this@MainActivity).apply {
                text = if (isArabic) "احتفظ بالنسخة في مكان آمن لاستعادة بياناتك لاحقًا." else "Keep the backup somewhere safe so you can restore your data later."
                setTextColor(textMutedColor())
                textSize = 12f
                setPadding(0, dp(6), 0, 0)
            })
        })

        body.addView(createAccordionSection(if (isArabic) "🛡️ صلاحيات التطبيق" else "🛡️ App Permissions") { container ->
            val overlayEnabled = Settings.canDrawOverlays(this@MainActivity)
            container.addView(createSettingsActionButton(
                if (overlayEnabled) {
                    if (isArabic) "✓ النافذة العائمة مفعلة" else "✓ Floating window enabled"
                } else {
                    if (isArabic) "تفعيل النافذة العائمة" else "Enable floating window"
                },
                "▣"
            ) { requestOverlayPermission() })
            container.addView(TextView(this@MainActivity).apply {
                text = if (isArabic)
                    "تحتاج هذه الصلاحية فقط لاستخدام فقاعة الشات أو نافذة التركيز العائمة."
                else
                    "This permission is only needed for the floating chat or focus window."
                setTextColor(textMutedColor())
                textSize = 12f
                setPadding(0, dp(6), 0, 0)
            })
        })

        body.addView(createAccordionSection(if (isArabic) "ℹ️ عن وَقْت والتحديث السحابي" else "ℹ️ About WAQT & Cloud Update") { container ->
            container.addView(TextView(this@MainActivity).apply {
                text = if (isArabic)
                    "وَقْت — مساحة هادئة لمهامك وعاداتك وتركيزك.\\nالتحديث الأونلاين التلقائي مفعل (GitHub Cloud Sync)."
                else
                    "WAQT — a calm space for your tasks, habits and focus.\\nAutomatic Online Cloud Update is enabled."
                setTextColor(textMutedColor())
                textSize = 13f
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(0, 0, 0, dp(8))
            })
            val autoUpdateRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(10))
            }
            autoUpdateRow.addView(TextView(this@MainActivity).apply {
                text = if (isArabic) "الفحص التلقائي عن تحديثات" else "Check for updates automatically"
                textSize = 13f; setTextColor(textPrimaryColor())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            autoUpdateRow.addView(Switch(this@MainActivity).apply {
                isChecked = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).getBoolean("ota_auto_check", false)
                buttonTintList = ColorStateList.valueOf(accentTealColor())
                setOnCheckedChangeListener { _, enabled ->
                    getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit().putBoolean("ota_auto_check", enabled).apply()
                }
            })
            container.addView(autoUpdateRow)
            container.addView(TextView(this@MainActivity).apply {
                text = if (isArabic) "لن يتم تثبيت أي تحديث تلقائيًا. هيوصلك إشعار/نافذة فيها اختيار واضح: تحديث الآن أو لاحقًا." else "Updates are never installed automatically. You will always choose Update now or Later."
                textSize = 12f; setTextColor(textMutedColor()); setPadding(0, 0, 0, dp(8))
            })
            container.addView(createSettingsActionButton(
                if (isArabic) "فحص التحديثات أونلاين الآن" else "Check for Online Updates Now",
                "🔄"
            ) {
                triggerGentleHaptic()
                Toast.makeText(this@MainActivity, if (isArabic) "جاري فحص التحديث من GitHub..." else "Checking GitHub for updates...", Toast.LENGTH_SHORT).show()
                checkForOnlineUpdates(manual = true)
            })
        })

        scroll.addView(body)
        d.setContentView(scroll)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.show()
        styleGlassDialog(d)
        d.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            (resources.displayMetrics.heightPixels * 0.84).toInt()
        )
    }

    private fun createSettingsActionButton(label: String, icon: String, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createPillDrawable(bgCardColor(), dp(13), "#3335B6AD")
            setPadding(dp(13), dp(12), dp(13), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            addView(TextView(this@MainActivity).apply {
                text = icon
                textSize = 17f
                setTextColor(accentOrangeColor())
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 14f
                setTextColor(textPrimaryColor())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            setOnClickListener { onClick() }
        }
    }

    private fun showDurationSettingDialog(title: String, currentValue: Int, isFocus: Boolean, parentDialog: Dialog) {
        val inputDialog = Dialog(this)
        inputDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(bgCardColor())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#5535B6AD"))
            }
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        panel.addView(TextView(this).apply {
            text = title
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textPrimaryColor())
        })
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(currentValue.toString())
            setTextColor(textPrimaryColor())
            setHintTextColor(textMutedColor())
            background = createPillDrawable(bgElevatedColor(), dp(12), null)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14); bottomMargin = dp(12) }
        }
        panel.addView(input)
        panel.addView(createSettingsActionButton(if (isArabic) "حفظ" else "Save", "✓") {
            val value = input.text.toString().toIntOrNull()
            if (value == null || value !in 1..240) {
                input.error = if (isArabic) "اكتب رقمًا من 1 إلى 240" else "Enter a value from 1 to 240"
                return@createSettingsActionButton
            }
            val prefs = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit()
            if (isFocus) {
                selectedPomodoroMinutes = value
                prefs.putInt(ReminderRepository.KEY_DEFAULT_FOCUS_MINS, value)
                if (!isPomodoroRunning && !isBreakSession) {
                    pomodoroTotalMillis = value * 60 * 1000L
                    pomodoroRemainingMillis = pomodoroTotalMillis
                }
            } else {
                selectedBreakMinutes = value
                prefs.putInt(ReminderRepository.KEY_DEFAULT_BREAK_MINS, value)
                if (!isPomodoroRunning && isBreakSession) {
                    pomodoroTotalMillis = value * 60 * 1000L
                    pomodoroRemainingMillis = pomodoroTotalMillis
                }
            }
            prefs.apply()
            inputDialog.dismiss()
            parentDialog.dismiss()
            renderCurrentTab()
            showAccordionSettingsDialog()
        })
        inputDialog.setContentView(panel)
        inputDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        inputDialog.show()
        inputDialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.84).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun createAccordionSection(title: String, buildContent: (LinearLayout) -> Unit): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createPillDrawable(bgElevatedColor(), dp(14), null)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }

        var isExpanded = false
        val contentHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(14), 0, dp(14), dp(12))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))

            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(textPrimaryColor())
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            val tvArrow = TextView(this@MainActivity).apply {
                text = "▸"
                textSize = 15f
                setTextColor(accentOrangeColor())
            }
            addView(tvArrow)

            setOnClickListener {
                isExpanded = !isExpanded
                tvArrow.text = if (isExpanded) "▾" else "▸"
                contentHolder.visibility = if (isExpanded) View.VISIBLE else View.GONE
            }
        }

        buildContent(contentHolder)
        root.addView(headerRow)
        root.addView(contentHolder)
        return root
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "Custom Audio"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) name = cursor.getString(idx)
            }
        }
        return name
    }

    private fun setupCardSwipeNavigation(card: View, onSwipe: (direction: Int) -> Unit) {
        var startX = 0f
        var startY = 0f

        card.setOnTouchListener { touchedView, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - startX
                    val dy = event.y - startY
                    if (abs(dy) > dp(50) || abs(dx) > dp(50)) {
                        if (abs(dy) > abs(dx)) {
                            onSwipe(if (dy < 0) 1 else -1)
                        } else {
                            onSwipe(if (dx < 0) 1 else -1)
                        }
                    } else {
                        // The touch listener consumes the gesture for swipe detection,
                        // so explicitly dispatch a normal click for taps.
                        touchedView.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun buildEmptyCardPlaceholder(title: String, subtitle: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = createPillDrawable(bgCardColor(), dp(20), "#2635B6AD")
            setPadding(dp(20), dp(36), dp(20), dp(36))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(24)
            }
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(textPrimaryColor())
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                setTextColor(textMutedColor())
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
        }
    }

    private fun createGlassCardDrawable(isForeground: Boolean, warm: Boolean): android.graphics.drawable.LayerDrawable {
        val radius = dp(24).toFloat()
        val accent = if (warm) Color.parseColor("#FFFFC58E")
            else if (isForeground) Color.parseColor("#FFBDEEFF")
            else Color.parseColor("#88A8DFFF")
        val shadow = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Color.argb(if (isDarkMode) 70 else 24, 0, 0, 0))
            setStroke(dp(2), Color.argb(if (isForeground) 105 else 55, 160, 220, 255))
        }
        val glass = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (isDarkMode) intArrayOf(
                Color.argb(190, 112, 166, 205),
                Color.argb(142, 45, 83, 126),
                Color.argb(176, 14, 31, 57)
            ) else intArrayOf(
                Color.argb(218, 255, 255, 255),
                Color.argb(178, 220, 239, 255),
                Color.argb(198, 188, 218, 244)
            )
        ).apply {
            cornerRadius = radius
            setStroke(dp(if (isForeground) 1.6f.toInt() else 1), accent)
        }
        val topReflection = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(105, 255, 255, 255), Color.argb(28, 235, 248, 255), Color.TRANSPARENT)
        ).apply {
            cornerRadius = radius
            setStroke(dp(1), Color.argb(80, 255, 255, 255))
        }
        val bottomTint = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(Color.argb(42, 90, 185, 255), Color.TRANSPARENT)
        ).apply { cornerRadius = radius }
        return android.graphics.drawable.LayerDrawable(arrayOf(shadow, glass, bottomTint, topReflection)).apply {
            setLayerInset(1, dp(2), dp(2), dp(2), dp(2))
            setLayerInset(2, dp(4), dp(4), dp(4), dp(4))
            setLayerInset(3, dp(3), dp(3), dp(3), dp(3))
        }
    }

    private fun create3DButtonDrawable(topColor: Int, bottomColor: Int, radiusPx: Int, edgeColor: Int): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(topColor, bottomColor)).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setStroke(dp(1), edgeColor)
        }
    }

    private fun createOrange3DDrawable(radiusPx: Int) = create3DButtonDrawable(
        Color.parseColor("#FFFFA65E"), Color.parseColor("#FFE8641C"), radiusPx, Color.parseColor("#FFFFC48F")
    )

    private fun createGlass3DDrawable(radiusPx: Int): android.graphics.drawable.LayerDrawable {
        val body = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (isDarkMode) intArrayOf(Color.argb(205, 104, 160, 202), Color.argb(165, 28, 62, 101), Color.argb(188, 10, 25, 48))
            else intArrayOf(Color.argb(230, 255, 255, 255), Color.argb(205, 220, 239, 255))
        ).apply {
            cornerRadius = radiusPx.toFloat()
            setStroke(dp(1), Color.argb(210, 180, 230, 255))
        }
        val shine = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(100, 255, 255, 255), Color.argb(18, 255, 255, 255), Color.TRANSPARENT)
        ).apply { cornerRadius = radiusPx.toFloat() }
        return android.graphics.drawable.LayerDrawable(arrayOf(body, shine)).apply {
            setLayerInset(1, dp(2), dp(2), dp(2), dp(2))
        }
    }

    private fun railEdge(): Int = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).getInt("rail_edge", 0)
    private fun railOffset(): Float = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).getFloat("rail_offset", 0.45f)

    private fun createRailDrawable(edge: Int = railEdge()): GradientDrawable =
        GradientDrawable(
            if (edge >= 2) GradientDrawable.Orientation.LEFT_RIGHT else GradientDrawable.Orientation.TOP_BOTTOM,
            if (isDarkMode) intArrayOf(Color.argb(224, 52, 91, 132), Color.argb(205, 16, 37, 67), Color.argb(220, 8, 20, 40))
            else intArrayOf(Color.argb(224, 245, 251, 255), Color.argb(210, 210, 231, 249))
        ).apply {
            val r = dp(24).toFloat()
            cornerRadii = when (edge) {
                0 -> floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
                1 -> floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
                2 -> floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
                else -> floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setStroke(dp(1), Color.argb(210, 184, 224, 255))
        }

    private fun positionRail(dock: View) {
        val p = dock.parent as? View ?: return
        val pw = p.width
        val ph = p.height
        if (pw == 0 || ph == 0) return
        val topReserve = dp(8)
        val botReserve = dp(104)
        val off = railOffset()
        when (railEdge()) {
            0, 1 -> {
                dock.x = if (railEdge() == 0) 0f else (pw - dock.width).toFloat()
                dock.y = topReserve + off * (ph - topReserve - botReserve - dock.height).coerceAtLeast(0)
            }
            2 -> { dock.y = 0f; dock.x = off * (pw - dock.width).coerceAtLeast(0) }
            else -> { dock.y = (ph - botReserve - dock.height).toFloat().coerceAtLeast(0f); dock.x = off * (pw - dock.width).coerceAtLeast(0) }
        }
        dock.alpha = 1f
    }

    private fun snapRail(dock: View, parent: View) {
        val pw = parent.width.toFloat()
        val ph = parent.height.toFloat()
        val cx = dock.x + dock.width / 2f
        val cy = dock.y + dock.height / 2f
        val dists = floatArrayOf(cx, pw - cx, cy, ph - cy)
        var edge = 0
        for (i in 1..3) if (dists[i] < dists[edge]) edge = i
        val topReserve = dp(8).toFloat()
        val botReserve = dp(104).toFloat()
        val off = if (edge <= 1) {
            ((cy - dock.height / 2f - topReserve) / (ph - topReserve - botReserve - dock.height).coerceAtLeast(1f)).coerceIn(0f, 1f)
        } else {
            ((cx - dock.width / 2f) / (pw - dock.width).coerceAtLeast(1f)).coerceIn(0f, 1f)
        }
        val safeOff = if (off.isNaN()) 0.45f else off
        getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit().putInt("rail_edge", edge).putFloat("rail_offset", safeOff).apply()
        renderCurrentTab()
    }

    private fun attachRailDrag(target: View, dock: View, isOpen: Boolean, setOpen: (Boolean) -> Unit) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        var moved = false
        val longPress = Runnable {
            dragging = true
            triggerGentleHaptic()
            dock.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start()
        }
        target.setOnTouchListener { _, e ->
          try {
            val parent = dock.parent as? View
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = dock.x; startY = dock.y
                    moved = false
                    dragging = target !== dock
                    if (!dragging) dock.postDelayed(longPress, 350)
                    dock.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) {
                        moved = true
                        if (!dragging) {
                            dock.removeCallbacks(longPress)
                            val inward = when (railEdge()) { 0 -> dx > dp(18); 1 -> -dx > dp(18); 2 -> dy > dp(18); else -> -dy > dp(18) }
                            if (!isOpen && inward) { setOpen(true); return@setOnTouchListener true }
                        }
                    }
                    if (dragging && parent != null) {
                        val maxX = maxOf(0f, (parent.width - dock.width).toFloat())
                        val maxY = maxOf(0f, (parent.height - dock.height).toFloat())
                        dock.x = minOf(maxX, maxOf(0f, startX + dx))
                        dock.y = minOf(maxY, maxOf(0f, startY + dy))
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dock.removeCallbacks(longPress)
                    dock.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    val wasDragging = dragging
                    dragging = false
                    if (wasDragging && moved && parent != null) {
                        dock.post { try { snapRail(dock, parent) } catch (_: Exception) { renderCurrentTab() } }
                    } else if (!moved && !isOpen && e.actionMasked == MotionEvent.ACTION_UP) {
                        dock.post { setOpen(true) }
                    }
                    true
                }
                else -> false
            }
          } catch (_: Exception) {
            false
          }
        }
    }

    private fun finishRail(dock: LinearLayout, isOpen: Boolean, setOpen: (Boolean) -> Unit) {
        val edge = railEdge()
        val horizontal = edge >= 2
        dock.orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        dock.gravity = Gravity.CENTER
        dock.background = createRailDrawable(edge)
        dock.setPadding(
            if (horizontal) dp(14) else dp(8), if (horizontal) dp(8) else dp(14),
            if (horizontal) dp(14) else dp(8), if (horizontal) dp(8) else dp(14)
        )
        val inward = when (edge) { 0 -> "▸"; 1 -> "◂"; 2 -> "▾"; else -> "▴" }
        val outward = when (edge) { 0 -> "◂"; 1 -> "▸"; 2 -> "▴"; else -> "▾" }
        for (i in 0 until dock.childCount) {
            val c = dock.getChildAt(i)
            if (c is TextView) {
                val txt = c.text.toString()
                if (txt == "▸") c.text = inward
                else if (txt == "◂") { c.text = outward; if (horizontal) c.setPadding(dp(10), 0, 0, 0) }
            }
            val lp = c.layoutParams
            if (horizontal && lp is LinearLayout.LayoutParams && lp.bottomMargin > 0) {
                lp.marginEnd = lp.bottomMargin
                lp.bottomMargin = 0
                c.layoutParams = lp
            }
        }
        var grip: View? = null
        if (isOpen) {
            grip = TextView(this).apply {
                text = "≡"
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#FFFFC48F"))
                layoutParams = if (horizontal) LinearLayout.LayoutParams(dp(30), dp(42)).apply { marginEnd = dp(8) }
                else LinearLayout.LayoutParams(dp(42), dp(30)).apply { bottomMargin = dp(6) }
            }
            dock.addView(grip, 0)
        }
        val thick = if (isOpen) dp(60) else dp(30)
        dock.layoutParams = (if (horizontal) FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, thick)
        else FrameLayout.LayoutParams(thick, ViewGroup.LayoutParams.WRAP_CONTENT)).apply { gravity = Gravity.TOP or Gravity.LEFT }
        if (!isOpen) { if (horizontal) dock.minimumWidth = dp(96) else dock.minimumHeight = dp(96) }
        dock.elevation = dp(16).toFloat()
        dock.alpha = 0f
        attachRailDrag(grip ?: dock, dock, isOpen, setOpen)
        dock.post { positionRail(dock) }
    }

    private fun railTint(icon: String): Int = Color.parseColor(
        when (icon) {
            "➕" -> "#FFFF8A3D"; "⚡" -> "#FFFFC93C"; "🔍" -> "#FF5AC8FA"; "📊" -> "#FFB085F5"
            "🏆" -> "#FFFFD166"; "🎧" -> "#FF4FD6C9"; "⛶" -> "#FF42C98A"; "🪟" -> "#FFFF7AA2"
            "🏷" -> "#FF7BD88F"; "🔔" -> "#FFFFB347"; "🔇" -> "#FFF16D68"; else -> "#FF4FD6C9"
        }
    )

    private fun attachDockSwipe(v: View, isOpen: Boolean, onChange: (Boolean) -> Unit) {
        var downX = 0f
        var swiped = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; swiped = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    if (!swiped && ((!isOpen && dx > dp(24)) || (isOpen && dx < -dp(24)))) {
                        swiped = true
                        onChange(!isOpen)
                    }
                }
            }
            swiped
        }
    }

    private fun createPillDrawable(fillColor: Int, radiusPx: Int, strokeColorHex: String?): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(fillColor)
            if (strokeColorHex != null) {
                setStroke(dp(1), Color.parseColor(strokeColorHex))
            }
        }
    }

    private fun triggerGentleHaptic() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun requestOverlayPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun setPomodoroDuration(minutes: Int) {
        inAppCountDownTimer?.cancel()
        AlarmScheduler.cancelPomodoroEnd(this)
        isPomodoroRunning = false
        ReminderRepository.clearActiveFocusState(this)
        isBreakSession = false
        selectedPomodoroMinutes = minutes
        pomodoroTotalMillis = minutes * 60 * 1000L
        pomodoroRemainingMillis = pomodoroTotalMillis
        activeFocusSessionId = UUID.randomUUID().toString()
        renderCurrentTab()
    }

    private fun toggleInAppPomodoro() {
        if (isPomodoroRunning) {
            inAppCountDownTimer?.cancel()
            AlarmScheduler.cancelPomodoroEnd(this)
            isPomodoroRunning = false
            ReminderRepository.saveActiveFocusState(
                this,
                startEpochMs = 0L,
                totalDurationMs = pomodoroTotalMillis,
                remainingOnPauseMs = pomodoroRemainingMillis,
                isRunning = false,
                linkedTaskId = selectedFocusTaskId,
                isBreakMode = isBreakSession
            )
        } else {
            startWallClockCountDown(pomodoroRemainingMillis)
        }
        renderCurrentTab()
    }

    private fun startWallClockCountDown(durationMs: Long) {
        inAppCountDownTimer?.cancel()
        isPomodoroRunning = true
        val alreadyElapsed = pomodoroTotalMillis - durationMs
        pomodoroStartWallClockMs = System.currentTimeMillis() - alreadyElapsed

        ReminderRepository.saveActiveFocusState(
            this,
            startEpochMs = pomodoroStartWallClockMs,
            totalDurationMs = pomodoroTotalMillis,
            remainingOnPauseMs = durationMs,
            isRunning = true,
            linkedTaskId = selectedFocusTaskId,
            isBreakMode = isBreakSession
        )
        AlarmScheduler.schedulePomodoroEnd(this, pomodoroStartWallClockMs + durationMs, isBreakSession, activeFocusSessionId)

        inAppCountDownTimer = object : CountDownTimer(durationMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val trueElapsed = System.currentTimeMillis() - pomodoroStartWallClockMs
                pomodoroRemainingMillis = (pomodoroTotalMillis - trueElapsed).coerceAtLeast(0L)
                circularTimerView?.updateTimerDisplay(pomodoroRemainingMillis, pomodoroTotalMillis)
                if (isFlipClockModeActive) {
                    renderCurrentTab()
                }
            }

            override fun onFinish() {
                AlarmScheduler.cancelPomodoroEnd(this@MainActivity)
                isPomodoroRunning = false
                ReminderRepository.clearActiveFocusState(this@MainActivity)
                triggerGentleHaptic()
                soundEngine.stopTick()
                soundEngine.playChime()

                if (!isBreakSession) {
                    ReminderRepository.recordCompletedFocusSession(
                        this@MainActivity,
                        sessionId = activeFocusSessionId,
                        taskId = selectedFocusTaskId,
                        actualMinutes = selectedPomodoroMinutes
                    )
                    activeFocusSessionId = UUID.randomUUID().toString()
                    focusCycleCount++
                    // Every 4th focus session earns a longer break.
                    val breakMins = if (focusCycleCount % 4 == 0) maxOf(selectedBreakMinutes * 3, 15) else selectedBreakMinutes
                    isBreakSession = true
                    pomodoroTotalMillis = breakMins * 60 * 1000L
                    Toast.makeText(this@MainActivity, if (isArabic) "🎉 أحسنت! خد استراحة $breakMins دقيقة" else "🎉 Great job! Take a $breakMins min break", Toast.LENGTH_LONG).show()
                } else {
                    isBreakSession = false
                    pomodoroTotalMillis = selectedPomodoroMinutes * 60 * 1000L
                    Toast.makeText(this@MainActivity, if (isArabic) "☕ خلصت الاستراحة، يلا نركّز" else "☕ Break over, back to focus", Toast.LENGTH_LONG).show()
                }
                pomodoroRemainingMillis = pomodoroTotalMillis
                renderCurrentTab()
            }
        }.start()
    }

    private fun launchFloatingFocusWindow(linkedTask: ReminderItem?) {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        val title = linkedTask?.title ?: (if (isArabic) "جلسة تركيز" else "Focus Session")
        val serviceIntent = Intent(this, FloatingCardService::class.java).apply {
            putExtra(FloatingCardService.EXTRA_ITEM_ID, linkedTask?.id ?: "pomodoro_session")
            putExtra(FloatingCardService.EXTRA_TITLE, title)
            putExtra(FloatingCardService.EXTRA_IS_HABIT, false)
            putExtra(FloatingCardService.EXTRA_CUSTOM_MINUTES, selectedPomodoroMinutes)
            putExtra(FloatingCardService.EXTRA_IS_ARABIC, isArabic)
            putExtra(FloatingCardService.EXTRA_AUTO_START_FOCUS, true)
            putExtra(FloatingCardService.EXTRA_SESSION_ID, activeFocusSessionId)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun startRichAmbientSound(modeIndex: Int) {
        stopAmbientSound()
        stopCustomAudio()
        if (modeIndex == -1) {
            playCustomAudio()
            return
        }
        if (modeIndex <= 0) return
        isAmbientPlaying = true
        // The bundled recordings are the actual seamless-loop sound pack supplied for WAQT.
        // Keep the index order identical to the labels in showWhiteNoiseConsoleModal().
        val mappedRes = when (modeIndex) {
            1 -> R.raw.rain
            2 -> R.raw.waves
            3 -> R.raw.fire
            4 -> R.raw.forest
            5 -> R.raw.water_table
            6 -> R.raw.library
            7 -> R.raw.air
            8 -> R.raw.night
            else -> R.raw.rain
        }
        customAudioUri = Uri.parse("android.resource://$packageName/$mappedRes")
        playCustomAudio()
    }

    private fun playCustomAudio() {
        val uri = customAudioUri ?: return
        try {
            stopCustomAudio()
            customAudioPlayer = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                isLooping = true
                setVolume(ambientVolume, ambientVolume)
                setOnPreparedListener { player -> player.start() }
                setOnErrorListener { player, _, _ ->
                    try { player.reset(); player.release() } catch (_: Exception) { }
                    customAudioPlayer = null
                    Toast.makeText(this@MainActivity, if (isArabic) "تعذر تشغيل الملف الصوتي" else "Unable to play this audio file", Toast.LENGTH_SHORT).show()
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            stopCustomAudio()
            Toast.makeText(this, if (isArabic) "تعذر فتح الملف الصوتي" else "Could not open audio file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopCustomAudio() {
        try { customAudioPlayer?.stop() } catch (_: Exception) { }
        try { customAudioPlayer?.release() } catch (_: Exception) { }
        customAudioPlayer = null
    }

    private fun stopAmbientSound() {
        isAmbientPlaying = false
        soundEngine.stopAmbient()
        stopCustomAudio()
    }

    private fun syncTickTock() {
        if (isTickTockSoundActive && isPomodoroRunning && !isBreakSession) {
            soundEngine.startTick(ambientVolume.coerceAtLeast(0.4f))
        } else {
            soundEngine.stopTick()
        }
    }

    private fun createQuickPresetTask(minutes: Int) {
        val now = Calendar.getInstance().apply { add(Calendar.MINUTE, minutes) }
        val title = if (isArabic) "تذكير سريع ($minutes دقيقة)" else "Quick Reminder ($minutes mins)"
        val item = ReminderItem(
            id = UUID.randomUUID().toString(),
            title = title,
            isHabit = false,
            hour = now.get(Calendar.HOUR_OF_DAY),
            minute = now.get(Calendar.MINUTE),
            priority = "high",
            dueDateEpochDay = ReminderRepository.currentEpochDay()
        )
        ReminderRepository.addOrUpdateItem(this, item)
        AlarmScheduler.scheduleExactDelayMillis(this, item, minutes * 60 * 1000L)
        Toast.makeText(this, "⏰ $title", Toast.LENGTH_SHORT).show()
        renderCurrentTab()
    }

    private fun showCustomMinutesDialog() {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createPillDrawable(bgCardColor(), dp(20), COLOR_ORANGE)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "45"
            setHintTextColor(textMutedColor())
            setTextColor(textPrimaryColor())
        }
        v.addView(et)
        val btn = Button(this).apply {
            text = "OK"
            setOnClickListener {
                val m = et.text.toString().toIntOrNull()?.coerceIn(1, 240) ?: 25
                setPomodoroDuration(m)
                d.dismiss()
            }
        }
        v.addView(btn)
        d.setContentView(v)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.show()
        styleGlassDialog(d)
    }

    // ------------------------------------------------------------------
    // Focus dashboard: daily goal, streak, quick ambient sounds, weekly chart
    // ------------------------------------------------------------------
    private val weekDayNamesAr = listOf("أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")
    private val weekDayNamesEn = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    private fun focusStreakDays(history: List<FocusSessionRecord>): Int {
        val days = history.filter { it.durationMinutes > 0 }.map { it.epochDay }.toSet()
        var d = ReminderRepository.currentEpochDay()
        if (d !in days) d = shiftEpoch(d, -1)
        var count = 0
        while (d in days) { count++; d = shiftEpoch(d, -1) }
        return count
    }

    private fun buildFocusDashboard(): View {
        val prefs = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        val goal = prefs.getInt(ReminderRepository.KEY_DAILY_FOCUS_GOAL_MINS, 120).coerceAtLeast(1)
        val todayMins = ReminderRepository.getTodayFocusMinutes(this)
        val history = ReminderRepository.getFocusHistory(this)
        val today = ReminderRepository.currentEpochDay()
        val sessionsToday = history.count { it.epochDay == today && it.durationMinutes > 0 }
        val streak = focusStreakDays(history)
        val pct = ((todayMins * 100f) / goal).toInt().coerceIn(0, 100)

        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        fun statCol(value: String, label: String): View = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(textPrimaryColor())
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(textMutedColor())
            })
        }

        wrap.addView(buildRankPill("focus"))

        // ---- stats + goal card (tap the card to change the daily goal)
        val statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlass3DDrawable(dp(22))
            elevation = dp(8).toFloat()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
            setOnClickListener {
                val options = listOf(30, 60, 90, 120, 180, 240)
                val next = options.firstOrNull { it > goal } ?: options.first()
                prefs.edit().putInt(ReminderRepository.KEY_DAILY_FOCUS_GOAL_MINS, next).apply()
                triggerGentleHaptic()
                Toast.makeText(this@MainActivity, if (isArabic) "🎯 الهدف اليومي: $next دقيقة" else "🎯 Daily goal: $next min", Toast.LENGTH_SHORT).show()
                renderCurrentTab()
            }
        }
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        statsRow.addView(statCol("$sessionsToday", if (isArabic) "جلسات اليوم" else "Sessions"))
        statsRow.addView(statCol("${todayMins / 60}:${String.format(Locale.US, "%02d", todayMins % 60)}", if (isArabic) "إجمالي التركيز" else "Focus time"))
        statsRow.addView(statCol("🔥 $streak", if (isArabic) "أيام متتالية" else "Day streak"))
        statsCard.addView(statsRow)

        statsCard.addView(TextView(this).apply {
            text = if (isArabic) "الهدف اليومي: $todayMins / $goal دقيقة  ($pct%)" else "Daily goal: $todayMins / $goal min  ($pct%)"
            textSize = 12f
            setTextColor(if (pct >= 100) accentTealColor() else textMutedColor())
            setPadding(0, dp(12), 0, dp(6))
        })
        val barRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(Color.argb(150, 0, 0, 0)) }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10))
        }
        barRow.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.parseColor("#FFB8501A"), Color.parseColor("#FFFFA65E"))).apply { cornerRadius = dp(6).toFloat() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, pct.coerceAtLeast(1).toFloat())
        })
        barRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (100 - pct).coerceAtLeast(0).toFloat())
        })
        statsCard.addView(barRow)
        wrap.addView(statsCard)

        // ---- quick ambient sounds
        wrap.addView(TextView(this).apply {
            text = if (isArabic) "🎧 الصوت المحيطي" else "🎧 Ambient sound"
            textSize = 13f
            setTextColor(textMutedColor())
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), 0, dp(4), dp(6))
        })
        val chips = listOf(
            Triple(1, "🌧", if (isArabic) "مطر" else "Rain"),
            Triple(2, "🌊", if (isArabic) "شاطئ البحر" else "Sea"),
            Triple(3, "🔥", if (isArabic) "نار مشتعلة" else "Fire"),
            Triple(4, "🌲", if (isArabic) "البرية" else "Forest"),
            Triple(5, "💧", if (isArabic) "تيار" else "Stream"),
            Triple(6, "📚", if (isArabic) "مكتبة" else "Library"),
            Triple(7, "🍃", if (isArabic) "رياح" else "Wind"),
            Triple(8, "🐸", if (isArabic) "ضفادع ليلاً" else "Night")
        )
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chipRow.addView(TextView(this).apply {
            text = if (isArabic) "⏹ إيقاف الصوت" else "⏹ Stop sound"
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (isAmbientPlaying) Color.parseColor("#0B1417") else textMutedColor())
            background = if (isAmbientPlaying) create3DButtonDrawable(Color.parseColor("#FFFF8E88"), Color.parseColor("#FFD64B45"), dp(18), Color.parseColor("#FFFFB5B0")) else createGlass3DDrawable(dp(18))
            elevation = dp(5).toFloat()
            setPadding(dp(14), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply { marginEnd = dp(8); bottomMargin = dp(6) }
            setOnClickListener {
                triggerGentleHaptic()
                stopAmbientSound()
                selectedAmbientIndex = 0
                prefs.edit().putInt(ReminderRepository.KEY_DEFAULT_SOUND_INDEX, 0).apply()
                renderCurrentTab()
            }
        })
        chips.forEach { (idx, icon, name) ->
            val active = isAmbientPlaying && selectedAmbientIndex == idx
            chipRow.addView(TextView(this).apply {
                text = "$icon $name"
                textSize = 13f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (active) Color.parseColor("#0B1417") else textPrimaryColor())
                background = if (active) createOrange3DDrawable(dp(18)) else createGlass3DDrawable(dp(18))
                elevation = dp(5).toFloat()
                setPadding(dp(14), 0, dp(14), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply { marginEnd = dp(8); bottomMargin = dp(6) }
                setOnClickListener {
                    triggerGentleHaptic()
                    if (active) {
                        stopAmbientSound()
                        selectedAmbientIndex = 0
                    } else {
                        selectedAmbientIndex = idx
                        startRichAmbientSound(idx)
                    }
                    prefs.edit().putInt(ReminderRepository.KEY_DEFAULT_SOUND_INDEX, selectedAmbientIndex).apply()
                    renderCurrentTab()
                }
            })
        }
        wrap.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(chipRow)
        })
        // User-selected audio sits beside the ambient presets, while selection remains in the left dock.
        val personalAudioRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createGlass3DDrawable(dp(16))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8); bottomMargin = dp(10) }
        }
        personalAudioRow.addView(TextView(this).apply {
            text = "🎵"
            textSize = 20f
            setPadding(0, 0, dp(10), 0)
        })
        personalAudioRow.addView(TextView(this).apply {
            text = customAudioName ?: (if (isArabic) "ملف صوتي من جهازك" else "Audio from your device")
            textSize = 13f
            setTextColor(textPrimaryColor())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        personalAudioRow.addView(TextView(this).apply {
            text = if (customAudioPlayer?.isPlaying == true) "⏸" else "▶"
            textSize = 18f
            setTextColor(accentTealColor())
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                if (customAudioPlayer?.isPlaying == true) {
                    stopCustomAudio()
                    isAmbientPlaying = false
                } else if (customAudioUri != null) {
                    stopAmbientSound()
                    playCustomAudio()
                    isAmbientPlaying = true
                } else {
                    isPickingAudioFromConsole = true
                    localAudioPickerLauncher.launch(arrayOf("audio/*"))
                }
                renderCurrentTab()
            }
        })
        personalAudioRow.addView(TextView(this).apply {
            text = "＋"
            textSize = 20f
            setTextColor(textPrimaryColor())
            setPadding(dp(8), dp(4), 0, dp(4))
            setOnClickListener {
                isPickingAudioFromConsole = true
                localAudioPickerLauncher.launch(arrayOf("audio/*"))
            }
        })
        wrap.addView(personalAudioRow)

        // ---- 7-day chart
        val weekTotals = (6 downTo 0).map { back ->
            val day = shiftEpoch(today, -back)
            val mins = history.filter { it.epochDay == day }.sumOf { it.durationMinutes }
            val names = if (isArabic) weekDayNamesAr else weekDayNamesEn
            names[epochToCal(day).get(Calendar.DAY_OF_WEEK) - 1] to mins
        }
        val chartCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlass3DDrawable(dp(22))
            elevation = dp(8).toFloat()
            setPadding(dp(16), dp(14), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        }
        val weekSum = weekTotals.sumOf { it.second }
        chartCard.addView(TextView(this).apply {
            text = if (isArabic) "📊 آخر ٧ أيام: ${weekSum / 60} س ${weekSum % 60} د" else "📊 Last 7 days: ${weekSum / 60}h ${weekSum % 60}m"
            textSize = 13f
            setTextColor(textPrimaryColor())
            setTypeface(typeface, Typeface.BOLD)
        })
        chartCard.addView(WeekChartView(this, weekTotals, goal).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(130))
        })
        wrap.addView(chartCard)
        return wrap
    }

    inner class WeekChartView(context: Context, private val data: List<Pair<String, Int>>, private val goalMins: Int) : View(context) {
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = dp(11).toFloat()
        }
        private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
            color = Color.argb(120, 79, 214, 201)
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(5).toFloat(), dp(4).toFloat()), 0f)
        }
        private val rect = RectF()

        init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (data.isEmpty()) return
            val w = width.toFloat()
            val h = height.toFloat()
            val labelH = dp(18).toFloat()
            val topPad = dp(16).toFloat()
            val chartH = h - labelH - topPad
            val maxVal = maxOf(goalMins, data.maxOf { it.second }, 1).toFloat()
            val slot = w / data.size
            val barW = slot * 0.5f

            val goalY = topPad + chartH * (1f - goalMins / maxVal)
            canvas.drawLine(0f, goalY, w, goalY, goalPaint)

            data.forEachIndexed { i, (name, mins) ->
                val isToday = i == data.lastIndex
                val cx = slot * i + slot / 2f
                val barH = (chartH * (mins / maxVal)).coerceAtLeast(dp(4).toFloat())
                val top = topPad + chartH - barH
                rect.set(cx - barW / 2f, top, cx + barW / 2f, topPad + chartH)
                val c1 = if (isToday) Color.parseColor("#FFFFA65E") else Color.parseColor("#FF4FD6C9")
                val c2 = if (isToday) Color.parseColor("#FFB8501A") else Color.parseColor("#FF146E69")
                barPaint.shader = android.graphics.LinearGradient(0f, top, 0f, topPad + chartH, c1, c2, Shader.TileMode.CLAMP)
                if (mins > 0) barPaint.setShadowLayer(dp(8).toFloat(), 0f, 0f, Color.argb(120, Color.red(c1), Color.green(c1), Color.blue(c1)))
                else barPaint.clearShadowLayer()
                barPaint.alpha = if (mins > 0) 255 else 70
                canvas.drawRoundRect(rect, dp(7).toFloat(), dp(7).toFloat(), barPaint)
                barPaint.clearShadowLayer()

                labelPaint.color = if (isToday) Color.parseColor("#FFFFA65E") else Color.parseColor("#FF9DB5B2")
                canvas.drawText(name, cx, h - dp(3).toFloat(), labelPaint)
                if (mins > 0) {
                    labelPaint.color = Color.WHITE
                    canvas.drawText("$mins", cx, top - dp(4).toFloat(), labelPaint)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Date helpers (the app stores days as year*366 + dayOfYear)
    // ------------------------------------------------------------------
    private fun epochToCal(e: Long): Calendar {
        val y = ((e - 1) / 366).toInt()
        val doy = (e - y * 366L).toInt().coerceIn(1, 366)
        return Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, y)
            set(Calendar.DAY_OF_YEAR, doy)
        }
    }

    private fun calToEpoch(c: Calendar): Long = c.get(Calendar.YEAR) * 366L + c.get(Calendar.DAY_OF_YEAR)

    private fun shiftEpoch(e: Long, days: Int): Long {
        val c = epochToCal(e)
        c.add(Calendar.DAY_OF_YEAR, days)
        return calToEpoch(c)
    }

    private fun epochLabel(e: Long): String {
        val c = epochToCal(e)
        return "${c.get(Calendar.DAY_OF_MONTH)}/${c.get(Calendar.MONTH) + 1}"
    }

    // ------------------------------------------------------------------
    // Ranks (titles that grow with your achievements)
    // ------------------------------------------------------------------
    private data class RankTier(val emoji: String, val ar: String, val en: String, val min: Int)

    private val taskRanks = listOf(
        RankTier("🌱", "مبتدئ", "Rookie", 0),
        RankTier("📋", "منظّم", "Organizer", 5),
        RankTier("⚡", "نشيط", "Go-getter", 15),
        RankTier("🎯", "محترف", "Pro", 40),
        RankTier("🦁", "وحش المهام", "Task Beast", 80),
        RankTier("👑", "أسطورة الإنجاز", "Legend", 150)
    )
    private val focusRanks = listOf(
        RankTier("🌱", "مبتدئ تركيز", "Focus Rookie", 0),
        RankTier("🎧", "متركّز", "Focused", 60),
        RankTier("🤿", "غوّاص تركيز", "Deep Diver", 300),
        RankTier("🦅", "صقر التركيز", "Focus Hawk", 900),
        RankTier("🧠", "عقل فولاذي", "Iron Mind", 2400),
        RankTier("⏳", "سيّد الوقت", "Time Master", 6000)
    )
    private val habitRanks = listOf(
        RankTier("🌱", "بادئ", "Starter", 0),
        RankTier("🔥", "مثابر", "Persistent", 3),
        RankTier("💪", "ملتزم", "Committed", 7),
        RankTier("🧱", "عادة راسخة", "Rock Solid", 21),
        RankTier("🛡", "صلب", "Unbreakable", 50),
        RankTier("👑", "أسطورة الاستمرار", "Streak Legend", 100)
    )

    private fun rankData(kind: String): Triple<List<RankTier>, Int, String> {
        return when (kind) {
            "tasks" -> {
                val done = ReminderRepository.getAllItems(this).count { !it.isHabit && (it.isCompletedToday || it.completionPercentage >= 100) }
                Triple(taskRanks, done, if (isArabic) "مهمة منجزة" else "tasks done")
            }
            "focus" -> {
                val mins = ReminderRepository.getFocusHistory(this).sumOf { it.durationMinutes }
                Triple(focusRanks, mins, if (isArabic) "دقيقة تركيز" else "focus min")
            }
            else -> {
                val best = ReminderRepository.getAllItems(this).filter { it.isHabit }.maxOfOrNull { maxOf(it.longestStreak, it.streakCount) } ?: 0
                Triple(habitRanks, best, if (isArabic) "يوم متتالي" else "day streak")
            }
        }
    }

    private fun buildRankPill(kind: String): View {
        val (tiers, value, unit) = rankData(kind)
        val cur = tiers.lastOrNull { value >= it.min } ?: tiers.first()
        val next = tiers.firstOrNull { it.min > value }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createGlass3DDrawable(dp(18))
            elevation = dp(5).toFloat()
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
            addView(TextView(this@MainActivity).apply {
                text = cur.emoji
                textSize = 24f
                setPadding(0, 0, dp(10), 0)
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = if (isArabic) cur.ar else cur.en
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(accentOrangeColor())
                })
                addView(TextView(this@MainActivity).apply {
                    text = if (next != null) "$value / ${next.min} $unit  →  ${next.emoji} ${if (isArabic) next.ar else next.en}" else "$value $unit  🏁"
                    textSize = 11f
                    setTextColor(textMutedColor())
                })
            })
            setOnClickListener { showRankDialog(kind) }
        }
    }

    private fun showRankDialog(kind: String) {
        val (tiers, value, unit) = rankData(kind)
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlass3DDrawable(dp(24))
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        v.addView(TextView(this).apply {
            text = (if (isArabic) "🏆 ألقابك  ($value $unit)" else "🏆 Your ranks  ($value $unit)")
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textPrimaryColor())
            setPadding(0, 0, 0, dp(10))
        })
        tiers.forEach { t ->
            val got = value >= t.min
            v.addView(TextView(this).apply {
                text = "${if (got) t.emoji else "🔒"}  ${if (isArabic) t.ar else t.en}  —  ${t.min}"
                textSize = 15f
                setTextColor(if (got) textPrimaryColor() else textMutedColor())
                if (got) setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(6), 0, dp(6))
            })
        }
        d.setContentView(v)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.88).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
        styleGlassDialog(d)
    }

    // ------------------------------------------------------------------
    // Stats dialog (day / week / month / custom range)
    // ------------------------------------------------------------------
    private fun showStatsDialog(kind: String) {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val today = ReminderRepository.currentEpochDay()
        var rFrom = today
        var rTo = today
        var mode = "day"

        val outer = ScrollView(this).apply { background = createGlass3DDrawable(dp(24)) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        outer.addView(box)
        val dynamic = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun line(text: String, big: Boolean = false, color: Int = textPrimaryColor()): View = TextView(this).apply {
            this.text = text
            textSize = if (big) 20f else 14f
            setTextColor(color)
            if (big) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(5))
        }

        fun bar(pct: Int, color: String): View {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(Color.argb(120, 0, 0, 0)) }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)).apply { topMargin = dp(6); bottomMargin = dp(8) }
            }
            row.addView(View(this).apply {
                background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(Color.parseColor(color)) }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, pct.coerceIn(1, 100).toFloat())
            })
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (100 - pct).coerceIn(0, 99).toFloat())
            })
            return row
        }

        fun refresh() {
            dynamic.removeAllViews()
            if (rFrom > rTo) { val t = rFrom; rFrom = rTo; rTo = t }

            val presets = listOf(
                "day" to (if (isArabic) "يوم" else "Day"),
                "week" to (if (isArabic) "أسبوع" else "Week"),
                "month" to (if (isArabic) "شهر" else "Month"),
                "custom" to (if (isArabic) "مخصص" else "Custom")
            )
            val chipRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
            }
            presets.forEach { (key, label) ->
                val sel = mode == key
                chipRow.addView(TextView(this).apply {
                    text = label
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(if (sel) Color.parseColor("#0B1417") else textPrimaryColor())
                    background = if (sel) createOrange3DDrawable(dp(16)) else createGlass3DDrawable(dp(16))
                    layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(6) }
                    setOnClickListener {
                        mode = key
                        when (key) {
                            "day" -> { rFrom = today; rTo = today }
                            "week" -> { rFrom = shiftEpoch(today, -6); rTo = today }
                            "month" -> { rFrom = shiftEpoch(today, -29); rTo = today }
                        }
                        refresh()
                    }
                })
            }
            dynamic.addView(chipRow)

            if (mode == "custom") {
                val pickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                fun pickBtn(label: String, isFrom: Boolean): View = TextView(this).apply {
                    text = "$label: ${epochLabel(if (isFrom) rFrom else rTo)}"
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(textPrimaryColor())
                    background = createGlass3DDrawable(dp(14))
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) }
                    setOnClickListener {
                        val c = epochToCal(if (isFrom) rFrom else rTo)
                        DatePickerDialog(this@MainActivity, { _, y, m, day ->
                            val picked = Calendar.getInstance().apply { clear(); set(y, m, day) }
                            if (isFrom) rFrom = calToEpoch(picked) else rTo = calToEpoch(picked)
                            refresh()
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    }
                }
                pickRow.addView(pickBtn(if (isArabic) "من" else "From", true))
                pickRow.addView(pickBtn(if (isArabic) "إلى" else "To", false))
                dynamic.addView(pickRow)
            } else {
                dynamic.addView(line("📅 ${epochLabel(rFrom)}  →  ${epochLabel(rTo)}", color = textMutedColor()))
            }

            when (kind) {
                "tasks" -> {
                    val items = ReminderRepository.getAllItems(this).filter { !it.isHabit && it.dueDateEpochDay in rFrom..rTo }
                    val done = items.count { it.isCompletedToday || it.completionPercentage >= 100 }
                    val late = items.count { !(it.isCompletedToday || it.completionPercentage >= 100) && it.dueDateEpochDay < today }
                    val pending = items.size - done
                    val pct = if (items.isEmpty()) 0 else done * 100 / items.size
                    dynamic.addView(line(if (isArabic) "✅ منجزة: $done" else "✅ Done: $done", true, Color.parseColor("#FF42C98A")))
                    dynamic.addView(line(if (isArabic) "⏳ غير منجزة: $pending" else "⏳ Not done: $pending", true, accentOrangeColor()))
                    dynamic.addView(line(if (isArabic) "⚠️ متأخرة: $late" else "⚠️ Late: $late"))
                    dynamic.addView(line(if (isArabic) "نسبة الإنجاز: $pct%  (من ${items.size} مهمة)" else "Completion: $pct%  (of ${items.size})"))
                    dynamic.addView(bar(pct, "#FF42C98A"))
                    val high = items.count { it.priority == "high" }
                    dynamic.addView(line(if (isArabic) "🔴 مهام عالية الأهمية: $high" else "🔴 High priority: $high", color = textMutedColor()))
                }
                "focus" -> {
                    val recs = ReminderRepository.getFocusHistory(this).filter { it.epochDay in rFrom..rTo }
                    val mins = recs.sumOf { it.durationMinutes }
                    val days = ((epochToCal(rTo).timeInMillis - epochToCal(rFrom).timeInMillis) / 86400000L + 1).toInt().coerceAtLeast(1)
                    dynamic.addView(line(if (isArabic) "🍅 جلسات: ${recs.size}" else "🍅 Sessions: ${recs.size}", true, accentOrangeColor()))
                    dynamic.addView(line(if (isArabic) "⏱ إجمالي التركيز: ${mins / 60} س ${mins % 60} د" else "⏱ Total: ${mins / 60}h ${mins % 60}m", true, accentTealColor()))
                    dynamic.addView(line(if (isArabic) "📈 متوسط اليوم: ${mins / days} دقيقة" else "📈 Daily average: ${mins / days} min"))
                    val top = recs.groupBy { it.taskTitle }.mapValues { e -> e.value.sumOf { it.durationMinutes } }.entries.sortedByDescending { it.value }.take(3)
                    top.forEach { dynamic.addView(line("• ${it.key}: ${it.value} ${if (isArabic) "د" else "min"}", color = textMutedColor())) }
                }
                else -> {
                    val habits = ReminderRepository.getAllItems(this).filter { it.isHabit }
                    var total = 0
                    val perHabit = habits.map { h ->
                        val c = h.completedDaysHistoryCsv.split(",").mapNotNull { it.trim().toLongOrNull() }.count { it in rFrom..rTo }
                        total += c
                        h to c
                    }.sortedByDescending { it.second }
                    dynamic.addView(line(if (isArabic) "✅ إجمالي الالتزام: $total" else "✅ Total check-ins: $total", true, Color.parseColor("#FF42C98A")))
                    dynamic.addView(line(if (isArabic) "🌱 عدد العادات: ${habits.size}" else "🌱 Habits: ${habits.size}"))
                    val best = habits.maxOfOrNull { maxOf(it.longestStreak, it.streakCount) } ?: 0
                    dynamic.addView(line(if (isArabic) "🔥 أطول استمرار: $best يوم" else "🔥 Longest streak: $best days"))
                    perHabit.take(6).forEach { (h, c) ->
                        dynamic.addView(line("• ${h.title}: $c ${if (isArabic) "يوم" else "days"}  (🔥 ${h.streakCount})", color = textMutedColor()))
                    }
                }
            }
        }

        box.addView(TextView(this).apply {
            text = when (kind) {
                "tasks" -> if (isArabic) "📊 إحصائيات المهام" else "📊 Task stats"
                "focus" -> if (isArabic) "📊 إحصائيات التركيز" else "📊 Focus stats"
                else -> if (isArabic) "📊 إحصائيات العادات" else "📊 Habit stats"
            }
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textPrimaryColor())
            setPadding(0, 0, 0, dp(10))
        })
        box.addView(dynamic)
        refresh()

        d.setContentView(outer)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
        styleGlassDialog(d)
    }

    // ------------------------------------------------------------------
    // Quick reminder with chosen duration, add-task FAB, priority filter
    // ------------------------------------------------------------------
    private fun showQuickReminderDialog() {
        val values = listOf(5, 10, 15, 30, 45, 60, 90, 120)
        val labels = values.map { if (isArabic) "⏰ بعد $it دقيقة" else "⏰ In $it min" } +
            listOf(if (isArabic) "✏️ مدة مخصصة..." else "✏️ Custom...")
        AlertDialog.Builder(this)
            .setTitle(if (isArabic) "تذكير سريع بعد قد إيه؟" else "Remind me in...")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < values.size) {
                    createQuickPresetTask(values[which])
                } else {
                    val et = EditText(this).apply {
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        hint = if (isArabic) "عدد الدقائق" else "Minutes"
                        setPadding(dp(24), dp(16), dp(24), dp(16))
                    }
                    AlertDialog.Builder(this)
                        .setTitle(if (isArabic) "مدة التذكير بالدقائق" else "Minutes")
                        .setView(et)
                        .setPositiveButton(if (isArabic) "ابدأ" else "Start") { _, _ ->
                            val m = et.text.toString().toIntOrNull()
                            if (m != null && m in 1..1440) createQuickPresetTask(m)
                            else Toast.makeText(this, if (isArabic) "اكتب رقم من 1 لـ 1440" else "Enter 1-1440", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton(if (isArabic) "إلغاء" else "Cancel", null)
                        .show()
                }
            }
            .show()
    }

    private fun buildAddFab(onTap: () -> Unit): View = TextView(this).apply {
        text = "＋"
        textSize = 30f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.parseColor("#0B1417"))
        background = createOrange3DDrawable(dp(30))
        elevation = dp(14).toFloat()
        layoutParams = FrameLayout.LayoutParams(dp(60), dp(60)).apply {
            gravity = Gravity.BOTTOM or Gravity.RIGHT
            rightMargin = dp(20)
            bottomMargin = dp(112)
        }
        setOnClickListener {
            triggerGentleHaptic()
            onTap()
        }
    }

    private fun buildPriorityFilterRow(): View {
        val opts = listOf(
            "all" to (if (isArabic) "كل الأهمية" else "Any"),
            "high" to (if (isArabic) "🔴 عالية" else "🔴 High"),
            "medium" to (if (isArabic) "🟡 متوسطة" else "🟡 Mid"),
            "low" to (if (isArabic) "🟢 منخفضة" else "🟢 Low")
        )
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
        }
        opts.forEach { (key, label) ->
            val sel = taskPriorityFilter == key
            row.addView(TextView(this).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (sel) Color.parseColor("#0B1417") else textPrimaryColor())
                background = if (sel) createOrange3DDrawable(dp(15)) else createGlass3DDrawable(dp(15))
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(5) }
                setOnClickListener {
                    taskPriorityFilter = key
                    currentTaskCarouselIndex = 0
                    renderCurrentTab()
                }
            })
        }
        return row
    }

    // ------------------------------------------------------------------
    // User-defined note categories
    // ------------------------------------------------------------------
    private fun getNoteCategories(): MutableList<String> {
        val raw = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).getString("waqt_note_categories", "[]") ?: "[]"
        val out = mutableListOf<String>()
        try {
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) out.add(arr.getString(i))
        } catch (_: Exception) {}
        return out
    }

    private fun saveNoteCategories(list: List<String>) {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(it) }
        getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE).edit().putString("waqt_note_categories", arr.toString()).apply()
    }

    private fun buildNoteCategoryRow(): View {
        val cats = getNoteCategories()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun chip(label: String, key: String): View {
            val sel = noteSelectedCategory == key
            return TextView(this).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (sel) Color.parseColor("#0B1417") else textPrimaryColor())
                background = if (sel) createOrange3DDrawable(dp(17)) else createGlass3DDrawable(dp(17))
                elevation = dp(4).toFloat()
                setPadding(dp(14), 0, dp(14), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginEnd = dp(8) }
                setOnClickListener {
                    noteSelectedCategory = key
                    renderCurrentTab()
                }
            }
        }
        row.addView(chip(if (isArabic) "الكل" else "All", "all"))
        cats.forEach { row.addView(chip(it, it)) }
        row.addView(TextView(this).apply {
            text = if (isArabic) "＋ تصنيف" else "＋ Category"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(accentTealColor())
            background = createGlass3DDrawable(dp(17))
            setPadding(dp(14), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36))
            setOnClickListener { showNoteCategoriesDialog() }
        })
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, dp(10))
            addView(row)
        }
    }

    private fun showNoteCategoriesDialog() {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createGlass3DDrawable(dp(24))
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun paint() {
            list.removeAllViews()
            val cats = getNoteCategories()
            if (cats.isEmpty()) {
                list.addView(TextView(this).apply {
                    text = if (isArabic) "لسه معندكش تصنيفات. اكتب اسم وأضف." else "No categories yet."
                    setTextColor(textMutedColor())
                    textSize = 13f
                    setPadding(0, dp(6), 0, dp(6))
                })
            }
            cats.forEach { c ->
                val r = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                }
                r.addView(TextView(this).apply {
                    text = c
                    textSize = 16f
                    setTextColor(textPrimaryColor())
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                r.addView(TextView(this).apply {
                    text = "🗑"
                    textSize = 18f
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    setOnClickListener {
                        val all = getNoteCategories()
                        all.remove(c)
                        saveNoteCategories(all)
                        if (noteSelectedCategory == c) noteSelectedCategory = "all"
                        paint()
                        renderCurrentTab()
                    }
                })
                list.addView(r)
            }
        }
        box.addView(TextView(this).apply {
            text = if (isArabic) "🏷 تصنيفات مدونتي" else "🏷 Your note categories"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textPrimaryColor())
            setPadding(0, 0, 0, dp(8))
        })
        box.addView(list)
        val et = EditText(this).apply {
            hint = if (isArabic) "اسم تصنيف جديد (مثال: 💡 أفكار فيديو)" else "New category (e.g. 💡 Ideas)"
            setHintTextColor(textMutedColor())
            setTextColor(textPrimaryColor())
            background = createPillDrawable(bgElevatedColor(), dp(12), null)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
        }
        box.addView(et)
        box.addView(TextView(this).apply {
            text = if (isArabic) "إضافة" else "Add"
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0B1417"))
            background = createOrange3DDrawable(dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(10) }
            setOnClickListener {
                val name = et.text.toString().trim()
                if (name.isNotBlank()) {
                    val all = getNoteCategories()
                    if (name !in all) all.add(name)
                    saveNoteCategories(all)
                    et.setText("")
                    paint()
                    renderCurrentTab()
                }
            }
        })
        paint()
        d.setContentView(box)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
        styleGlassDialog(d)
    }

    inner class AtmosphereBackgroundView(context: Context) : View(context) {
        private val petrolPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val orangePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val lowerPetrolPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.parseColor(if (isDarkMode) "#07111F" else "#DCEBFA"))
            val w = width.toFloat()
            val h = height.toFloat()
            petrolPaint.shader = RadialGradient(
                w * 0.88f, h * 0.18f, w * 0.72f,
                intArrayOf(Color.argb(155, 70, 168, 255), Color.argb(65, 47, 117, 205), Color.TRANSPARENT),
                floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP
            )
            orangePaint.shader = RadialGradient(
                w * 0.02f, h * 0.66f, w * 0.62f,
                intArrayOf(Color.argb(82, 156, 105, 255), Color.argb(25, 125, 85, 220), Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
            lowerPetrolPaint.shader = RadialGradient(
                w * 0.62f, h * 0.92f, w * 0.78f,
                intArrayOf(Color.argb(110, 43, 205, 220), Color.argb(30, 30, 130, 190), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, petrolPaint)
            canvas.drawRect(0f, 0f, w, h, orangePaint)
            canvas.drawRect(0f, 0f, w, h, lowerPetrolPaint)
            petrolPaint.shader = null
            orangePaint.shader = null
            lowerPetrolPaint.shader = null
        }
    }

    inner class GTARadialWheelView(context: Context) : View(context) {
        var activeSector: Int = 0
            set(value) { field = value; invalidate() }

        private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(176, 2, 8, 10)
        }
        private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = dp(4).toFloat()
            color = Color.argb(90, 53, 182, 173)
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
            color = Color.argb(160, 88, 220, 205)
        }
        private val selectedEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            color = Color.argb(235, 255, 174, 108)
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dp(12).toFloat()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dp(21).toFloat()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val outerOval = RectF()
        private val innerOval = RectF()
        private val sectorPath = Path()

        init {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        private fun buildAnnularSector(cx: Float, cy: Float, outerR: Float, innerR: Float, start: Float, sweep: Float): Path {
            val outer = RectF(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
            val inner = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
            return Path().apply {
                val startRad = Math.toRadians(start.toDouble())
                moveTo(cx + outerR * cos(startRad).toFloat(), cy + outerR * sin(startRad).toFloat())
                arcTo(outer, start, sweep, false)
                val end = start + sweep
                val endRad = Math.toRadians(end.toDouble())
                lineTo(cx + innerR * cos(endRad).toFloat(), cy + innerR * sin(endRad).toFloat())
                arcTo(inner, end, -sweep, false)
                close()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

            val cx = width / 2f
            val outerR = (width * 0.55f).coerceAtMost(dp(300).toFloat())
            val innerR = outerR - dp(84).toFloat()
            val cy = height + dp(50).toFloat()
            outerOval.set(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
            innerOval.set(cx - innerR, cy - innerR, cx + innerR, cy + innerR)

            // Deep soft shadow behind the complete raised arc.
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(92).toFloat()
                strokeCap = Paint.Cap.ROUND
                color = Color.argb(105, 0, 0, 0)
                setShadowLayer(dp(22).toFloat(), 0f, dp(8).toFloat(), Color.argb(210, 0, 0, 0))
            }
            canvas.drawArc(outerOval, 180f, 180f, false, shadow)

            // Sector order runs visually from left to right: Notes, Habits, Focus, Tasks.
            val labels = if (isArabic)
                listOf("📝" to "مدونتي", "🌱" to "العادات", "◉" to "التركيز", "🎯" to "المهام")
            else listOf("📝" to "Notes", "🌱" to "Habits", "◉" to "Focus", "🎯" to "Tasks")
            val tabForSector = listOf(3, 1, 2, 0)
            for (i in 0..3) {
                val startAngle = 180f + i * 45f + 1.1f
                val sweep = 42.8f
                val selected = tabForSector[i] == activeSector
                val path = buildAnnularSector(cx, cy, outerR, innerR, startAngle, sweep)
                fillPaint.shader = if (selected) {
                    android.graphics.LinearGradient(
                        cx, cy - outerR, cx, cy - innerR,
                        intArrayOf(Color.parseColor("#FFFFA65E"), Color.parseColor("#FFFF7A2F"), Color.parseColor("#CCDB5B22")),
                        null, Shader.TileMode.CLAMP
                    )
                } else {
                    android.graphics.LinearGradient(
                        cx, cy - outerR, cx, cy - innerR,
                        intArrayOf(Color.parseColor("#E9274549"), Color.parseColor("#F2142D31"), Color.parseColor("#EE0C2024")),
                        null, Shader.TileMode.CLAMP
                    )
                }
                if (selected) fillPaint.setShadowLayer(dp(18).toFloat(), 0f, 0f, Color.argb(190, 255, 112, 42))
                else fillPaint.clearShadowLayer()
                canvas.drawPath(path, fillPaint)
                fillPaint.shader = null
                canvas.drawPath(path, if (selected) selectedEdgePaint else edgePaint)

                val mid = Math.toRadians((180f + i * 45f + 22.5f).toDouble())
                val labelR = (outerR + innerR) / 2f
                val x = cx + labelR * cos(mid).toFloat()
                val y = cy + labelR * sin(mid).toFloat()
                iconPaint.color = if (selected) Color.parseColor("#102326") else Color.parseColor("#D9F5F0")
                labelPaint.color = if (selected) Color.parseColor("#102326") else Color.parseColor("#F0F7F4")
                iconPaint.setShadowLayer(if (selected) dp(4).toFloat() else dp(2).toFloat(), 0f, dp(1).toFloat(), Color.argb(90, 0, 0, 0))
                canvas.drawText(labels[i].first, x, y - dp(1), iconPaint)
                canvas.drawText(labels[i].second, x, y + dp(17), labelPaint)
                iconPaint.clearShadowLayer()
            }

            // Inner lip gives the arc a machined, glass-like edge.
            val lip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = dp(2).toFloat()
                color = Color.argb(105, 156, 255, 240)
            }
            canvas.drawArc(innerOval, 194f, 152f, false, lip)
            canvas.drawArc(outerOval, 194f, 152f, false, outerGlowPaint)
        }
    }

    inner class HourglassAnimatedView(context: Context) : View(context) {
        var progressFraction: Float = 1f
        private val glassStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(3).toFloat()
            color = Color.argb(210, 160, 235, 228)
        }
        private val glassFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(30, 160, 235, 228)
        }
        private val sandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val streamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#FFFFC48F")
        }
        private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#FFFFD9A8")
        }
        private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val path = Path()
        private var phase = 0f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            val capH = dp(12).toFloat()
            val top = h * 0.06f + capH
            val bottom = h * 0.94f - capH
            val mid = (top + bottom) / 2f
            val halfW = w * 0.32f
            val neck = dp(4).toFloat()
            val bulb = (bottom - top) * 0.12f

            path.reset()
            path.moveTo(cx - halfW, top)
            path.lineTo(cx + halfW, top)
            path.quadTo(cx + halfW, mid - bulb, cx + neck, mid)
            path.quadTo(cx + halfW, mid + bulb, cx + halfW, bottom)
            path.lineTo(cx - halfW, bottom)
            path.quadTo(cx - halfW, mid + bulb, cx - neck, mid)
            path.quadTo(cx - halfW, mid - bulb, cx - halfW, top)
            path.close()

            canvas.drawPath(path, glassFill)

            val p = progressFraction.coerceIn(0f, 1f)
            sandPaint.shader = android.graphics.LinearGradient(0f, top, 0f, bottom, Color.parseColor("#FFFFB26B"), Color.parseColor("#FFE8641C"), Shader.TileMode.CLAMP)

            canvas.save()
            canvas.clipPath(path)
            // sand left in the top bulb (surface sinks as time passes, with a small funnel dip)
            val topLevel = mid - (mid - top) * p
            if (p > 0.005f) {
                canvas.drawRect(cx - halfW, topLevel, cx + halfW, mid, sandPaint)
                canvas.drawCircle(cx, topLevel, dp(5).toFloat() * p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 0, 0, 0) })
            }
            // sand piled in the bottom bulb with a mound
            val pile = (bottom - mid) * (1f - p)
            if (pile > 1f) {
                val level = bottom - pile
                val peak = (pile * 0.35f).coerceAtMost(dp(26).toFloat())
                path.reset()
                path.moveTo(cx - halfW, bottom)
                path.lineTo(cx - halfW, level + peak * 0.6f)
                path.quadTo(cx - halfW * 0.35f, level + peak * 0.5f, cx, level - peak * 0.4f)
                path.quadTo(cx + halfW * 0.35f, level + peak * 0.5f, cx + halfW, level + peak * 0.6f)
                path.lineTo(cx + halfW, bottom)
                path.close()
                canvas.drawPath(path, sandPaint)
            }
            // falling stream while the timer runs
            if (isPomodoroRunning && p > 0.01f && p < 0.995f) {
                val targetY = bottom - pile - (pile * 0.35f).coerceAtMost(dp(26).toFloat()) * 0.4f
                streamPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(5).toFloat(), dp(4).toFloat()), phase)
                canvas.drawLine(cx, mid - dp(2), cx, targetY, streamPaint)
                for (i in 0 until 5) {
                    val ty = mid + ((phase * 3f + i * 37f) % (targetY - mid).coerceAtLeast(10f))
                    canvas.drawCircle(cx + ((i % 3) - 1) * (1.5f * resources.displayMetrics.density), ty, (1.4f * resources.displayMetrics.density), grainPaint)
                }
                phase += dp(3).toFloat() * 0.6f
            }
            canvas.restore()

            // rebuild outline for the glass stroke
            path.reset()
            path.moveTo(cx - halfW, top)
            path.lineTo(cx + halfW, top)
            path.quadTo(cx + halfW, mid - bulb, cx + neck, mid)
            path.quadTo(cx + halfW, mid + bulb, cx + halfW, bottom)
            path.lineTo(cx - halfW, bottom)
            path.quadTo(cx - halfW, mid + bulb, cx - neck, mid)
            path.quadTo(cx - halfW, mid - bulb, cx - halfW, top)
            path.close()
            canvas.drawPath(path, glassStroke)

            // wooden-gold caps
            capPaint.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, Color.parseColor("#FFFFA65E"), Color.parseColor("#FFB8501A"), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(cx - halfW - dp(10), top - capH, cx + halfW + dp(10), top, dp(5).toFloat(), dp(5).toFloat(), capPaint)
            canvas.drawRoundRect(cx - halfW - dp(10), bottom, cx + halfW + dp(10), bottom + capH, dp(5).toFloat(), dp(5).toFloat(), capPaint)

            // glass shine
            canvas.drawLine(cx - halfW + dp(10), top + dp(14), cx - halfW + dp(10), mid - dp(24), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); strokeCap = Paint.Cap.ROUND; color = Color.argb(90, 255, 255, 255)
            })

            if (isPomodoroRunning) postInvalidateOnAnimation()
        }
    }

    inner class CircularTimerRingView(context: Context) : View(context) {
        private var progressFraction: Float = 1f
        private var timeString: String = "25:00"

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(14).toFloat()
            color = Color.argb(150, 0, 0, 0)
            strokeCap = Paint.Cap.ROUND
        }

        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(14).toFloat()
            color = accentOrangeColor()
            strokeCap = Paint.Cap.ROUND
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textPrimaryColor()
            textSize = dp(42).toFloat()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentTealColor()
            textSize = dp(13).toFloat()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        private val ovalRect = RectF()
        private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND
            color = Color.argb(if (isDarkMode) 110 else 150, 255, 255, 255)
        }

        init {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        fun updateTimerDisplay(remainingMs: Long, totalMs: Long) {
            progressFraction = if (totalMs > 0) (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f
            val totalSec = (remainingMs / 1000L).coerceAtLeast(0L)
            val m = totalSec / 60L
            val s = totalSec % 60L
            timeString = String.format(Locale.US, "%02d:%02d", m, s)
            progressPaint.color = if (isBreakSession) Color.parseColor("#42C98A") else accentOrangeColor()
            textPaint.color = textPrimaryColor()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val pad = dp(16).toFloat()
            ovalRect.set(pad, pad, width - pad, height - pad)

            val cx0 = width / 2f
            val cy0 = height / 2f
            val outerR = (width / 2f) - dp(4)

            // The light theme uses a clean soft-glass bezel: no black software shadow.
            bezelPaint.shader = android.graphics.LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                if (isDarkMode) intArrayOf(Color.parseColor("#FF2E5A5E"), Color.parseColor("#FF0A1A1D"))
                else intArrayOf(Color.parseColor("#FFEAF4FC"), Color.parseColor("#FFB8D0E6")),
                null, Shader.TileMode.CLAMP
            )
            if (isDarkMode) bezelPaint.setShadowLayer(dp(12).toFloat(), 0f, dp(4).toFloat(), Color.argb(85, 0, 0, 0))
            else bezelPaint.clearShadowLayer()
            canvas.drawCircle(cx0, cy0, outerR, bezelPaint)
            bezelPaint.clearShadowLayer()

            // Recessed glass disc inside the ring, adapted to the active appearance.
            discPaint.shader = RadialGradient(
                cx0, cy0 - outerR * 0.25f, outerR,
                if (isDarkMode) intArrayOf(Color.parseColor("#FF16363A"), Color.parseColor("#FF081518"), Color.parseColor("#FF050E10"))
                else intArrayOf(Color.parseColor("#FFFFFFFF"), Color.parseColor("#FFF0F7FD"), Color.parseColor("#FFDCEAF6")),
                floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx0, cy0, outerR - dp(7), discPaint)

            canvas.drawArc(ovalRect, 0f, 360f, false, trackPaint)

            // Glowing orange progress with gradient (bright head, deeper tail)
            val sweepAngle = 360f * progressFraction
            val baseColor = progressPaint.color
            glowPaint.strokeWidth = progressPaint.strokeWidth
            glowPaint.color = baseColor
            glowPaint.shader = android.graphics.SweepGradient(
                cx0, cy0,
                intArrayOf(Color.parseColor("#FFB8501A"), baseColor, Color.parseColor("#FFFFC48F"), Color.parseColor("#FFB8501A")),
                floatArrayOf(0f, 0.55f, 0.9f, 1f)
            )
            if (isDarkMode) glowPaint.setShadowLayer(dp(8).toFloat(), 0f, 0f, Color.argb(115, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)))
            else glowPaint.clearShadowLayer()
            canvas.save()
            canvas.rotate(-90f, cx0, cy0)
            canvas.drawArc(ovalRect, 0f, sweepAngle, false, glowPaint)
            canvas.restore()
            glowPaint.clearShadowLayer()

            // Glass highlight on top edge
            canvas.drawArc(RectF(cx0 - outerR + dp(10), cy0 - outerR + dp(10), cx0 + outerR - dp(10), cy0 + outerR - dp(10)), 200f, 80f, false, highlightPaint)

            val centerX = width / 2f
            val centerY = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f) - dp(6)
            canvas.drawText(timeString, centerX, centerY, textPaint)

            val label = when {
                isBreakSession -> if (isArabic) "☕ استراحة" else "☕ BREAK"
                isPomodoroRunning -> if (isArabic) "🔥 جلسة جارية" else "🔥 ACTIVE"
                else -> if (isArabic) "المس لضبط الوقت" else "TAP TO SET TIME"
            }
            canvas.drawText(label, centerX, centerY + dp(28), subTextPaint)
        }
    }

    private fun checkForOnlineUpdates(manual: Boolean) {
        val updateUrl = "https://raw.githubusercontent.com/Madboly1/waqt-updates/main/update.json"
        thread(name = "waqt-ota-check") {
            var responseCode = -1
            try {
                val conn = (URL(updateUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("Pragma", "no-cache")
                }
                responseCode = conn.responseCode
                if (responseCode !in 200..299) throw IllegalStateException("HTTP $responseCode")
                val jsonText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                conn.disconnect()
                val root = org.json.JSONObject(jsonText)
                val remoteCode = root.optInt("versionCode", 0)
                val remoteName = root.optString("versionName", "")
                val remoteMsg = root.optString("updateMessage", "")
                val apkUrl = root.optString("apkDownloadUrl", "")
                val currentCode = if (Build.VERSION.SDK_INT >= 28) {
                    packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0).versionCode
                }
                val isNewer = remoteCode > currentCode && apkUrl.startsWith("https://")
                runOnUiThread {
                    if (isNewer) {
                        val title = if (isArabic) "تحديث جديد متاح 🚀" else "New update available 🚀"
                        val details = buildString {
                            append(if (isArabic) "الإصدار الحالي: " else "Current version: ")
                            append(packageManager.getPackageInfo(packageName, 0).versionName)
                            append("\n")
                            append(if (isArabic) "الإصدار الجديد: " else "New version: ")
                            append(remoteName)
                            if (remoteMsg.isNotBlank()) append("\n\n").append(remoteMsg)
                        }
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle(title)
                            .setMessage(details)
                            .setNegativeButton(if (isArabic) "لاحقًا" else "Later", null)
                            .setPositiveButton(if (isArabic) "تحميل التحديث" else "Download update") { _, _ ->
                                try {
                                    val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                    val safeVersion = remoteName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                                    val request = android.app.DownloadManager.Request(Uri.parse(apkUrl))
                                        .setTitle(if (isArabic) "تحميل تحديث وَقْت $remoteName" else "Downloading WAQT $remoteName")
                                        .setDescription(if (isArabic) "سيظهر إشعار عند اكتمال التحميل. اضغط عليه لتثبيت التحديث." else "A notification will appear when the download is complete. Tap it to install.")
                                        .setMimeType("application/vnd.android.package-archive")
                                        .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        .setAllowedOverMetered(true)
                                        .setAllowedOverRoaming(false)
                                        .setDestinationInExternalPublicDir(
                                            android.os.Environment.DIRECTORY_DOWNLOADS,
                                            "WAQT-update-$safeVersion.apk"
                                        )
                                    downloadManager.enqueue(request)
                                    Toast.makeText(
                                        this@MainActivity,
                                        if (isArabic) "بدأ تحميل التحديث… هتلاقي إشعار عند اكتماله." else "Update download started. You'll be notified when it finishes.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } catch (downloadError: Exception) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        (if (isArabic) "تعذر بدء التحميل: " else "Could not start download: ") + (downloadError.message ?: "Unknown error"),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            .show()
                    } else if (manual) {
                        val msg = if (remoteCode <= currentCode) {
                            if (isArabic) "أنت تستخدم أحدث إصدار بالفعل ✓" else "You are up to date ✓"
                        } else {
                            if (isArabic) "رابط التحديث غير جاهز حاليًا" else "Update download is not ready yet"
                        }
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                if (manual) runOnUiThread {
                    val reason = e.message ?: "Network error"
                    Toast.makeText(this@MainActivity,
                        (if (isArabic) "تعذر فحص التحديثات: " else "Update check failed: ") + reason,
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

}
