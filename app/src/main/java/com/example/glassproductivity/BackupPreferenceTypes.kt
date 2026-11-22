package com.example.glassproductivity

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/** Backup-only codec. Never migrates or edits the live store on app startup. */
internal object BackupPreferenceTypes {
    private val legacyLongKeys = setOf(
        "waqt_focus_epoch_day", "active_focus_start_ms", "active_focus_total_ms",
        "active_focus_remaining_pause_ms"
    )

    fun typeOf(value: Any?): String? = when (value) {
        is Boolean -> "boolean"
        is Int -> "int"
        is Long -> "long"
        is Float -> "float"
        is String -> "string"
        is Set<*> -> if (value.all { it is String }) "stringSet" else null
        else -> null
    }

    fun jsonValue(value: Any): Any = when (value) {
        // Decimal strings avoid JSON-number narrowing and precision loss.
        is Long -> value.toString()
        is Float -> value.toString()
        is Set<*> -> JSONArray(value.toList())
        else -> value
    }

    fun decode(
        name: String, key: String, value: Any, declared: String?,
        existing: Any?, requireType: Boolean
    ): Any {
        fun invalid(): Nothing = throw WaqtCloudBackup.RestoreException(
            "نوع إعداد غير صالح في النسخة الاحتياطية: $name/$key"
        )
        if (requireType && declared == null) invalid()
        val knownType = when {
            name == "waqt_productivity_prefs" && key in legacyLongKeys -> "long"
            name == "waqt_prefs" && key == "rail_offset" -> "float"
            else -> null
        }
        val legacyType = knownType ?: when {
            value is Number && existing is Long -> "long"
            value is Number && existing is Float -> "float"
            value is Number && existing is Int -> "int"
            else -> null
        }
        val type = declared ?: legacyType ?: when (value) {
            is Boolean -> "boolean"
            is Int -> "int"
            is Long -> "long"
            is Double -> "float"
            is String -> "string"
            is JSONArray -> "stringSet"
            else -> invalid()
        }
        // Metadata cannot override the app's known typed-read contracts.
        if (knownType != null && declared != null && declared != knownType) invalid()
        return when (type) {
            "boolean" -> value as? Boolean ?: invalid()
            "string" -> value as? String ?: invalid()
            "int", "long" -> {
                if (value !is Number && !(declared != null && value is String)) invalid()
                val decimal = try { BigDecimal(value.toString()) } catch (_: Exception) { invalid() }
                try {
                    if (type == "int") decimal.intValueExact() else decimal.longValueExact()
                } catch (_: ArithmeticException) { invalid() }
            }
            "float" -> {
                if (value !is Number && !(declared != null && value is String)) invalid()
                val number = value.toString().toFloatOrNull() ?: invalid()
                if (!number.isFinite()) invalid()
                if (number == 0f && value.toString().toDoubleOrNull()?.let { it != 0.0 } == true) invalid()
                number
            }
            "stringSet" -> {
                val array = value as? JSONArray ?: invalid()
                val strings = linkedSetOf<String>()
                for (i in 0 until array.length()) strings.add(array.get(i) as? String ?: invalid())
                strings
            }
            else -> invalid()
        }
    }

    fun put(editor: SharedPreferences.Editor, key: String, value: Any, replacements: Map<String, String>) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> {
                var rewritten: String = value
                for ((original, replacement) in replacements) rewritten = rewritten.replace(original, replacement)
                editor.putString(key, rewritten)
            }
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
}
