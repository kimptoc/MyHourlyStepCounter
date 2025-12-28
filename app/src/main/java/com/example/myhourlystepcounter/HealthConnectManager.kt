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
        const val PREFERRED_DATA_SOURCE = "com.sec.android.app.shealth" // Samsung Health
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

        android.util.Log.d(TAG, "====== FETCHING HOURLY STEPS ======")
        android.util.Log.d(TAG, "Start time: $startOfHour -> $startInstant")
        android.util.Log.d(TAG, "End time: ${startOfHour.plusHours(1)} -> $endInstant")

        // Manually calculate from individual records (aggregate API has bugs)
        var manualTotal = 0L
        try {
            val readRequest = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
            )
            val recordsResponse = healthConnectClient.readRecords(readRequest)

            android.util.Log.d(TAG, "Found ${recordsResponse.records.size} individual step records")
            recordsResponse.records.forEach { record ->
                val source = record.metadata.dataOrigin.packageName
                // Only count records from Samsung Health
                if (source == PREFERRED_DATA_SOURCE) {
                    manualTotal += record.count
                    android.util.Log.d(TAG, "  + ${record.count} steps from $source")
                } else {
                    android.util.Log.d(TAG, "  Skipping ${record.count} steps from $source (not preferred source)")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error reading individual records", e)
        }

        android.util.Log.d(TAG, "Manual total for hour $startOfHour: $manualTotal steps")
        android.util.Log.d(TAG, "===================================")

        return manualTotal
    }

    suspend fun getStepsForDay(date: LocalDateTime): Long {
        val zoneId = ZoneId.systemDefault()
        val startOfDay = date.toLocalDate().atStartOfDay()
        val startInstant = startOfDay.atZone(zoneId).toInstant()
        val endInstant = startOfDay.plusDays(1).atZone(zoneId).toInstant()

        android.util.Log.d(TAG, "====== FETCHING DAILY STEPS ======")
        android.util.Log.d(TAG, "Start of day: $startOfDay -> $startInstant")
        android.util.Log.d(TAG, "End of day: ${startOfDay.plusDays(1)} -> $endInstant")

        // Manually calculate from all individual records with pagination handling
        var manualTotal = 0L
        val allRecords = mutableListOf<StepsRecord>()
        try {
            var pageToken: String? = null
            var pageNum = 1

            do {
                val readRequest = ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant),
                    pageToken = pageToken
                )
                val recordsResponse = healthConnectClient.readRecords(readRequest)

                android.util.Log.d(TAG, "Page $pageNum: ${recordsResponse.records.size} records")
                allRecords.addAll(recordsResponse.records)
                pageToken = recordsResponse.pageToken
                pageNum++
            } while (pageToken != null)

            android.util.Log.d(TAG, "Total records across all pages: ${allRecords.size}")

            // Use a Set to track unique record IDs to avoid double-counting
            val uniqueRecordIds = mutableSetOf<String>()

            // Group by data source for analysis
            val stepsBySource = mutableMapOf<String, Long>()

            allRecords.forEach { record ->
                val recordId = record.metadata.id
                val source = record.metadata.dataOrigin.packageName

                if (uniqueRecordIds.add(recordId)) {
                    // Track all sources for analysis
                    stepsBySource[source] = (stepsBySource[source] ?: 0L) + record.count

                    // Only count records from Samsung Health
                    if (source == PREFERRED_DATA_SOURCE) {
                        manualTotal += record.count

                        // Log first 10 and last 10 records for analysis
                        if (manualTotal <= 10 * 100 || uniqueRecordIds.size > allRecords.size - 10) {
                            android.util.Log.d(TAG, "  Record #${uniqueRecordIds.size}: ${record.count} steps from $source at ${record.startTime.atZone(zoneId)} to ${record.endTime.atZone(zoneId)}")
                        }
                    }
                } else {
                    android.util.Log.w(TAG, "Duplicate record detected: $recordId")
                }
            }

            android.util.Log.d(TAG, "Unique records: ${uniqueRecordIds.size}")
            android.util.Log.d(TAG, "Steps by data source (all sources):")
            stepsBySource.forEach { (source, steps) ->
                val status = if (source == PREFERRED_DATA_SOURCE) "COUNTED" else "IGNORED"
                android.util.Log.d(TAG, "  $source: $steps steps [$status]")
            }
            android.util.Log.d(TAG, "Manual total for day (Samsung Health only): $manualTotal steps")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error reading daily records", e)
        }

        android.util.Log.d(TAG, "==================================")

        return manualTotal
    }

    suspend fun getHourlyStepsForDay(date: LocalDateTime): Map<Int, Long> {
        val zoneId = ZoneId.systemDefault()
        val startOfDay = date.toLocalDate().atStartOfDay()
        val hourlySteps = mutableMapOf<Int, Long>()

        // Get all records for the day and manually organize by hour with pagination
        try {
            val startInstant = startOfDay.atZone(zoneId).toInstant()
            val endInstant = startOfDay.plusDays(1).atZone(zoneId).toInstant()

            val uniqueRecordIds = mutableSetOf<String>()
            var pageToken: String? = null

            do {
                val readRequest = ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant),
                    pageToken = pageToken
                )
                val recordsResponse = healthConnectClient.readRecords(readRequest)

                // Group steps by hour, avoiding duplicates, only from Samsung Health
                recordsResponse.records.forEach { record ->
                    val recordId = record.metadata.id
                    val source = record.metadata.dataOrigin.packageName

                    if (uniqueRecordIds.add(recordId) && source == PREFERRED_DATA_SOURCE) {
                        val recordHour = record.startTime.atZone(zoneId).hour
                        hourlySteps[recordHour] = (hourlySteps[recordHour] ?: 0L) + record.count
                    }
                }

                pageToken = recordsResponse.pageToken
            } while (pageToken != null)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error reading hourly records for day", e)
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
