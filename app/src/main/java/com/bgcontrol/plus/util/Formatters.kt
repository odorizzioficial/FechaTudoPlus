package com.bgcontrol.plus.util

import android.content.Context
import com.bgcontrol.plus.R
import java.util.concurrent.TimeUnit

object Formatters {

    /** Formata bytes em MB/GB. Null vira "—": informação indisponível, nunca inventada. */
    fun memory(context: Context, bytes: Long?): String {
        if (bytes == null || bytes <= 0) return context.getString(R.string.not_available)
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format("%.1f GB", mb / 1024.0)
        } else {
            String.format("%.0f MB", mb)
        }
    }

    fun memoryValue(bytes: Long?): Pair<String, String>? {
        if (bytes == null || bytes <= 0) return null
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format("%.1f", mb / 1024.0) to "GB"
        } else {
            String.format("%.0f", mb) to "MB"
        }
    }

    fun relativeTime(context: Context, timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0) return context.getString(R.string.never)
        val diff = System.currentTimeMillis() - timestamp
        if (diff < TimeUnit.MINUTES.toMillis(1)) return context.getString(R.string.time_just_now)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        if (minutes < 60) return context.getString(R.string.time_minutes_ago, minutes.toInt())
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        if (hours < 24) return context.getString(R.string.time_hours_ago, hours.toInt())
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return context.getString(R.string.time_days_ago, days.toInt())
    }
}
