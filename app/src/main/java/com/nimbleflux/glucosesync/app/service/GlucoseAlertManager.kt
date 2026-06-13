package com.nimbleflux.glucosesync.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.nimbleflux.glucosesync.app.R
import com.nimbleflux.glucosesync.app.ui.MainActivity

class GlucoseAlertManager(private val context: Context) {

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var lastHighAlertTime: Long = 0
    private var lastLowAlertTime: Long = 0

    fun checkAndAlert(
        glucoseMmol: Double,
        unit: String,
        highThresholdMmol: Double,
        lowThresholdMmol: Double,
        alertsEnabled: Boolean,
        overrideDnd: Boolean,
        repeatMinutes: Int,
        soundEnabled: Boolean = true,
        vibrateEnabled: Boolean = true,
        vibrateDurationSeconds: Int = 3
    ) {
        if (!alertsEnabled) return

        val now = System.currentTimeMillis()
        val repeatMs = repeatMinutes * 60_000L

        val displayValue = if (unit == "mg/dL") glucoseMmol * 18 else glucoseMmol
        val displayHigh = if (unit == "mg/dL") highThresholdMmol * 18 else highThresholdMmol
        val displayLow = if (unit == "mg/dL") lowThresholdMmol * 18 else lowThresholdMmol

        val suffix = channelSuffix(soundEnabled, vibrateEnabled, vibrateDurationSeconds)

        if (glucoseMmol > highThresholdMmol && (now - lastHighAlertTime) >= repeatMs) {
            lastHighAlertTime = now
            showAlert(
                id = NOTIFY_HIGH,
                title = context.getString(R.string.alert_high_title),
                channel = if (overrideDnd) "glucose_high_dnd_$suffix" else "glucose_high_$suffix",
                glucose = displayValue,
                threshold = displayHigh,
                unit = unit,
                comparison = context.getString(R.string.alert_comparison_above),
                soundEnabled = soundEnabled,
                vibrateEnabled = vibrateEnabled,
                vibrateDurationSeconds = vibrateDurationSeconds,
                overrideDnd = overrideDnd
            )
        } else if (glucoseMmol < lowThresholdMmol && (now - lastLowAlertTime) >= repeatMs) {
            lastLowAlertTime = now
            showAlert(
                id = NOTIFY_LOW,
                title = context.getString(R.string.alert_low_title),
                channel = if (overrideDnd) "glucose_low_dnd_$suffix" else "glucose_low_$suffix",
                glucose = displayValue,
                threshold = displayLow,
                unit = unit,
                comparison = context.getString(R.string.alert_comparison_below),
                soundEnabled = soundEnabled,
                vibrateEnabled = vibrateEnabled,
                vibrateDurationSeconds = vibrateDurationSeconds,
                overrideDnd = overrideDnd
            )
        }
    }

    private fun channelSuffix(sound: Boolean, vibrate: Boolean, duration: Int): String {
        return "${if (sound) "s" else "x"}${if (vibrate) "v$duration" else "x"}"
    }

    private fun showAlert(
        id: Int,
        title: String,
        channel: String,
        glucose: Double,
        threshold: Double,
        unit: String,
        comparison: String,
        soundEnabled: Boolean,
        vibrateEnabled: Boolean,
        vibrateDurationSeconds: Int,
        overrideDnd: Boolean
    ) {
        val vibPattern = buildVibratePattern(vibrateEnabled, vibrateDurationSeconds)
        ensureChannel(channel, soundEnabled, vibPattern, overrideDnd)

        val text = context.getString(R.string.alert_threshold_text, glucose, unit, comparison, threshold)

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)

        if (overrideDnd) {
            builder.setFullScreenIntent(tapIntent, true)
        }

        nm.notify(id, builder.build())
    }

    private fun buildVibratePattern(enabled: Boolean, durationSeconds: Int): LongArray {
        if (!enabled) return longArrayOf(0)

        val buzzMs = 300L
        val gapMs = 100L
        val cycleMs = buzzMs + gapMs
        val cycles = (durationSeconds * 1000L / cycleMs).coerceIn(1, 30).toInt()
        val pattern = LongArray(1 + cycles * 2)
        pattern[0] = 0
        for (i in 0 until cycles) {
            pattern[1 + i * 2] = buzzMs
            pattern[2 + i * 2] = gapMs
        }
        return pattern
    }

    private fun ensureChannel(
        channelId: String,
        soundEnabled: Boolean,
        vibratePattern: LongArray,
        overrideDnd: Boolean
    ) {
        val existing = nm.getNotificationChannel(channelId)
        if (existing != null) {
            if (existing.canBypassDnd() != overrideDnd) {
                nm.deleteNotificationChannel(channelId)
            } else {
                return
            }
        }

        val sound = if (soundEnabled) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else null

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(channelId, context.getString(R.string.notification_channel_alert), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.notification_channel_alert_desc)
            if (sound != null) setSound(sound, audioAttrs) else setSound(null, null)
            enableVibration(true)
            vibrationPattern = vibratePattern
            setBypassDnd(overrideDnd)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFY_HIGH = 1001
        private const val NOTIFY_LOW = 1002
    }
}
