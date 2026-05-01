package com.techpuram.app.gpsmapcamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class
VerificationManager(private val context: Context, private val client: OkHttpClient) {

    suspend fun requestSessionKey(latitude: String, longitude: String, address: String): Pair<String, String>? {
        return withContext<Pair<String, String>?>(Dispatchers.IO) {
            try {
                val currentTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .format(Date())

                val jsonBody = JSONObject().apply {
                    put("time", currentTime)
                    put("latitude", latitude.toDouble())
                    put("longitude", longitude.toDouble())
                    put("address", address)
                    put("mobile_id", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                }

                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json".toMediaType())

                Log.d("Verification", "Sending request to: https://geogpscamera.in/api/addPhoto")
                Log.d("Verification", "Request body: ${jsonBody.toString()}")

                val request = Request.Builder()
                    .url("https://geogpscamera.in/api/addPhoto")
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    Log.d("Verification", "Response code: ${response.code}")
                    val responseBodyStr = response.body?.string()
                    Log.d("Verification", "Response body: $responseBodyStr")

                    if (!response.isSuccessful) {
                        Log.e("Verification", "Server returned error: ${response.code}")
                        Log.e("Verification", "Error response: $responseBodyStr")
                        return@withContext null
                    }

                    if (responseBodyStr != null) {
                        val jsonResponse = JSONObject(responseBodyStr)

                        // Check if the response indicates success
                        val success = jsonResponse.optBoolean("success", false)
                        if (success) {
                            val data = jsonResponse.optJSONObject("data")
                            if (data != null) {
                                val sessionId = data.optString("session_id")
                                val photoId = data.optString("photo_id")

                                if (sessionId.isNotEmpty() && photoId.isNotEmpty()) {
                                    return@withContext Pair(sessionId, photoId)
                                }
                            }
                        }
                    }

                    return@withContext null
                }
            } catch (e: Exception) {
                Log.e("Verification", "Error requesting session key: ${e.message}", e)
                return@withContext null
            }
        }
    }

    suspend fun createThumbnail(bitmap: Bitmap): String? {
        return withContext<String?>(Dispatchers.IO) {
            try {
                // Create a scaled down version of the bitmap for the thumbnail
                val width = bitmap.width
                val height = bitmap.height
                val thumbnailSize = if (width > height) 256 else (256f * width / height).toInt()

                val thumbnailBitmap = Bitmap.createScaledBitmap(
                    bitmap, thumbnailSize, thumbnailSize, true
                )

                // Convert the thumbnail to a Base64 string
                val outputStream = ByteArrayOutputStream()
                thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val byteArray = outputStream.toByteArray()

                return@withContext Base64.encodeToString(byteArray, Base64.DEFAULT)
            } catch (e: Exception) {
                Log.e("Verification", "Error creating thumbnail: ${e.message}", e)
                return@withContext null
            }
        }
    }

    suspend fun calculateImageChecksum(bitmap: Bitmap): String? {
        return withContext<String?>(Dispatchers.IO) {
            try {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                val byteArray = outputStream.toByteArray()

                val messageDigest = MessageDigest.getInstance("SHA-256")
                val digestBytes = messageDigest.digest(byteArray)

                // Convert digest bytes to hex string - ensure it's properly padded
                val checksum = digestBytes.joinToString("") { "%02x".format(it) }

                return@withContext checksum
            } catch (e: Exception) {
                Log.e("Verification", "Error calculating checksum: ${e.message}", e)
                return@withContext null
            }
        }
    }

    suspend fun calculatePdfChecksum(pdfFile: File): String? {
        return withContext<String?>(Dispatchers.IO) {
            try {
                val byteArray = pdfFile.readBytes()
                val messageDigest = MessageDigest.getInstance("SHA-256")
                val digestBytes = messageDigest.digest(byteArray)

                // Convert digest bytes to hex string - ensure it's properly padded
                val checksum = digestBytes.joinToString("") { "%02x".format(it) }

                return@withContext checksum
            } catch (e: Exception) {
                Log.e("Verification", "Error calculating PDF checksum: ${e.message}", e)
                return@withContext null
            }
        }
    }

    suspend fun sendVerificationData(sessionId: String, checksum: String?, thumbnail: String?, pdfChecksum: String? = null): Boolean {
        return withContext<Boolean>(Dispatchers.IO) {
            try {
                if (checksum == null || thumbnail == null) {
                    return@withContext false
                }

                val jsonBody = JSONObject().apply {
                    put("session_id", sessionId)
                    put("checksum", checksum)
                    put("thumbnail", thumbnail)
                    if (pdfChecksum != null) {
                        put("pdfChecksum", pdfChecksum)
                    }
                }

                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://geogpscamera.in/api/addPhoto")
                    .patch(requestBody)
                    .build()

                Log.d("Verification", "Sending PATCH request: ${jsonBody.toString()}")

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    Log.d("Verification", "PATCH response: Code=${response.code}, Body=$responseBody")
                    return@withContext response.isSuccessful
                }
            } catch (e: Exception) {
                Log.e("Verification", "Error sending verification data: ${e.message}", e)
                return@withContext false
            }
        }
    }

    fun createPdfFromImage(bitmap: Bitmap): File? {
        return try {
            val pdfFile = File(context.cacheDir, "verified_document_${System.currentTimeMillis()}.pdf")

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = pdfDocument.startPage(pageInfo)

            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDocument.finishPage(page)

            pdfFile.outputStream().use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }

            pdfDocument.close()
            pdfFile
        } catch (e: Exception) {
            Log.e("Verification", "Error creating PDF: ${e.message}", e)
            null
        }
    }

    fun saveImageToGallery(bitmap: Bitmap): Uri? {
        return try {
            val filename = "verified_img_${System.currentTimeMillis()}.jpg"
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/GPSMapCamera")
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
                return uri
            } else {
                return null
            }
        } catch (e: Exception) {
            Log.e("Verification", "Error saving image to gallery: ${e.message}", e)
            return null
        }
    }
}