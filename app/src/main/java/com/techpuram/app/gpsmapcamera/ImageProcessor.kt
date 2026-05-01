package com.techpuram.app.gpsmapcamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.Color as AndroidColor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.text.SimpleDateFormat
import java.util.*

class ImageProcessor(private val context: Context, private val client: OkHttpClient) {

    // Function to generate actual Google Map bitmap using Static Maps API
    suspend fun generateGoogleMapBitmap(
        latitude: Double,
        longitude: Double,
        width: Int,
        height: Int
    ): Bitmap? {
        return withContext<Bitmap?>(Dispatchers.IO) {
            try {
                // Maps API key is injected at build time from local.properties
                // via BuildConfig (see app/build.gradle.kts).
                val apiKey = BuildConfig.MAPS_API_KEY
                
                // Build Google Maps Static API URL - higher zoom for small geo tag
                val mapUrl = "https://maps.googleapis.com/maps/api/staticmap?" +
                        "center=$latitude,$longitude" +
                        "&zoom=18" + // Higher zoom for small geo tag map
                        "&size=${width}x${height}" +
                        "&maptype=roadmap" + // Same map type as UI
                        "&markers=color:red%7Csize:normal%7C$latitude,$longitude" + // Red marker exactly like UI
                        "&style=feature:poi|visibility:simplified" + // Simplify POIs to match Compose map
                        "&style=feature:transit|visibility:off" + // Hide transit to match Compose map
                        "&key=$apiKey"
                
                Log.d("GoogleMap", "Fetching map from: $mapUrl")
                
                // Download the map image
                val request = Request.Builder()
                    .url(mapUrl)
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val inputStream = response.body?.byteStream()
                        if (inputStream != null) {
                            val mapBitmap = BitmapFactory.decodeStream(inputStream)
                            Log.d("GoogleMap", "Successfully generated map bitmap: ${mapBitmap?.width}x${mapBitmap?.height}")
                            return@withContext mapBitmap
                        }
                    } else {
                        Log.e("GoogleMap", "Failed to fetch map: ${response.code} ${response.message}")
                    }
                    return@withContext null
                }
            } catch (e: Exception) {
                Log.e("GoogleMap", "Error generating map bitmap: ${e.message}", e)
                return@withContext null
            }
        }
    }

    // Function to add geotag overlay to bitmap for saving - IDENTICAL to UI display
    suspend fun addGeotagOverlayToBitmap(
        originalBitmap: Bitmap,
        latitude: String,
        longitude: String,
        address: String,
        verificationId: String? = null
    ): Bitmap? {
        return withContext<Bitmap?>(Dispatchers.IO) {
            try {
                val overlayBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(overlayBitmap)

                val width = overlayBitmap.width
                val height = overlayBitmap.height

                // Calculate overlay dimensions based on image size - SAME AS UI
                val mapWidth = if (width > height) width * 0.25f else width * 0.3f
                val mapHeight = mapWidth * 0.75f
                val textBoxWidth = width - mapWidth - (width * 0.06f)

                // Position at bottom with padding - SAME AS UI
                val bottomPadding = height * 0.02f
                val sidePadding = width * 0.02f
                val overlayY = height - mapHeight - bottomPadding

                // Font sizes - increased for better readability to match UI
                val baseFontSize = width * 0.028f
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

                // Draw verification headers if verification ID exists - SAME AS UI
                var actualOverlayY = overlayY
                if (verificationId != null) {
                    val headerHeight = smallFontSize * 1.8f
                    val headerY = overlayY - headerHeight
                    actualOverlayY = headerY
                    
                    // Header background paint
                    val headerBgPaint = Paint().apply {
                        color = AndroidColor.argb(102, 0, 0, 0) // 0.4 alpha like UI
                        isAntiAlias = true
                        pathEffect = CornerPathEffect(4f)
                    }
                    
                    val headerTextPaint = Paint().apply {
                        color = AndroidColor.WHITE
                        textSize = smallFontSize * 0.9f
                        isAntiAlias = true
                        typeface = Typeface.DEFAULT_BOLD
                        textAlign = Paint.Align.CENTER
                    }
                    
                    // GeoGPSCamera header above map
                    val mapHeaderRect = RectF(sidePadding, headerY, sidePadding + mapWidth, overlayY)
                    canvas.drawRoundRect(mapHeaderRect, 4f, 4f, headerBgPaint)
                    canvas.drawText("GeoGPSCamera", mapHeaderRect.centerX(), headerY + smallFontSize, headerTextPaint)
                    
                    // Verification ID header above text
                    val textHeaderX = sidePadding + mapWidth + (width * 0.02f)
                    val textHeaderRect = RectF(textHeaderX, headerY, textHeaderX + textBoxWidth, overlayY)
                    canvas.drawRoundRect(textHeaderRect, 4f, 4f, headerBgPaint)
                    canvas.drawText("Verification ID: $verificationId", textHeaderRect.centerX(), headerY + smallFontSize, headerTextPaint)
                }

                // Draw map preview background (dark semi-transparent rounded rectangle) - SAME AS UI
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

                // Draw map preview border - SAME AS UI
                val mapBorderPaint = Paint().apply {
                    color = AndroidColor.argb(180, 255, 255, 255) // Semi-transparent white
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    isAntiAlias = true
                    pathEffect = CornerPathEffect(12f)
                }
                canvas.drawRoundRect(mapRect, 12f, 12f, mapBorderPaint)
                
                // Draw actual Google Map using Static Maps API - SAME AS UI
                try {
                    val mapBitmap = generateGoogleMapBitmap(
                        latitude.toDouble(),
                        longitude.toDouble(),
                        mapRect.width().toInt(),
                        mapRect.height().toInt()
                    )
                    
                    if (mapBitmap != null) {
                        // Draw the actual Google Map
                        val srcRect = android.graphics.Rect(0, 0, mapBitmap.width, mapBitmap.height)
                        val destRect = android.graphics.Rect(
                            mapRect.left.toInt(),
                            mapRect.top.toInt(),
                            mapRect.right.toInt(),
                            mapRect.bottom.toInt()
                        )
                        
                        // First save the canvas state and clip to rounded rect
                        canvas.save()
                        val clipPath = Path().apply {
                            addRoundRect(mapRect, 12f, 12f, Path.Direction.CW)
                        }
                        canvas.clipPath(clipPath)
                        canvas.drawBitmap(mapBitmap, srcRect, destRect, null)
                        canvas.restore()
                        
                    } else {
                        // Fallback: Draw simple map background if API fails
                        val fallbackPaint = Paint().apply {
                            color = AndroidColor.rgb(242, 243, 240)
                            isAntiAlias = true
                        }
                        canvas.drawRoundRect(mapRect, 12f, 12f, fallbackPaint)
                    }
                    
                } catch (e: Exception) {
                    Log.e("MapGeneration", "Failed to generate map: ${e.message}")
                    // Fallback: Draw simple map background
                    val fallbackPaint = Paint().apply {
                        color = AndroidColor.rgb(242, 243, 240)
                        isAntiAlias = true
                    }
                    canvas.drawRoundRect(mapRect, 12f, 12f, fallbackPaint)
                }

                // Draw text background (dark semi-transparent rounded rectangle) - SAME AS UI
                val textBoxX = sidePadding + mapWidth + (width * 0.02f)
                val textRect = RectF(
                    textBoxX,
                    overlayY,
                    textBoxX + textBoxWidth,
                    overlayY + mapHeight
                )
                canvas.drawRoundRect(textRect, 12f, 12f, mapPaint)

                // Text positioning - SAME AS UI
                val textPadding = width * 0.015f
                val textStartX = textBoxX + textPadding
                var currentY = overlayY + textPadding + baseFontSize

                // Extract and format data - SAME AS UI
                val (mainAddress, plusCode) = if (address.contains(". ")) {
                    val parts = address.split(". ")
                    Pair(parts[0], parts.getOrNull(1) ?: "")
                } else {
                    Pair(address, "")
                }

                val addressDisplay = if (address.isEmpty() || address == "Fetching address...") {
                    ""
                } else {
                    mainAddress
                }

                // ROW 1: Address (Indian format, multiple lines if needed) - SAME AS UI
                if (addressDisplay.isNotEmpty()) {
                    val maxTextWidth = textBoxWidth - (textPadding * 2)
                    val addressLines = breakTextIntoLines(addressDisplay, addressPaint, maxTextWidth)

                    for (line in addressLines.take(3)) { // Max 3 lines
                        canvas.drawText(line, textStartX, currentY, addressPaint)
                        currentY += baseFontSize * 1.2f
                    }
                }

                // ROW 2: Coordinates with full precision - SAME AS UI
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

                // ROW 3: Plus Code and Date/Time - SAME AS UI
                val displayPlusCode = if (plusCode.isNotEmpty()) {
                    plusCode
                } else {
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

                // Draw date/time on the right side - SAME AS UI
                val dateTimeWidth = infoPaint.measureText(currentDateTime)
                val dateTimeX = textBoxX + textBoxWidth - textPadding - dateTimeWidth
                canvas.drawText(currentDateTime, dateTimeX, currentY, infoPaint)
                currentY += smallFontSize * 1.5f

                // ROW 4: Removed verification code as it's already in the header (if present)

                return@withContext overlayBitmap
            } catch (e: Exception) {
                Log.e("GeotagOverlay", "Error adding geotag overlay: ${e.message}", e)
                return@withContext null
            }
        }
    }

    // Helper function for text breaking
    private fun breakTextIntoLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines
    }
}