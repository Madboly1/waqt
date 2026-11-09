package com.example.glassproductivity

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.function.Consumer
import kotlin.math.abs

class FloatingCardService : Service() {

    companion object {
        const val CHANNEL_ID = "waqt_floating_overlay_channel"
        const val NOTIFICATION_ID = 4041

        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_IS_HABIT = "extra_is_habit"
        const val EXTRA_STREAK_COUNT = "extra_streak_count"
        const val EXTRA_STREAK_GOAL = "extra_streak_goal"
        const val EXTRA_IS_ARABIC = "extra_is_arabic"
        const val EXTRA_CUSTOM_MINUTES = "extra_custom_minutes"
        const val EXTRA_AUTO_START_FOCUS = "extra_auto_start_focus"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_IS_FLOATING_NOTE = "extra_is_floating_note"

        const val ACTION_DATA_UPDATED = "com.example.glassproductivity.ACTION_DATA_UPDATED"
        const val ACTION_OPEN_FLOATING_NOTE = "com.example.glassproductivity.ACTION_OPEN_FLOATING_NOTE"

        private const val DEFAULT_POMODORO_MS = 25 * 60 * 1000L
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private lateinit var cardExpandedContainer: LinearLayout
    private lateinit var bubbleMinimizedContainer: LinearLayout
    private lateinit var tvBadge: TextView
    private lateinit var tvBrandMark: TextView
    private lateinit var btnMinimize: TextView
    private lateinit var btnClose: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var habitStreakContainer: LinearLayout
    private lateinit var tvStreakCount: TextView
    private lateinit var tvStreakGoal: TextView
    private lateinit var progressHabitStreak: ProgressBar
    private lateinit var actionButtonsRow: LinearLayout
    private lateinit var btnStartPomodoro: TextView
    private lateinit var btnSnooze: TextView
    private lateinit var pomodoroActiveContainer: LinearLayout
    private lateinit var tvPomodoroStatus: TextView
    private lateinit var tvPomodoroTimer: TextView
    private lateinit var progressPomodoro: ProgressBar
    private lateinit var btnPauseResumePomodoro: TextView
    private lateinit var btnStopPomodoro: TextView
    private lateinit var tvBubbleIcon: TextView
    private lateinit var tvBubbleText: TextView

    // واجهة تدوين الملاحظة العائمة السريعة فوق التطبيقات
    private var floatingNoteContainer: LinearLayout? = null
    private var etFloatingNoteTitle: EditText? = null
    private var etFloatingNoteContent: EditText? = null

    private var currentItemId: String = ""
    private var currentTitle: String = "WAQT Session"
    private var isHabit: Boolean = false
    private var isFloatingNoteMode: Boolean = false
    private var streakCount: Int = 0
    private var streakGoal: Int = 7
    private var isArabic: Boolean = true
    private var isMinimized: Boolean = false

    private var savedExpandedX: Int = 0
    private var savedExpandedY: Int = 180

    private var countDownTimer: CountDownTimer? = null
    private var pomodoroDurationMs: Long = DEFAULT_POMODORO_MS
    private var remainingPomodoroMs: Long = DEFAULT_POMODORO_MS
    private var activeSessionId: String = UUID.randomUUID().toString()
    private var isPomodoroRunning: Boolean = false
    private var isPomodoroActiveMode: Boolean = false

    private var blurListener: Consumer<Boolean>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startOverlayForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        var autoStartFocus = false
        if (intent != null) {
            currentItemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: ""
            currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Focus Session"
            isHabit = intent.getBooleanExtra(EXTRA_IS_HABIT, false)
            streakCount = intent.getIntExtra(EXTRA_STREAK_COUNT, 1)
            streakGoal = intent.getIntExtra(EXTRA_STREAK_GOAL, 7).coerceAtLeast(1)
            isArabic = intent.getBooleanExtra(EXTRA_IS_ARABIC, true)
            autoStartFocus = intent.getBooleanExtra(EXTRA_AUTO_START_FOCUS, false)
            activeSessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: UUID.randomUUID().toString()
            isFloatingNoteMode = intent.getBooleanExtra(EXTRA_IS_FLOATING_NOTE, false) || (intent.action == ACTION_OPEN_FLOATING_NOTE)

            val customMins = intent.getIntExtra(EXTRA_CUSTOM_MINUTES, 25).coerceAtLeast(1)
            pomodoroDurationMs = customMins * 60 * 1000L
            remainingPomodoroMs = pomodoroDurationMs
        }

        if (floatingView == null) {
            inflateAndAttachFloatingCard()
        } else {
            bindCardData()
            expandFromBubbleIfNeeded()
        }

        if (autoStartFocus && !isPomodoroRunning) {
            startEmbeddedPomodoro()
        }

        triggerGentleHaptic()
        return START_REDELIVER_INTENT
    }

    private fun startOverlayForeground() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_waqt)
            .setContentTitle(if (isArabic) "وَقْت • النافذة العائمة السريعة" else "WAQT • Floating Overlay Active")
            .setContentText(if (isArabic) "ملاحظات سريعة ومؤقت تركيز فوق التطبيقات" else "Quick notes & focus timer over apps")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun inflateAndAttachFloatingCard() {
        val inflater = LayoutInflater.from(this)
        val rootView = inflater.inflate(R.layout.layout_floating_card, null)
        floatingView = rootView

        cardExpandedContainer = rootView.findViewById(R.id.cardExpandedContainer)
        bubbleMinimizedContainer = rootView.findViewById(R.id.bubbleMinimizedContainer)
        tvBadge = rootView.findViewById(R.id.tvBadge)
        tvBrandMark = rootView.findViewById(R.id.tvBrandMark)
        btnMinimize = rootView.findViewById(R.id.btnMinimize)
        btnClose = rootView.findViewById(R.id.btnClose)
        tvTitle = rootView.findViewById(R.id.tvTitle)
        tvSubtitle = rootView.findViewById(R.id.tvSubtitle)
        habitStreakContainer = rootView.findViewById(R.id.habitStreakContainer)
        tvStreakCount = rootView.findViewById(R.id.tvStreakCount)
        tvStreakGoal = rootView.findViewById(R.id.tvStreakGoal)
        progressHabitStreak = rootView.findViewById(R.id.progressHabitStreak)
        actionButtonsRow = rootView.findViewById(R.id.actionButtonsRow)
        btnStartPomodoro = rootView.findViewById(R.id.btnStartPomodoro)
        btnSnooze = rootView.findViewById(R.id.btnSnooze)
        pomodoroActiveContainer = rootView.findViewById(R.id.pomodoroActiveContainer)
        tvPomodoroStatus = rootView.findViewById(R.id.tvPomodoroStatus)
        tvPomodoroTimer = rootView.findViewById(R.id.tvPomodoroTimer)
        progressPomodoro = rootView.findViewById(R.id.progressPomodoro)
        btnPauseResumePomodoro = rootView.findViewById(R.id.btnPauseResumePomodoro)
        btnStopPomodoro = rootView.findViewById(R.id.btnStopPomodoro)
        tvBubbleIcon = rootView.findViewById(R.id.tvBubbleIcon)
        tvBubbleText = rootView.findViewById(R.id.tvBubbleText)

        // إضافة واجهة تدوين الملاحظة العائمة السريعة
        attachFloatingNoteViews(cardExpandedContainer)

        // FLAG_NOT_TOUCH_MODAL lets touches outside the little window reach the app underneath
        // (without it, the note window swallows every touch and the whole screen feels frozen).
        var windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            windowFlags = windowFlags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            windowFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val screenWidth = resources.displayMetrics.widthPixels
            val estimatedCardWidthPx = dpToPx(344)
            x = ((screenWidth - estimatedCardWidthPx) / 2).coerceAtLeast(16)
            y = dpToPx(96)
            savedExpandedX = x
            savedExpandedY = y

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurBehindRadius = 32
            }
        }

        setupGlassBlurAndFallback()
        bindCardData()
        setupClickListeners()
        setupDragAndDropTouchListener(rootView)

        windowManager.addView(rootView, layoutParams)
    }

    private fun attachFloatingNoteViews(parent: LinearLayout) {
        floatingNoteContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dpToPx(10), 0, 0)
        }

        etFloatingNoteTitle = EditText(this).apply {
            hint = if (isArabic) "عنوان الملاحظة السريعة..." else "Quick Note Title..."
            setHintTextColor(Color.parseColor("#A5B4B4"))
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1B383C"))
                cornerRadius = dpToPx(12).toFloat()
            }
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(8)
            }
        }
        floatingNoteContainer?.addView(etFloatingNoteTitle)

        etFloatingNoteContent = EditText(this).apply {
            hint = if (isArabic) "اكتب فكرتك أو الملاحظة هنا..." else "Type your note here..."
            setHintTextColor(Color.parseColor("#A5B4B4"))
            setTextColor(Color.WHITE)
            textSize = 13f
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1B383C"))
                cornerRadius = dpToPx(12).toFloat()
            }
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(10)
            }
        }
        floatingNoteContainer?.addView(etFloatingNoteContent)

        val btnSaveNote = Button(this).apply {
            text = if (isArabic) "💾 حفظ الملاحظة في وَقْت" else "💾 Save Note to WAQT"
            isAllCaps = false
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#0B1417"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF8A3D"))
            setOnClickListener {
                saveFloatingNote()
            }
        }
        floatingNoteContainer?.addView(btnSaveNote)
        parent.addView(floatingNoteContainer)
    }

    private fun saveFloatingNote() {
        val title = etFloatingNoteTitle?.text?.toString()?.trim() ?: ""
        val content = etFloatingNoteContent?.text?.toString()?.trim() ?: ""
        if (title.isBlank() && content.isBlank()) {
            Toast.makeText(this, if (isArabic) "يرجى كتابة عنوان أو محتوى للملاحظة" else "Please write a title or note", Toast.LENGTH_SHORT).show()
            return
        }
        val note = NoteItem(
            id = UUID.randomUUID().toString(),
            title = if (title.isNotBlank()) title else (if (isArabic) "ملاحظة سريعة" else "Quick Note"),
            content = content,
            category = "general",
            colorHex = "#142A2E",
            isPinned = true,
            timestampMillis = System.currentTimeMillis()
        )
        ReminderRepository.addOrUpdateNote(this, note)
        sendBroadcast(Intent(ACTION_DATA_UPDATED).setPackage(packageName))
        triggerGentleHaptic()
        Toast.makeText(this, if (isArabic) "✓ تم حفظ الملاحظة في تطبيق وَقْت بنجاح!" else "✓ Note saved to WAQT!", Toast.LENGTH_SHORT).show()
        etFloatingNoteTitle?.text?.clear()
        etFloatingNoteContent?.text?.clear()
        minimizeToBubble()
    }

    private fun setupGlassBlurAndFallback() {
        applyGlassSurfaceStyling()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurListener = Consumer<Boolean> { isBlurEnabled ->
                floatingView?.let { view ->
                    if (view.isAttachedToWindow) {
                        layoutParams.blurBehindRadius = if (isBlurEnabled) 32 else 0
                        try { windowManager.updateViewLayout(view, layoutParams) } catch (_: Exception) {}
                    }
                }
            }
            blurListener?.let { windowManager.addCrossWindowBlurEnabledListener(it) }
        }
    }

    private fun applyGlassSurfaceStyling() {
        val bgColor = Color.argb(235, 14, 34, 45) // Petroleum Teal #142A2E with glass opacity

        val calmCardDrawable = GradientDrawable().apply {
            color = ColorStateList.valueOf(bgColor)
            cornerRadius = dpToPx(20).toFloat()
            setStroke(dpToPx(1), Color.parseColor("#6635B6AD"))
        }

        val calmBubbleDrawable = GradientDrawable().apply {
            color = ColorStateList.valueOf(bgColor)
            cornerRadius = dpToPx(24).toFloat()
            setStroke(dpToPx(1), Color.parseColor("#FF8A3D"))
        }

        cardExpandedContainer.background = calmCardDrawable
        bubbleMinimizedContainer.background = calmBubbleDrawable
    }

    private fun bindCardData() {
        tvBrandMark.text = if (isArabic) "وَقْت" else "WAQT"

        if (isFloatingNoteMode) {
            tvBadge.text = if (isArabic) "📝 ملاحظة عائمة" else "📝 Floating Note"
            tvTitle.text = if (isArabic) "تدوين ملاحظة سريعة" else "Quick Note"
            tvSubtitle.text = if (isArabic) "دون فكرتك وأنت في أي تطبيق آخر" else "Take notes over any app"
            habitStreakContainer.visibility = View.GONE
            actionButtonsRow.visibility = View.GONE
            pomodoroActiveContainer.visibility = View.GONE
            floatingNoteContainer?.visibility = View.VISIBLE
            tvBubbleIcon.text = "📝"
            tvBubbleText.text = if (isArabic) "ملاحظة" else "Note"

            // تمكين إدخال النصوص للملاحظة العائمة
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            layoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            // On the first bind the view is not attached yet (addView comes after), and calling
            // updateViewLayout on a detached view throws and crashes the whole app.
            floatingView?.let {
                if (it.isAttachedToWindow) {
                    try { windowManager.updateViewLayout(it, layoutParams) } catch (_: Exception) {}
                }
            }
            return
        }

        // وضع التركيز أو التنبيه العادي
        floatingNoteContainer?.visibility = View.GONE
        tvTitle.text = currentTitle

        if (isHabit) {
            tvBadge.text = if (isArabic) "🌱 عادة يومية" else "🌱 Daily Habit"
            tvSubtitle.text = if (isArabic) "اضغط (—) للتصغير لفقاعة أو (✕) للإغلاق" else "Minimize (_) or Close (✕)"
            habitStreakContainer.visibility = View.VISIBLE
            tvStreakCount.text = if (isArabic) "🔥 سلسلة $streakCount أيام" else "🔥 $streakCount-day streak"
            tvStreakGoal.text = "$streakCount / $streakGoal " + (if (isArabic) "أيام" else "days")
            progressHabitStreak.max = streakGoal
            progressHabitStreak.progress = streakCount.coerceAtMost(streakGoal)
            tvBubbleIcon.text = if (isPomodoroActiveMode) "🍅" else "🌱"
        } else {
            tvBadge.text = if (isArabic) "🎯 جلسة ومهمة" else "🎯 Focus & Task"
            tvSubtitle.text = if (isArabic) "اضغط (—) للتصغير إلى فقاعة عائمة" else "Tap (—) to minimize to edge bubble"
            habitStreakContainer.visibility = View.GONE
            tvBubbleIcon.text = if (isPomodoroActiveMode) "🍅" else "🎯"
        }

        val mins = (pomodoroDurationMs / 60000L).toInt()
        btnSnooze.text = if (isArabic) "💤 غفوة 10 د" else "💤 10m"
        btnStartPomodoro.text = if (isArabic) "🍅 تركيز $mins د" else "🍅 Focus ${mins}m"

        if (!isPomodoroActiveMode) {
            tvBubbleText.text = if (isArabic) "وَقْت" else "WAQT"
        }
    }

    private fun setupClickListeners() {
        btnMinimize.setOnClickListener {
            minimizeToBubble()
        }

        btnClose.setOnClickListener {
            dismissOverlaySafely()
        }

        btnStartPomodoro.setOnClickListener {
            startEmbeddedPomodoro()
        }

        btnSnooze.setOnClickListener {
            snoozeForTenMinutes()
        }

        btnPauseResumePomodoro.setOnClickListener {
            if (isPomodoroRunning) {
                pausePomodoroTimer()
            } else {
                resumePomodoroTimer()
            }
        }

        btnStopPomodoro.setOnClickListener {
            stopAndRecordActualPomodoroTime()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragAndDropTouchListener(rootView: View) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        val dragListener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        floatingView?.let { v -> if (v.isAttachedToWindow) { try { windowManager.updateViewLayout(v, layoutParams) } catch (_: Exception) {} } }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        if (isMinimized) {
                            expandFromBubbleIfNeeded()
                        }
                    } else {
                        if (isMinimized) {
                            snapBubbleToNearestEdge()
                        } else {
                            savedExpandedX = layoutParams.x
                            savedExpandedY = layoutParams.y
                        }
                    }
                    true
                }

                else -> false
            }
        }

        rootView.setOnTouchListener(dragListener)
        cardExpandedContainer.setOnTouchListener(dragListener)
        bubbleMinimizedContainer.setOnTouchListener(dragListener)
    }

    private fun minimizeToBubble() {
        if (isMinimized) return
        savedExpandedX = layoutParams.x
        savedExpandedY = layoutParams.y
        isMinimized = true

        cardExpandedContainer.visibility = View.GONE
        bubbleMinimizedContainer.visibility = View.VISIBLE

        if (isFloatingNoteMode) {
            tvBubbleIcon.text = "📝"
            tvBubbleText.text = if (isArabic) "ملاحظة" else "Note"
        } else if (!isPomodoroActiveMode) {
            tvBubbleIcon.text = if (isHabit) "🌱" else "🎯"
            tvBubbleText.text = if (isArabic) "وَقْت" else "WAQT"
        }

        snapBubbleToNearestEdge()
    }

    private fun expandFromBubbleIfNeeded() {
        if (!isMinimized) return
        isMinimized = false

        bubbleMinimizedContainer.visibility = View.GONE
        cardExpandedContainer.visibility = View.VISIBLE

        layoutParams.x = savedExpandedX
        layoutParams.y = savedExpandedY
        floatingView?.let { v -> if (v.isAttachedToWindow) { try { windowManager.updateViewLayout(v, layoutParams) } catch (_: Exception) {} } }
    }

    private fun snapBubbleToNearestEdge() {
        val screenWidth = resources.displayMetrics.widthPixels
        val midpoint = screenWidth / 2
        val targetX = if (layoutParams.x + dpToPx(40) < midpoint) {
            dpToPx(8)
        } else {
            screenWidth - dpToPx(116)
        }
        layoutParams.x = targetX
        floatingView?.let { v -> if (v.isAttachedToWindow) { try { windowManager.updateViewLayout(v, layoutParams) } catch (_: Exception) {} } }
    }

    private fun startEmbeddedPomodoro() {
        isPomodoroActiveMode = true
        remainingPomodoroMs = pomodoroDurationMs
        activeSessionId = UUID.randomUUID().toString()

        actionButtonsRow.visibility = View.GONE
        pomodoroActiveContainer.visibility = View.VISIBLE
        tvBubbleIcon.text = "🍅"
        progressPomodoro.max = (pomodoroDurationMs / 1000).toInt()

        startPomodoroCountDown(remainingPomodoroMs)
    }

    private fun startPomodoroCountDown(durationMs: Long) {
        countDownTimer?.cancel()
        isPomodoroRunning = true
        val startWallClock = System.currentTimeMillis()
        ReminderRepository.saveActiveFocusState(
            this,
            startEpochMs = startWallClock,
            totalDurationMs = pomodoroDurationMs,
            remainingOnPauseMs = durationMs,
            isRunning = true,
            linkedTaskId = currentItemId.takeIf { it.isNotBlank() && it != "pomodoro_session" },
            isBreakMode = false
        )

        btnPauseResumePomodoro.text = if (isArabic) "⏸ إيقاف مؤقت" else "⏸ Pause"
        tvPomodoroStatus.text = if (isArabic) "🍅 جلسة تركيز جارية" else "🍅 Focus Active"

        countDownTimer = object : CountDownTimer(durationMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingPomodoroMs = millisUntilFinished
                val formatted = formatMillisToMmSs(millisUntilFinished)
                tvPomodoroTimer.text = formatted
                tvBubbleText.text = formatted
                progressPomodoro.progress = (millisUntilFinished / 1000L).toInt()
            }

            override fun onFinish() {
                remainingPomodoroMs = 0L
                isPomodoroRunning = false
                ReminderRepository.clearActiveFocusState(this@FloatingCardService)
                val fullMinutes = (pomodoroDurationMs / 60000L).toInt().coerceAtLeast(1)
                ReminderRepository.recordCompletedFocusSession(
                    this@FloatingCardService,
                    sessionId = activeSessionId,
                    taskId = currentItemId.takeIf { it.isNotBlank() && it != "pomodoro_session" },
                    actualMinutes = fullMinutes
                )

                tvPomodoroTimer.text = "00:00"
                tvBubbleText.text = if (isArabic) "تم!" else "Done!"
                tvPomodoroStatus.text = if (isArabic) "✅ اكتملت الجلسة (+$fullMinutes د)" else "✅ Completed (+${fullMinutes}m)"
                progressPomodoro.progress = 0
                btnPauseResumePomodoro.text = if (isArabic) "🔄 جلسة جديدة" else "🔄 Restart"

                triggerGentleHaptic()
                sendBroadcast(Intent(ACTION_DATA_UPDATED).setPackage(packageName))
                if (isMinimized) {
                    expandFromBubbleIfNeeded()
                }
            }
        }.start()
    }

    private fun pausePomodoroTimer() {
        countDownTimer?.cancel()
        isPomodoroRunning = false
        ReminderRepository.saveActiveFocusState(
            this,
            startEpochMs = 0L,
            totalDurationMs = pomodoroDurationMs,
            remainingOnPauseMs = remainingPomodoroMs,
            isRunning = false,
            linkedTaskId = currentItemId.takeIf { it.isNotBlank() && it != "pomodoro_session" },
            isBreakMode = false
        )
        btnPauseResumePomodoro.text = if (isArabic) "▶ استئناف" else "▶ Resume"
        tvPomodoroStatus.text = if (isArabic) "⏸ متوقف مؤقتاً" else "⏸ Paused"
    }

    private fun resumePomodoroTimer() {
        val nextDuration = if (remainingPomodoroMs <= 0L) {
            activeSessionId = UUID.randomUUID().toString()
            pomodoroDurationMs
        } else {
            remainingPomodoroMs
        }
        startPomodoroCountDown(nextDuration)
    }

    private fun stopAndRecordActualPomodoroTime() {
        countDownTimer?.cancel()
        val spentMs = (pomodoroDurationMs - remainingPomodoroMs).coerceAtLeast(0L)
        val spentMinutes = (spentMs / 60000L).toInt()
        if (spentMinutes >= 1 && remainingPomodoroMs > 0L) {
            ReminderRepository.recordCompletedFocusSession(
                this,
                sessionId = activeSessionId,
                taskId = currentItemId.takeIf { it.isNotBlank() && it != "pomodoro_session" },
                actualMinutes = spentMinutes
            )
            Toast.makeText(
                this,
                if (isArabic) "تم تسجيل $spentMinutes دقيقة تركيز فعلية" else "Logged $spentMinutes min actual focus",
                Toast.LENGTH_SHORT
            ).show()
        }
        ReminderRepository.clearActiveFocusState(this)
        isPomodoroRunning = false
        isPomodoroActiveMode = false
        remainingPomodoroMs = pomodoroDurationMs

        pomodoroActiveContainer.visibility = View.GONE
        actionButtonsRow.visibility = View.VISIBLE
        tvBubbleIcon.text = if (isHabit) "🌱" else "🎯"
        tvBubbleText.text = if (isArabic) "وَقْت" else "WAQT"
        sendBroadcast(Intent(ACTION_DATA_UPDATED).setPackage(packageName))
    }

    private fun formatMillisToMmSs(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun snoozeForTenMinutes() {
        countDownTimer?.cancel()
        val item = ReminderRepository.getItemById(this, currentItemId)
            ?: ReminderItem(
                id = currentItemId.ifBlank { UUID.randomUUID().toString() },
                title = currentTitle,
                isHabit = isHabit,
                hour = 9,
                minute = 0,
                streakCount = streakCount,
                streakGoal = streakGoal
            )

        AlarmScheduler.scheduleSnooze(this, item, snoozeMinutes = 10)
        Toast.makeText(
            this,
            if (isArabic) "💤 تم تأجيل «$currentTitle» لمدة 10 دقائق" else "💤 Snoozed \"$currentTitle\" for 10m",
            Toast.LENGTH_SHORT
        ).show()
        stopSelf()
    }

    private fun dismissOverlaySafely() {
        countDownTimer?.cancel()
        if (isPomodoroActiveMode && isPomodoroRunning) {
            val spentMins = ((pomodoroDurationMs - remainingPomodoroMs) / 60000L).toInt()
            if (spentMins >= 1) {
                ReminderRepository.recordCompletedFocusSession(
                    this,
                    sessionId = activeSessionId,
                    taskId = currentItemId.takeIf { it.isNotBlank() && it != "pomodoro_session" },
                    actualMinutes = spentMins
                )
            }
            ReminderRepository.clearActiveFocusState(this)
        } else if (currentItemId.isNotBlank() && currentItemId != "pomodoro_session" && !isFloatingNoteMode) {
            ReminderRepository.markCompleted(this, currentItemId)
        }
        sendBroadcast(Intent(ACTION_DATA_UPDATED).setPackage(packageName))
        stopSelf()
    }

    private fun triggerGentleHaptic() {
        try {
            val prefs = getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("pref_haptics", true)) return
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WAQT Floating Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays WAQT floating quick notes and focus timer over apps"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurListener?.let {
                try {
                    windowManager.removeCrossWindowBlurEnabledListener(it)
                } catch (_: Exception) {
                }
            }
        }
        floatingView?.let { view ->
            if (view.isAttachedToWindow) {
                windowManager.removeView(view)
            }
        }
        floatingView = null
        super.onDestroy()
    }
}