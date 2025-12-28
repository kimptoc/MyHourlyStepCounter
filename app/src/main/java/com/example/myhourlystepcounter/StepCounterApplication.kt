package com.example.myhourlystepcounter

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class StepCounterApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d(TAG, "Application started, scheduling background work")
        scheduleStepCountSync()
    }

    private fun scheduleStepCountSync() {
        // Create constraints - only run when device is not in battery saver mode
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // We don't need network for Health Connect
            .build()

        // Create periodic work request - runs every 15 minutes (minimum interval)
        val workRequest = PeriodicWorkRequestBuilder<StepCountWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES // Flex interval - can run between 10-15 minutes
        )
            .setConstraints(constraints)
            .addTag(StepCountWorker.TAG)
            .build()

        // Schedule the work (replace any existing work)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            StepCountWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing if already scheduled
            workRequest
        )

        android.util.Log.d(TAG, "Background sync scheduled: every 15 minutes")
    }

    companion object {
        const val TAG = "StepCounterApp"
    }
}
