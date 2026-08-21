package com.example.ui.util

import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeFormatter {

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun formatProgressInfo(positionMs: Long, durationMs: Long): String {
        val currentStr = formatDuration(positionMs)
        val totalStr = formatDuration(durationMs)
        return "$currentStr / $totalStr"
    }

    fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0L) return "Never"
        val diff = System.currentTimeMillis() - timestamp
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> "${days / 7}w ago"
        }
    }
}
