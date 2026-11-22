package com.example.glassproductivity

import android.content.SharedPreferences

/** Read-only compatibility for known numeric preferences from legacy backups. */
internal object SavedNumericPreferences {
    fun long(prefs: SharedPreferences, key: String, fallback: Long): Long = when (val raw = prefs.all[key]) {
        is Int -> raw.toLong()
        else -> prefs.getLong(key, fallback)
    }

    fun float(prefs: SharedPreferences, key: String, fallback: Float): Float = when (val raw = prefs.all[key]) {
        is Int -> raw.toFloat()
        else -> prefs.getFloat(key, fallback)
    }
}
