package com.techpuram.app.gpsmapcamera.ui.components

import androidx.camera.core.AspectRatio
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.MapType
import com.techpuram.app.gpsmapcamera.R

/**
 * Create a box with the proper aspect ratio for camera preview
 */
@Composable
fun AspectRatioBox(
    aspectRatio: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(
                when (aspectRatio) {
                    AspectRatio.RATIO_4_3 -> 4f/3f
                    AspectRatio.RATIO_16_9 -> 16f/9f
                    else -> 16f/9f // Default
                }
            ),
        content = content
    )
}

/**
 * Grid overlay with different grid options
 */
@Composable
fun GridOverlay(
    gridType: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val strokeWidth = 1.dp.toPx()

        when (gridType) {
            "rule3" -> {
                // Rule of thirds
                val thirdWidth = width / 3
                val thirdHeight = height / 3

                // Draw vertical lines
                for (i in 1..2) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(thirdWidth * i, 0f),
                        end = Offset(thirdWidth * i, height),
                        strokeWidth = 1f
                    )
                }

                // Draw horizontal lines
                for (i in 1..2) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(0f, thirdHeight * i),
                        end = Offset(width, thirdHeight * i),
                        strokeWidth = 1f
                    )
                }
            }
            "grid4" -> {
                // 4x4 grid
                val cellWidth = width / 4
                val cellHeight = height / 4

                // Draw vertical lines
                for (i in 1..3) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(cellWidth * i, 0f),
                        end = Offset(cellWidth * i, height),
                        strokeWidth = 1f
                    )
                }

                // Draw horizontal lines
                for (i in 1..3) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(0f, cellHeight * i),
                        end = Offset(width, cellHeight * i),
                        strokeWidth = 1f
                    )
                }
            }
            "grid9" -> {
                // 3x3 grid with center lines
                val thirdWidth = width / 3
                val thirdHeight = height / 3

                // Draw vertical lines
                for (i in 1..2) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(thirdWidth * i, 0f),
                        end = Offset(thirdWidth * i, height),
                        strokeWidth = 1f
                    )
                }

                // Draw horizontal lines
                for (i in 1..2) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(0f, thirdHeight * i),
                        end = Offset(width, thirdHeight * i),
                        strokeWidth = 1f
                    )
                }

                // Draw center lines
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(width / 2, 0f),
                    end = Offset(width / 2, height),
                    strokeWidth = 0.5f
                )

                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(0f, height / 2),
                    end = Offset(width, height / 2),
                    strokeWidth = 0.5f
                )
            }
            else -> {
                // No grid
            }
        }
    }
}

/**
 * Settings panel for camera controls
 */
@Composable
fun CameraSettingsPanel(
    gridType: String,
    onGridTypeChanged: (String) -> Unit,
    aspectRatio: Int,
    onAspectRatioChanged: (Int) -> Unit,
    autoOrientation: Boolean,
    onAutoOrientationChanged: (Boolean) -> Unit,
    flashMode: Int,
    onFlashModeChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(8.dp)
    ) {
        // Row 1: Grid and Aspect Ratio
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Grid Type Selection
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.grid), color = Color.White, fontSize = 12.sp)
                Row {
                    IconButton(onClick = { onGridTypeChanged("none") }) {
                        Icon(
                            imageVector = Icons.Filled.GridOff,
                            contentDescription = stringResource(R.string.no_grid),
                            tint = if (gridType == "none") Color.Cyan else Color.White
                        )
                    }
                    IconButton(onClick = { onGridTypeChanged("rule3") }) {
                        Icon(
                            imageVector = Icons.Filled.Grid3x3,
                            contentDescription = stringResource(R.string.rule_of_thirds),
                            tint = if (gridType == "rule3") Color.Cyan else Color.White
                        )
                    }
                    IconButton(onClick = { onGridTypeChanged("grid9") }) {
                        Icon(
                            imageVector = Icons.Filled.GridOn,
                            contentDescription = stringResource(R.string.grid_3x3),
                            tint = if (gridType == "grid9") Color.Cyan else Color.White
                        )
                    }
                }
            }

            Divider(
                modifier = Modifier
                    .height(40.dp)
                    .width(1.dp),
                color = Color.Gray
            )

            // Aspect Ratio Selection
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.ratio), color = Color.White, fontSize = 12.sp)
                Row {
                    IconButton(onClick = { onAspectRatioChanged(AspectRatio.RATIO_4_3) }) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(24.dp)
                                .background(
                                    if (aspectRatio == AspectRatio.RATIO_4_3) Color.Cyan else Color.White,
                                    RoundedCornerShape(4.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "4:3",
                                color = Color.Black,
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(onClick = { onAspectRatioChanged(AspectRatio.RATIO_16_9) }) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(24.dp)
                                .background(
                                    if (aspectRatio == AspectRatio.RATIO_16_9) Color.Cyan else Color.White,
                                    RoundedCornerShape(4.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "16:9",
                                color = Color.Black,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // Row 2: Orientation and Level
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Auto Orientation
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.auto_rotation), color = Color.White, fontSize = 12.sp)
                Switch(
                    checked = autoOrientation,
                    onCheckedChange = onAutoOrientationChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Cyan,
                        checkedTrackColor = Color.DarkGray,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }

            Divider(
                modifier = Modifier
                    .height(40.dp)
                    .width(1.dp),
                color = Color.Gray
            )

            // Flash Mode
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Flash", color = Color.White, fontSize = 12.sp)
                Row {
                    IconButton(onClick = { 
                        onFlashModeChanged(androidx.camera.core.ImageCapture.FLASH_MODE_OFF) 
                    }) {
                        Icon(
                            imageVector = Icons.Filled.FlashOff,
                            contentDescription = "Flash Off",
                            tint = if (flashMode == androidx.camera.core.ImageCapture.FLASH_MODE_OFF) Color.Cyan else Color.White
                        )
                    }
                    IconButton(onClick = { 
                        onFlashModeChanged(androidx.camera.core.ImageCapture.FLASH_MODE_ON) 
                    }) {
                        Icon(
                            imageVector = Icons.Filled.FlashOn,
                            contentDescription = "Flash On",
                            tint = if (flashMode == androidx.camera.core.ImageCapture.FLASH_MODE_ON) Color.Cyan else Color.White
                        )
                    }
                    IconButton(onClick = { 
                        onFlashModeChanged(androidx.camera.core.ImageCapture.FLASH_MODE_AUTO) 
                    }) {
                        Icon(
                            imageVector = Icons.Filled.FlashAuto,
                            contentDescription = "Flash Auto",
                            tint = if (flashMode == androidx.camera.core.ImageCapture.FLASH_MODE_AUTO) Color.Cyan else Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Map view component for showing the current location
 */
@Composable
fun MapView(latitude: Double, longitude: Double) {
    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 18f)
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = false,
            mapType = MapType.NORMAL
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = false,
            rotationGesturesEnabled = false,
            scrollGesturesEnabled = false,
            tiltGesturesEnabled = false,
            zoomGesturesEnabled = false,
            mapToolbarEnabled = false
        )
    ) {
        Marker(
            state = MarkerState(position = LatLng(latitude, longitude)),
            title = stringResource(R.string.your_location)
        )
    }
}