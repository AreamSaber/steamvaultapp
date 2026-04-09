package com.example.steam_vault_app.data.cloudbackup

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object CloudBackupAutoBackupTiming {
    fun computeDelayMillis(
        lastUploadAt: String?,
        nowMillis: Long,
        debounceMillis: Long,
        minUploadIntervalMillis: Long,
    ): Long {
        val lastUploadMillis = parseIsoUtc(lastUploadAt)
        val cooldownMillis = if (lastUploadMillis == null) {
            0L
        } else {
            (lastUploadMillis + minUploadIntervalMillis - nowMillis).coerceAtLeast(0L)
        }
        return maxOf(debounceMillis, cooldownMillis)
    }

    fun parseIsoUtc(value: String?): Long? {
        if (value.isNullOrBlank()) {
            return null
        }
        return try {
            isoFormatter().parse(value)?.time
        } catch (_: ParseException) {
            null
        }
    }

    fun formatIsoUtc(timestampMillis: Long): String {
        return isoFormatter().format(Date(timestampMillis))
    }

    private fun isoFormatter(): SimpleDateFormat {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
