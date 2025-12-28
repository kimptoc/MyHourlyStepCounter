package com.example.myhourlystepcounter

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class HealthConnectManager(private val context: Context) {
    private val healthConnectClient by lazy {
        android.util.Log.d("HealthConnectManager", "Creating HealthConnectClient...")
        HealthConnectClient.getOrCreate(context)
    }

    companion object {
        const val TAG = "HealthConnectManager"
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
        )
    }

    suspend fun hasAllPermissions(): Boolean {
        android.util.Log.d(TAG, "Checking permissions...")
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        android.util.Log.d(TAG, "Granted permissions: $granted")
        android.util.Log.d(TAG, "Required permissions: $PERMISSIONS")
        val hasAll = granted.containsAll(PERMISSIONS)
        android.util.Log.d(TAG, "Has all permissions: $hasAll")
        return hasAll
    }

    suspend fun getStepsForHour(startOfHour: LocalDateTime): Long {
        val zoneId = ZoneId.systemDefault()
        val startInstant = startOfHour.atZone(zoneId).toInstant()
        val endInstant = startOfHour.plusHours(1).atZone(zoneId).toInstant()

        val response = healthConnectClient.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
            )
        )

        return response[StepsRecord.COUNT_TOTAL] ?: 0L
    }

    suspend fun getStepsForDay(date: LocalDateTime): Long {
        val zoneId = ZoneId.systemDefault()
        val startOfDay = date.toLocalDate().atStartOfDay()
        val startInstant = startOfDay.atZone(zoneId).toInstant()
        val endInstant = startOfDay.plusDays(1).atZone(zoneId).toInstant()

        val response = healthConnectClient.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
            )
        )

        return response[StepsRecord.COUNT_TOTAL] ?: 0L
    }

    suspend fun getHourlyStepsForDay(date: LocalDateTime): Map<Int, Long> {
        val zoneId = ZoneId.systemDefault()
        val startOfDay = date.toLocalDate().atStartOfDay()
        val hourlySteps = mutableMapOf<Int, Long>()

        for (hour in 0 until 24) {
            val startOfHour = startOfDay.plusHours(hour.toLong())
            val startInstant = startOfHour.atZone(zoneId).toInstant()
            val endInstant = startOfHour.plusHours(1).atZone(zoneId).toInstant()

            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
                )
            )

            val steps = response[StepsRecord.COUNT_TOTAL] ?: 0L
            if (steps > 0) {
                hourlySteps[hour] = steps
            }
        }

        return hourlySteps
    }

    fun isAvailable(): Boolean {
        android.util.Log.d(TAG, "Checking Health Connect availability...")
        val status = HealthConnectClient.getSdkStatus(context)
        android.util.Log.d(TAG, "SDK Status raw value: $status")

        // Check if Health Connect is available as framework module (Android 14+)
        val isFrameworkInstalled = try {
            context.packageManager.getPackageInfo("com.android.healthconnect.controller", 0)
            true
        } catch (e: Exception) {
            false
        }
        android.util.Log.d(TAG, "Framework Health Connect installed: $isFrameworkInstalled")

        // Check if Health Connect app is installed (Android 13 and below)
        val isAppInstalled = try {
            context.packageManager.getPackageInfo("com.google.android.apps.healthdata", 0)
            true
        } catch (e: Exception) {
            false
        }
        android.util.Log.d(TAG, "Standalone Health Connect app installed: $isAppInstalled")

        when (status) {
            HealthConnectClient.SDK_AVAILABLE -> android.util.Log.d(TAG, "Status: SDK_AVAILABLE")
            HealthConnectClient.SDK_UNAVAILABLE -> android.util.Log.d(TAG, "Status: SDK_UNAVAILABLE - Health Connect not installed")
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> android.util.Log.d(TAG, "Status: SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED - Update needed")
            else -> android.util.Log.d(TAG, "Status: UNKNOWN ($status)")
        }

        return status == HealthConnectClient.SDK_AVAILABLE
    }

    fun getSdkStatus(): Int {
        return HealthConnectClient.getSdkStatus(context)
    }

    fun getInstallUri(): android.net.Uri? {
        val status = HealthConnectClient.getSdkStatus(context)
        android.util.Log.d(TAG, "Getting install URI for status: $status")
        return when (status) {
            HealthConnectClient.SDK_UNAVAILABLE -> {
                android.util.Log.d(TAG, "Returning install URI for unavailable SDK")
                android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata")
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                android.util.Log.d(TAG, "Returning update URI for outdated provider")
                android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata")
            }
            else -> {
                android.util.Log.d(TAG, "SDK available, no install URI needed")
                null
            }
        }
    }

    fun needsUpdate(): Boolean {
        val status = HealthConnectClient.getSdkStatus(context)
        return status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
    }
}
