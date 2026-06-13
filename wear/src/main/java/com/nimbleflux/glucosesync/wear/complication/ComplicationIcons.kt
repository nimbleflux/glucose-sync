package com.nimbleflux.glucosesync.wear.complication

import android.content.Context
import android.graphics.drawable.Icon
import com.nimbleflux.glucosesync.wear.R

object ComplicationIcons {

    fun trendIconResId(trend: String): Int = when (trend) {
        "\u2B06" -> R.drawable.ic_trend_rising_rapidly
        "\u2191" -> R.drawable.ic_trend_rising
        "\u2197" -> R.drawable.ic_trend_rising_slowly
        "\u2192" -> R.drawable.ic_trend_stable
        "\u2198" -> R.drawable.ic_trend_falling_slowly
        "\u2193" -> R.drawable.ic_trend_falling
        "\u2B07" -> R.drawable.ic_trend_falling_rapidly
        else -> R.drawable.ic_trend_unknown
    }

    fun trendIcon(context: Context, trend: String): Icon =
        Icon.createWithResource(context, trendIconResId(trend))

    fun dropletIcon(context: Context): Icon =
        Icon.createWithResource(context, R.drawable.ic_droplet)

    fun batteryIcon(context: Context): Icon =
        Icon.createWithResource(context, R.drawable.ic_battery)
}
