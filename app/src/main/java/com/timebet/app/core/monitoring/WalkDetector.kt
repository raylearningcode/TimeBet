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
 * Detects walking via accelerometer peak detection.
 *
 * Algorithm: samples accelerometer at SENSOR_DELAY_GAME (~20ms),
 * detects magnitude peaks above 1.2g, requires ≥3 peaks in 3 seconds
 * to transition to WALKING, and 10s of no peaks to return to STATIONARY.
 */
class WalkDetector(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _walkState = MutableStateFlow<WalkState>(WalkState.Stationary)
    val walkState: StateFlow<WalkState> = _walkState.asStateFlow()

    private var peakTimestamps = mutableListOf<Long>()
    private var lastPeakTime = 0L

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble())

            val now = System.currentTimeMillis()

            if (magnitude > 1.2) { // peak above 1.2g threshold
                if (now - lastPeakTime >= 300) { // minimum 300ms gap between peaks
                    peakTimestamps.add(now)
                    lastPeakTime = now

                    // Keep only peaks from the last 3 seconds
                    peakTimestamps.removeAll { now - it > 3000 }

                    if (peakTimestamps.size >= 3 && _walkState.value !is WalkState.Walking) {
                        _walkState.value = WalkState.Walking
                    }
                }
            }

            // Check for stationary: no peaks in 10 seconds
            if (_walkState.value is WalkState.Walking) {
                peakTimestamps.removeAll { now - it > 10000 }
                if (peakTimestamps.isEmpty()) {
                    _walkState.value = WalkState.Stationary
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(sensorListener)
        _walkState.value = WalkState.Stationary
    }
}

sealed class WalkState {
    data object Stationary : WalkState()
    data object Walking : WalkState()
}
