package com.nimbleflux.glucosesync.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.nimbleflux.glucosesync.wear.R
import com.nimbleflux.glucosesync.wear.repository.GlucoseRepository
import com.nimbleflux.glucosesync.wear.ui.MainActivity

class GlucoseDeltaComplicationProviderService : ComplicationDataSourceService() {

    private val repo by lazy { GlucoseRepository.getInstance(this) }

    private fun tapAction(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val state = repo.state.value
        val glucose = state.glucose
        val trend = state.trend
        val delta = state.delta
        val unit = state.unit

        if (glucose <= 0.0) {
            val trendImg = ComplicationIcons.trendIcon(this, trend)
            val data = when (request.complicationType) {
                ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("--").build(),
                    contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_glucose, "--", unit)).build()
                ).apply { trendImg?.let { setMonochromaticImage(MonochromaticImage.Builder(it).build()) } }
                    .setTapAction(tapAction()).build()
                ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("--").build(),
                    contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_glucose, "--", unit)).build()
                ).setTitle(
                    PlainComplicationText.Builder(getString(R.string.complication_title_glucose)).build()
                ).apply { trendImg?.let { setSmallImage(SmallImage.Builder(it, SmallImageType.ICON).build()) } }
                    .setTapAction(tapAction()).build()
                else -> NoDataComplicationData()
            }
            listener.onComplicationData(data)
            return
        }

        val glucoseText = String.format("%.1f", glucose)
        val sign = if (delta != null && delta >= 0) "+" else ""
        val deltaPart = if (delta != null) " (${sign}${String.format("%.1f", delta)})" else ""
        val displayText = "$glucoseText$deltaPart"

        val trendImg = ComplicationIcons.trendIcon(this, trend)

        val data = when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(displayText).build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_glucose_delta, glucoseText, unit, deltaPart)).build()
            ).apply { trendImg?.let { setMonochromaticImage(MonochromaticImage.Builder(it).build()) } }
                .setTapAction(tapAction()).build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("$displayText $unit").build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_glucose_delta, glucoseText, unit, deltaPart)).build()
            ).setTitle(
                PlainComplicationText.Builder(getString(R.string.complication_title_glucose)).build()
            ).apply { trendImg?.let { setSmallImage(SmallImage.Builder(it, SmallImageType.ICON).build()) } }
                .setTapAction(tapAction()).build()

            else -> {
                listener.onComplicationData(NoDataComplicationData())
                return
            }
        }
        listener.onComplicationData(data)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        val trendImg = ComplicationIcons.trendIcon(this, "\u2192")
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("5.6 (+0.3)").build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_glucose_delta, "5.6", "mmol/L", " +0.3")).build()
            ).apply { trendImg?.let { setMonochromaticImage(MonochromaticImage.Builder(it).build()) } }.build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("5.6 (+0.3) mmol/L").build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_glucose_delta, "5.6", "mmol/L", " +0.3")).build()
            ).setTitle(
                PlainComplicationText.Builder(getString(R.string.complication_title_glucose)).build()
            ).apply { trendImg?.let { setSmallImage(SmallImage.Builder(it, SmallImageType.ICON).build()) } }.build()
            else -> NoDataComplicationData()
        }
    }
}
