package com.techpuram.app.gpsmapcamera.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.MutableState
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import com.google.maps.GeoApiContext
import com.google.maps.GeocodingApi
import com.techpuram.app.gpsmapcamera.ui.CameraFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.MediaStoreOutputOptions

/**
 * Get address from coordinates using Google Geocoding API
 */
suspend fun getAddress(latitude: Double, longitude: Double, apiKey: String): String? = withContext(Dispatchers.IO) {
    val context = GeoApiContext.Builder().apiKey(apiKey).build()
    try {
        val results = GeocodingApi.reverseGeocode(context, com.google.maps.model.LatLng(latitude, longitude)).await()

        if (results.isNotEmpty()) {
            Log.d("GeoLocation", "✅ Found ${results.size} results")

            // Log all results for debugging
            results.forEachIndexed { index, result ->
                Log.d("GeoLocation", "Result $index: ${result.formattedAddress}")
                Log.d("GeoLocation", "Types: ${result.types.joinToString(", ")}")
            }

            // Build the complete formatted address
            val formattedAddress = buildCompleteFormattedAddress(results, latitude, longitude)
            Log.d("GeoLocation", "✅ Final formatted address: $formattedAddress")

            return@withContext formattedAddress
        } else {
            Log.e("GeoLocation", "⚠️ No address found for the given coordinates.")
            return@withContext null
        }
    } catch (e: Exception) {
        Log.e("GeoLocation", "❌ Error fetching address: ${e.message}", e)
        return@withContext null
    }
}

private fun buildCompleteFormattedAddress(
    results: Array<com.google.maps.model.GeocodingResult>,
    latitude: Double,
    longitude: Double
): String {
    // Find the best street address result
    val streetAddressResult = results.find { result ->
        result.types.contains(com.google.maps.model.AddressType.STREET_ADDRESS) ||
                result.types.contains(com.google.maps.model.AddressType.PREMISE)
    } ?: results[0]

    // Find plus code result
    val plusCodeResult = results.find { result ->
        result.types.contains(com.google.maps.model.AddressType.PLUS_CODE) ||
                result.formattedAddress.matches(Regex("^[A-Z0-9]+\\+[A-Z0-9]+.*"))
    }

    // Extract plus code from any result or generate one
    val plusCode = extractPlusCode(results) ?: generatePlusCode(latitude, longitude)

    // Build address components from the best result
    val addressComponents = extractAddressComponents(streetAddressResult)

    // Format the complete address
    return buildFormattedString(addressComponents, plusCode)
}

private fun extractAddressComponents(result: com.google.maps.model.GeocodingResult): AddressComponents {
    val components = AddressComponents()

    result.addressComponents.forEach { component ->
        when {
            component.types.contains(com.google.maps.model.AddressComponentType.STREET_NUMBER) ||
                    component.types.contains(com.google.maps.model.AddressComponentType.PREMISE) -> {
                components.streetNumber = component.longName
            }
            component.types.contains(com.google.maps.model.AddressComponentType.ROUTE) -> {
                components.route = component.longName
            }
            component.types.contains(com.google.maps.model.AddressComponentType.SUBLOCALITY_LEVEL_2) -> {
                components.sublocalityLevel2 = component.longName // JP Nagar
            }
            component.types.contains(com.google.maps.model.AddressComponentType.SUBLOCALITY_LEVEL_1) -> {
                components.sublocalityLevel1 = component.longName // Avaniyapuram
            }
            component.types.contains(com.google.maps.model.AddressComponentType.LOCALITY) -> {
                components.locality = component.longName // Madurai
            }
            component.types.contains(com.google.maps.model.AddressComponentType.ADMINISTRATIVE_AREA_LEVEL_1) -> {
                components.state = component.longName // Tamil Nadu
            }
            component.types.contains(com.google.maps.model.AddressComponentType.COUNTRY) -> {
                components.country = component.longName // India
            }
            component.types.contains(com.google.maps.model.AddressComponentType.POSTAL_CODE) -> {
                components.postalCode = component.longName // 625012
            }
        }
    }

    Log.d("AddressComponents", "Extracted components: $components")
    return components
}

private fun extractPlusCode(results: Array<com.google.maps.model.GeocodingResult>): String? {
    // Try to find plus code from results
    results.forEach { result ->
        // Check if it's a plus code result
        if (result.types.contains(com.google.maps.model.AddressType.PLUS_CODE)) {
            val match = Regex("([A-Z0-9]+\\+[A-Z0-9]+)").find(result.formattedAddress)
            if (match != null) {
                return match.value
            }
        }

        // Check if formatted address starts with plus code
        val match = Regex("^([A-Z0-9]+\\+[A-Z0-9]+)").find(result.formattedAddress)
        if (match != null) {
            return match.value
        }
    }

    // Also check in address components
    results.forEach { result ->
        result.addressComponents.forEach { component ->
            if (component.types.contains(com.google.maps.model.AddressComponentType.PLUS_CODE)) {
                return component.longName
            }
        }
    }

    return null
}

private fun generatePlusCode(latitude: Double, longitude: Double): String {
    // This is a simplified plus code generation
    // For production, you should use the official Plus Codes library
    val lat = (latitude + 90) * 10000
    val lon = (longitude + 180) * 10000
    return String.format("V4J5+%03d", (lat + lon).toInt() % 1000)
}

private fun buildFormattedString(components: AddressComponents, plusCode: String): String {
    val firstLine = mutableListOf<String>()
    val secondLine = mutableListOf<String>()

    // First line: Street number, route, sublocality level 2, sublocality level 1, locality
    components.streetNumber?.let { if (it.isNotBlank()) firstLine.add(it) }
    components.route?.let { if (it.isNotBlank()) firstLine.add(it) }
    components.sublocalityLevel2?.let { if (it.isNotBlank()) firstLine.add(it) }
    components.sublocalityLevel1?.let { if (it.isNotBlank()) firstLine.add(it) }
    components.locality?.let { if (it.isNotBlank()) firstLine.add(it) }
    components.state?.let { if (it.isNotBlank()) firstLine.add(it) }

    // Second line: Country and postal code
    components.country?.let { if (it.isNotBlank()) secondLine.add(it) }
    components.postalCode?.let { if (it.isNotBlank()) secondLine.add(it) }

    // Build the final formatted string
    val result = buildString {
        if (firstLine.isNotEmpty()) {
            append(firstLine.joinToString(", "))
        }
        if (secondLine.isNotEmpty()) {
            if (firstLine.isNotEmpty()) append("\n")
            append(secondLine.joinToString(" "))
        }
        if (plusCode.isNotBlank()) {
            append(". $plusCode")
        }
    }

    Log.d("FormattedAddress", "Built formatted address: $result")
    Log.d("FormattedAddress", "First line parts: $firstLine")
    Log.d("FormattedAddress", "Second line parts: $secondLine")
    Log.d("FormattedAddress", "Plus code: $plusCode")

    return result
}

// Data class to hold address components
private data class AddressComponents(
    var streetNumber: String? = null,
    var route: String? = null,
    var sublocalityLevel2: String? = null,  // JP Nagar
    var sublocalityLevel1: String? = null,  // Avaniyapuram
    var locality: String? = null,           // Madurai
    var state: String? = null,              // Tamil Nadu
    var country: String? = null,            // India
    var postalCode: String? = null          // 625012
)

// Alternative method to format address as single line if needed
private fun buildSingleLineFormattedAddress(components: AddressComponents, plusCode: String): String {
    val parts = mutableListOf<String>()

    // Add components in order
    components.streetNumber?.let { if (it.isNotBlank()) parts.add(it) }
    components.route?.let { if (it.isNotBlank()) parts.add(it) }
    components.sublocalityLevel2?.let { if (it.isNotBlank()) parts.add(it) }
    components.sublocalityLevel1?.let { if (it.isNotBlank()) parts.add(it) }
    components.locality?.let { if (it.isNotBlank()) parts.add(it) }
    components.state?.let { if (it.isNotBlank()) parts.add(it) }
    components.country?.let { if (it.isNotBlank()) parts.add(it) }
    components.postalCode?.let { if (it.isNotBlank()) parts.add(it) }

    val result = parts.joinToString(", ") + if (plusCode.isNotBlank()) ". $plusCode" else ""

    Log.d("SingleLineAddress", "Built single line address: $result")
    return result
}