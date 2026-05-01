package com.techpuram.app.gpsmapcamera


import com.techpuram.app.gpsmapcamera.util.getAddress
import android.content.Context
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Reverse geocoding helper.
 * Returns a clean human-readable address for given lat/lon.
 */
suspend fun getAddress(
    context: Context,
    latitude: Double,
    longitude: Double,
    apiKey: String
): String? = withContext(Dispatchers.IO) {
    val TAG = "getAddress"

    try {
        // 1️⃣ Try Google Geocoding API
        val urlStr =
            "https://maps.googleapis.com/maps/api/geocode/json?latlng=$latitude,$longitude&key=$apiKey&language=en"

        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000

        if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            if (json.getString("status") == "OK") {
                val results = json.getJSONArray("results")
                if (results.length() > 0) {
                    val formattedAddress = results.getJSONObject(0).getString("formatted_address")
                    if (!formattedAddress.contains("+")) {
                        Log.d(TAG, "✅ Google API Address: $formattedAddress")
                        return@withContext formattedAddress
                    } else {
                        Log.d(TAG, "ℹ️ Got Plus Code, will try fallback...")
                    }
                }
            } else {
                Log.w(TAG, "⚠️ Google API returned status: ${json.getString("status")}")
            }
        } else {
            Log.w(TAG, "⚠️ Google API HTTP code: ${conn.responseCode}")
        }
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error calling Google API: ${e.message}", e)
    }

    // 2️⃣ Fallback to Android Geocoder
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
        if (!addresses.isNullOrEmpty()) {
            val addr = addresses[0].getAddressLine(0)
            Log.d(TAG, "✅ Fallback Geocoder Address: $addr")
            return@withContext addr
        }
    } catch (e: Exception) {
        Log.e(TAG, "❌ Error using Geocoder: ${e.message}", e)
    }

    // 3️⃣ If all fails, return coords
    Log.w(TAG, "⚠️ Returning coordinates as address")
    "Lat: %.5f, Lon: %.5f".format(latitude, longitude)
}