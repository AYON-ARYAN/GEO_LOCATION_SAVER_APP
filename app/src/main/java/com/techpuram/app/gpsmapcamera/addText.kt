package com.techpuram.app.gpsmapcamera

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.media.ExifInterface


fun addMapAndTextToImage(
    context: Context,
    imageUri: Uri,
    addressText: String,
    latitude: Double,
    longitude: Double
): Uri? {
    val apiKey = BuildConfig.MAPS_API_KEY
    val TAG = "MapImageProcessor"

    try {
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
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(originalBitmapRaw, 0, 0, originalBitmapRaw.width, originalBitmapRaw.height, matrix, true)
        } else {
            originalBitmapRaw
        }

        inputStream?.close()

        if (originalBitmap == null) {
            Log.e(TAG, "Failed to decode original image")
            return null
        }

        val newBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(newBitmap)

        // Image dimensions (4032 x 3024)
        val imageWidth = newBitmap.width.toFloat()
        val imageHeight = newBitmap.height.toFloat()

        // Calculate sizes based on image dimensions
        val padding = imageWidth * 0.03f  // 3% padding
        val mapWidth = (imageWidth * 0.06f).toInt()  // 20% bigger than current 5% (now 6%)
        val mapHeight = (mapWidth * 0.75f).toInt()   // 3:4 aspect ratio for map
        val timestamp = getCurrentTimestamp()

        // Text sizes (slightly adjusted for balance)
        val titleTextSize = imageWidth * 0.021f  // Slightly larger for better readability
        val infoTextSize = imageWidth * 0.017f

        // Text paints
        val textPaint = Paint().apply {
            textSize = titleTextSize
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setShadowLayer(5f, 2f, 2f, Color.BLACK)
            isAntiAlias = true
        }

        val infoTextPaint = Paint().apply {
            textSize = infoTextSize
            color = Color.rgb(230, 230, 230)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
            isAntiAlias = true
        }

        val coordText = String.format("Lat: %.5f, Lon: %.5f", latitude, longitude)
        val timeText = "Time: $timestamp"

        // Calculate available width for address text with extra right padding
        val textAreaWidth = imageWidth - (mapWidth + 5 * padding)  // Increased right padding
        val addressLines = breakTextIntoLines(addressText, textPaint, textAreaWidth).take(3)

        // Calculate line spacing
        val lineSpacing = textPaint.textSize * 1.4f
        val infoSpacing = infoTextPaint.textSize * 1.5f

        // Calculate total text block height
        val textBlockHeight = (addressLines.size * lineSpacing) + (2 * infoSpacing)

        // Create overlay - slightly taller to accommodate larger map
        val overlayHeight = maxOf(mapHeight * 1.6f, textBlockHeight) + 2.5f * padding
        val overlayTop = imageHeight - overlayHeight

        // Draw gradient overlay
        val gradientPaint = Paint()
        val gradientColors = intArrayOf(
            Color.argb(0, 0, 0, 0),
            Color.argb(180, 0, 0, 0),
            Color.argb(220, 0, 0, 0)
        )
        val gradientPositions = floatArrayOf(0f, 0.5f, 1f)
        gradientPaint.shader = LinearGradient(
            0f, overlayTop - padding,
            0f, imageHeight,
            gradientColors, gradientPositions, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, overlayTop - padding, imageWidth, imageHeight, gradientPaint)

        // Draw map with rounded corners (now 20% bigger)
        val mapX = padding
        val mapY = overlayTop + (overlayHeight - mapHeight) / 2
        val mapBitmap = fetchMapBitmapBlocking(latitude, longitude, apiKey, mapWidth, mapHeight)

        mapBitmap?.let {
            // Create rounded corners for map
            val roundedMapBitmap = getRoundedCornerBitmap(it, imageWidth * 0.012f)

            // Draw shadow first
            val shadowPaint = Paint().apply {
                setShadowLayer(10f, 0f, 3f, Color.argb(130, 0, 0, 0))
            }
            canvas.drawRect(mapX, mapY, mapX + mapWidth, mapY + mapHeight, shadowPaint)

            // Then draw the map
            canvas.drawBitmap(roundedMapBitmap, mapX, mapY, null)

            // White border with rounded corners
            val borderPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                isAntiAlias = true
            }
            val borderRect = RectF(mapX, mapY, mapX + mapWidth, mapY + mapHeight)
            canvas.drawRoundRect(borderRect, imageWidth * 0.012f, imageWidth * 0.012f, borderPaint)
        }

        // Draw text with significant right shift to prevent overlap
        val textStartX = mapX + mapWidth + padding * 2.5f  // Increased from 2.0f to 2.5f
        val textStartY = overlayTop + (overlayHeight - textBlockHeight) / 2 + textPaint.textSize

        // Current Y position for drawing text
        var currentTextY = textStartY

        // Draw address lines
        addressLines.forEach { line ->
            canvas.drawText(line, textStartX, currentTextY, textPaint)
            currentTextY += lineSpacing
        }

        // Add extra spacing before coordinates
        currentTextY += lineSpacing * 0.5f

        // Draw coordinates and time
        canvas.drawText(coordText, textStartX, currentTextY, infoTextPaint)
        currentTextY += infoSpacing
        canvas.drawText(timeText, textStartX, currentTextY, infoTextPaint)

        return saveBitmapToGallery(context, newBitmap)

    } catch (e: Exception) {
        Log.e(TAG, "Error processing image: ${e.message}", e)
        return null
    }
}

// Helper function to create rounded corner bitmap
private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadius: Float): Bitmap {
    val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)

    val paint = Paint().apply {
        isAntiAlias = true
        color = Color.BLACK
    }

    val rect = Rect(0, 0, bitmap.width, bitmap.height)
    val rectF = RectF(rect)

    canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)

    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(bitmap, rect, rect, paint)

    return output
}



// Local function to get current timestamp
private fun getCurrentTimestamp(): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    dateFormat.timeZone = TimeZone.getDefault()
    return dateFormat.format(Date())
}

// Blocking map fetch that handles the network operation using a thread and latch
private fun fetchMapBitmapBlocking(lat: Double, lon: Double, apiKey: String, width: Int, height: Int): Bitmap? {
    val TAG = "MapFetch"
    val latch = CountDownLatch(1)
    var resultBitmap: Bitmap? = null

    // Create a separate thread for network operation
    Thread {
        try {
            // Make sure static map URL is correctly formatted
            val urlString = "https://maps.googleapis.com/maps/api/staticmap?" +
                    "center=$lat,$lon" +
                    "&zoom=15" +
                    "&size=${width}x${height}" +
                    "&scale=2" + // Higher resolution
                    "&maptype=roadmap" +
                    "&markers=color:red|$lat,$lon" +
                    "&key=$apiKey"

            Log.d(TAG, "Requesting map from URL: $urlString")

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doInput = true
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorMsg = "HTTP error code: $responseCode, message: ${connection.responseMessage}"
                Log.e(TAG, errorMsg)

                // Try to read error response for debugging
                val errorStream = connection.errorStream
                if (errorStream != null) {
                    val errorResponse = errorStream.bufferedReader().use { it.readText() }
                    Log.e(TAG, "Error response: $errorResponse")
                }
            } else {
                val input: InputStream = connection.inputStream
                resultBitmap = BitmapFactory.decodeStream(input)
                input.close()

                if (resultBitmap == null) {
                    Log.e(TAG, "Failed to decode bitmap from stream")
                } else {
                    Log.d(TAG, "Successfully fetched map: ${resultBitmap!!.width}x${resultBitmap!!.height}")
                }
            }

            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching map: ${e.message}", e)
        } finally {
            latch.countDown()
        }
    }.start()

    // Wait for the network operation to complete with a timeout
    try {
        latch.await(20, TimeUnit.SECONDS)
    } catch (e: InterruptedException) {
        Log.e(TAG, "Map fetch timeout", e)
    }

    return resultBitmap
}

// Break text into lines that fit within a given width
fun breakTextIntoLines(text: String, paint: Paint, maxWidth: Float): List<String> {
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

// Save bitmap to gallery and return the URI
fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
    val filename = "geotagged_${System.currentTimeMillis()}.jpg"
    var fos: OutputStream? = null
    var imageUri: Uri? = null

    try {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures") // 👈 Save geo-tagged images to main Pictures folder
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val contentResolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        imageUri = contentResolver.insert(collection, contentValues)

        imageUri?.let {
            fos = contentResolver.openOutputStream(it)
            fos?.let { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(it, contentValues, null, null)
            }
        }

        return imageUri
    } catch (e: Exception) {
        Log.e("ImageSaver", "Error saving image: ${e.message}", e)
        return null
    } finally {
        fos?.close()
    }
}
