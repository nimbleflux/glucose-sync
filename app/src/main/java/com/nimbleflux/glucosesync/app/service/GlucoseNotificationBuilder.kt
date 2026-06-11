package com.nimbleflux.glucosesync.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.nimbleflux.glucosesync.app.R
import com.nimbleflux.glucosesync.app.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GlucoseNotificationBuilder {

    fun build(
        context: Context,
        channelId: String,
        glucose: Double?,
        trend: String,
        delta: Double?,
        unit: String,
        batteryPercent: Double?,
        timestamp: Long
    ): Notification {
        val glucoseVal = glucose ?: return buildDefault(context, channelId)

        val isMmol = unit == "mmol/L"
        val displayGlucose = if (isMmol) glucoseVal else glucoseVal * 18
        val glucoseText = String.format("%.1f", displayGlucose)

        val sign = if (delta != null && delta >= 0) "+" else ""
        val deltaText = if (delta != null) " (${sign}${String.format("%.1f", delta)})" else ""

        val timeText = formatTime(timestamp)
        val batteryText = if (batteryPercent != null) {
            " ${context.getString(R.string.notification_battery_format, (batteryPercent * 100).toInt())}"
        } else ""

        val color = glucoseColor(glucoseVal, isMmol)

        val collapsedView = RemoteViews(context.packageName, R.layout.notification_glucose_collapsed).apply {
            setTextViewText(R.id.notification_glucose, glucoseText)
            setTextViewText(R.id.notification_trend, trend)
            setTextViewText(R.id.notification_unit, unit)
            setTextViewText(R.id.notification_delta, deltaText)
            setTextViewText(R.id.notification_battery, batteryText)
            setTextColor(R.id.notification_glucose, color)
        }

        val expandedView = RemoteViews(context.packageName, R.layout.notification_glucose_expanded).apply {
            setTextViewText(R.id.notification_glucose, glucoseText)
            setTextViewText(R.id.notification_trend, trend)
            setTextViewText(R.id.notification_unit, unit)
            setTextViewText(R.id.notification_delta, deltaText)
            setTextViewText(R.id.notification_time, timeText)
            setTextViewText(R.id.notification_battery, batteryText)
            setTextColor(R.id.notification_glucose, color)
        }

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(context, channelId)
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun buildDefault(context: Context, channelId: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_monitoring_glucose))
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    private fun formatTime(timestamp: Long): String {
        val diffSec = System.currentTimeMillis() / 1000 - timestamp
        return when {
            diffSec < 60 -> "Updated just now"
            diffSec < 3600 -> "Updated ${diffSec / 60}m ago"
            else -> {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                "Updated at ${sdf.format(Date(timestamp * 1000))}"
            }
        }
    }

    private fun glucoseColor(glucoseMmol: Double, isMmol: Boolean): Int {
        val g = if (isMmol) glucoseMmol else glucoseMmol / 18
        return when {
            g < 3.9 -> Color.parseColor("#EF5350")
            g > 10.0 -> Color.parseColor("#EF5350")
            g < 4.4 || g > 9.0 -> Color.parseColor("#FFA726")
            else -> Color.parseColor("#66BB6A")
        }
    }
}
