package com.techpuram.app.gpsmapcamera

import android.content.SharedPreferences
import android.content.Context
import android.location.Location
import android.util.Log
import com.techpuram.app.gpsmapcamera.util.getAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Geocoding cache manager to reduce API calls
 */
class GeocodingCacheManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("geocoding_cache", Context.MODE_PRIVATE)
    private val CACHE_EXPIRY_HOURS = 24 // Cache expires after 24 hours
    private val MIN_DISTANCE_METERS = 50.0 // Minimum distance to trigger new geocoding
    private val CACHE_PREFIX = "geocache_"
    private val LAST_LAT_KEY = "last_lat"
    private val LAST_LON_KEY = "last_lon"
    private val LAST_ADDRESS_KEY = "last_address"
    private val LAST_TIMESTAMP_KEY = "last_timestamp"
    private val MANUAL_ADDRESS_KEY = "manual_address"
    private val MANUAL_ADDRESS_LAT_KEY = "manual_address_lat"
    private val MANUAL_ADDRESS_LON_KEY = "manual_address_lon"

    /**
     * Get cached address or null if not available/expired
     */
    fun getCachedAddress(latitude: Double, longitude: Double): String? {
        val cacheKey = generateCacheKey(latitude, longitude)
        val cachedData = prefs.getString(cacheKey, null)

        if (cachedData != null) {
            val parts = cachedData.split("|")
            if (parts.size >= 2) {
                val timestamp = parts[0].toLongOrNull() ?: 0
                val address = parts[1]

                // Check if cache is still valid (not expired)
                if (System.currentTimeMillis() - timestamp < CACHE_EXPIRY_HOURS * 60 * 60 * 1000) {
                    Log.d("GeocodingCache", "✅ Using cached address: $address")
                    return address
                } else {
                    Log.d("GeocodingCache", "⏰ Cache expired for location")
                    // Remove expired cache
                    prefs.edit().remove(cacheKey).apply()
                }
            }
        }

        return null
    }

    /**
     * Cache the address for given coordinates
     */
    fun cacheAddress(latitude: Double, longitude: Double, address: String) {
        val cacheKey = generateCacheKey(latitude, longitude)
        val cacheValue = "${System.currentTimeMillis()}|$address"

        prefs.edit().putString(cacheKey, cacheValue).apply()

        // Also update the last known location and address
        prefs.edit()
            .putFloat(LAST_LAT_KEY, latitude.toFloat())
            .putFloat(LAST_LON_KEY, longitude.toFloat())
            .putString(LAST_ADDRESS_KEY, address)
            .putLong(LAST_TIMESTAMP_KEY, System.currentTimeMillis())
            .apply()

        Log.d("GeocodingCache", "💾 Cached address for ${latitude}, ${longitude}: $address")
    }

    /**
     * Check if we should make a new geocoding call based on distance and time
     */
    fun shouldUpdateAddress(newLatitude: Double, newLongitude: Double): Boolean {
        val lastLat = prefs.getFloat(LAST_LAT_KEY, Float.NaN).toDouble()
        val lastLon = prefs.getFloat(LAST_LON_KEY, Float.NaN).toDouble()
        val lastTimestamp = prefs.getLong(LAST_TIMESTAMP_KEY, 0)

        // If no previous location, definitely update
        if (lastLat.isNaN() || lastLon.isNaN()) {
            Log.d("GeocodingCache", "🎯 No previous location, will update")
            return true
        }

        // Calculate distance from last known location
        val distance = calculateDistance(lastLat, lastLon, newLatitude, newLongitude)

        // Check time since last update (avoid too frequent calls even if moving)
        val timeSinceLastUpdate = System.currentTimeMillis() - lastTimestamp
        val minUpdateInterval = 30 * 1000 // 30 seconds minimum

        val shouldUpdate = distance > MIN_DISTANCE_METERS && timeSinceLastUpdate > minUpdateInterval

        Log.d("GeocodingCache", "📍 Distance from last: ${distance.toInt()}m, Time since last: ${timeSinceLastUpdate/1000}s, Should update: $shouldUpdate")

        return shouldUpdate
    }

    /**
     * Set manually edited address for current location
     */
    fun setManualAddress(latitude: Double, longitude: Double, address: String) {
        prefs.edit()
            .putString(MANUAL_ADDRESS_KEY, address)
            .putFloat(MANUAL_ADDRESS_LAT_KEY, latitude.toFloat())
            .putFloat(MANUAL_ADDRESS_LON_KEY, longitude.toFloat())
            .apply()
        
        // Also cache it as a regular address
        cacheAddress(latitude, longitude, address)
        
        Log.d("GeocodingCache", "✏️ Set manual address for ${latitude}, ${longitude}: $address")
    }
    
    /**
     * Get manually edited address if current location matches
     */
    fun getManualAddress(latitude: Double, longitude: Double): String? {
        val manualAddress = prefs.getString(MANUAL_ADDRESS_KEY, null)
        if (manualAddress != null) {
            val manualLat = prefs.getFloat(MANUAL_ADDRESS_LAT_KEY, Float.NaN).toDouble()
            val manualLon = prefs.getFloat(MANUAL_ADDRESS_LON_KEY, Float.NaN).toDouble()
            
            // Check if current location is close to manually edited location (within 100m)
            if (!manualLat.isNaN() && !manualLon.isNaN()) {
                val distance = calculateDistance(latitude, longitude, manualLat, manualLon)
                if (distance < 100.0) { // Within 100 meters
                    Log.d("GeocodingCache", "📝 Using manual address: $manualAddress")
                    return manualAddress
                }
            }
        }
        return null
    }
    
    /**
     * Clear manual address
     */
    fun clearManualAddress() {
        prefs.edit()
            .remove(MANUAL_ADDRESS_KEY)
            .remove(MANUAL_ADDRESS_LAT_KEY)
            .remove(MANUAL_ADDRESS_LON_KEY)
            .apply()
        Log.d("GeocodingCache", "🗑️ Cleared manual address")
    }

    /**
     * Get the last known address (for immediate display while waiting for new geocoding)
     */
    fun getLastKnownAddress(): String? {
        return prefs.getString(LAST_ADDRESS_KEY, null)
    }

    /**
     * Generate cache key based on coordinates (rounded to reduce precision)
     */
    private fun generateCacheKey(latitude: Double, longitude: Double): String {
        // Round to 3 decimal places (~50m precision) to group nearby locations
        val roundedLat = (latitude * 1000).roundToInt() / 1000.0
        val roundedLon = (longitude * 1000).roundToInt() / 1000.0
        return "$CACHE_PREFIX${roundedLat}_${roundedLon}"
    }

    /**
     * Calculate distance between two coordinates in meters
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // Earth radius in meters

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Clear all cached data
     */
    fun clearCache() {
        val keys = prefs.all.keys.filter { it.startsWith(CACHE_PREFIX) }
        val editor = prefs.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
        Log.d("GeocodingCache", "🗑️ Cleared geocoding cache")
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        val allKeys = prefs.all.keys
        val cacheKeys = allKeys.filter { it.startsWith(CACHE_PREFIX) }
        var validEntries = 0
        val currentTime = System.currentTimeMillis()

        cacheKeys.forEach { key ->
            val cachedData = prefs.getString(key, null)
            if (cachedData != null) {
                val parts = cachedData.split("|")
                if (parts.size >= 2) {
                    val timestamp = parts[0].toLongOrNull() ?: 0
                    if (currentTime - timestamp < CACHE_EXPIRY_HOURS * 60 * 60 * 1000) {
                        validEntries++
                    }
                }
            }
        }

        return CacheStats(cacheKeys.size, validEntries)
    }
}

data class CacheStats(val totalEntries: Int, val validEntries: Int)

/**
 * Optimized address fetcher with caching
 */
class OptimizedAddressFetcher(
    private val context: Context,
    private val apiKey: String
) {
    private val cacheManager = GeocodingCacheManager(context)

    /**
     * Get address with smart caching
     */
    suspend fun getAddressOptimized(latitude: Double, longitude: Double): String? {
        // First, check if there's a manually edited address for this location
        val manualAddress = cacheManager.getManualAddress(latitude, longitude)
        if (manualAddress != null) {
            return manualAddress
        }
        
        // Then check if we have a cached address for this location
        val cachedAddress = cacheManager.getCachedAddress(latitude, longitude)
        if (cachedAddress != null) {
            return cachedAddress
        }

        // Check if we should make an API call based on distance/time
        if (!cacheManager.shouldUpdateAddress(latitude, longitude)) {
            // Return last known address if we shouldn't update yet
            val lastKnown = cacheManager.getLastKnownAddress()
            if (lastKnown != null) {
                Log.d("OptimizedGeocoding", "Using last known address (too close/recent)")
                return lastKnown
            }
        }

        // Make the API call
        Log.d("OptimizedGeocoding", "Making geocoding API call for $latitude, $longitude")
        val address = getAddress(latitude, longitude, apiKey)

        // Cache the result if successful
        if (address != null) {
            cacheManager.cacheAddress(latitude, longitude, address)
        }

        return address
    }

    /**
     * Get immediate address (cached or last known) for UI display
     */
    fun getImmediateAddress(latitude: Double, longitude: Double): String {
        // First try manual address
        val manual = cacheManager.getManualAddress(latitude, longitude)
        if (manual != null) return manual
        
        // Try cached next
        val cached = cacheManager.getCachedAddress(latitude, longitude)
        if (cached != null) return cached

        // Fall back to last known
        val lastKnown = cacheManager.getLastKnownAddress()
        if (lastKnown != null) return lastKnown

        return "Fetching address..."
    }
    
    /**
     * Set manually edited address
     */
    fun setManualAddress(latitude: Double, longitude: Double, address: String) {
        cacheManager.setManualAddress(latitude, longitude, address)
    }
    
    /**
     * Clear manually edited address
     */
    fun clearManualAddress() {
        cacheManager.clearManualAddress()
    }

    /**
     * Clear cache (for testing or manual refresh)
     */
    fun clearCache() = cacheManager.clearCache()

    /**
     * Get cache statistics
     */
    fun getCacheStats() = cacheManager.getCacheStats()
}

/**
 * Location update strategy to minimize API calls
 */
class SmartLocationManager {
    companion object {
        // Location request intervals
        const val FAST_INTERVAL = 5000L    // 5 seconds when moving
        const val SLOW_INTERVAL = 30000L   // 30 seconds when stationary
        const val STATIONARY_THRESHOLD = 5.0 // meters

        private var lastLocation: Location? = null
        private var lastMovementTime = System.currentTimeMillis()

        /**
         * Determine if device is stationary
         */
        fun isStationary(newLocation: Location): Boolean {
            val lastLoc = lastLocation
            if (lastLoc == null) {
                lastLocation = newLocation
                return false
            }

            val distance = lastLoc.distanceTo(newLocation)
            val isStationary = distance < STATIONARY_THRESHOLD

            if (!isStationary) {
                lastMovementTime = System.currentTimeMillis()
            }

            lastLocation = newLocation
            return isStationary && (System.currentTimeMillis() - lastMovementTime > 60000) // 1 min stationary
        }

        /**
         * Get appropriate location request interval based on movement
         */
        fun getLocationInterval(currentLocation: Location): Long {
            return if (isStationary(currentLocation)) SLOW_INTERVAL else FAST_INTERVAL
        }
    }
}