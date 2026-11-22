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

    fun enterIfRecorded(activity: Activity): Boolean {
        val prefs = activity.getSharedPreferences("waqt_prefs", Context.MODE_PRIVATE)
        val crash = try { prefs.getString("last_crash", null) } catch (_: Exception) { null }
        if (crash.isNullOrBlank()) return false
        active[activity] = true
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
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
            setPadding(0, dp(16), 0, dp(16))
        })
        body.addView(TextView(activity).apply {
            text = crash
            textSize = 12f
            setTextColor(Color.rgb(40, 35, 30))
            setTextIsSelectable(true)
            contentDescription = "WAQT_STARTUP_RECOVERY_TRACE"
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
        activity.setContentView(ScrollView(activity).apply {
            addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        return true
    }
}
