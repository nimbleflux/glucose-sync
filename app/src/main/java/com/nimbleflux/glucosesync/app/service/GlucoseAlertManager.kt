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
import com.nimbleflux.glucosesync.app.data.SettingsStore
import com.nimbleflux.glucosesync.app.ui.MainActivity

class GlucoseAlertManager private constructor(private val context: Context) {

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val settingsStore = SettingsStore(context)

    /**
     * Throttle windows are persisted in SettingsStore so that service
     * restarts (which used to reset these to 0) cannot violate the
     * repeat-window contract. Reads happen inline; writes are async.
     */
    private var lastHighAlertTime: Long
        get() = settingsStore.getLastHighAlertTime()
        set(value) = settingsStore.setLastHighAlertTime(value)

    private var lastLowAlertTime: Long
        get() = settingsStore.getLastLowAlertTime()
        set(value) = settingsStore.setLastLowAlertTime(value)

    private var lastStaleAlertTime: Long
        get() = settingsStore.getLastStaleAlertTime()
        set(value) = settingsStore.setLastStaleAlertTime(value)

    companion object {
        private const val NOTIFY_HIGH = 1001
        private const val NOTIFY_LOW = 1002
        private const val NOTIFY_STALE = 1003
        private const val STALE_CHANNEL_ID = "glucose_stale"

        @Volatile private var instance: GlucoseAlertManager? = null

        fun getInstance(context: Context): GlucoseAlertManager {
            return instance ?: synchronized(this) {
                instance ?: GlucoseAlertManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * In-memory counter for hysteresis on the threshold alerts. Requires
     * two consecutive out-of-range polls before alerting, preventing
     * boundary thrash when readings hover right at the threshold.
     * Reset to 0 whenever a reading is in range.
     */
    private var consecutiveHighCount = 0
    private var consecutiveLowCount = 0

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

        // Hysteresis: require 2 consecutive out-of-range readings before
        // alerting. Eliminates noise from a single spurious reading that
        // crosses the threshold for one poll cycle.
        val requiredConsecutive = 2

        if (glucoseMmol > highThresholdMmol) {
            consecutiveHighCount++
            consecutiveLowCount = 0
            if (consecutiveHighCount >= requiredConsecutive && (now - lastHighAlertTime) >= repeatMs) {
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
            }
        } else if (glucoseMmol < lowThresholdMmol) {
            consecutiveLowCount++
            consecutiveHighCount = 0
            if (consecutiveLowCount >= requiredConsecutive && (now - lastLowAlertTime) >= repeatMs) {
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
        } else {
            // In range - reset both counters so the next excursion starts fresh.
            consecutiveHighCount = 0
            consecutiveLowCount = 0
        }
    }

    /**
     * Detect signal loss: if no reading has arrived within
     * [staleThresholdMinutes], fire a notification on the dedicated stale
     * channel. Throttled to one alert per threshold window so we don't
     * spam every polling cycle while the user is still stale.
     *
     * Cleared automatically once fresh data arrives (the throttle tracker
     * resets when ageSec drops below the threshold).
     */
    fun checkStaleAlert(
        lastReadingEpochSec: Long,
        staleThresholdMinutes: Int = 15,
        alertsEnabled: Boolean,
        lastFetchError: String? = null
    ) {
        if (!alertsEnabled) return
        val nowSec = System.currentTimeMillis() / 1000
        val ageSec = nowSec - lastReadingEpochSec
        if (ageSec < staleThresholdMinutes * 60L) {
            // Fresh - reset the throttle tracker so the next staleness
            // gets a fresh immediate alert instead of waiting for the
            // repeat window to elapse.
            if (lastStaleAlertTime != 0L) lastStaleAlertTime = 0L
            return
        }
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastStaleAlertTime < staleThresholdMinutes * 60_000L) return
        lastStaleAlertTime = nowMs

        ensureStaleChannel()
        val ageMinutes = (ageSec / 60L).toInt()
        val text = if (lastFetchError != null) {
            context.getString(R.string.alert_stale_text_with_error, ageMinutes, lastFetchError)
        } else {
            context.getString(R.string.alert_stale_text, ageMinutes)
        }
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(context, STALE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.alert_stale_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
        nm.notify(NOTIFY_STALE, builder.build())
    }

    private fun ensureStaleChannel() {
        if (nm.getNotificationChannel(STALE_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            STALE_CHANNEL_ID,
            context.getString(R.string.notification_channel_stale),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_stale_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
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
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)

        // Note: we deliberately do NOT call setFullScreenIntent() here.
        // Full-screen intents are designed for incoming-call-style
        // takeovers - they wake the screen, bypass the lock screen, and
        // launch the activity directly. For a CGM alert the right
        // behaviour is a high-priority notification that sounds/vibrates
        // and shows on the lock screen, but lets the user decide whether
        // to open the app. IMPORTANCE_HIGH + CATEGORY_ALARM + bypassDnd
        // already deliver that.

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
        // Don't claim DND bypass if the app doesn't actually have the
        // permission. The channel would be created with bypassDnd=true
        // but the system silently ignores it, giving the user a false
        // sense of security.
        val hasDndAccess = nm.isNotificationPolicyAccessGranted
        val effectiveOverrideDnd = overrideDnd && hasDndAccess

        val existing = nm.getNotificationChannel(channelId)
        if (existing != null) {
            if (existing.canBypassDnd() != effectiveOverrideDnd) {
                nm.deleteNotificationChannel(channelId)
            } else {
                return
            }
        }

        // Prune orphaned sibling channels. Channel IDs follow the patterns:
        //   glucose_high_sXvY, glucose_high_dnd_sXvY
        //   glucose_low_sXvY,  glucose_low_dnd_sXvY
        // Use a fixed base prefix (glucose_high or glucose_low) so both
        // DND and non-DND variants are pruned when the suffix changes.
        val basePrefix = when {
            channelId.startsWith("glucose_high") -> "glucose_high"
            channelId.startsWith("glucose_low") -> "glucose_low"
            else -> ""
        }
        if (basePrefix.isNotEmpty()) {
            nm.notificationChannels.forEach { ch ->
                if (ch.id != channelId && ch.id.startsWith(basePrefix + "_")) {
                    nm.deleteNotificationChannel(ch.id)
                }
            }
        }

        val sound = if (soundEnabled) {
            // When DND override is on, use TYPE_ALARM so the sound
            // passes through DND's "Alarms only" policy. The audio
            // attributes already use USAGE_ALARM — the ringtone URI
            // must match or the system may mute it.
            val ringtoneType = if (effectiveOverrideDnd) {
                RingtoneManager.TYPE_ALARM
            } else {
                RingtoneManager.TYPE_NOTIFICATION
            }
            RingtoneManager.getDefaultUri(ringtoneType)
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
            setBypassDnd(effectiveOverrideDnd)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
    }
}
