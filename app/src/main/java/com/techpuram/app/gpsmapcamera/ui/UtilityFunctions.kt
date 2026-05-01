package com.techpuram.app.gpsmapcamera.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale
import com.techpuram.app.gpsmapcamera.R

/**
 * Check if GPS is enabled and prompt the user to enable it if not
 */
@Composable
fun CheckGpsStatus(onGpsEnabled: () -> Unit) {
    val context = LocalContext.current
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager }
    var isGpsEnabled by remember { mutableStateOf(locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) }
    var showEnableGpsDialog by remember { mutableStateOf(false) }

    // Check if GPS is enabled when this composable first launches
    LaunchedEffect(Unit) {
        isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        if (isGpsEnabled) {
            onGpsEnabled()
        } else {
            showEnableGpsDialog = true
        }
    }

    // Show dialog if GPS is disabled
    if (showEnableGpsDialog) {
        AlertDialog(
            onDismissRequest = { /* User must make a choice */ },
            title = { Text(stringResource(R.string.enable_gps)) },
            text = {
                Text(text = stringResource(R.string.gps_enabled_message))
            },
            confirmButton = {
                Button(onClick = {
                    // Direct user to Location settings
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    context.startActivity(intent)
                    showEnableGpsDialog = false
                }) {
                    Text(text = stringResource(R.string.enable_gps))
                }
            },
            dismissButton = {
                Button(onClick = {
                    showEnableGpsDialog = false
                }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    // Add a listener to detect when GPS status changes
    DisposableEffect(Unit) {
        val gpsStatusReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == android.location.LocationManager.PROVIDERS_CHANGED_ACTION) {
                    val newGpsStatus = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                    if (newGpsStatus && !isGpsEnabled) {
                        // GPS was just enabled
                        onGpsEnabled()
                    }
                    isGpsEnabled = newGpsStatus

                    // Show dialog again if GPS is turned off
                    if (!isGpsEnabled) {
                        showEnableGpsDialog = true
                    }
                }
            }
        }

        // Register the broadcast receiver
        context.registerReceiver(
            gpsStatusReceiver,
            IntentFilter(android.location.LocationManager.PROVIDERS_CHANGED_ACTION)
        )

        // Clean up when the composable is disposed
        onDispose {
            context.unregisterReceiver(gpsStatusReceiver)
        }
    }
}


/**
 * Permission handler for camera, location, and audio permissions
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionHandler(
    cameraPermissionState: PermissionState,
    locationPermissionState: PermissionState,
    audioPermissionState: PermissionState? = null,
    isVideoModeActive: Boolean = false,
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current

    // State to show rationale dialogs
    var showCameraRationale by remember { mutableStateOf(false) }
    var showLocationRationale by remember { mutableStateOf(false) }
    var showAudioRationale by remember { mutableStateOf(false) }

    // State to handle permanent denial
    var showPermanentDenialMessage by remember { mutableStateOf(false) }

    // Check if all permissions are granted
    val allPermissionsGranted = if (isVideoModeActive && audioPermissionState != null) {
        cameraPermissionState.status.isGranted &&
                locationPermissionState.status.isGranted &&
                audioPermissionState.status.isGranted
    } else {
        cameraPermissionState.status.isGranted &&
                locationPermissionState.status.isGranted
    }

    // Effect to call onPermissionsGranted when permissions change to granted
    LaunchedEffect(allPermissionsGranted) {
        if (allPermissionsGranted) {
            onPermissionsGranted()
        }
    }

    // Function to open app settings
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    // Request permissions and handle denial
    LaunchedEffect(
        cameraPermissionState.status.isGranted,
        locationPermissionState.status.isGranted,
        cameraPermissionState.status.shouldShowRationale,
        locationPermissionState.status.shouldShowRationale,
        audioPermissionState?.status?.isGranted,
        audioPermissionState?.status?.shouldShowRationale,
        isVideoModeActive
    ) {
        if (!cameraPermissionState.status.isGranted) {
            if (cameraPermissionState.status.shouldShowRationale) {
                // Show rationale for camera permission
                showCameraRationale = true
            } else {
                // Only request if we haven't shown rationale yet
                if (!showCameraRationale) {
                    cameraPermissionState.launchPermissionRequest()
                }
            }
        }

        if (!locationPermissionState.status.isGranted) {
            if (locationPermissionState.status.shouldShowRationale) {
                // Show rationale for location permission
                showLocationRationale = true
            } else {
                // Only request if we haven't shown rationale yet
                if (!showLocationRationale) {
                    locationPermissionState.launchPermissionRequest()
                }
            }
        }

        // Handle audio permission for video recording
        if (isVideoModeActive && audioPermissionState != null && !audioPermissionState.status.isGranted) {
            if (audioPermissionState.status.shouldShowRationale) {
                // Show rationale for audio permission
                showAudioRationale = true
            } else {
                // Only request if we haven't shown rationale yet
                if (!showAudioRationale) {
                    audioPermissionState.launchPermissionRequest()
                }
            }
        }
    }

    // Handle permanent denial
    if (showPermanentDenialMessage) {
        AlertDialog(
            onDismissRequest = { showPermanentDenialMessage = false },
            title = { Text(text = stringResource(R.string.permission_required)) },
            text = { Text(stringResource(R.string.grant_permissions_message)) },
            confirmButton = {
                Button(onClick = {
                    openAppSettings()
                    showPermanentDenialMessage = false
                }) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                Button(onClick = { showPermanentDenialMessage = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Show rationale for camera permission
    if (showCameraRationale) {
        AlertDialog(
            onDismissRequest = { showCameraRationale = false },
            title = { Text(text = stringResource(R.string.camera_permission_required)) },
            text = { Text(text = stringResource(R.string.camera_permission_message)) },
            confirmButton = {
                Button(onClick = {
                    cameraPermissionState.launchPermissionRequest()
                    showCameraRationale = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                Button(onClick = { showCameraRationale = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Show rationale for location permission
    if (showLocationRationale) {
        AlertDialog(
            onDismissRequest = { showLocationRationale = false },
            title = { Text(text = stringResource(R.string.location_permission_required)) },
            text = { Text(text = stringResource(R.string.location_permission_message)) },
            confirmButton = {
                Button(onClick = {
                    locationPermissionState.launchPermissionRequest()
                    showLocationRationale = false
                }) {
                    Text(text = stringResource(R.string.ok))
                }
            },
            dismissButton = {
                Button(onClick = { showLocationRationale = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    // Show rationale for audio permission
    if (showAudioRationale) {
        AlertDialog(
            onDismissRequest = { showAudioRationale = false },
            title = { Text(stringResource(R.string.audio_permission_required)) },
            text = { Text(stringResource(R.string.audio_permission_message)) },
            confirmButton = {
                Button(onClick = {
                    audioPermissionState?.launchPermissionRequest()
                    showAudioRationale = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                Button(onClick = { showAudioRationale = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Only show permission UI if permissions are not granted
    if (!allPermissionsGranted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.permissions_required),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isVideoModeActive && audioPermissionState != null)
                        "This app requires camera, location, and microphone permissions to function properly."
                    else
                        stringResource(R.string.grant_permissions_message),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Camera permission button
                if (!cameraPermissionState.status.isGranted) {
                    Button(
                        onClick = { cameraPermissionState.launchPermissionRequest() },
                        modifier = Modifier.fillMaxWidth(0.8f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!cameraPermissionState.status.isGranted)
                                MaterialTheme.colorScheme.primary
                            else
                                Color.Green
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                            Text("Grant Camera Permission")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Location permission button
                if (!locationPermissionState.status.isGranted) {
                    Button(
                        onClick = { locationPermissionState.launchPermissionRequest() },
                        modifier = Modifier.fillMaxWidth(0.8f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!locationPermissionState.status.isGranted)
                                MaterialTheme.colorScheme.primary
                            else
                                Color.Green
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null)
                            Text("Grant Location Permission")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Audio permission button (only if in video mode)
                if (isVideoModeActive && audioPermissionState != null && !audioPermissionState.status.isGranted) {
                    Button(
                        onClick = { audioPermissionState.launchPermissionRequest() },
                        modifier = Modifier.fillMaxWidth(0.8f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!audioPermissionState.status.isGranted)
                                MaterialTheme.colorScheme.primary
                            else
                                Color.Green
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null)
                            Text("Grant Microphone Permission")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Settings button for permanently denied permissions
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showPermanentDenialMessage = true },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(text = stringResource(R.string.open_settings))
                }
            }
        }
    }
}