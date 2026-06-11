package com.nimbleflux.glucosesync.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.nimbleflux.glucosesync.wear.R
import com.nimbleflux.glucosesync.wear.repository.GlucoseRepository
import com.nimbleflux.glucosesync.wear.ui.MainActivity

class BatteryComplicationProviderService : ComplicationDataSourceService() {

    private val repo by lazy { GlucoseRepository.getInstance(this) }

    private fun tapAction(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    private val batteryMono by lazy {
        MonochromaticImage.Builder(ComplicationIcons.batteryIcon(this)).build()
    }

    private val batterySmall by lazy {
        SmallImage.Builder(ComplicationIcons.batteryIcon(this), SmallImageType.ICON).build()
    }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val battery = repo.state.value.batteryPercent

        if (battery == null) {
            val data = when (request.complicationType) {
                ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("91%").build(),
                    contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_battery, "91")).build()
                ).setMonochromaticImage(batteryMono)
                    .setTapAction(tapAction()).build()
                ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("91%").build(),
                    contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_battery, "91")).build()
                ).setTitle(
                    PlainComplicationText.Builder(getString(R.string.complication_title_battery)).build()
                ).setSmallImage(batterySmall)
                    .setTapAction(tapAction()).build()
                ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                    value = 0.91f,
                    min = 0.0f,
                    max = 1.0f,
                    contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_battery, "91")).build()
                ).setText(PlainComplicationText.Builder("91%").build())
                    .setMonochromaticImage(batteryMono)
                    .setTapAction(tapAction()).build()
                else -> NoDataComplicationData()
            }
            listener.onComplicationData(data)
            return
        }

        val pctText = (battery * 100).toInt().toString()

        val data = when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("$pctText%").build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_battery, pctText)).build()
            ).setMonochromaticImage(batteryMono)
                .setTapAction(tapAction()).build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("$pctText%").build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_battery, pctText)).build()
            ).setTitle(
                PlainComplicationText.Builder(getString(R.string.complication_title_battery)).build()
            ).setSmallImage(batterySmall)
                .setTapAction(tapAction()).build()

            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = battery.toFloat(),
                min = 0.0f,
                max = 1.0f,
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_battery, pctText)).build()
            ).setText(PlainComplicationText.Builder("$pctText%").build())
                .setMonochromaticImage(batteryMono)
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
                text = PlainComplicationText.Builder("91%").build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_battery, "91")).build()
            ).setMonochromaticImage(batteryMono).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("91%").build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_battery, "91")).build()
            ).setTitle(
                PlainComplicationText.Builder(getString(R.string.complication_title_battery)).build()
            ).setSmallImage(batterySmall).build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = 0.91f,
                min = 0.0f,
                max = 1.0f,
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_battery, "91")).build()
            ).setText(PlainComplicationText.Builder("91%").build())
                .setMonochromaticImage(batteryMono).build()
            else -> NoDataComplicationData()
        }
    }
}
