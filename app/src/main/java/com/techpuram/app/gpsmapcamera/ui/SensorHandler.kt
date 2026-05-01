package com.techpuram.app.gpsmapcamera.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * A lifecycle-aware sensor handler that properly registers and unregisters
 * sensors to optimize battery usage.
 */
@Composable
fun SensorHandler(
    context: Context,
    showLevelIndicator: Boolean,
    onSensorValuesChanged: (pitch: Float, roll: Float) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Get sensor manager
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    // Get rotation vector sensor, fallback to gravity sensor if not available
    val rotationSensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    // Create sensor event listener
    val sensorEventListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                try {
                    when (event.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR -> {
                            // Process rotation vector - most accurate
                            val rotationMatrix = FloatArray(9)
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                            val orientationAngles = FloatArray(3)
                            SensorManager.getOrientation(rotationMatrix, orientationAngles)

                            // Convert radians to degrees
                            val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                            val roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                            // Pass values to callback
                            onSensorValuesChanged(pitch, roll)
                        }
                        Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                            // Fallback for devices without rotation vector sensor
                            if (event.values.size >= 3) {
                                val x = event.values[0]
                                val y = event.values[1]
                                val z = event.values[2]

                                // Calculate roll and pitch from gravity
                                val roll = Math.toDegrees(kotlin.math.atan2(x.toDouble(), kotlin.math.sqrt((y * y + z * z).toDouble()))).toFloat()
                                val pitch = Math.toDegrees(kotlin.math.atan2(-y.toDouble(), z.toDouble())).toFloat()

                                onSensorValuesChanged(pitch, roll)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Silently handle sensor errors to prevent crashes
                    android.util.Log.w("SensorHandler", "Sensor processing error: ${e.message}")
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                // Not needed for this implementation
            }
        }
    }

    // Properly register/unregister sensors with lifecycle awareness
    DisposableEffect(lifecycleOwner, showLevelIndicator) {
        // Only register sensors if level indicator is shown
        if (!showLevelIndicator) {
            return@DisposableEffect onDispose { }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Register sensors when activity is visible
                    rotationSensor?.let { sensor ->
                        try {
                            val registered = sensorManager.registerListener(
                                sensorEventListener,
                                sensor,
                                // Use SENSOR_DELAY_UI for level indicator - good balance of accuracy and battery
                                SensorManager.SENSOR_DELAY_UI
                            )
                            if (!registered) {
                                android.util.Log.w("SensorHandler", "Failed to register sensor: ${sensor.name}")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SensorHandler", "Error registering sensor: ${e.message}", e)
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Unregister when activity is not visible to save battery
                    sensorManager.unregisterListener(sensorEventListener)
                }
                else -> { /* Ignore other events */ }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            // Clean up by removing observer and unregistering sensors
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(sensorEventListener)
        }
    }
}