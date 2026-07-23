package com.timebet.app.core.monitoring

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detects walking using Android's built-in TYPE_STEP_DETECTOR sensor.
 *
 * Unlike raw accelerometer peak detection, this sensor uses the phone's
 * hardware step-detection algorithm — far more reliable and battery-efficient.
 * Fires one event per detected step.
 *
 * Detection logic:
 * - Counts steps in a rolling 5-second window
 * - ≥2 steps in 5 seconds → WALKING
 * - 0 steps in 10 seconds → STATIONARY
 * - Reads sensitivity multiplier from SharedPreferences (timebet_walk)
 */
class WalkDetector(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val _walkState = MutableStateFlow<WalkState>(WalkState.Stationary)
    val walkState: StateFlow<WalkState> = _walkState.asStateFlow()

    // Rolling window of step timestamps
    private val stepTimestamps = mutableListOf<Long>()
    private var lastCheckTime = 0L

    /** Whether walk detection is enabled (from settings) */
    private fun isEnabled(): Boolean {
        return context.getSharedPreferences("timebet_walk", Context.MODE_PRIVATE)
            .getBoolean("walk_detection_enabled", true)
    }

    /** Current multiplier from settings */
    fun getMultiplier(): Double {
        return context.getSharedPreferences("timebet_walk", Context.MODE_PRIVATE)
            .getFloat("walk_multiplier", 2.0f).toDouble()
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_STEP_DETECTOR) return
            if (!isEnabled()) {
                if (_walkState.value is WalkState.Walking) {
                    _walkState.value = WalkState.Stationary
                }
                return
            }

            val now = System.currentTimeMillis()

            // Each event = 1 detected step
            stepTimestamps.add(now)

            // Keep only steps from the last 5 seconds
            stepTimestamps.removeAll { now - it > 5000 }

            // ≥2 steps in 5 seconds → walking
            if (stepTimestamps.size >= 2 && _walkState.value !is WalkState.Walking) {
                _walkState.value = WalkState.Walking
            }

            lastCheckTime = now
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun scheduleStationaryCheck() {
        mainHandler.postDelayed({
            val now = System.currentTimeMillis()
            stepTimestamps.removeAll { now - it > 10000 }
            if (stepTimestamps.isEmpty() && _walkState.value is WalkState.Walking) {
                _walkState.value = WalkState.Stationary
            }
            if (_walkState.value is WalkState.Walking) {
                scheduleStationaryCheck()
            }
        }, 3000)
    }

    fun start() {
        stepDetector?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(sensorListener)
        stepTimestamps.clear()
        _walkState.value = WalkState.Stationary
    }

    /** Call when transitioning TO walking state to start stationary checks */
    fun onWalkingStarted() {
        scheduleStationaryCheck()
    }
}

sealed class WalkState {
    data object Stationary : WalkState()
    data object Walking : WalkState()
}
