package com.techpuram.app.gpsmapcamera

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.techpuram.app.gpsmapcamera.ui.theme.GPSmapCameraTheme
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ResultActivity : ComponentActivity() {
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var verificationManager: VerificationManager
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize components
        imageProcessor = ImageProcessor(this, httpClient)
        verificationManager = VerificationManager(this, httpClient)

        // Get image file path and location data from intent
        // Check both "image_file_path" and "imageUri" for backward compatibility
        val imageFilePath = intent.getStringExtra("image_file_path") ?: intent.getStringExtra("imageUri")
        val latitude = intent.getStringExtra("latitude") ?: "0.0"
        val longitude = intent.getStringExtra("longitude") ?: "0.0"
        val address = intent.getStringExtra("address") ?: ""

        Log.d("ResultActivity", "Received image: $imageFilePath")
        Log.d("ResultActivity", "Location: $latitude, $longitude")
        Log.d("ResultActivity", "Address: $address")

        if (imageFilePath == null) {
            Toast.makeText(this, "Error: No image file provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            GPSmapCameraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GalleryScreen(
                        imageFilePath = imageFilePath,
                        latitude = latitude,
                        longitude = longitude,
                        address = address
                    )
                }
            }
        }
    }

    @Composable
    private fun GalleryScreen(
        imageFilePath: String,
        latitude: String,
        longitude: String,
        address: String
    ) {
        var originalImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var processedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var isProcessing by remember { mutableStateOf(true) }
        var currentVerificationCode by remember { mutableStateOf<String?>(null) }
        var showSaveFormatDialog by remember { mutableStateOf(false) }
        var showShareFormatDialog by remember { mutableStateOf(false) }
        var isGeneratingVerification by remember { mutableStateOf(false) }
        var showVerificationDialog by remember { mutableStateOf(false) }

        val context = LocalContext.current

        // Load and process image (without verification initially)
        LaunchedEffect(imageFilePath) {
            try {
                // Load original image
                val originalBitmap = loadAndRotateImage(imageFilePath)
                originalImageBitmap = originalBitmap

                if (originalBitmap != null) {
                    // Add geotag overlay WITHOUT verification code initially
                    val overlayBitmap = imageProcessor.addGeotagOverlayToBitmap(
                        originalBitmap,
                        latitude,
                        longitude,
                        address,
                        null // No verification code initially
                    )

                    processedImageBitmap = overlayBitmap
                    isProcessing = false
                } else {
                    isProcessing = false
                    Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ResultActivity", "Error processing image: ${e.message}", e)
                isProcessing = false
                Toast.makeText(context, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Function to generate verification code
        fun generateVerificationCode() {
            isGeneratingVerification = true
            lifecycleScope.launch {
                try {
                    // Start verification process
                    val sessionResult = verificationManager.requestSessionKey(latitude, longitude, address)
                    currentVerificationCode = sessionResult?.second

                    Log.d("ResultActivity", "Verification code generated: $currentVerificationCode")

                    // Regenerate image with verification code
                    originalImageBitmap?.let { original ->
                        val overlayBitmap = imageProcessor.addGeotagOverlayToBitmap(
                            original,
                            latitude,
                            longitude,
                            address,
                            currentVerificationCode
                        )
                        processedImageBitmap = overlayBitmap

                        // Send verification data if we have verification code
                        if (currentVerificationCode != null && sessionResult?.first != null && overlayBitmap != null) {
                            val checksum = verificationManager.calculateImageChecksum(overlayBitmap)
                            val thumbnail = verificationManager.createThumbnail(overlayBitmap)
                            
                            if (checksum != null && thumbnail != null) {
                                verificationManager.sendVerificationData(
                                    sessionResult.first, 
                                    checksum, 
                                    thumbnail
                                )
                            }
                        }
                    }
                    
                    Toast.makeText(context, "Verification code generated successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("ResultActivity", "Error generating verification: ${e.message}", e)
                    Toast.makeText(context, "Failed to generate verification code", Toast.LENGTH_SHORT).show()
                } finally {
                    isGeneratingVerification = false
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { finish() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Text(
                    text = "Geo-tagged Result",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(modifier = Modifier.size(40.dp)) // Placeholder for alignment
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Image Display with Overlay
            ImageDisplayWithOverlay(
                processedImageBitmap = processedImageBitmap,
                isProcessing = isProcessing,
                latitude = latitude,
                longitude = longitude,
                address = address,
                currentVerificationCode = currentVerificationCode
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Verification Button (if not generated)
            if (currentVerificationCode == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { generateVerificationCode() },
                        enabled = !isProcessing && !isGeneratingVerification,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9C27B0) // Purple color
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(50.dp)
                    ) {
                        if (isGeneratingVerification) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Generating...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Generate Verification",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Generate Verification",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Edit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(
                    onClick = {
                        // Navigate back to edit the location/address
                        val intent = Intent(this@ResultActivity, PhotoPreviewActivity::class.java).apply {
                            putExtra("imageUri", imageFilePath)
                            putExtra("latitude", latitude)
                            putExtra("longitude", longitude)
                            putExtra("address", address)
                        }
                        startActivity(intent)
                        finish()
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Location",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Edit Location",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Save Button
                Button(
                    onClick = { showSaveFormatDialog = true },
                    enabled = processedImageBitmap != null && !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Share Button
                Button(
                    onClick = { showShareFormatDialog = true },
                    enabled = processedImageBitmap != null && !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Information Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Image Information",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    InfoRow("📍 Location", "$latitude, $longitude")
                    if (address.isNotEmpty()) {
                        InfoRow("🏠 Address", address)
                    }
                    currentVerificationCode?.let { code ->
                        InfoRow("🔐 Verification", code)
                    }
                    InfoRow("📊 Status", if (isProcessing) "Processing..." else "Ready")
                }
            }
        }

        // Save and Share dialogs
        SaveShareDialogs(
            showSaveFormatDialog = showSaveFormatDialog,
            showShareFormatDialog = showShareFormatDialog,
            onDismissDialog = { dialogType ->
                when (dialogType) {
                    "save" -> showSaveFormatDialog = false
                    "share" -> showShareFormatDialog = false
                }
            },
            saveBitmap = processedImageBitmap
        )
    }

    @Composable
    private fun InfoRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp
            )
            Text(
                text = value,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }

    private suspend fun loadAndRotateImage(imagePath: String): Bitmap? {
        return try {
            val bitmap: Bitmap?
            val exif: ExifInterface
            
            // Check if it's a URI or file path
            if (imagePath.startsWith("content://") || imagePath.startsWith("file://")) {
                // Handle URI
                val uri = Uri.parse(imagePath)
                Log.d("ResultActivity", "Loading image from URI: $uri")
                
                // Load bitmap from URI
                val inputStream = contentResolver.openInputStream(uri)
                bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                if (bitmap == null) {
                    Log.e("ResultActivity", "Failed to decode bitmap from URI: $uri")
                    return null
                }
                
                // Get EXIF from URI
                val exifInputStream = contentResolver.openInputStream(uri)
                exif = ExifInterface(exifInputStream!!)
                exifInputStream.close()
            } else {
                // Handle file path
                val file = File(imagePath)
                Log.d("ResultActivity", "Loading image from file: $imagePath")
                
                if (!file.exists()) {
                    Log.e("ResultActivity", "Image file does not exist: $imagePath")
                    return null
                }

                // Load the bitmap
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(imagePath, options)

                // Calculate sample size to avoid memory issues
                options.inSampleSize = calculateInSampleSize(options, 1080, 1920)
                options.inJustDecodeBounds = false

                bitmap = BitmapFactory.decodeFile(imagePath, options)
                if (bitmap == null) {
                    Log.e("ResultActivity", "Failed to decode bitmap from file: $imagePath")
                    return null
                }
                
                // Get EXIF from file
                exif = ExifInterface(imagePath)
            }

            // Check and apply EXIF rotation
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            if (!matrix.isIdentity) {
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                bitmap.recycle()
                return rotatedBitmap
            }

            return bitmap
        } catch (e: Exception) {
            Log.e("ResultActivity", "Error loading and rotating image: ${e.message}", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}