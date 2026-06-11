package com.nimbleflux.glucosesync.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.nimbleflux.glucosesync.wear.R
import com.nimbleflux.glucosesync.wear.repository.GlucoseRepository
import com.nimbleflux.glucosesync.wear.ui.MainActivity

class DeltaComplicationProviderService : ComplicationDataSourceService() {

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
        val delta = state.delta
        val trend = state.trend
        val unit = state.unit

        val trendImg = ComplicationIcons.trendIcon(this, trend)

        if (delta == null) {
            val data = when (request.complicationType) {
                ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("--").build(),
                    contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_delta, "--")).build()
                ).apply { trendImg?.let { setMonochromaticImage(MonochromaticImage.Builder(it).build()) } }
                    .setTapAction(tapAction()).build()
                ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("--").build(),
                    contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_delta, "--")).build()
                ).setTitle(
                    PlainComplicationText.Builder(getString(R.string.complication_title_delta)).build()
                ).apply { trendImg?.let { setSmallImage(SmallImage.Builder(it, SmallImageType.ICON).build()) } }
                    .setTapAction(tapAction()).build()
                else -> NoDataComplicationData()
            }
            listener.onComplicationData(data)
            return
        }

        val sign = if (delta >= 0) "+" else ""
        val deltaText = "$sign${String.format("%.1f", delta)}"

        val data = when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(deltaText).build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_delta, deltaText)).build()
            ).apply { trendImg?.let { setMonochromaticImage(MonochromaticImage.Builder(it).build()) } }
                .setTapAction(tapAction()).build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("$deltaText $unit").build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_delta, deltaText)).build()
            ).setTitle(
                PlainComplicationText.Builder(getString(R.string.complication_title_delta)).build()
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
                text = PlainComplicationText.Builder("+0.3").build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_delta, "+0.3")).build()
            ).apply { trendImg?.let { setMonochromaticImage(MonochromaticImage.Builder(it).build()) } }.build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("+0.3 mmol/L").build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_delta, "+0.3")).build()
            ).setTitle(
                PlainComplicationText.Builder(getString(R.string.complication_title_delta)).build()
            ).apply { trendImg?.let { setSmallImage(SmallImage.Builder(it, SmallImageType.ICON).build()) } }.build()
            else -> NoDataComplicationData()
        }
    }
}
