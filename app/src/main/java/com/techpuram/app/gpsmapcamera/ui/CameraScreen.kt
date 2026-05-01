package com.techpuram.app.gpsmapcamera.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.CornerPathEffect
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.internal.utils.ImageUtil.rotateBitmap
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.techpuram.app.gpsmapcamera.SettingsActivity
import android.content.Intent
import coil.decode.DecodeUtils.calculateInSampleSize
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.techpuram.app.gpsmapcamera.R
import com.techpuram.app.gpsmapcamera.ui.components.CameraSettingsPanel
import com.techpuram.app.gpsmapcamera.ui.components.GridOverlay
import com.techpuram.app.gpsmapcamera.ui.components.HorizontalLevelIndicator
import com.techpuram.app.gpsmapcamera.ui.components.MapView
import com.techpuram.app.gpsmapcamera.util.CheckGpsStatus
import com.techpuram.app.gpsmapcamera.util.PermissionHandler
import com.techpuram.app.gpsmapcamera.util.SensorHandler
// ADD THESE NEW IMPORTS FOR OPTIMIZED GEOCODING
import com.techpuram.app.gpsmapcamera.OptimizedAddressFetcher
import com.techpuram.app.gpsmapcamera.SmartLocationManager
import com.techpuram.app.gpsmapcamera.util.AddressParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onImageCaptured: (Uri, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val executor = ContextCompat.getMainExecutor(context)

    // CHANGE 1: Initialize optimized address fetcher
    val addressFetcher = remember {
        OptimizedAddressFetcher(context, com.techpuram.app.gpsmapcamera.BuildConfig.MAPS_API_KEY)
    }

    // Permission states
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // State variables
    var latitude by remember { mutableStateOf("Fetching...") }
    var longitude by remember { mutableStateOf("Fetching...") }
    var address by remember { mutableStateOf("Fetching address...") }
    // CHANGE 2: Add loading state for address updates
    var isUpdatingAddress by remember { mutableStateOf(false) }

    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var aspectRatio by remember { mutableStateOf(AspectRatio.RATIO_4_3) }
    var gridType by remember { mutableStateOf("rule3") }
    var autoOrientation by remember { mutableStateOf(true) }
    var showLevelIndicator by remember { mutableStateOf(true) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var showFiltersPanel by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(CameraFilter.NORMAL) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isCapturing by remember { mutableStateOf(false) }
    // Removed frozen frame states - using immediate navigation instead

    // Camera state
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var preview: Preview? by remember { mutableStateOf(null) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }

    // CHANGE 3: Add location callback reference for cleanup
    var locationCallback: LocationCallback? by remember { mutableStateOf(null) }

    // Helper function to get the camera provider
    suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { future ->
            future.addListener({
                continuation.resume(future.get())
            }, executor)
        }
    }

    // Sensor data for level indicator
    var pitch by remember { mutableStateOf(0f) }
    var roll by remember { mutableStateOf(0f) }

    // Register sensor listener when level indicator is active
    if (showLevelIndicator) {
        SensorHandler(
            context = context,
            showLevelIndicator = showLevelIndicator
        ) { newPitch, newRoll ->
            pitch = newPitch
            roll = newRoll
        }
    }

    // Handle auto orientation
    DisposableEffect(autoOrientation) {
        val activity = context as? ComponentActivity
        if (autoOrientation) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onDispose {
            // Reset to default when component is disposed
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Reset capturing state when returning to camera screen
    LaunchedEffect(Unit) {
        isCapturing = false
    }

    // Setup camera when permissions are granted
    LaunchedEffect(aspectRatio, lensFacing, flashMode) {
        if (cameraPermissionState.status.isGranted) {
            try {
                cameraProvider = context.getCameraProvider()
                cameraProvider?.unbindAll()

                // Get the display rotation using newer API
                val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.display?.rotation ?: android.view.Surface.ROTATION_0
                } else {
                    @Suppress("DEPRECATION")
                    (context as ComponentActivity).windowManager.defaultDisplay.rotation
                }

                preview = Preview.Builder()
                    .setTargetAspectRatio(aspectRatio)
                    .setTargetRotation(rotation)
                    .build()

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetAspectRatio(aspectRatio)
                    .setTargetRotation(rotation)
                    .setFlashMode(flashMode)
                    .build()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                Log.d("CameraScreen", "Camera use cases bound successfully")
            } catch (exc: Exception) {
                Log.e("CameraScreen", "Camera binding failed", exc)
            }
        } else {
            Log.e("CameraScreen", "Camera permission not granted")
        }
    }

    // CHANGE 4: Replace the entire GPS status check section with optimized version
    if (locationPermissionState.status.isGranted) {
        CheckGpsStatus(onGpsEnabled = {
            // Create optimized location request with dynamic intervals
            val initialLocationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                SmartLocationManager.FAST_INTERVAL
            ).build()

            val optimizedLocationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.locations.firstOrNull()?.let { location ->
                        val newLat = location.latitude.toString()
                        val newLon = location.longitude.toString()

                        // Always update coordinates immediately
                        latitude = newLat
                        longitude = newLon

                        // Only fetch address if location has moved significantly to reduce API calls
                        if (!SmartLocationManager.isStationary(location)) {
                            // Get immediate cached/last known address for UI
                            val immediateAddress = addressFetcher.getImmediateAddress(
                                location.latitude,
                                location.longitude
                            )
                            
                            // Only update address if it's different to prevent unnecessary UI updates
                            if (immediateAddress != address) {
                                address = immediateAddress
                            }

                            // Check if we need to make an API call for fresh address (throttled)
                            coroutineScope.launch {
                                try {
                                    isUpdatingAddress = true
                                    val freshAddress = addressFetcher.getAddressOptimized(
                                        location.latitude,
                                        location.longitude
                                    )

                                    if (freshAddress != null && freshAddress != address) {
                                        address = freshAddress
                                        Log.d("Location", "✅ Updated address: $freshAddress")
                                    }
                                } catch (e: Exception) {
                                    Log.e("Location", "Failed to get fresh address", e)
                                } finally {
                                    isUpdatingAddress = false
                                }
                            }
                        }

                        // Note: Removed dynamic interval adjustment to prevent infinite location update loops
                    }
                }
            }

            locationCallback = optimizedLocationCallback

            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.requestLocationUpdates(
                    initialLocationRequest,
                    optimizedLocationCallback,
                    context.mainLooper
                )
            }
        })
    }

    // Update permission handler to include audio permissions
    PermissionHandler(
        cameraPermissionState = cameraPermissionState,
        locationPermissionState = locationPermissionState,
        audioPermissionState = audioPermissionState,
        onPermissionsGranted = { /* Camera handles this through LaunchedEffect */ }
    )

    fun applyFilterToSavedImage(uri: Uri, filter: CameraFilter) {
        if (filter == CameraFilter.NORMAL) {
            return
        }

        coroutineScope.launch {
            try {
                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                }

                val filteredBitmap = CameraFilter.applyFilter(bitmap, filter)

                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    filteredBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    outputStream.close()
                }

                Log.d("CameraScreen", "Filter applied successfully: ${filter.displayName}")
            } catch (e: Exception) {
                Log.e("CameraScreen", "Error applying filter", e)
            }
        }
    }

    // Removed capturePreviewFrame - using immediate navigation instead

    // Photo capture function
    fun takePhoto() {
        val currentImageCapture = imageCapture ?: return
        
        // Set capturing state to true
        isCapturing = true

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            
            // Save original captures to a temporary folder (not visible in main gallery)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/.GPSCamera_temp")
            }

            if (latitude != "Fetching..." && longitude != "Fetching...") {
                try {
                    val lat = latitude.toDouble()
                    val lon = longitude.toDouble()
                    put(MediaStore.Images.Media.LATITUDE, lat)
                    put(MediaStore.Images.Media.LONGITUDE, lon)
                } catch (e: Exception) {
                    Log.e("CameraScreen", "Error adding location to image metadata", e)
                }
            }
        }

        // Create temporary file instead of saving directly to gallery
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        currentImageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    outputFileResults.savedUri?.let { uri ->
                        coroutineScope.launch {
                            try {
                                Log.d("CameraScreen", "Image saved, starting optimized processing: $uri")

                                // Process image with optimizations but wait for completion
                                launch(Dispatchers.IO) {
                                    val processedBitmap = processImageWithRotationAndFilter(
                                        context,
                                        uri,
                                        selectedFilter
                                    )

                                    if (processedBitmap != null) {
                                        // Save the processed bitmap back to the same URI
                                        val outputStream = context.contentResolver.openOutputStream(uri)
                                        if (outputStream != null) {
                                            val success = processedBitmap.compress(
                                                Bitmap.CompressFormat.JPEG,
                                                85, // Reduced quality for faster saving
                                                outputStream
                                            )
                                            outputStream.close()

                                            withContext(Dispatchers.Main) {
                                                if (success) {
                                                    Log.d("CameraScreen", "Image processed and saved successfully")
                                                } else {
                                                    Log.e("CameraScreen", "Failed to compress processed image")
                                                }
                                                // Navigate immediately 
                                                isCapturing = false
                                                onImageCaptured(uri, latitude, longitude, address)
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                Log.e("CameraScreen", "Could not open output stream")
                                                isCapturing = false
                                                onImageCaptured(uri, latitude, longitude, address)
                                            }
                                        }

                                        // Clean up bitmap
                                        if (!processedBitmap.isRecycled) {
                                            processedBitmap.recycle()
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            Log.e("CameraScreen", "Failed to process image, using original")
                                            isCapturing = false
                                            onImageCaptured(uri, latitude, longitude, address)
                                        }
                                    }
                                }

                            } catch (e: Exception) {
                                Log.e("CameraScreen", "Error processing captured image: ${e.message}", e)
                                isCapturing = false
                                onImageCaptured(uri, latitude, longitude, address)
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraScreen", "Photo capture failed: ${exception.message}", exception)
                    Toast.makeText(
                        context,
                        "Failed to save photo",
                        Toast.LENGTH_SHORT
                    ).show()
                    isCapturing = false
                }
            }
        )
    }


    // UI Layout
    Box(modifier = Modifier.fillMaxSize())  {
        // Camera Preview with BoxWithConstraints for calculating aspect ratio dimensions
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
                // Access the constraints within the BoxWithConstraints scope
                val screenWidth = this.maxWidth

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    val containerHeight = when (aspectRatio) {
                        AspectRatio.RATIO_16_9 -> screenWidth * (16f / 9f)
                        else -> screenWidth * (4f / 3f) // 4:3 or default
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(containerHeight)
                            .align(Alignment.Center)
                            .clip(RectangleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        // Camera Preview using AndroidView
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Base camera preview
                            AndroidView(
                                factory = { ctx ->
                                    // Create PreviewView
                                    val previewView = PreviewView(ctx).apply {
                                        // Use FIT_CENTER to show the full image without cropping
                                        scaleType = PreviewView.ScaleType.FIT_CENTER
                                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE

                                        // Make sure PreviewView uses the full container
                                        layoutParams = android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }

                                    preview?.let {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    } ?: Log.e(
                                        "CameraScreen",
                                        "Preview is null when setting surface provider"
                                    )

                                    previewView
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { view ->
                                    preview?.setSurfaceProvider(view.surfaceProvider)
                                }
                            )

                            // Real-time filter overlay
                            SimpleFilterOverlay(
                                filter = selectedFilter,
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            // Show capture feedback with loading indicator
                            if (isCapturing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.3f))
                                        .zIndex(10f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(48.dp),
                                        strokeWidth = 4.dp
                                    )
                                }
                            }
                        }

                        // Overlay elements - they stay in the constrained box now
                        if (gridType != "none") {
                            GridOverlay(
                                gridType = gridType,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(2f)
                            )
                        }

                        // Level indicator centered in the screen
                        if (showLevelIndicator) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(4f),
                                contentAlignment = Alignment.Center
                            ) {
                                HorizontalLevelIndicator(
                                    roll = roll,
                                    isActive = true,
                                    modifier = Modifier.width(240.dp)
                                )
                            }
                        }

                        // Aspect ratio indicator
                        Text(
                            text = when (aspectRatio) {
                                AspectRatio.RATIO_4_3 -> "4:3"
                                AspectRatio.RATIO_16_9 -> "16:9"
                                else -> "4:3"
                            },
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.7f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .zIndex(5f)
                        )

                        // Geo tag overlay at the bottom
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .zIndex(10f)
                        ) {
                            val isLandscape = maxWidth > maxHeight
                            val geoTagWidth = if (isLandscape) 480.dp else maxWidth
                            
                            // Map and location info
                            val mapWidth = if (aspectRatio == AspectRatio.RATIO_4_3) 100.dp else 120.dp
                            val mapHeight = if (aspectRatio == AspectRatio.RATIO_4_3) 75.dp else 90.dp

                            Row(
                                modifier = Modifier
                                    .width(geoTagWidth)
                                    .align(Alignment.Center)
                                    .background(Color.Transparent)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Map Preview
                                Box(
                                    modifier = Modifier
                                        .width(mapWidth)
                                        .height(mapHeight)
                                        .border(
                                            1.dp,
                                            Color.White.copy(alpha = 0.7f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .background(
                                            Color.Black.copy(alpha = 0.5f),
                                            RoundedCornerShape(6.dp)
                                        )
                                ) {
                                    val isLocationValid = remember(latitude, longitude) {
                                        latitude != "Fetching..." && longitude != "Fetching..." &&
                                                runCatching { latitude.toDouble(); longitude.toDouble() }.isSuccess
                                    }

                                    if (isLocationValid) {
                                        val lat = latitude.toDouble()
                                        val lng = longitude.toDouble()
                                        MapView(lat, lng)
                                    } else {
                                        // Loading indicator
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(30.dp),
                                                    color = Color.White,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Loading...",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // CHANGE 5: Address display with loading indicator
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(mapHeight)
                                        .background(
                                            Color.Black.copy(alpha = 0.5f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    // Add small loading indicator when updating address
                                    if (isUpdatingAddress) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(12.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(8.dp),
                                                color = Color.White,
                                                strokeWidth = 1.dp
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Calculate font sizes - increased for better readability
                                        val baseFontSize = 13.sp
                                        val smallFontSize = 12.sp

                                        // Format current date and time
                                        val currentDateTime = remember {
                                            val sdf = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.US)
                                            sdf.format(System.currentTimeMillis())
                                        }

                                        // Extract plus code from address if present
                                        val (mainAddress, plusCode) = remember(address) {
                                            if (address.contains(". ")) {
                                                val parts = address.split(". ")
                                                Pair(parts[0], parts.getOrNull(1) ?: "")
                                            } else {
                                                Pair(address, "")
                                            }
                                        }

                                        // ROW 1: Main address in Indian format (can be multi-line)
                                        val addressDisplay = remember(address) {
                                            when {
                                                address.isEmpty() || address == "Fetching address..." -> ""
                                                else -> mainAddress
                                            }
                                        }

                                        // Display address with multiple lines support
                                        if (addressDisplay.isNotEmpty()) {
                                            Text(
                                                text = addressDisplay,
                                                color = Color.White,
                                                fontSize = baseFontSize,
                                                maxLines = 3, // Allow up to 3 lines for Indian addresses
                                                fontWeight = FontWeight.Normal,
                                                lineHeight = 12.sp
                                            )
                                        }

                                        // ROW 2: Full precision coordinates (Lat, Lon)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Globe icon
                                            Image(
                                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                                contentDescription = "Globe Icon",
                                                modifier = Modifier.size(16.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))

                                            // Format coordinates with full precision (6 decimal places)
                                            val displayLat = if (latitude != "Fetching...") {
                                                try {
                                                    String.format("%.6f", latitude.toDouble())
                                                } catch (e: Exception) {
                                                    "0.000000"
                                                }
                                            } else "0.000000"

                                            val displayLon = if (longitude != "Fetching...") {
                                                try {
                                                    String.format("%.6f", longitude.toDouble())
                                                } catch (e: Exception) {
                                                    "0.000000"
                                                }
                                            } else "0.000000"

                                            Text(
                                                text = "Lat: $displayLat | Lon: $displayLon",
                                                color = Color.White,
                                                fontSize = smallFontSize,
                                                maxLines = 1
                                            )
                                        }

                                        // ROW 3: Plus Code and Date/Time
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Left: Plus code
                                            val displayPlusCode = if (plusCode.isNotEmpty()) {
                                                plusCode
                                            } else {
                                                // Generate a simple plus code if not available
                                                if (latitude != "Fetching..." && longitude != "Fetching...") {
                                                    try {
                                                        val lat = latitude.toDouble()
                                                        val lon = longitude.toDouble()
                                                        "W4PQ+${String.format("%02X", ((lat + lon).toInt() % 256))}"
                                                    } catch (e: Exception) {
                                                        "W4PQ+GH"
                                                    }
                                                } else {
                                                    "W4PQ+GH"
                                                }
                                            }

                                            Text(
                                                text = displayPlusCode,
                                                color = Color.White,
                                                fontSize = smallFontSize,
                                                maxLines = 1,
                                                fontWeight = FontWeight.Medium
                                            )

                                            // Right: Date and time
                                            Text(
                                                text = currentDateTime,
                                                color = Color.White,
                                                fontSize = smallFontSize,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }

        // Bottom controls positioned at bottom of screen
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            BoxWithConstraints {
                val isLandscape = maxWidth > maxHeight
                val controlsWidth = if (isLandscape) 400.dp else this.maxWidth
                
                Box(
                    modifier = Modifier
                        .width(controlsWidth)
                        .align(Alignment.Center)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(16.dp))
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    // App Settings button (far left)
                    IconButton(
                        onClick = {
                            val intent = Intent(context, SettingsActivity::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "App Settings",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    // Filters button
                    IconButton(
                        onClick = {
                            showFiltersPanel = !showFiltersPanel
                            if (showFiltersPanel) {
                                showSettingsPanel = false
                            }
                        },
                        modifier = Modifier.size(50.dp)
                    ) {
                        Icon(
                            imageVector = if (showFiltersPanel) Icons.Filled.Close else Icons.Filled.FilterAlt,
                            contentDescription = stringResource(R.string.photo_filters),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Capture Button with press animation (center)
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .graphicsLayer {
                                scaleX = if (isPressed) 0.9f else 1f
                                scaleY = if (isPressed) 0.9f else 1f
                            }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { 
                                if (!isCapturing) {
                                    takePhoto() 
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Photo capture button design with press feedback
                        Canvas(modifier = Modifier.size(62.dp)) {
                            val strokeWidth = 4.dp.toPx()
                            val outerAlpha = if (isPressed) 1f else 0.8f
                            val innerColor = if (isPressed) Color.Gray else Color.White
                            
                            drawCircle(
                                color = Color.White.copy(alpha = outerAlpha),
                                style = Stroke(width = strokeWidth)
                            )
                            drawCircle(
                                color = innerColor,
                                radius = (size.minDimension - strokeWidth * 2) / 2
                            )
                        }
                        
                        // Show loading indicator while capturing
                        if (isCapturing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    // Camera flip/rotate button
                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier.size(50.dp)
                    ) {
                        Icon(
                            Icons.Filled.Cameraswitch,
                            contentDescription = stringResource(R.string.switch_camera),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    // Camera Settings button (far right)
                    IconButton(
                        onClick = {
                            showSettingsPanel = !showSettingsPanel
                            if (showSettingsPanel) {
                                showFiltersPanel = false
                            }
                        },
                        modifier = Modifier.size(50.dp)
                    ) {
                        Icon(
                            imageVector = if (showSettingsPanel) Icons.Filled.Close else Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            } // End of BoxWithConstraints
        } // End of bottom controls Box

        // Settings panel overlay
        if (showSettingsPanel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showSettingsPanel = false }
                    .zIndex(20f)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
                    .zIndex(21f),
                contentAlignment = Alignment.BottomCenter
            ) {
                CameraSettingsPanel(
                    gridType = gridType,
                    onGridTypeChanged = { gridType = it },
                    aspectRatio = aspectRatio,
                    onAspectRatioChanged = { aspectRatio = it },
                    autoOrientation = autoOrientation,
                    onAutoOrientationChanged = { autoOrientation = it },
                    flashMode = flashMode,
                    onFlashModeChanged = { flashMode = it }
                )
            }
        }

        // Filters panel overlay
        if (showFiltersPanel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showFiltersPanel = false }
                    .zIndex(20f)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
                    .zIndex(21f),
                contentAlignment = Alignment.BottomCenter
            ) {
                CameraFilterSelector(
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it }
                )
            }
        }
    } // End of root Box

    // CHANGE 6: Updated cleanup to include location updates
    DisposableEffect(Unit) {
        onDispose {
            locationCallback?.let { callback ->
                fusedLocationClient.removeLocationUpdates(callback)
            }
            cameraProvider?.unbindAll()
        }
    }
}

@Composable
fun SimpleFilterOverlay(
    filter: CameraFilter,
    modifier: Modifier = Modifier
) {
    if (filter == CameraFilter.NORMAL) return

    val (overlayColor, alpha) = when (filter) {
        CameraFilter.GRAYSCALE -> Color.Gray to 0.2f
        CameraFilter.SEPIA -> Color(0xFFD2691E) to 0.25f
        CameraFilter.WARM -> Color(0xFFFF6B35) to 0.15f
        CameraFilter.COOL -> Color(0xFF4FC3F7) to 0.15f
        CameraFilter.VINTAGE -> Color(0xFF795548) to 0.3f
        else -> Color.Transparent to 0f
    }

    Box(
        modifier = modifier.background(
            overlayColor.copy(alpha = alpha)
        )
    )
}

private fun addGeoTagOverlay(
    originalBitmap: Bitmap,
    latitude: String,
    longitude: String,
    address: String,
    aspectRatio: Int
): Bitmap {
    // Create a mutable copy of the bitmap
    val overlayBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(overlayBitmap)

    val width = overlayBitmap.width
    val height = overlayBitmap.height

    // Calculate overlay dimensions based on image size
    val mapWidth = if (aspectRatio == AspectRatio.RATIO_4_3) width * 0.25f else width * 0.3f
    val mapHeight = if (aspectRatio == AspectRatio.RATIO_4_3) mapWidth * 0.75f else mapWidth * 0.75f
    val textBoxWidth = width - mapWidth - (width * 0.06f) // Account for spacing

    // Position at bottom with padding
    val bottomPadding = height * 0.02f
    val sidePadding = width * 0.02f
    val overlayY = height - mapHeight - bottomPadding

    // Draw map preview background (dark semi-transparent rounded rectangle)
    val mapPaint = Paint().apply {
        color = AndroidColor.argb(128, 0, 0, 0) // Semi-transparent black
        isAntiAlias = true
        pathEffect = CornerPathEffect(12f)
    }

    val mapRect = RectF(
        sidePadding,
        overlayY,
        sidePadding + mapWidth,
        overlayY + mapHeight
    )
    canvas.drawRoundRect(mapRect, 12f, 12f, mapPaint)

    // Draw map preview border
    val mapBorderPaint = Paint().apply {
        color = AndroidColor.argb(180, 255, 255, 255) // Semi-transparent white
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
        pathEffect = CornerPathEffect(12f)
    }
    canvas.drawRoundRect(mapRect, 12f, 12f, mapBorderPaint)

    // Draw text background (dark semi-transparent rounded rectangle)
    val textBoxX = sidePadding + mapWidth + (width * 0.02f)
    val textRect = RectF(
        textBoxX,
        overlayY,
        textBoxX + textBoxWidth,
        overlayY + mapHeight
    )
    canvas.drawRoundRect(textRect, 12f, 12f, mapPaint)

    // Configure text paints - increased for better readability
    val baseFontSize = width * 0.028f // Responsive font size
    val smallFontSize = width * 0.025f

    val addressPaint = Paint().apply {
        color = AndroidColor.WHITE
        textSize = baseFontSize
        isAntiAlias = true
        typeface = Typeface.DEFAULT
    }

    val coordPaint = Paint().apply {
        color = AndroidColor.WHITE
        textSize = smallFontSize
        isAntiAlias = true
        typeface = Typeface.DEFAULT
    }

    val infoPaint = Paint().apply {
        color = AndroidColor.WHITE
        textSize = smallFontSize
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    // Text positioning
    val textPadding = width * 0.015f
    val textStartX = textBoxX + textPadding
    var currentY = overlayY + textPadding + baseFontSize

    // Extract and format data
    val (mainAddress, plusCode) = if (address.contains(". ")) {
        val parts = address.split(". ")
        Pair(parts[0], parts.getOrNull(1) ?: "")
    } else {
        Pair(address, "")
    }

    // ROW 1: Address (Indian format, multiple lines if needed)
    val addressDisplay = if (address.isEmpty() || address == "Fetching address...") {
        ""
    } else {
        mainAddress
    }

    if (addressDisplay.isNotEmpty()) {
        // Handle multi-line address
        val maxTextWidth = textBoxWidth - (textPadding * 2)
        val addressLines = wrapText(addressDisplay, addressPaint, maxTextWidth)

        for (line in addressLines.take(3)) { // Max 3 lines
            canvas.drawText(line, textStartX, currentY, addressPaint)
            currentY += baseFontSize * 1.2f
        }
    }

    // ROW 2: Coordinates with full precision
    currentY += smallFontSize * 0.5f
    val displayLat = if (latitude != "Fetching...") {
        try {
            String.format("%.6f", latitude.toDouble())
        } catch (e: Exception) {
            "0.000000"
        }
    } else "0.000000"

    val displayLon = if (longitude != "Fetching...") {
        try {
            String.format("%.6f", longitude.toDouble())
        } catch (e: Exception) {
            "0.000000"
        }
    } else "0.000000"

    canvas.drawText("Lat: $displayLat | Lon: $displayLon", textStartX, currentY, coordPaint)
    currentY += smallFontSize * 1.5f

    // ROW 3: Plus Code and Date/Time
    val displayPlusCode = if (plusCode.isNotEmpty()) {
        plusCode
    } else {
        // Generate a simple plus code if not available
        if (latitude != "Fetching..." && longitude != "Fetching...") {
            try {
                val lat = latitude.toDouble()
                val lon = longitude.toDouble()
                "W4PQ+${String.format("%02X", ((lat + lon).toInt() % 256))}"
            } catch (e: Exception) {
                "W4PQ+GH"
            }
        } else {
            "W4PQ+GH"
        }
    }

    val currentDateTime = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.US).format(Date())
    canvas.drawText(displayPlusCode, textStartX, currentY, infoPaint)

    // Draw date/time on the right side
    val dateTimeWidth = infoPaint.measureText(currentDateTime)
    val dateTimeX = textBoxX + textBoxWidth - textPadding - dateTimeWidth
    canvas.drawText(currentDateTime, dateTimeX, currentY, infoPaint)
    currentY += smallFontSize * 1.5f

    // ROW 4: Removed verification code as it's already shown at the top

    return overlayBitmap
}

// Helper function to wrap text to multiple lines
fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var currentLine = ""

    for (word in words) {
        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
        val textWidth = paint.measureText(testLine)

        if (textWidth <= maxWidth) {
            currentLine = testLine
        } else {
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
                currentLine = word
            } else {
                // Single word is too long, add it anyway
                lines.add(word)
            }
        }
    }

    if (currentLine.isNotEmpty()) {
        lines.add(currentLine)
    }

    return lines
}

// Helper function to generate verification code
fun generateVerificationCode(latitude: String, longitude: String): String {
    return if (latitude != "Fetching..." && longitude != "Fetching...") {
        try {
            val lat = latitude.toDouble()
            val lon = longitude.toDouble()
            val timeHash = System.currentTimeMillis() / 1000 / 3600 // Changes every hour
            val hash = (lat * 1000 + lon * 1000 + timeHash).toInt()
            String.format("%06d", Math.abs(hash % 1000000))
        } catch (e: Exception) {
            "000000"
        }
    } else {
        "000000"
    }
}

// Image processing function moved outside the composable
private suspend fun processImageWithRotationAndFilter(
    context: Context,
    imageUri: Uri,
    filter: CameraFilter
): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            Log.d("ImageProcessing", "Starting image processing for: $imageUri")

            // Load the image directly without any resizing to avoid cropping
            val inputStream = context.contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Log.e("ImageProcessing", "Could not open input stream")
                return@withContext null
            }

            // Use BitmapFactory.Options to potentially downsample large images
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            // First, get the dimensions without loading the bitmap
            val dimensionStream = context.contentResolver.openInputStream(imageUri)
            BitmapFactory.decodeStream(dimensionStream, null, options)
            dimensionStream?.close()
            
            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            
            Log.d("ImageProcessing", "Original image dimensions: ${originalWidth}x${originalHeight}")
            
            // Calculate sample size for large images (downscale if larger than 4K)
            val maxDimension = 4096
            val sampleSize = if (originalWidth > maxDimension || originalHeight > maxDimension) {
                val scaleFactor = minOf(originalWidth / maxDimension, originalHeight / maxDimension)
                scaleFactor.coerceAtLeast(1)
            } else {
                1
            }
            
            // Load the bitmap with sample size
            options.apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            
            val originalBitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            if (originalBitmap == null) {
                Log.e("ImageProcessing", "Failed to decode bitmap")
                return@withContext null
            }

            Log.d("ImageProcessing", "Loaded bitmap with sampleSize $sampleSize: ${originalBitmap.width}x${originalBitmap.height}")

            // Check EXIF rotation and apply only if the image actually needs it
            val rotation = getImageRotation(context, imageUri)
            Log.d("ImageProcessing", "EXIF rotation detected: $rotation°")
            
            // Apply rotation if EXIF indicates the image is rotated
            val rotationFixedBitmap = if (rotation != 0f) {
                Log.d("ImageProcessing", "Applying EXIF rotation: $rotation°")
                rotateBitmapByDegrees(originalBitmap, rotation)
            } else {
                Log.d("ImageProcessing", "No rotation needed")
                originalBitmap
            }
            
            // Apply filter if needed
            val finalBitmap = if (filter != CameraFilter.NORMAL) {
                Log.d("ImageProcessing", "Applying filter: ${filter.displayName}")
                val filteredBitmap = CameraFilter.applyFilter(rotationFixedBitmap, filter)

                // Clean up intermediate bitmap if it's different
                if (rotationFixedBitmap != originalBitmap && !rotationFixedBitmap.isRecycled) {
                    rotationFixedBitmap.recycle()
                }

                filteredBitmap
            } else {
                rotationFixedBitmap
            }

            // Clean up original bitmap if it's different from final
            if (originalBitmap != finalBitmap && !originalBitmap.isRecycled) {
                originalBitmap.recycle()
            }

            Log.d("ImageProcessing", "Final processed bitmap: ${finalBitmap.width}x${finalBitmap.height}")
            return@withContext finalBitmap

        } catch (e: Exception) {
            Log.e("ImageProcessing", "Error processing image: ${e.message}", e)
            return@withContext null
        }
    }
}

// Helper function to get image rotation from EXIF data
private fun getImageRotation(context: Context, imageUri: Uri): Float {
    return try {
        val inputStream = context.contentResolver.openInputStream(imageUri)
        val exif = ExifInterface(inputStream!!)
        inputStream.close()

        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        Log.e("ImageProcessing", "Error reading EXIF orientation: ${e.message}", e)
        0f
    }
}

// Helper function to rotate bitmap by degrees
private fun rotateBitmapByDegrees(bitmap: Bitmap, degrees: Float): Bitmap {
    return if (degrees == 0f) {
        bitmap
    } else {
        val matrix = Matrix().apply { postRotate(degrees) }
        try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            Log.e("ImageProcessing", "Error rotating bitmap: ${e.message}", e)
            bitmap // Return original if rotation fails
        }
    }
}


// Helper function to calculate sample size for efficient bitmap loading
private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}


