package com.nimbleflux.medtrumwatch.shared.domain

import kotlin.random.Random

object DemoData {

    private var currentGlucose = 5.6
    private var trendDirection = 0
    private val random = Random(System.currentTimeMillis())

    private val trendNames = mapOf(
        -2 to "\u2B06\uFE0F",
        -1 to "\u2197\uFE0F",
         0 to "\u2192",
         1 to "\u2197\uFE0F",
         2 to "\u2B06\uFE0F"
    )

    val demoDisplayName = "Demo User"

    fun nextGlucose(): Pair<Double, String> {
        val delta = random.nextDouble(-0.3, 0.3)
        currentGlucose = (currentGlucose + delta).coerceIn(3.5, 12.0)

        val newTrend = when {
            delta < -0.15 -> (trendDirection - 1).coerceAtLeast(-2)
            delta > 0.15 -> (trendDirection + 1).coerceAtMost(2)
            else -> trendDirection
        }
        trendDirection = newTrend

        val trendSymbol = trendNames[newTrend] ?: "\u2192"
        return currentGlucose to trendSymbol
    }

    fun snapshot(history: List<GlucoseHistoryPoint> = emptyList()): GlucoseSnapshot {
        val (glucose, _) = nextGlucose()
        val trend = TrendArrow.fromDelta(trendDirection.toDouble())
        return GlucoseSnapshot(
            glucose = glucose,
            timestamp = System.currentTimeMillis() / 1000 - random.nextLong(0, 120),
            trend = trend,
            unit = "mmol/L",
            sensorActive = true,
            history = history
        )
    }

    fun generateHistory(): List<GlucoseHistoryPoint> {
        val now = System.currentTimeMillis() / 1000
        val points = mutableListOf<GlucoseHistoryPoint>()
        var value = 5.5
        for (i in 0 until 48) {
            value = (value + Random.nextDouble(-0.4, 0.4)).coerceIn(3.2, 13.0)
            points.add(GlucoseHistoryPoint(now - (48 - i) * 900, value))
        }
        return points
    }
}
