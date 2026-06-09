package com.nimbleflux.glucosesync.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.nimbleflux.glucosesync.wear.R
import com.nimbleflux.glucosesync.wear.repository.GlucoseRepository
import com.nimbleflux.glucosesync.wear.ui.MainActivity

class GlucoseComplicationProviderService : ComplicationDataSourceService() {

    private val repo by lazy { GlucoseRepository(this) }

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("complication_config", MODE_PRIVATE)
    }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val glucose = repo.getGlucose()
        val trend = repo.getTrend()
        val unit = repo.getUnit()
        val stale = repo.isStale()

        if (glucose <= 0f) {
            listener.onComplicationData(NoDataComplicationData())
            return
        }

        val highMmol = prefs.getFloat("high_threshold_mmol", 10.0f)
        val lowMmol = prefs.getFloat("low_threshold_mmol", 3.9f)
        val isMmol = unit == "mmol/L"
        val maxVal = if (isMmol) highMmol * 1.5f else highMmol * 1.5f * 18f
        val highVal = if (isMmol) highMmol else highMmol * 18f
        val lowVal = if (isMmol) lowMmol else lowMmol * 18f
        val midVal = (highVal + lowVal) / 2f
        val displayGlucose = if (isMmol) glucose else glucose * 18f

        val glucoseText = String.format("%.1f", displayGlucose)
        val glucoseContentDesc = getString(R.string.complication_content_description_glucose, glucoseText, unit)
        val displayText = if (trend.isNotEmpty()) "$glucoseText $trend" else glucoseText

        val tapAction = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val data = when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(displayText).build(),
                contentDescription = PlainComplicationText.Builder(glucoseContentDesc).build()
            ).setTapAction(tapAction).build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(displayText).build(),
                contentDescription = PlainComplicationText.Builder(glucoseContentDesc).build()
            ).setTitle(
                PlainComplicationText.Builder(
                    if (stale) getString(R.string.glucose_stale) else getString(R.string.complication_title_glucose)
                ).build()
            ).setTapAction(tapAction).build()

            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = displayGlucose,
                min = 0.0f,
                max = maxVal,
                contentDescription = PlainComplicationText.Builder(glucoseContentDesc).build()
            ).setText(
                PlainComplicationText.Builder(displayText).build()
            ).setTapAction(tapAction).build()

            ComplicationType.GOAL_PROGRESS -> GoalProgressComplicationData.Builder(
                value = displayGlucose,
                targetValue = midVal,
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_content_description_glucose_range, glucoseText)).build()
            ).setText(
                PlainComplicationText.Builder(displayText).build()
            ).setTapAction(tapAction).build()

            ComplicationType.WEIGHTED_ELEMENTS -> {
                val inRange = displayGlucose in lowVal..highVal
                val color = if (inRange) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()
                val elements = listOf(
                    WeightedElementsComplicationData.Element(1.0f, color)
                )
                WeightedElementsComplicationData.Builder(
                    elements = elements,
                    contentDescription = PlainComplicationText.Builder(glucoseContentDesc).build()
                ).setText(
                    PlainComplicationText.Builder(displayText).build()
                ).setTapAction(tapAction).build()
            }

            else -> {
                listener.onComplicationData(NoDataComplicationData())
                return
            }
        }

        listener.onComplicationData(data)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        val previewText = getString(R.string.complication_preview_glucose_text)
        val previewDesc = getString(R.string.complication_preview_glucose_content_description)
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(previewText).build(),
                contentDescription = PlainComplicationText.Builder(previewDesc).build()
            ).build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = 5.6f,
                min = 0.0f,
                max = 22.0f,
                contentDescription = PlainComplicationText.Builder(previewDesc).build()
            ).setText(PlainComplicationText.Builder(previewText).build()).build()
            ComplicationType.GOAL_PROGRESS -> GoalProgressComplicationData.Builder(
                value = 5.6f,
                targetValue = 7.0f,
                contentDescription = PlainComplicationText.Builder(previewDesc).build()
            ).setText(PlainComplicationText.Builder(previewText).build()).build()
            ComplicationType.WEIGHTED_ELEMENTS -> WeightedElementsComplicationData.Builder(
                elements = listOf(WeightedElementsComplicationData.Element(1.0f, 0xFF4CAF50.toInt())),
                contentDescription = PlainComplicationText.Builder(previewDesc).build()
            ).setText(PlainComplicationText.Builder(previewText).build()).build()
            else -> NoDataComplicationData()
        }
    }
}
