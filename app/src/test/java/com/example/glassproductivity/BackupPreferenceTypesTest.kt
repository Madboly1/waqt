package com.example.glassproductivity

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupPreferenceTypesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun prefs(name: String = "waqt_productivity_prefs") = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    @Before fun reset() {
        prefs().edit().clear().commit()
        prefs("waqt_prefs").edit().clear().commit()
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
    }
    private fun zip(root: JSONObject): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use {
            it.putNextEntry(ZipEntry("backup.json"))
            it.write(root.toString().toByteArray())
            it.closeEntry()
        }
        return out.toByteArray()
    }
    private fun legacy(version: Int = 5) = JSONObject().put("app", "WAQT").put("schemaVersion", version)
    private fun restore(root: JSONObject) = WaqtCloudBackup.restore(context, zip(root))

    @Test fun exactTypesSurviveFreshRestoreAndRepeatedRoundTrip() {
        prefs().edit().putInt("n_int", 42).putLong("n_small_long", 42L)
            .putLong("n_max_long", Long.MAX_VALUE).putLong("n_min_long", Long.MIN_VALUE)
            .putLong("active_focus_total_ms", 1500000L)
            .putFloat("n_integral_float", 1f).putFloat("n_fraction", .45f)
            .putFloat("n_zero", 0f).putBoolean("n_bool", true)
            .putString("n_text", "KEEP").putStringSet("n_set", setOf("one", "اتنين")).commit()
        prefs("waqt_prefs").edit().putFloat("rail_offset", 1f).commit()
        val expected = prefs().all.toMap()
        var backup = WaqtCloudBackup.build(context, null)
        repeat(2) {
            prefs().edit().clear().commit(); prefs("waqt_prefs").edit().clear().commit()
            WaqtCloudBackup.restore(context, backup)
            assertEquals(expected, prefs().all)
            for ((key, value) in expected) assertEquals(key, value?.javaClass, prefs().all[key]?.javaClass)
            assertEquals(1f, prefs("waqt_prefs").getFloat("rail_offset", 0f), 0f)
            assertEquals(1500000L, ReminderRepository.getActiveFocusState(context).getLong("totalMs"))
            backup = WaqtCloudBackup.build(context, null)
        }
    }
    @Test fun legacySchemasRecoverAllKnownLongAndFloatContractsOnFreshStores() {
        for (version in listOf(1, 4, 5)) {
            prefs().edit().clear().commit(); prefs("waqt_prefs").edit().clear().commit()
            restore(legacy(version).put("preferences", JSONObject()
                .put("waqt_productivity_prefs", JSONObject()
                    .put("active_focus_start_ms", 0).put("active_focus_total_ms", 1500000)
                    .put("active_focus_remaining_pause_ms", 42).put("waqt_focus_epoch_day", 741808))
                .put("waqt_prefs", JSONObject().put("rail_offset", 1))))
            for (key in listOf("active_focus_start_ms", "active_focus_total_ms", "active_focus_remaining_pause_ms", "waqt_focus_epoch_day")) {
                assertTrue(key, prefs().all[key] is Long)
            }
            assertEquals(1500000L, ReminderRepository.getActiveFocusState(context).getLong("totalMs"))
            assertEquals(1f, prefs("waqt_prefs").getFloat("rail_offset", 0f), 0f)
        }
    }
    @Test fun legacyUnknownNumericUsesExistingTypeWithoutChangingUnrelatedData() {
        prefs().edit().putLong("custom", 8L).putString("keep", "unchanged").commit()
        restore(legacy().put("preferences", JSONObject().put("waqt_productivity_prefs", JSONObject().put("custom", 7))))
        assertEquals(7L, prefs().getLong("custom", 0L))
        assertEquals("unchanged", prefs().getString("keep", null))
    }
    @Test fun newMetadataOverridesAnOldWrongNumericType() {
        prefs().edit().putLong("new_numeric", 7L).commit()
        val backup = WaqtCloudBackup.build(context, null)
        prefs().edit().putInt("new_numeric", 99).commit()
        WaqtCloudBackup.restore(context, backup)
        assertEquals(7L, prefs().getLong("new_numeric", 0L))
    }
    @Test fun legacyStringSetsRemainStringSets() {
        restore(legacy().put("preferences", JSONObject().put("waqt_productivity_prefs", JSONObject()
            .put("set", JSONArray().put("a").put("b")))))
        assertEquals(setOf("a", "b"), prefs().getStringSet("set", emptySet()))
    }
    @Test fun malformedMetadataRejectsBeforeAnyPreferenceOrFileWrite() {
        for (bad in listOf("unknown", "int", "string")) {
            prefs().edit().clear().putString("keep", "unchanged").commit()
            val before = prefs().all.toMap()
            val root = legacy(6).put("preferences", JSONObject().put("waqt_productivity_prefs", JSONObject()
                .put("keep", "changed").put("active_focus_total_ms", "1500000")))
                .put("preferenceTypes", JSONObject().put("waqt_productivity_prefs", JSONObject()
                    .put("keep", "string").put("active_focus_total_ms", bad)))
            try { restore(root); fail("must reject $bad") } catch (_: WaqtCloudBackup.RestoreException) { }
            assertEquals(before, prefs().all)
            assertFalse(java.io.File(context.filesDir, "restored_media").exists())
        }
    }
    @Test fun missingMetadataOverflowAndFractionalLongRejectWithoutWrites() {
        val invalid = listOf(
            legacy(6).put("preferences", JSONObject().put("waqt_prefs", JSONObject().put("rail_offset", 1))),
            legacy().put("preferences", JSONObject().put("waqt_productivity_prefs", JSONObject().put("active_focus_total_ms", 1.5))),
            legacy().put("preferences", JSONObject().put("waqt_productivity_prefs", JSONObject().put("active_focus_total_ms", 1e30))),
            legacy().put("preferences", JSONObject().put("waqt_prefs", JSONObject().put("rail_offset", 1e300))),
            legacy().put("preferences", JSONObject().put("waqt_prefs", JSONObject().put("rail_offset", "wrong type")))
        )
        for (root in invalid) {
            prefs().edit().putString("keep", "unchanged").commit()
            try { restore(root); fail("must reject malformed type") } catch (_: WaqtCloudBackup.RestoreException) { }
            assertEquals("unchanged", prefs().getString("keep", null))
            assertFalse(java.io.File(context.filesDir, "restored_media").exists())
        }
    }
}
