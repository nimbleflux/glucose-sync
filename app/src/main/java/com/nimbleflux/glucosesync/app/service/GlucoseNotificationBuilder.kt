package com.nimbleflux.glucosesync.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Icon
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
        timestamp: Long,
        showGlucoseIcon: Boolean = true
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

        val contentView = RemoteViews(context.packageName, R.layout.notification_glucose).apply {
            setTextViewText(R.id.notification_glucose, glucoseText)
            setTextViewText(R.id.notification_trend, trend)
            setTextViewText(R.id.notification_unit, unit)
            setTextViewText(R.id.notification_delta, deltaText)
            setTextViewText(R.id.notification_time, timeText)
            setTextViewText(R.id.notification_battery, batteryText)

            val color = glucoseColor(glucoseVal, isMmol)
            setTextColor(R.id.notification_glucose, color)
        }

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = Notification.Builder(context, channelId)
            .setCustomContentView(contentView)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setContentIntent(tapIntent)
            .setOngoing(true)

        if (showGlucoseIcon) {
            builder.setSmallIcon(createGlucoseIcon(context, glucoseText, trend))
        } else {
            builder.setSmallIcon(R.drawable.ic_notification)
        }

        return builder.build()
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

    private fun createGlucoseIcon(context: Context, glucoseText: String, trend: String): Icon {
        val density = context.resources.displayMetrics.density
        val height = (24 * density).toInt().coerceAtLeast(48)
        val width = height * 2

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cornerRadius = height / 2f
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bgPaint.color = Color.WHITE
        bgPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, bgPaint)

        val arrow = trendArrowChar(trend)
        val label = glucoseText.replace(".0", "").replace(".", "")
        val combined = if (arrow != null) "$label$arrow" else label

        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            typeface = Typeface.DEFAULT_BOLD
            textSize = height * 0.55f
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
        }

        val textBounds = Rect()
        clearPaint.getTextBounds(combined, 0, combined.length, textBounds)
        val textY = height / 2f + textBounds.height() / 2f
        canvas.drawText(combined, width / 2f, textY, clearPaint)

        return Icon.createWithBitmap(bitmap)
    }

    private fun trendArrowChar(trend: String): String? = when (trend) {
        "↑↑" -> "⇈"
        "↑" -> "↑"
        "↗" -> "↗"
        "→" -> "→"
        "↘" -> "↘"
        "↓" -> "↓"
        "↓↓" -> "⇊"
        else -> null
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
