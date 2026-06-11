package com.nimbleflux.glucosesync.shared.domain

import kotlin.random.Random

object DemoData {

    private var currentGlucose = 5.6
    private var trendDirection = 0
    private val random = Random(System.currentTimeMillis())

    private val trendNames = mapOf(
        -3 to "\u2B07",
        -2 to "\u2193",
        -1 to "\u2198",
         0 to "\u2192",
         1 to "\u2197",
         2 to "\u2191",
         3 to "\u2B06"
    )

    val demoDisplayName = "Demo User"

    fun nextGlucose(): Pair<Double, String> {
        val delta = random.nextDouble(-0.3, 0.3)
        currentGlucose = (currentGlucose + delta).coerceIn(3.5, 12.0)

        val newTrend = when {
            delta < -0.15 -> (trendDirection - 1).coerceAtLeast(-3)
            delta > 0.15 -> (trendDirection + 1).coerceAtMost(3)
            else -> trendDirection
        }
        trendDirection = newTrend

        val trendSymbol = trendNames[newTrend] ?: "\u2192"
        return currentGlucose to trendSymbol
    }

    fun snapshot(history: List<GlucoseHistoryPoint> = emptyList()): GlucoseSnapshot {
        val (glucose, _) = nextGlucose()
        val trend = when (trendDirection) {
            -3 -> TrendArrow.FALLING_RAPIDLY
            -2 -> TrendArrow.FALLING
            -1 -> TrendArrow.FALLING_SLOWLY
             1 -> TrendArrow.RISING_SLOWLY
             2 -> TrendArrow.RISING
             3 -> TrendArrow.RISING_RAPIDLY
             else -> TrendArrow.STABLE
        }
        val now = System.currentTimeMillis() / 1000
        val delta = if (history.size >= 2) {
            history.last().glucoseMmol - history[history.size - 2].glucoseMmol
        } else null

        val computedHistory = if (history.size >= 2) history else generateHistory()
        val tir = computeTIR(computedHistory)
        val avg = computeAverage(computedHistory)

        return GlucoseSnapshot(
            glucose = glucose,
            timestamp = now - random.nextLong(0, 120),
            trend = trend,
            unit = "mmol/L",
            sensorActive = true,
            history = history,
            iob = 1.2 + random.nextDouble(-0.3, 0.3),
            basalRate = 0.8,
            lastBolus = 2.5,
            lastBolusTime = now - random.nextLong(1800, 7200),
            remainingDose = 142.0 + random.nextDouble(-10.0, 10.0),
            batteryPercent = 0.85 + random.nextDouble(-0.05, 0.10),
            delta = delta,
            highThreshold = 10.0,
            lowThreshold = 3.9,
            timeInRange = tir,
            averageGlucose = avg,
            alerts = generateAlerts(now)
        )
    }

    fun generateHistory(): List<GlucoseHistoryPoint> {
        val now = System.currentTimeMillis() / 1000
        val points = mutableListOf<GlucoseHistoryPoint>()
        var value = 5.5
        for (i in 0 until 96) {
            value = (value + Random.nextDouble(-0.4, 0.4)).coerceIn(3.2, 13.0)
            points.add(GlucoseHistoryPoint(now - (96 - i) * 900, value))
        }
        return points
    }

    private fun computeTIR(history: List<GlucoseHistoryPoint>): Double {
        if (history.isEmpty()) return 0.0
        val inRange = history.count { it.glucoseMmol in 3.9..10.0 }
        return inRange.toDouble() / history.size * 100.0
    }

    private fun computeAverage(history: List<GlucoseHistoryPoint>): Double {
        if (history.isEmpty()) return 0.0
        return history.map { it.glucoseMmol }.average()
    }

    private fun generateAlerts(now: Long): List<AlertEntry> {
        return listOf(
            AlertEntry(now - 3600, "High glucose alert", "sensor"),
            AlertEntry(now - 7200, "Basal rate changed", "pump")
        )
    }
}
