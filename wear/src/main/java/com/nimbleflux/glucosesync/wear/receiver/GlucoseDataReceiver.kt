package com.nimbleflux.glucosesync.wear.receiver

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.WearableListenerService
import com.nimbleflux.glucosesync.wear.repository.GlucoseRepository

class GlucoseDataReceiver : WearableListenerService() {

    private val repo by lazy { GlucoseRepository.getInstance(this) }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/glucose") {
                val data = event.dataItem.data ?: continue
                val map = DataMap.fromByteArray(data)
                val glucose = map.getDouble("glucose")
                val timestamp = map.getLong("timestamp")
                val trend = map.getString("trend", "")
                val unit = map.getString("unit", "mmol/L")

                Log.d(TAG, "Received glucose: $glucose $unit at $timestamp")

                repo.saveGlucose(
                    glucose = glucose,
                    timestamp = timestamp,
                    trend = trend,
                    unit = unit,
                    iob = if (map.getDouble("iob", -1.0) >= 0) map.getDouble("iob") else null,
                    delta = if (map.getDouble("delta", -999.0) > -998) map.getDouble("delta") else null,
                    batteryPercent = if (map.getDouble("batteryPercent", -1.0) >= 0) map.getDouble("batteryPercent") else null,
                    basalRate = if (map.getDouble("basalRate", -1.0) >= 0) map.getDouble("basalRate") else null,
                    lastBolus = if (map.getDouble("lastBolus", -1.0) >= 0) map.getDouble("lastBolus") else null,
                    lastBolusTime = if (map.getLong("lastBolusTime", 0) > 0) map.getLong("lastBolusTime") else null,
                    remainingDose = if (map.getDouble("remainingDose", -1.0) >= 0) map.getDouble("remainingDose") else null,
                    highThreshold = if (map.getDouble("highThreshold", -1.0) >= 0) map.getDouble("highThreshold") else null,
                    lowThreshold = if (map.getDouble("lowThreshold", -1.0) >= 0) map.getDouble("lowThreshold") else null,
                    timeInRange = if (map.getDouble("timeInRange", -1.0) >= 0) map.getDouble("timeInRange") else null,
                    averageGlucose = if (map.getDouble("averageGlucose", -1.0) >= 0) map.getDouble("averageGlucose") else null
                )
            }
        }
    }

    companion object {
        private const val TAG = "GlucoseDataReceiver"
    }
}
