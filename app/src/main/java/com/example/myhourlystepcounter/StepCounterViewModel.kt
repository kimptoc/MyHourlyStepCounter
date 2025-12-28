package com.example.myhourlystepcounter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class HourlyStepRecord(
    val hour: Int,
    val steps: Long,
    val hourLabel: String
)

data class StepCounterState(
    val currentDateTime: String = "",
    val hourlySteps: Long = 0,
    val dailySteps: Long = 0,
    val healthConnectAvailable: Boolean = false,
    val healthConnectNeedsUpdate: Boolean = false,
    val healthConnectInstallUri: android.net.Uri? = null,
    val permissionsGranted: Boolean = false,
    val stepHistory: List<HourlyStepRecord> = emptyList()
)

class StepCounterViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(StepCounterState())
    val state: StateFlow<StepCounterState> = _state.asStateFlow()

    private val healthConnectManager = HealthConnectManager(application)
    private var timeUpdateJob: Job? = null
    private var stepUpdateJob: Job? = null

    init {
        android.util.Log.d("StepCounterViewModel", "===== VIEWMODEL INIT STARTED =====")

        // Check if Health Connect is available
        android.util.Log.d("StepCounterViewModel", "About to check Health Connect availability...")
        val isAvailable = healthConnectManager.isAvailable()
        val needsUpdate = healthConnectManager.needsUpdate()
        val installUri = healthConnectManager.getInstallUri()
        android.util.Log.d("StepCounterViewModel", "Health Connect available: $isAvailable")
        android.util.Log.d("StepCounterViewModel", "Needs update: $needsUpdate")
        android.util.Log.d("StepCounterViewModel", "Install URI: $installUri")
        _state.value = _state.value.copy(
            healthConnectAvailable = isAvailable,
            healthConnectNeedsUpdate = needsUpdate,
            healthConnectInstallUri = installUri
        )

        // Check permissions
        viewModelScope.launch {
            try {
                val hasPermissions = healthConnectManager.hasAllPermissions()
                android.util.Log.d("StepCounterViewModel", "Has permissions: $hasPermissions")
                _state.value = _state.value.copy(permissionsGranted = hasPermissions)
            } catch (e: Exception) {
                android.util.Log.e("StepCounterViewModel", "Error checking permissions", e)
            }
        }

        // Start time and step update coroutines
        startUpdates()
    }

    private fun updateDateTime() {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedDateTime = now.format(formatter)

        _state.value = _state.value.copy(currentDateTime = formattedDateTime)
    }

    private suspend fun updateStepCounts() {
        if (!_state.value.permissionsGranted || !_state.value.healthConnectAvailable) {
            return
        }

        try {
            val now = LocalDateTime.now()
            val startOfCurrentHour = now.withMinute(0).withSecond(0).withNano(0)

            // Get current hour steps
            val hourlySteps = healthConnectManager.getStepsForHour(startOfCurrentHour)

            // Get daily total steps
            val dailySteps = healthConnectManager.getStepsForDay(now)

            // Get hourly history for the day
            val hourlyData = healthConnectManager.getHourlyStepsForDay(now)
            val history = buildHistoryList(hourlyData, now.hour)

            _state.value = _state.value.copy(
                hourlySteps = hourlySteps,
                dailySteps = dailySteps,
                stepHistory = history
            )
        } catch (e: Exception) {
            // Handle errors gracefully
            e.printStackTrace()
        }
    }

    private fun buildHistoryList(hourlyData: Map<Int, Long>, currentHour: Int): List<HourlyStepRecord> {
        val history = mutableListOf<HourlyStepRecord>()

        // Add hours from previous hour back to midnight (most recent first)
        for (hour in (currentHour - 1) downTo 0) {
            val steps = hourlyData[hour] ?: 0L
            if (steps > 0) {
                val hourLabel = String.format("%02d:00", hour)
                history.add(HourlyStepRecord(hour, steps, hourLabel))
            }
        }

        return history
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            try {
                android.util.Log.d("StepCounterViewModel", "Refreshing permissions...")
                val hasPermissions = healthConnectManager.hasAllPermissions()
                android.util.Log.d("StepCounterViewModel", "Permissions granted: $hasPermissions")
                _state.value = _state.value.copy(permissionsGranted = hasPermissions)

                if (hasPermissions) {
                    // Immediately update step counts after getting permissions
                    android.util.Log.d("StepCounterViewModel", "Updating step counts...")
                    updateStepCounts()
                }
            } catch (e: Exception) {
                android.util.Log.e("StepCounterViewModel", "Error refreshing permissions", e)
            }
        }
    }

    private fun startUpdates() {
        // Start time update coroutine (updates every second for time display)
        timeUpdateJob = viewModelScope.launch {
            while (true) {
                updateDateTime()
                delay(1000) // Update every second
            }
        }

        // Start step count update coroutine (updates every 5 seconds)
        stepUpdateJob = viewModelScope.launch {
            while (true) {
                delay(5000) // Update every 5 seconds
                updateStepCounts()
            }
        }
    }

    fun pauseUpdates() {
        android.util.Log.d("StepCounterViewModel", "Pausing updates (app backgrounded)")
        timeUpdateJob?.cancel()
        stepUpdateJob?.cancel()
    }

    fun resumeUpdates() {
        android.util.Log.d("StepCounterViewModel", "Resuming updates (app foregrounded)")
        if (timeUpdateJob?.isActive != true || stepUpdateJob?.isActive != true) {
            startUpdates()
        }
    }
}
