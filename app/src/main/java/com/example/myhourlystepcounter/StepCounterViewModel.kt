package com.example.myhourlystepcounter

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class HourlyStepRecord(
    val hour: Int,
    val steps: Int,
    val hourLabel: String
)

data class StepCounterState(
    val currentDateTime: String = "",
    val hourlySteps: Int = 0,
    val sensorAvailable: Boolean = false,
    val stepHistory: List<HourlyStepRecord> = emptyList()
)

class StepCounterViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {
    private val _state = MutableStateFlow(StepCounterState())
    val state: StateFlow<StepCounterState> = _state.asStateFlow()

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private var initialStepCount: Int? = null
    private var lastHour: Int = -1
    private var lastDay: Int = -1
    private var hourStartStepCount: Int? = null
    private val hourlyStepMap = mutableMapOf<Int, Int>() // Map of hour to step count
    private var latestStepCount: Int = 0 // Latest step count from sensor

    init {
        // Check if step counter sensor is available
        _state.value = _state.value.copy(sensorAvailable = stepSensor != null)

        // Register sensor listener
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // Start time update coroutine (updates every second for time display)
        viewModelScope.launch {
            while (true) {
                updateDateTime()
                delay(1000) // Update every second
            }
        }

        // Start step count update coroutine (updates every 5 seconds)
        viewModelScope.launch {
            while (true) {
                delay(5000) // Update every 5 seconds
                updateStepCount()
            }
        }
    }

    private fun updateDateTime() {
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedDateTime = now.format(formatter)

        val currentHour = now.hour
        val currentDay = now.dayOfYear

        // Check if day has changed - clear history for new day
        if (lastDay != -1 && lastDay != currentDay) {
            hourlyStepMap.clear()
            lastDay = currentDay
        } else if (lastDay == -1) {
            lastDay = currentDay
        }

        // Check if hour has changed
        if (lastHour != currentHour && lastHour != -1) {
            // Save the previous hour's step count to history
            val previousHourSteps = _state.value.hourlySteps
            hourlyStepMap[lastHour] = previousHourSteps

            // New hour started, reset hourly step count
            hourStartStepCount = initialStepCount

            // Update history list
            updateStepHistory(currentHour)
        }

        lastHour = currentHour

        _state.value = _state.value.copy(currentDateTime = formattedDateTime)
    }

    private fun updateStepHistory(currentHour: Int) {
        // Build history list from stored hourly steps (most recent first)
        val history = mutableListOf<HourlyStepRecord>()

        // Add hours from current hour back to midnight, excluding current hour
        for (hour in (currentHour - 1) downTo 0) {
            val steps = hourlyStepMap[hour] ?: 0
            if (steps > 0 || hourlyStepMap.containsKey(hour)) {
                val hourLabel = String.format("%02d:00", hour)
                history.add(HourlyStepRecord(hour, steps, hourLabel))
            }
        }

        _state.value = _state.value.copy(stepHistory = history)
    }

    private fun updateStepCount() {
        // Calculate hourly steps from the latest sensor reading
        val hourlySteps = hourStartStepCount?.let { startCount ->
            latestStepCount - startCount
        } ?: 0

        _state.value = _state.value.copy(hourlySteps = hourlySteps)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                val totalSteps = it.values[0].toInt()

                // Initialize on first reading
                if (initialStepCount == null) {
                    initialStepCount = totalSteps
                    hourStartStepCount = totalSteps
                }

                // Store the latest step count (will be used by updateStepCount every 5 seconds)
                latestStepCount = totalSteps
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for step counter
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(this)
    }
}
