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

    private fun trendMono(trend: String) =
        MonochromaticImage.Builder(ComplicationIcons.trendIcon(this, trend)).build()

    private fun trendSmall(trend: String) =
        SmallImage.Builder(ComplicationIcons.trendIcon(this, trend), SmallImageType.ICON).build()

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val state = repo.state.value
        val delta = state.delta
        val trend = state.trend
        val unit = state.unit

        if (delta == null) {
            listener.onComplicationData(NoDataComplicationData())
            return
        }

        val sign = if (delta >= 0) "+" else ""
        val deltaText = "$sign${String.format("%.1f", delta)}"

        val data = when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(deltaText).build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_delta, deltaText)).build()
            ).setMonochromaticImage(trendMono(trend))
                .setTapAction(tapAction()).build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("$deltaText $unit").build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_delta, deltaText)).build()
            ).setTitle(
                PlainComplicationText.Builder(getString(R.string.complication_title_delta)).build()
            ).setSmallImage(trendSmall(trend))
                .setTapAction(tapAction()).build()

            else -> {
                listener.onComplicationData(NoDataComplicationData())
                return
            }
        }
        listener.onComplicationData(data)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        val mono = trendMono("\u2192")
        val small = trendSmall("\u2192")
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("+0.3").build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_delta, "+0.3")).build()
            ).setMonochromaticImage(mono).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("+0.3 mmol/L").build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_delta, "+0.3")).build()
            ).setTitle(
                PlainComplicationText.Builder(getString(R.string.complication_title_delta)).build()
            ).setSmallImage(small).build()
            else -> NoDataComplicationData()
        }
    }
}
