package com.example.glassproductivity

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Diagnostic entry only. Never clears the stored crash or edits business data. */
internal object StartupRecovery {
    private val active = java.util.WeakHashMap<Activity, Boolean>()

    fun isActive(activity: Activity): Boolean = active[activity] == true

    /** Keep the trace, but do not trap a repaired, readable timer behind diagnostics. */
    fun canContinueAfterHandledFocusCrash(context: Context, crash: String): Boolean {
        if (!crash.contains("java.lang.ClassCastException") ||
            !crash.contains("java.lang.Integer cannot be cast to java.lang.Long") ||
            !crash.contains("ReminderRepository.getActiveFocusState")) return false
        return runCatching { ReminderRepository.getActiveFocusState(context) }.isSuccess
    }

    fun enterIfRecorded(activity: Activity): Boolean {
        val prefs = activity.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        val crash = try { prefs.getString("last_crash", null) } catch (_: Exception) { null }
        if (crash.isNullOrBlank()) return false
        if (canContinueAfterHandledFocusCrash(activity, crash)) return false
        active[activity] = true
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setBackgroundColor(Color.rgb(250, 247, 240))
        }
        body.addView(TextView(activity).apply {
            text = "فتح آمن للتشخيص"
            textSize = 23f
            setTextColor(Color.rgb(35, 30, 25))
        })
        body.addView(TextView(activity).apply {
            text = "الشاشة دي بتعرض سبب الإغلاق المسجل بدل فتح الواجهة العادية. مش بتمسح المهام أو الإعدادات. ما تحذفش التطبيق ولا تمسح بياناته."
            textSize = 16f
            setTextColor(Color.rgb(45, 40, 35))
            setPadding(0, dp(16), 0, dp(12))
        })
        body.addView(Button(activity).apply {
            text = "نسخ تفاصيل العطل"
            setOnClickListener {
                (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Waqt startup diagnostic", crash))
            }
        })
        body.addView(Button(activity).apply {
            text = "إغلاق"
            setOnClickListener { activity.finish() }
        })
        val trace = TextView(activity).apply {
            text = crash
            textSize = 12f
            setTextColor(Color.rgb(40, 35, 30))
            setTextIsSelectable(true)
            contentDescription = "WAQT_STARTUP_RECOVERY_TRACE"
            setPadding(0, dp(12), 0, dp(12))
        }
        body.addView(ScrollView(activity).apply {
            addView(trace, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        activity.setContentView(body)
        body.requestFocus()
        return true
    }
}
