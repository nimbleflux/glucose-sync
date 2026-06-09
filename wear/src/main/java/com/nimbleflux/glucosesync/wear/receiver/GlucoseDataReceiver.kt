package com.nimbleflux.glucosesync.wear.receiver

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.WearableListenerService
import com.nimbleflux.glucosesync.wear.repository.GlucoseRepository

class GlucoseDataReceiver : WearableListenerService() {

    private val repo by lazy { GlucoseRepository(this) }

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

                repo.saveGlucose(glucose, timestamp, trend, unit)
            }
        }
    }

    companion object {
        private const val TAG = "GlucoseDataReceiver"
    }
}
