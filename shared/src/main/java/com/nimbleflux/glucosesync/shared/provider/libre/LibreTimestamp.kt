package com.nimbleflux.glucosesync.shared.provider.libre

import java.text.SimpleDateFormat
import java.util.*

object LibreTimestamp {

    fun parseToEpochSeconds(timestamp: String?): Long {
        if (timestamp.isNullOrBlank()) return 0L
        val normalized = normalizeTimestamp(timestamp)
        val formats = listOf(
            "M/d/yyyy h:mm:ss a",
            "MM/dd/yyyy h:mm:ss a",
            "M/dd/yyyy h:mm:ss a",
            "MM/d/yyyy h:mm:ss a",
            "M/d/yyyy hh:mm:ss a",
            "M/d/yyyy H:mm:ss"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(normalized) ?: continue
                return date.time / 1000
            } catch (_: Exception) { }
        }
        return 0L
    }

    private fun normalizeTimestamp(ts: String): String {
        return ts.trim()
    }
}
