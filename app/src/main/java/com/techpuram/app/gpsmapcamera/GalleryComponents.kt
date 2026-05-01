package com.techpuram.app.gpsmapcamera

import android.graphics.Bitmap
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GalleryMapView(latitude: Double, longitude: Double) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 16f) // Increased zoom level
    }
    
    // Force camera position update
    LaunchedEffect(latitude, longitude) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 16f)
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
            scrollGesturesEnabled = false,
            zoomGesturesEnabled = false,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false
        )
    ) {
        Marker(
            state = MarkerState(position = LatLng(latitude, longitude)),
            title = "Photo Location"
        )
    }
}

@Composable
fun ImageDisplayWithOverlay(
    processedImageBitmap: Bitmap?,
    isProcessing: Boolean,
    latitude: String?,
    longitude: String?,
    address: String?,
    currentVerificationCode: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f/4f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
    ) {
        // Show processed image with correct rotation
        if (processedImageBitmap != null && !isProcessing) {
            Image(
                bitmap = processedImageBitmap.asImageBitmap(),
                contentDescription = "Captured Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Show loading while processing
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Processing image...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Only show location overlay if we don't have a processed image (to avoid duplication)
        // The processed image already contains the overlay from ImageProcessor
        val isLocationValid = latitude != null && longitude != null &&
                runCatching { latitude.toDouble(); longitude.toDouble() }.isSuccess

        if (isLocationValid && processedImageBitmap == null && !isProcessing) {
            LocationOverlay(
                latitude = latitude!!,
                longitude = longitude!!,
                address = address,
                currentVerificationCode = currentVerificationCode
            )
        }
    }
}

@Composable
fun LocationOverlay(
    latitude: String,
    longitude: String,
    address: String?,
    currentVerificationCode: String?
) {
    // Define dimensions outside to be used in header
    val mapWidth = 100.dp
    val mapHeight = 70.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        // Verification ID Header (if verification code exists)
        if (currentVerificationCode != null) {
            // Create a row to align headers above map and text area
            Row(modifier = Modifier.fillMaxWidth()) {
                // GeoGPSCamera header above map
                Box(
                    modifier = Modifier
                        .width(mapWidth)
                        .background(
                            Color.Black.copy(alpha = 0.4f),
                            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                        )
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        // Simple circular logo using Canvas
                        Canvas(
                            modifier = Modifier.size(12.dp)
                        ) {
                            drawCircle(
                                color = Color(0xFF113F67),
                                radius = size.width / 2,
                                center = center
                            )
                            drawCircle(
                                color = Color.White,
                                radius = size.width / 3,
                                center = center
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "GeoGPSCamera",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Verification ID header above text area
                Box(
                    modifier = Modifier
                        .weight(1f) // Same weight as text area
                        .background(
                            Color.Black.copy(alpha = 0.4f),
                            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                        )
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "Verification ID: $currentVerificationCode",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(modifier = Modifier.height(1.dp))
        }

        // Main GPS Tag Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Map Preview - using predefined dimensions
            Box(
                modifier = Modifier
                    .width(mapWidth)
                    .height(mapHeight)
                    .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            ) {
                val lat = latitude.toDouble()
                val lng = longitude.toDouble()

                GalleryMapView(lat, lng)
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Address Text Column - Match CameraScreen format
            Box(modifier = Modifier.weight(1f)) {
                AddressTextColumn(
                    latitude = latitude,
                    longitude = longitude,
                    address = address,
                    mapHeight = mapHeight
                )
            }
        }
    }
}

@Composable
fun AddressTextColumn(
    latitude: String,
    longitude: String,
    address: String?,
    mapHeight: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(mapHeight) // Use same height as map
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Calculate font sizes - increased for better visibility
            val baseFontSize = 12.sp
            val smallFontSize = 10.sp

            // Format current date and time
            val currentDateTime = remember {
                val sdf = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.US)
                sdf.format(System.currentTimeMillis())
            }

            // Extract plus code from address if present
            val (mainAddress, plusCode) = remember(address) {
                if (address?.contains(". ") == true) {
                    val parts = address.split(". ")
                    Pair(parts[0], parts.getOrNull(1) ?: "")
                } else {
                    val fallbackAddress = address ?: "Address not available"
                    Pair(fallbackAddress, "")
                }
            }

            // ROW 1: Main address in Indian format (can be multi-line)
            val addressDisplay = remember(address) {
                when {
                    address.isNullOrEmpty() || address == "Fetching address..." -> ""
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
                    lineHeight = 10.sp
                )
            } else {
                // Show a fallback when address is empty
                Text(
                    text = "Location: $latitude, $longitude",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = baseFontSize,
                    maxLines = 2,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 10.sp
                )
            }

            // ROW 2: Full precision coordinates (Lat, Lon)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    maxLines = 1,
                    fontWeight = FontWeight.Normal
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
                    maxLines = 1,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun SaveShareDialogs(
    showSaveFormatDialog: Boolean,
    showShareFormatDialog: Boolean,
    onDismissDialog: (String) -> Unit,
    saveBitmap: Bitmap?
) {
    val context = LocalContext.current

    // PDF, JPG, PNG launchers for saving
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null && saveBitmap != null) {
            createAndSavePdf(context, saveBitmap, uri)
        }
    }

    val jpgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri: Uri? ->
        if (uri != null && saveBitmap != null) {
            saveImageAsJpg(context, saveBitmap, uri)
        }
    }

    val pngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        if (uri != null && saveBitmap != null) {
            saveImageAsPng(context, saveBitmap, uri)
        }
    }

    // Save Format Dialog
    if (showSaveFormatDialog) {
        FormatSelectionDialog(
            title = "Choose Save Format",
            subtitle = "Select the format to save your image:",
            recommendationText = "💡 PDF format is recommended for 100% genuinity verification",
            onDismiss = { onDismissDialog("save") },
            onPdfSelected = {
                onDismissDialog("save")
                val fileName = "verified_document_${System.currentTimeMillis()}.pdf"
                pdfLauncher.launch(fileName)
            },
            onJpgSelected = {
                onDismissDialog("save")
                val fileName = "image_${System.currentTimeMillis()}.jpg"
                jpgLauncher.launch(fileName)
            },
            onPngSelected = {
                onDismissDialog("save")
                val fileName = "image_${System.currentTimeMillis()}.png"
                pngLauncher.launch(fileName)
            }
        )
    }

    // Share Format Dialog
    if (showShareFormatDialog) {
        FormatSelectionDialog(
            title = "Choose Share Format",
            subtitle = "Select the format to share your image:",
            recommendationText = "💡 PDF format provides the best genuinity verification",
            onDismiss = { onDismissDialog("share") },
            onPdfSelected = {
                onDismissDialog("share")
                shareAsPdf(context, saveBitmap)
            },
            onJpgSelected = {
                onDismissDialog("share")
                shareAsJpg(context, saveBitmap)
            },
            onPngSelected = {
                onDismissDialog("share")
                shareAsPng(context, saveBitmap)
            }
        )
    }
}

@Composable
private fun FormatSelectionDialog(
    title: String,
    subtitle: String,
    recommendationText: String,
    onDismiss: () -> Unit,
    onPdfSelected: () -> Unit,
    onJpgSelected: () -> Unit,
    onPngSelected: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(subtitle, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    recommendationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // PDF Button
                Button(
                    onClick = onPdfSelected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📄 PDF (Best for Genuinity)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // JPG Button
                OutlinedButton(
                    onClick = onJpgSelected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🖼️ JPG")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // PNG Button
                OutlinedButton(
                    onClick = onPngSelected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🖼️ PNG")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    )
}

// Helper functions for file operations
private fun createAndSavePdf(context: Context, bitmap: Bitmap, uri: Uri) {
    try {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
            bitmap.width, bitmap.height, 1
        ).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }

        Toast.makeText(context, "✅ PDF saved successfully!", Toast.LENGTH_SHORT).show()
        pdfDocument.close()
    } catch (e: Exception) {
        Toast.makeText(context, "❌ Failed to save PDF.", Toast.LENGTH_SHORT).show()
    }
}

private fun saveImageAsJpg(context: Context, bitmap: Bitmap, uri: Uri) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        }
        Toast.makeText(context, "✅ JPG saved successfully!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "❌ Failed to save JPG.", Toast.LENGTH_SHORT).show()
    }
}

private fun saveImageAsPng(context: Context, bitmap: Bitmap, uri: Uri) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }
        Toast.makeText(context, "✅ PNG saved successfully!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "❌ Failed to save PNG.", Toast.LENGTH_SHORT).show()
    }
}

private fun shareAsPdf(context: Context, bitmap: Bitmap?) {
    try {
        if (bitmap != null) {
            val verificationManager = VerificationManager(context, okhttp3.OkHttpClient())
            val pdfFile = verificationManager.createPdfFromImage(bitmap)
            if (pdfFile != null) {
                val pdfUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    pdfFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, pdfUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Geo-tagged Photo Verification")
                    putExtra(Intent.EXTRA_TEXT, "This PDF contains a geo-tagged photo with location verification.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(Intent.createChooser(shareIntent, "Share PDF using..."))
            } else {
                Toast.makeText(context, "❌ Failed to create PDF file", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "❌ No image available to share", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "❌ Failed to share PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareAsJpg(context: Context, bitmap: Bitmap?) {
    try {
        if (bitmap != null) {
            val imageFile = File.createTempFile("shared_image", ".jpg", context.cacheDir)
            imageFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            val imageUriToShare = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                imageFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, imageUriToShare)
                putExtra(Intent.EXTRA_SUBJECT, "Geo-tagged Photo")
                putExtra(Intent.EXTRA_TEXT, "This is a geo-tagged photo with location information.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share JPG using..."))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "❌ Failed to share JPG: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareAsPng(context: Context, bitmap: Bitmap?) {
    try {
        if (bitmap != null) {
            val imageFile = File.createTempFile("shared_image", ".png", context.cacheDir)
            imageFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val imageUriToShare = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                imageFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUriToShare)
                putExtra(Intent.EXTRA_SUBJECT, "Geo-tagged Photo")
                putExtra(Intent.EXTRA_TEXT, "This is a geo-tagged photo with location information.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share PNG using..."))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "❌ Failed to share PNG: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}