package com.nimbleflux.glucosesync.wear.tile

import android.graphics.Color
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nimbleflux.glucosesync.wear.repository.GlucoseRepository
import java.util.concurrent.TimeUnit

class GlucoseTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val repo = GlucoseRepository.getInstance(this)
        val state = repo.state.value

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setFreshnessIntervalMillis(TimeUnit.MINUTES.toMillis(1))
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(buildLayout(state))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion("1").build())
    }

    private fun buildLayout(state: com.nimbleflux.glucosesync.wear.repository.WatchGlucoseState): LayoutElementBuilders.LayoutElement {
        if (state.glucose <= 0.0 || state.timestamp == 0L) {
            return tileColumn("Glucose", "No data", "Waiting for phone", Color.GRAY, Color.GRAY)
        }

        val now = System.currentTimeMillis() / 1000
        val ageMin = ((now - state.timestamp) / 60).toInt().coerceAtLeast(0)
        val glucoseText = if (state.unit == "mg/dL") {
            String.format("%.0f %s", state.glucose * 18, state.unit)
        } else {
            String.format("%.1f %s", state.glucose, state.unit)
        }
        val trendText = state.trend.ifBlank { "" }
        val ageText = if (ageMin < 1) "now" else "${ageMin}m ago"
        val inRange = state.glucose in state.lowThreshold..state.highThreshold
        val valueColor = if (inRange) Color.rgb(0x66, 0xBB, 0x6A) else Color.rgb(0xEF, 0x53, 0x50)

        return tileColumn("Glucose", glucoseText, "$trendText  $ageText", valueColor, Color.LTGRAY)
    }

    private fun tileColumn(
        title: String,
        value: String,
        subtitle: String,
        valueColor: Int,
        subtitleColor: Int
    ): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.ExpandedDimensionProp.Builder().build())
            .setHeight(DimensionBuilders.ExpandedDimensionProp.Builder().build())
            .addContent(text(title, 12f, Color.GRAY))
            .addContent(spacer(4f))
            .addContent(text(value, 40f, valueColor, bold = true))
            .addContent(spacer(2f))
            .addContent(text(subtitle, 13f, subtitleColor))
            .build()

    private fun text(content: String, sizeSp: Float, color: Int, bold: Boolean = false) =
        LayoutElementBuilders.Text.Builder()
            .setText(content)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.SpProp.Builder().setValue(sizeSp).build())
                    .setColor(ColorBuilders.ColorProp.Builder(color).build())
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .build()
            )
            .build()

    private fun spacer(heightDp: Float) =
        LayoutElementBuilders.Spacer.Builder()
            .setHeight(DimensionBuilders.DpProp.Builder(heightDp).build())
            .build()
}
