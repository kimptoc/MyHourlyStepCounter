package com.example.myhourlystepcounter

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDateTime

/**
 * Background worker that periodically syncs step data from Health Connect.
 * This ensures data stays up-to-date even when the app is backgrounded.
 */
class StepCountWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        android.util.Log.d(TAG, "StepCountWorker started")

        return try {
            val healthConnectManager = HealthConnectManager(applicationContext)

            // Check if Health Connect is available and we have permissions
            if (!healthConnectManager.isAvailable()) {
                android.util.Log.w(TAG, "Health Connect not available, skipping sync")
                return Result.success()
            }

            if (!healthConnectManager.hasAllPermissions()) {
                android.util.Log.w(TAG, "Missing permissions, skipping sync")
                return Result.success()
            }

            // Fetch latest data to keep cache fresh
            val now = LocalDateTime.now()
            val startOfCurrentHour = now.withMinute(0).withSecond(0).withNano(0)

            // Fetch current hour and daily steps (this updates Health Connect's internal cache)
            val hourlySteps = healthConnectManager.getStepsForHour(startOfCurrentHour)
            val dailySteps = healthConnectManager.getStepsForDay(now)

            android.util.Log.d(TAG, "Background sync complete - Hour: $hourlySteps, Day: $dailySteps")

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error during background sync", e)
            // Retry with exponential backoff
            Result.retry()
        }
    }

    companion object {
        const val TAG = "StepCountWorker"
        const val WORK_NAME = "StepCountBackgroundSync"
    }
}
