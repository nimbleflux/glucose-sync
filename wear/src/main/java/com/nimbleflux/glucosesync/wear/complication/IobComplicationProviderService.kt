package com.nimbleflux.glucosesync.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.nimbleflux.glucosesync.wear.R
import com.nimbleflux.glucosesync.wear.repository.GlucoseRepository
import com.nimbleflux.glucosesync.wear.ui.MainActivity

class IobComplicationProviderService : ComplicationDataSourceService() {

    private val repo by lazy { GlucoseRepository.getInstance(this) }

    private fun tapAction(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    private val dropletMono by lazy {
        MonochromaticImage.Builder(ComplicationIcons.dropletIcon(this)).build()
    }

    private val dropletSmall by lazy {
        SmallImage.Builder(ComplicationIcons.dropletIcon(this), SmallImageType.ICON).build()
    }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val iob = repo.state.value.iob

        if (iob == null) {
            val data = when (request.complicationType) {
                ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("--").build(),
                    contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_iob, "--")).build()
                ).setMonochromaticImage(dropletMono)
                    .setTapAction(tapAction()).build()
                ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("-- U").build(),
                    contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_iob, "--")).build()
                ).setTitle(
                    PlainComplicationText.Builder(getString(R.string.complication_title_iob)).build()
                ).setSmallImage(dropletSmall)
                    .setTapAction(tapAction()).build()
                else -> NoDataComplicationData()
            }
            listener.onComplicationData(data)
            return
        }

        val iobText = String.format("%.1f", iob)

        val data = when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(iobText).build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_iob, iobText)).build()
            ).setMonochromaticImage(dropletMono)
                .setTapAction(tapAction()).build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("$iobText U").build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_iob, iobText)).build()
            ).setTitle(
                PlainComplicationText.Builder(getString(R.string.complication_title_iob)).build()
            ).setSmallImage(dropletSmall)
                .setTapAction(tapAction()).build()

            else -> {
                listener.onComplicationData(NoDataComplicationData())
                return
            }
        }
        listener.onComplicationData(data)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("1.2").build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_iob, "1.2")).build()
            ).setMonochromaticImage(dropletMono).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("1.2 U").build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_iob, "1.2")).build()
            ).setTitle(
                PlainComplicationText.Builder(getString(R.string.complication_title_iob)).build()
            ).setSmallImage(dropletSmall).build()
            else -> NoDataComplicationData()
        }
    }
}
