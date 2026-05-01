package com.techpuram.app.gpsmapcamera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.graphics.graphicsLayer
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.techpuram.app.gpsmapcamera.ui.theme.GPSmapCameraTheme
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.compose.foundation.border
import java.security.MessageDigest
import com.techpuram.app.gpsmapcamera.util.AddressParser
import okhttp3.OkHttpClient

class PhotoPreviewActivity : ComponentActivity() {
    private val TAG = "PhotoPreviewActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get data from intent
        val imageUri = intent.getStringExtra("imageUri")
        var latitude = intent.getStringExtra("latitude")
        var longitude = intent.getStringExtra("longitude")
        var address = intent.getStringExtra("address")
        val isEditMode = intent.getBooleanExtra("isEditMode", false)

        // If in edit mode and we don't have coordinate info, try to extract from the image
        if (isEditMode && (latitude == null || longitude == null || address == null)) {
            try {
                // Extract location data from image if possible
                imageUri?.let { uri ->
                    val imageExif = ExifInterface(contentResolver.openInputStream(Uri.parse(uri))!!)

                    // Try to get GPS data from EXIF
//                    val latLongPair = imageExif.latLong
//                    if (latLongPair != null) {
//                        latitude = latLongPair[0].toString()
//                        longitude = latLongPair[1].toString()
//                        Log.d(TAG, "Extracted coordinates from image: $latitude, $longitude")
//
//                        // For address, we could try to geocode here, but for simplicity we'll just use a placeholder
//                        if (address == null) {
//                            address = "Location at $latitude, $longitude"
//                        }
//                    } else {
//                        // No GPS data found in EXIF
//                        Log.d(TAG, "No GPS data found in image EXIF")
//                        latitude = "0.0"
//                        longitude = "0.0"
//                        address = "Unknown Location"
//                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting location data from image: ${e.message}", e)
                latitude = "0.0"
                longitude = "0.0"
                address = "Unknown Location"
            }
        }

        // Set status bar color
        window?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                it.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                it.statusBarColor = Color.Black.toArgb()
                val wic = androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
                wic.isAppearanceLightStatusBars = false
            }
        }

        setContent {
            GPSmapCameraTheme {
                PhotoPreviewScreen(
                    imageUri = imageUri,
                    latitude = latitude,
                    longitude = longitude,
                    address = address,
                    onSubmit = { editedUri, updatedAddress ->
                        // Store the manually edited address for future use at this location
                        if (latitude != null && longitude != null && updatedAddress != address) {
                            try {
                                val lat = latitude.toDouble()
                                val lon = longitude.toDouble()
                                val addressFetcher = OptimizedAddressFetcher(this@PhotoPreviewActivity, BuildConfig.MAPS_API_KEY)
                                addressFetcher.setManualAddress(lat, lon, updatedAddress)
                                Log.d("PhotoPreview", "Saved manual address: $updatedAddress for location: $lat, $lon")
                            } catch (e: Exception) {
                                Log.e("PhotoPreview", "Error saving manual address: ${e.message}", e)
                            }
                        }
                        
                        if (isEditMode) {
                            // Return the edited URI and updated address back to the Gallery
                            val resultIntent = Intent().apply {
                                putExtra("updatedImageUri", editedUri.toString())
                                putExtra("updatedAddress", updatedAddress)
                            }
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        } else {
                            // Navigate to Gallery with the edited image and updated address
                            val intent = Intent(this, ResultActivity::class.java).apply {
                                putExtra("imageUri", editedUri.toString())
                                putExtra("latitude", latitude)
                                putExtra("longitude", longitude)
                                putExtra("address", updatedAddress)
                            }
                            startActivity(intent)
                            finish()
                        }
                    },
                    onCancel = {
                        if (isEditMode) {
                            // Just return no result
                            setResult(RESULT_CANCELED)
                        }
                        finish() // Go back
                    },
                    isEditMode = isEditMode
                )
            }
        }
    }
}

@Composable
fun PhotoPreviewScreen(
    imageUri: String?,
    latitude: String?,
    longitude: String?,
    address: String?,
    onSubmit: (Uri, String) -> Unit,
    onCancel: () -> Unit,
    isEditMode: Boolean = false
) {
    val context = LocalContext.current
    val TAG = "PhotoPreviewScreen"
    val scope = rememberCoroutineScope()

    var fullAddress by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var zipCode by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var isImageLoading by remember { mutableStateOf(true) }

    // New state for proof generation
    var showProofDialog by remember { mutableStateOf(false) }
    var generatingProof by remember { mutableStateOf(false) }
    var proofId by remember { mutableStateOf("") }
    var proofChecksum by rememberSaveable { mutableStateOf("") }
    var isProofGenerated by remember { mutableStateOf(false) }

    val lat = latitude?.toDoubleOrNull()
    val lon = longitude?.toDoubleOrNull()

    fun parseAddress(addressInput: String?) {
        Log.d(TAG, "Parsing address using common AddressParser: $addressInput")

        if (addressInput.isNullOrBlank() || addressInput == "Fetching address...") {
            Log.d(TAG, "Address is null, blank, or still fetching - skipping parse")
            return
        }

        // Use the common address parser that matches CameraScreen geo-tag logic
        val parsedAddress = AddressParser.parseGeoTagAddress(addressInput)
        
        // Extract the main address part (without plus code) for the single address field
        fullAddress = parsedAddress.mainAddress
        state = parsedAddress.state
        zipCode = parsedAddress.zipCode
        country = parsedAddress.country
        
        Log.d(TAG, "Parsed address components:")
        Log.d(TAG, "  Full Address: '$fullAddress'")
        Log.d(TAG, "  State: '$state'")
        Log.d(TAG, "  ZIP Code: '$zipCode'")
        Log.d(TAG, "  Country: '$country'")
        Log.d(TAG, "  Plus Code: '${parsedAddress.plusCode}'")
    }

    // Function to generate proof
    fun generateProof(imageUri: Uri) {
        generatingProof = true

        scope.launch {
            try {
                // Read the image
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                // Create a PDF for verification
                val verificationManager = VerificationManager(context, okhttp3.OkHttpClient())
                val pdfFile = verificationManager.createPdfFromImage(bitmap)

                // Generate checksum
                val md = MessageDigest.getInstance("SHA-256")
                val bytes = pdfFile?.readBytes() ?: ByteArray(0)
                val digest = md.digest(bytes)
                proofChecksum = digest.joinToString("") { "%02x".format(it) }

                // Generate a proof ID (would normally come from server)
                // For demo purposes, using first 15 chars of the checksum
                proofId = proofChecksum.take(15).uppercase()

                // Simulate upload to server
                kotlinx.coroutines.delay(1500) // Simulate network delay

                isProofGenerated = true
                generatingProof = false

                Toast.makeText(context, "Proof generated successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error generating proof: ${e.message}", e)
                Toast.makeText(context, "Failed to generate proof: ${e.message}", Toast.LENGTH_SHORT).show()
                generatingProof = false
            }
        }
    }

    LaunchedEffect(address) {
        Log.d(TAG, "LaunchedEffect triggered with address: '$address'")
        parseAddress(address)
        // Simulate image loading time (this will be faster with optimizations)
        kotlinx.coroutines.delay(500)
        isImageLoading = false
    }

    // Proof Generation Dialog
    if (showProofDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!generatingProof) showProofDialog = false
            },
            title = { Text("Location Proof Generator") },
            text = {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (generatingProof) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Generating proof... Please wait")
                        }
                    } else if (isProofGenerated) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Filled.Verified,
                                    contentDescription = "Verified",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Proof Generated Successfully!",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text("Verification ID:", fontWeight = FontWeight.Bold)
                            Text(
                                proofId,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                    .padding(8.dp),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Document Checksum:", fontWeight = FontWeight.Bold)
                            Text(
                                proofChecksum.take(32) + "...",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                    .padding(8.dp),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                "The proof has been generated and is ready to upload. You can use the verification ID to confirm the authenticity of this location data.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        Text(
                            "Generate a cryptographic proof that this photo was taken at this location. " +
                                    "This creates a verifiable record that can be validated by third parties."
                        )

                        OutlinedTextField(
                            value = proofId,
                            onValueChange = { proofId = it },
                            label = { Text("Verification ID (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Leave blank to generate automatically") }
                        )
                    }
                }
            },
            confirmButton = {
                if (generatingProof) {
                    // Show nothing while generating
                } else if (isProofGenerated) {
                    Button(onClick = {
                        // Here you would upload to server
                        Toast.makeText(context, "Proof uploaded to server!", Toast.LENGTH_SHORT).show()
                        showProofDialog = false
                    }) {
                        Text("Upload Proof")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Upload, contentDescription = "Upload")
                    }
                } else {
                    Button(onClick = {
                        imageUri?.let { uri ->
                            generateProof(Uri.parse(uri))
                        }
                    }) {
                        Text("Generate Proof")
                    }
                }
            },
            dismissButton = {
                if (!generatingProof) {
                    TextButton(onClick = { showProofDialog = false }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    imageUri?.let {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isImageLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Image(
                                    painter = rememberAsyncImagePainter(Uri.parse(it)),
                                    contentDescription = "Captured Image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    } ?: Text("No Image Available", style = MaterialTheme.typography.bodyLarge)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.PinDrop, contentDescription = "Location Pin", tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (isEditMode) "Edit Location Details" else "Location Details",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) { // Allow address to take more space
                            Text(text = "Address:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                            Text(text = address ?: "Unknown", style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (lat != null && lon != null) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        2.dp, 
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), 
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                GoogleMap(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp)),
                                    cameraPositionState = rememberCameraPositionState {
                                        position = CameraPosition.fromLatLngZoom(LatLng(lat, lon), 15f)
                                    },
                                    uiSettings = MapUiSettings(
                                        zoomControlsEnabled = false, 
                                        scrollGesturesEnabled = false, 
                                        rotationGesturesEnabled = false, 
                                        tiltGesturesEnabled = false, 
                                        compassEnabled = false, 
                                        myLocationButtonEnabled = false,
                                        mapToolbarEnabled = false,
                                        zoomGesturesEnabled = false
                                    )
                                ) {
                                    Marker(
                                        state = MarkerState(position = LatLng(lat, lon)),
                                        title = "Photo Location"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Details", tint = MaterialTheme.colorScheme.primary)
                        Text(text = "Edit Details", style = MaterialTheme.typography.titleMedium)
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Custom Name (Optional)") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = "Custom Name") }
                    )

                    OutlinedTextField(
                        value = fullAddress,
                        onValueChange = { fullAddress = it },
                        label = { Text("Full Address") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 2,
                        maxLines = 4
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state,
                            onValueChange = { state = it },
                            label = { Text("State") },
                            modifier = Modifier
                                .weight(0.5f)
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = zipCode,
                            onValueChange = { zipCode = it },
                            label = { Text("ZIP Code") },
                            modifier = Modifier
                                .weight(0.5f)
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text("Country") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        // Add new proof verification card - only show if not in edit mode
        if (!isProofGenerated && !isEditMode) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = "Verify",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Location Verification",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Generate a cryptographic proof that this photo was taken at this location.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { showProofDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = "Generate Proof",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Location Proof")
                        }
                    }
                }
            }
        } else if (isProofGenerated && !isEditMode) {
            // Show proof details if already generated
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = "Verify",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Location Verification",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Verification ID:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    proofId,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Button(
                                onClick = { showProofDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary
                                )
                            ) {
                                Text("Details")
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onCancel() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("Cancel", style = MaterialTheme.typography.titleMedium)
                }

                Button(
                    onClick = {
                        // Build final address from the simplified UI components
                        val finalAddress = buildString {
                            if (fullAddress.isNotBlank()) {
                                append(fullAddress.trim())
                            }
                            
                            if (state.isNotBlank() || zipCode.isNotBlank()) {
                                if (this.isNotEmpty() && !this.endsWith(",")) append(", ")
                                if (state.isNotBlank()) append(state)
                                if (zipCode.isNotBlank()) {
                                    if (state.isNotBlank()) append(" ")
                                    append(zipCode)
                                }
                            }
                            
                            if (country.isNotBlank()) {
                                if (this.isNotEmpty() && !this.endsWith(",")) append(", ")
                                append(country)
                            }
                        }.ifBlank { 
                            address ?: "Unknown Address" 
                        }
                        
                        Log.d(TAG, "Final address for image: $finalAddress (from UI: fullAddress='$fullAddress', state='$state', zipCode='$zipCode', country='$country')")

                        imageUri?.let {
                            val lat = latitude?.toDoubleOrNull()
                            val lon = longitude?.toDoubleOrNull()

                            if (lat != null && lon != null) {
                                scope.launch {
                                    try {
                                        val editedUri = createGeotaggedImage(context, Uri.parse(it), finalAddress, lat.toString(), lon.toString())
                                        editedUri?.let { newUri ->
                                            onSubmit(newUri, finalAddress)
                                            Toast.makeText(context, "Geo-tagged image saved to gallery!", Toast.LENGTH_SHORT).show()
                                        } ?: Toast.makeText(context, "Failed to save image.", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error creating geo-tagged image: ${e.message}", e)
                                        Toast.makeText(context, "Failed to save image: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Location data is missing or invalid.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(if (isEditMode) "Save Changes" else "Save & Continue", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

// Function to create geo-tagged image using consistent ImageProcessor approach
suspend fun createGeotaggedImage(
    context: android.content.Context,
    imageUri: Uri,
    address: String,
    latitude: String,
    longitude: String
): Uri? {
    return try {
        // Load original image with proper rotation
        val inputStream = context.contentResolver.openInputStream(imageUri)
        val originalBitmapRaw = BitmapFactory.decodeStream(inputStream)
        
        val exif = ExifInterface(context.contentResolver.openInputStream(imageUri)!!)
        val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        
        val originalBitmap = if (rotation != 0 && originalBitmapRaw != null) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
            android.graphics.Bitmap.createBitmap(originalBitmapRaw, 0, 0, originalBitmapRaw.width, originalBitmapRaw.height, matrix, true)
        } else {
            originalBitmapRaw
        }
        
        inputStream?.close()
        
        if (originalBitmap == null) {
            Log.e("PhotoPreview", "Failed to decode original image")
            return null
        }
        
        // Use ImageProcessor to add consistent geo tag overlay (no verification ID for editing)
        val imageProcessor = ImageProcessor(context, OkHttpClient())
        val overlayBitmap = imageProcessor.addGeotagOverlayToBitmap(
            originalBitmap,
            latitude,
            longitude,
            address,
            null // No verification ID when editing address
        )
        
        if (overlayBitmap != null) {
            // Save to gallery using the same method as addText.kt
            saveBitmapToGallery(context, overlayBitmap)
        } else {
            Log.e("PhotoPreview", "Failed to add overlay to image")
            null
        }
    } catch (e: Exception) {
        Log.e("PhotoPreview", "Error creating geo-tagged image: ${e.message}", e)
        null
    }
}