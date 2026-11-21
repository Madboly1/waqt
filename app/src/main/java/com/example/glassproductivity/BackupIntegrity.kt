package com.example.glassproductivity

import org.json.JSONObject

/**
 * Integrity accounting for cloud backups: what went in, what was skipped,
 * and what a restore could not place. Pure JVM (org.json only) so the rules
 * stay unit-testable without Android.
 */
object BackupIntegrity {

    const val REPORT_ENTRY = "backup-report.json"

    /** What a backup run managed to include, and what it had to skip. */
    data class BuildReport(
        val includedAppMedia: Int,
        val includedExternalMedia: Int,
        val skippedExternalMedia: Int
    ) {
        val includedMedia: Int get() = includedAppMedia + includedExternalMedia
    }

    /** What a restore placed, what it failed to place, and what the backup itself was missing. */
    data class RestoreReport(
        val restoredMedia: Int,
        val failedMedia: Int,
        val skippedAtBackup: Int
    ) {
        /** Items the user must be told are not present after restore. */
        val missingTotal: Int get() = failedMedia + skippedAtBackup
    }

    fun reportJson(report: BuildReport): String = JSONObject()
        .put("kind", "waqt-backup-report")
        .put("includedAppMedia", report.includedAppMedia)
        .put("includedExternalMedia", report.includedExternalMedia)
        .put("skippedExternalMedia", report.skippedExternalMedia)
        .toString()

    /** Old backups carry no report; every count then reads as zero. */
    fun parseReport(raw: String?): BuildReport {
        if (raw.isNullOrBlank()) return BuildReport(0, 0, 0)
        return try {
            val o = JSONObject(raw)
            BuildReport(
                includedAppMedia = o.optInt("includedAppMedia", 0),
                includedExternalMedia = o.optInt("includedExternalMedia", 0),
                skippedExternalMedia = o.optInt("skippedExternalMedia", 0)
            )
        } catch (_: Exception) {
            BuildReport(0, 0, 0)
        }
    }

    /**
     * The single skip rule for gallery-referenced media: unreadable, empty,
     * or oversized items are skipped (counted by the caller), never fatal.
     */
    fun readExternalOrNull(maxBytes: Int, read: () -> ByteArray?): ByteArray? = try {
        val bytes = read()
        if (bytes == null || bytes.isEmpty() || bytes.size > maxBytes) null else bytes
    } catch (_: Exception) {
        null
    }
}
