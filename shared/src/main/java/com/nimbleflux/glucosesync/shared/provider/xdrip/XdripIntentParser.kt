package com.nimbleflux.glucosesync.shared.provider.xdrip

import android.content.Intent
import com.nimbleflux.glucosesync.shared.domain.TrendArrow

/**
 * Data captured from a single xDrip+ BgReading broadcast.
 *
 * Plain data holder (no Android dependencies beyond itself) so it can be
 * persisted, returned from a parser, and shared between the runtime receiver
 * in [XdripBroadcastProvider] and the manifest-declared receiver.
 */
data class XdripReading(
    val glucoseMgDl: Double,
    val timestampMs: Long,
    val deltaMgDl: Double?,
    val trend: TrendArrow = TrendArrow.UNKNOWN,
    val batteryPercent: Double? = null
)

/**
 * Result of inspecting an incoming xDrip+/Diabox broadcast.
 *
 * - [NoGlucose]: a broadcast arrived for a watched action but carried no
 *   usable glucose value (a key/format mismatch). The caller increments its
 *   "seen but rejected" diagnostic counter.
 * - [Accepted]: a usable reading was extracted.
 */
sealed interface XdripParseResult {
    data object NoGlucose : XdripParseResult
    data class Accepted(val reading: XdripReading) : XdripParseResult
}

/**
 * Parses an xDrip+ / Diabox glucose broadcast intent into an [XdripReading].
 *
 * Extracted from the anonymous receiver in [XdripBroadcastProvider] so the
 * exact same parsing logic is shared by:
 *  - the runtime-registered receiver (live path while the app/service is in
 *    the foreground), and
 *  - the manifest-declared receiver (the durability backstop that captures
 *    broadcasts even while the process is otherwise idle).
 *
 * Stock xDrip+ sends fully-qualified double extras; the Diabox convention
 * sends bare float extras. We prefer the modern keys and fall back so the
 * provider works with either source app.
 */
object XdripIntentParser {

    /** Assumed minutes between CGM readings, used to turn a per-minute slope into a per-reading delta. */
    const val DELTA_APPROX_MINUTES = 5.0

    fun parse(intent: Intent): XdripParseResult {
        // Accept both the stock xDrip+ action and the Diabox-style legacy
        // action; a single receiver handles either.
        when (intent.action) {
            XdripBroadcastProvider.ACTION_BG_ESTIMATE,
            XdripBroadcastProvider.ACTION_BG_READING -> Unit
            else -> return XdripParseResult.NoGlucose
        }

        return interpret(
            action = intent.action,
            modernGlucose = intent.getDoubleExtra(XdripBroadcastProvider.EXTRA_BG_ESTIMATE, Double.NaN),
            legacyGlucose = intent.getFloatExtra(XdripBroadcastProvider.EXTRA_BG_VALUE, -1f),
            hasTimestamp = intent.hasExtra(XdripBroadcastProvider.EXTRA_TIMESTAMP),
            modernTimestamp = intent.getLongExtra(XdripBroadcastProvider.EXTRA_TIMESTAMP, 0L),
            legacyTimestamp = intent.getLongExtra(XdripBroadcastProvider.EXTRA_BG_TIMESTAMP, 0L),
            hasSlope = intent.hasExtra(XdripBroadcastProvider.EXTRA_BG_SLOPE),
            slope = intent.getDoubleExtra(XdripBroadcastProvider.EXTRA_BG_SLOPE, Double.NaN),
            hasLegacyDelta = intent.hasExtra(XdripBroadcastProvider.EXTRA_BG_DELTA),
            legacyDelta = intent.getFloatExtra(XdripBroadcastProvider.EXTRA_BG_DELTA, Float.NaN),
            slopeName = intent.getStringExtra(XdripBroadcastProvider.EXTRA_BG_SLOPE_NAME),
            hasBattery = intent.hasExtra(XdripBroadcastProvider.EXTRA_SENSOR_BATTERY),
            battery = intent.getIntExtra(XdripBroadcastProvider.EXTRA_SENSOR_BATTERY, -1),
            nowMs = System.currentTimeMillis()
        )
    }

    /**
     * Pure interpretation of the resolved extras — no [Intent] dependency, so
     * it's unit-testable on a plain JVM (the shared module's unit tests run
     * without Robolectric, with `isReturnDefaultValues = true` stubbing).
     * [parse] reads the extras off the Intent and hands them here.
     */
    internal fun interpret(
        action: String?,
        modernGlucose: Double,
        legacyGlucose: Float,
        hasTimestamp: Boolean,
        modernTimestamp: Long,
        legacyTimestamp: Long,
        hasSlope: Boolean,
        slope: Double,
        hasLegacyDelta: Boolean,
        legacyDelta: Float,
        slopeName: String?,
        hasBattery: Boolean,
        battery: Int,
        nowMs: Long
    ): XdripParseResult {
        // Stock xDrip+ extras are doubles under fully-qualified keys;
        // Diabox-style extras are floats under bare keys. Prefer the
        // modern keys and fall back so the provider works with either
        // source app.
        val glucose = when {
            !modernGlucose.isNaN() && modernGlucose > 0.0 -> modernGlucose
            else -> legacyGlucose.toDouble()
        }

        if (glucose <= 0.0) {
            // Broadcasts arriving but with no usable glucose value — most
            // often a key/format mismatch between this app and the source.
            return XdripParseResult.NoGlucose
        }

        val timestamp = if (hasTimestamp) modernTimestamp else legacyTimestamp
        val effectiveTimestamp = if (timestamp > 0) timestamp else nowMs

        // Delta: xDrip+ sends a slope (mg/dL per minute), not an absolute
        // delta. Approximate the per-reading delta as slope × 5 min, which
        // matches the typical CGM reading interval. Diabox sends an
        // absolute bgDelta (mg/dL) directly.
        val deltaMgDl: Double? = when {
            hasSlope -> if (!slope.isNaN()) slope * DELTA_APPROX_MINUTES else null
            hasLegacyDelta -> if (!legacyDelta.isNaN()) legacyDelta.toDouble() else null
            else -> null
        }

        // Trend arrow: xDrip+ sends the slope name (e.g. "DoubleUp").
        val trend = if (slopeName != null) mapSlopeName(slopeName) else TrendArrow.UNKNOWN

        // Transmitter battery (0-100). Only present in the stock xDrip+
        // broadcast; Diabox doesn't send it.
        val batteryPct: Double? = if (hasBattery && battery in 0..100) battery / 100.0 else null

        return XdripParseResult.Accepted(
            XdripReading(
                glucoseMgDl = glucose,
                timestampMs = effectiveTimestamp,
                deltaMgDl = deltaMgDl,
                trend = trend,
                batteryPercent = batteryPct
            )
        )
    }

    /**
     * Map an xDrip+ slope-name string to our TrendArrow enum. Values follow
     * the names emitted by xDrip+'s `BroadcastGlucose` (e.g. "DoubleUp",
     * "Flat"). `"9"` is sent when the user has hidden the slope — treat it as
     * unknown so the coordinator can derive an arrow from history.
     */
    fun mapSlopeName(slopeName: String?): TrendArrow = when (slopeName?.lowercase()) {
        "doubleup" -> TrendArrow.RISING_RAPIDLY
        "singleup" -> TrendArrow.RISING
        "fortyfiveup" -> TrendArrow.RISING_SLOWLY
        "flat" -> TrendArrow.STABLE
        "fortyfivedown" -> TrendArrow.FALLING_SLOWLY
        "singledown" -> TrendArrow.FALLING
        "doubledown" -> TrendArrow.FALLING_RAPIDLY
        else -> TrendArrow.UNKNOWN
    }
}
