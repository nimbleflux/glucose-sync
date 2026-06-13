package com.nimbleflux.glucosesync.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import com.nimbleflux.glucosesync.app.BuildConfig

class PollingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (BuildConfig.DEBUG) Log.d(TAG, "WorkManager fallback: ensuring polling service is running")
        try {
            val intent = Intent(applicationContext, GlucosePollingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Failed to start polling service: ${e.message}")
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "PollingWorker"
        private const val WORK_NAME = "glucose_polling_keepalive"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PollingWorker>(
                15, java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
