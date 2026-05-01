package com.techpuram.app.gpsmapcamera.util

import android.util.Log

data class ParsedAddress(
    val fullAddress: String,
    val mainAddress: String,
    val plusCode: String,
    val streetNumber: String,
    val street: String,
    val apartment: String,
    val city: String,
    val state: String,
    val zipCode: String,
    val country: String
)

object AddressParser {
    private const val TAG = "AddressParser"

    /**
     * Parse address using the same logic as CameraScreen geo-tag
     * This ensures consistency between capture and edit screens
     * 
     * Expected Google Geocoding format:
     * [Street Number], [Route], [Sublocality Level 2], [Sublocality Level 1], [Locality], [State]
     * [Country] [Postal Code]. [Plus Code]
     */
    fun parseGeoTagAddress(fullAddress: String?): ParsedAddress {
        Log.d(TAG, "Parsing geo-tag address: $fullAddress")

        if (fullAddress.isNullOrBlank() || fullAddress == "Fetching address...") {
            Log.d(TAG, "Address is null, blank, or fetching - returning empty parsed address")
            return ParsedAddress(
                fullAddress = fullAddress ?: "",
                mainAddress = "",
                plusCode = "",
                streetNumber = "",
                street = "",
                apartment = "",
                city = "",
                state = "",
                zipCode = "",
                country = ""
            )
        }

        // Step 1: Extract main address and plus code (same as CameraScreen)
        val (mainAddress, plusCode) = if (fullAddress.contains(". ")) {
            val parts = fullAddress.split(". ")
            Pair(parts[0], parts.getOrNull(1) ?: "")
        } else {
            Pair(fullAddress, "")
        }

        Log.d(TAG, "Extracted mainAddress: '$mainAddress', plusCode: '$plusCode'")

        // Step 2: Parse the main address into components (Google Geocoding format)
        val addressComponents = parseGoogleGeocodingAddress(mainAddress)
        
        Log.d(TAG, "Parsed components: $addressComponents")

        return ParsedAddress(
            fullAddress = fullAddress,
            mainAddress = mainAddress,
            plusCode = plusCode,
            streetNumber = addressComponents.streetNumber,
            street = addressComponents.street,
            apartment = addressComponents.apartment,
            city = addressComponents.city,
            state = addressComponents.state,
            zipCode = addressComponents.zipCode,
            country = addressComponents.country
        )
    }

    /**
     * Parse Google Geocoding API formatted address
     * Format: [Street Number], [Route], [Sublocality Level 2], [Sublocality Level 1], [Locality], [State]
     *         [Country] [Postal Code]
     * Example: "123, Main Street, JP Nagar, Avaniyapuram, Madurai, Tamil Nadu\nIndia 625012"
     * 
     * Note: State is included in the FIRST line (comma-separated), not the second line
     */
    private fun parseGoogleGeocodingAddress(address: String): AddressComponents {
        if (address.isBlank()) {
            return AddressComponents()
        }

        Log.d(TAG, "Parsing Google Geocoding address: '$address'")

        try {
            // Split by newline to separate address lines and country/postal line
            val lines = address.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            Log.d(TAG, "Address lines: $lines")
            Log.d(TAG, "Number of lines: ${lines.size}")
            
            // If there's only one line, it might be a single-line format
            // In that case, try to parse differently
            if (lines.size == 1) {
                Log.d(TAG, "Single line format detected, attempting alternative parsing")
                return parseSingleLineGoogleFormat(address)
            }

            var streetNumber = ""
            var street = ""
            var apartment = ""
            var city = ""
            var state = ""
            var zipCode = ""
            var country = ""

            // Parse first line: comma-separated address components
            if (lines.isNotEmpty()) {
                val firstLine = lines[0]
                val parts = firstLine.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                Log.d(TAG, "First line parts: $parts")

                when (parts.size) {
                    1 -> {
                        // Only one component, likely just street or area
                        street = parts[0]
                    }
                    2 -> {
                        // Street Number, Route or Route, Area
                        val firstPart = parts[0]
                        if (firstPart.matches("\\d+[A-Za-z]?[-/]?\\d*".toRegex())) {
                            streetNumber = firstPart
                            street = parts[1]
                        } else {
                            street = "$firstPart, ${parts[1]}"
                        }
                    }
                    3 -> {
                        // Area, Subarea, State OR Street, Area, State
                        val firstPart = parts[0]
                        if (firstPart.matches("\\d+[A-Za-z]?[-/]?\\d*".toRegex())) {
                            streetNumber = firstPart
                            street = parts[1]
                            // parts[2] is likely state
                            state = parts[2]
                        } else {
                            // All parts are areas/locations, take last as state
                            street = "${parts[0]}, ${parts[1]}"
                            state = parts[2] // Tamil Nadu
                        }
                    }
                    4 -> {
                        // Street Number, Route, Sublocality Level 2, Sublocality Level 1
                        val firstPart = parts[0]
                        if (firstPart.matches("\\d+[A-Za-z]?[-/]?\\d*".toRegex())) {
                            streetNumber = firstPart
                            street = parts[1]
                        } else {
                            street = "${parts[0]}, ${parts[1]}"
                        }
                        // parts[2], parts[3] are sublocalalities
                    }
                    5 -> {
                        // Street Number, Route, Sublocality Level 2, Sublocality Level 1, Locality
                        val firstPart = parts[0]
                        if (firstPart.matches("\\d+[A-Za-z]?[-/]?\\d*".toRegex())) {
                            streetNumber = firstPart
                            street = parts[1]
                        } else {
                            street = "${parts[0]}, ${parts[1]}"
                        }
                        city = parts[4] // Locality (city)
                    }
                    6 -> {
                        // Street Number, Route, Sublocality Level 2, Sublocality Level 1, Locality, State
                        val firstPart = parts[0]
                        if (firstPart.matches("\\d+[A-Za-z]?[-/]?\\d*".toRegex())) {
                            streetNumber = firstPart
                            street = parts[1]
                        } else {
                            street = "${parts[0]}, ${parts[1]}"
                        }
                        city = parts[4] // Locality (city)
                        state = parts[5] // Administrative Area Level 1 (state)
                    }
                    else -> {
                        // More than 6 parts, try to identify key components
                        val firstPart = parts[0]
                        if (firstPart.matches("\\d+[A-Za-z]?[-/]?\\d*".toRegex())) {
                            streetNumber = firstPart
                            street = parts[1]
                        } else {
                            street = "${parts[0]}, ${parts[1]}"
                        }
                        
                        // Take last 2 parts as city and state if available
                        if (parts.size >= 2) {
                            state = parts.last()
                            if (parts.size >= 3) {
                                city = parts[parts.size - 2]
                            }
                        }
                    }
                }
            }

            // Parse second line: Country and Postal Code
            if (lines.size >= 2) {
                val secondLine = lines[1]
                Log.d(TAG, "Second line: '$secondLine'")
                
                // Pattern: "Country PostalCode" or just "Country" or just "PostalCode"
                val countryPostalMatch = Regex("^(.+?)\\s+(\\d{6})$").find(secondLine)
                if (countryPostalMatch != null) {
                    country = countryPostalMatch.groupValues[1].trim()
                    zipCode = countryPostalMatch.groupValues[2]
                } else {
                    // Check if it's just a postal code
                    if (secondLine.matches("\\d{6}".toRegex())) {
                        zipCode = secondLine
                    } else {
                        // Assume it's just the country
                        country = secondLine
                    }
                }
            }

            Log.d(TAG, "Parsed Google Geocoding components:")
            Log.d(TAG, "  Street Number: '$streetNumber'")
            Log.d(TAG, "  Street: '$street'")
            Log.d(TAG, "  City: '$city'")
            Log.d(TAG, "  State: '$state'")
            Log.d(TAG, "  ZIP Code: '$zipCode'")
            Log.d(TAG, "  Country: '$country'")

            return AddressComponents(
                streetNumber = streetNumber,
                street = street,
                apartment = apartment,
                city = city,
                state = state,
                zipCode = zipCode,
                country = country
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Google Geocoding address: ${e.message}", e)
            // Fallback to original parsing method
            return parseIndianAddress(address)
        }
    }

    /**
     * Parse single-line Google Geocoding format
     * Example: "123, Main Street, JP Nagar, Avaniyapuram, Madurai, Tamil Nadu, India 625012"
     */
    private fun parseSingleLineGoogleFormat(address: String): AddressComponents {
        Log.d(TAG, "Parsing single-line Google format: '$address'")
        
        try {
            val parts = address.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            Log.d(TAG, "Single-line parts: $parts")

            var streetNumber = ""
            var street = ""
            var apartment = ""
            var city = ""
            var state = ""
            var zipCode = ""
            var country = ""

            // Look for country and postal code in the last part
            if (parts.isNotEmpty()) {
                val lastPart = parts.last()
                Log.d(TAG, "Last part: '$lastPart'")
                
                // Check if last part contains country and postal code
                val countryPostalMatch = Regex("^(.+?)\\s+(\\d{6})$").find(lastPart)
                if (countryPostalMatch != null) {
                    country = countryPostalMatch.groupValues[1].trim()
                    zipCode = countryPostalMatch.groupValues[2]
                    Log.d(TAG, "Found country: '$country', zipCode: '$zipCode'")
                    
                    // Parse the remaining parts (excluding the last one with country/postal)
                    val addressParts = parts.dropLast(1)
                    Log.d(TAG, "Address parts (without country/postal): $addressParts")
                    
                    when (addressParts.size) {
                        1 -> {
                            street = addressParts[0]
                        }
                        2 -> {
                            val firstPart = addressParts[0]
                            if (firstPart.matches("\\d+[A-Za-z]?[-/]?\\d*".toRegex())) {
                                streetNumber = firstPart
                                street = addressParts[1]
                            } else {
                                street = "$firstPart, ${addressParts[1]}"
                            }
                        }
                        3 -> {
                            val firstPart = addressParts[0]
                            if (firstPart.matches("\\d+[A-Za-z]?[-/]?\\d*".toRegex())) {
                                streetNumber = firstPart
                                street = addressParts[1]
                                // addressParts[2] could be state
                                state = addressParts[2]
                            } else {
                                street = "${addressParts[0]}, ${addressParts[1]}"
                                state = addressParts[2] // Take last as state
                            }
                        }
                        4 -> {
                            val firstPart = addressParts[0]
                            if (firstPart.matches("\\d+[A-Za-z]?[-/]?\\d*".toRegex())) {
                                streetNumber = firstPart
                                street = addressParts[1]
                            } else {
                                street = "${addressParts[0]}, ${addressParts[1]}"
                            }
                            city = addressParts[3] // Take last as city
                        }
                        5 -> {
                            val firstPart = addressParts[0]
                            if (firstPart.matches("\\d+[A-Za-z]?[-/]?\\d*".toRegex())) {
                                streetNumber = firstPart
                                street = addressParts[1]
                            } else {
                                street = "${addressParts[0]}, ${addressParts[1]}"
                            }
                            state = addressParts[4] // Take last as state
                            city = addressParts[3] // Take second last as city
                        }
                        6 -> {
                            val firstPart = addressParts[0]
                            if (firstPart.matches("\\d+[A-Za-z]?[-/]?\\d*".toRegex())) {
                                streetNumber = firstPart
                                street = addressParts[1]
                            } else {
                                street = "${addressParts[0]}, ${addressParts[1]}"
                            }
                            state = addressParts[5] // Take last as state
                            city = addressParts[4] // Take second last as city
                        }
                        else -> {
                            // More than 6 parts, take first 2 as street, last 2 as city/state
                            val firstPart = addressParts[0]
                            if (firstPart.matches("\\d+[A-Za-z]?[-/]?\\d*".toRegex())) {
                                streetNumber = firstPart
                                street = addressParts[1]
                            } else {
                                street = "${addressParts[0]}, ${addressParts[1]}"
                            }
                            if (addressParts.size >= 2) {
                                state = addressParts.last()
                                if (addressParts.size >= 3) {
                                    city = addressParts[addressParts.size - 2]
                                }
                            }
                        }
                    }
                } else {
                    // No country/postal pattern found, treat all as address components
                    val firstPart = parts[0]
                    if (firstPart.matches("\\d+[A-Za-z]?[-/]?\\d*".toRegex())) {
                        streetNumber = firstPart
                        if (parts.size > 1) street = parts[1]
                    } else {
                        street = parts.take(2).joinToString(", ")
                    }
                    
                    // Take last parts as city and state
                    if (parts.size >= 2) {
                        state = parts.last()
                        if (parts.size >= 3) {
                            city = parts[parts.size - 2]
                        }
                    }
                }
            }

            Log.d(TAG, "Single-line parsed components:")
            Log.d(TAG, "  Street Number: '$streetNumber'")
            Log.d(TAG, "  Street: '$street'")
            Log.d(TAG, "  City: '$city'")
            Log.d(TAG, "  State: '$state'")
            Log.d(TAG, "  ZIP Code: '$zipCode'")
            Log.d(TAG, "  Country: '$country'")

            return AddressComponents(
                streetNumber = streetNumber,
                street = street,
                apartment = apartment,
                city = city,
                state = state,
                zipCode = zipCode,
                country = country
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing single-line Google format: ${e.message}", e)
            // Fallback to original parsing
            return parseIndianAddress(address)
        }
    }

    /**
     * Parse Indian address format into components
     * Handles formats like: "Street Number, Street Name, Area, City, State PIN"
     * Also handles Google Geocoding API formats
     */
    private fun parseIndianAddress(address: String): AddressComponents {
        if (address.isBlank()) {
            return AddressComponents()
        }

        val parts = address.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        Log.d(TAG, "Address parts: $parts")

        var streetNumber = ""
        var street = ""
        var apartment = ""
        var city = ""
        var state = ""
        var zipCode = ""
        var country = ""

        try {
            when {
                parts.isEmpty() -> {
                    // No comma-separated parts, try to extract from full address
                    extractFromFullAddress(address)?.let { components ->
                        return components
                    }
                    // Fallback: put everything in street
                    street = address
                }
                parts.size == 1 -> {
                    // Single part, try to extract components or put in street
                    extractFromFullAddress(parts[0])?.let { components ->
                        return components
                    }
                    street = parts[0]
                }
                parts.size >= 2 -> {
                    // Multiple parts - parse based on Indian address patterns
                    
                    // Last part often contains state and PIN code
                    val lastPart = parts.last()
                    val stateAndPin = extractStateAndPin(lastPart)
                    state = stateAndPin.first
                    zipCode = stateAndPin.second

                    // Look for country in the last part if no state found
                    if (state.isBlank() && lastPart.lowercase().contains("india")) {
                        country = "India"
                    }

                    // Second last part often contains city (if we have more than 2 parts)
                    if (parts.size >= 3) {
                        city = parts[parts.size - 2]
                    }

                    // Remaining parts form the street address
                    val streetParts = if (parts.size >= 3) {
                        parts.dropLast(2) // Remove city and state/pin
                    } else {
                        parts.dropLast(1) // Remove state/pin
                    }

                    // Try to extract street number from first part
                    if (streetParts.isNotEmpty()) {
                        val firstPart = streetParts[0]
                        val numberMatch = Regex("^(\\d+[A-Za-z]?[-/]?\\d*)\\s*(.*)").find(firstPart)
                        if (numberMatch != null) {
                            streetNumber = numberMatch.groupValues[1]
                            val remainingFirst = numberMatch.groupValues[2]
                            street = if (remainingFirst.isNotBlank()) {
                                (listOf(remainingFirst) + streetParts.drop(1)).joinToString(", ")
                            } else {
                                streetParts.drop(1).joinToString(", ")
                            }
                        } else {
                            street = streetParts.joinToString(", ")
                        }
                    }

                    // If we only had 2 parts and no city was extracted, first part might be street
                    if (parts.size == 2 && city.isBlank()) {
                        street = parts[0]
                    }
                }
            }

            // If street is still empty, use the full address
            if (street.isBlank() && streetNumber.isBlank()) {
                street = address
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing address components: ${e.message}", e)
            // Fallback: put everything in street field
            street = address
        }

        return AddressComponents(
            streetNumber = streetNumber,
            street = street,
            apartment = apartment,
            city = city,
            state = state,
            zipCode = zipCode,
            country = country
        )
    }

    /**
     * Extract components from a full address string without comma separation
     * Handles formats like: "123 Main Street Bangalore Karnataka 560001"
     */
    private fun extractFromFullAddress(address: String): AddressComponents? {
        if (address.isBlank()) return null

        try {
            val words = address.split("\\s+".toRegex()).filter { it.isNotEmpty() }
            if (words.isEmpty()) return null

            var streetNumber = ""
            var street = ""
            var city = ""
            var state = ""
            var zipCode = ""
            var country = ""

            // Look for ZIP code (6 digits)
            val zipIndex = words.indexOfFirst { it.matches("\\d{6}".toRegex()) }
            if (zipIndex != -1) {
                zipCode = words[zipIndex]
                
                // State is usually before ZIP code
                if (zipIndex > 0) {
                    state = words[zipIndex - 1]
                }
                
                // City is usually before state
                if (zipIndex > 1) {
                    city = words[zipIndex - 2]
                }
            }

            // Look for street number at the beginning
            if (words.isNotEmpty()) {
                val firstWord = words[0]
                if (firstWord.matches("\\d+[A-Za-z]?[-/]?\\d*".toRegex())) {
                    streetNumber = firstWord
                    
                    // Remaining words before city/state/zip form the street
                    val endIndex = when {
                        zipIndex > 2 -> zipIndex - 2 // Before city
                        zipIndex > 1 -> zipIndex - 1 // Before state
                        zipIndex > 0 -> zipIndex     // Before zip
                        else -> words.size
                    }
                    
                    if (endIndex > 1) {
                        street = words.subList(1, endIndex).joinToString(" ")
                    }
                }
            }

            // If no street number found, take first few words as street
            if (streetNumber.isBlank() && street.isBlank()) {
                val endIndex = when {
                    zipIndex > 2 -> zipIndex - 2
                    zipIndex > 1 -> zipIndex - 1
                    zipIndex > 0 -> zipIndex
                    else -> maxOf(1, words.size - 2) // Leave last 2 words for city/state
                }
                
                if (endIndex > 0) {
                    street = words.subList(0, endIndex).joinToString(" ")
                }
            }

            // Check for country
            if (words.any { it.lowercase().contains("india") }) {
                country = "India"
            }

            return AddressComponents(
                streetNumber = streetNumber,
                street = street,
                apartment = "",
                city = city,
                state = state,
                zipCode = zipCode,
                country = country
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting from full address: ${e.message}", e)
            return null
        }
    }

    /**
     * Extract state and PIN code from last part of address
     * Handles formats like: "State 123456", "State", "123456"
     */
    private fun extractStateAndPin(lastPart: String): Pair<String, String> {
        val trimmed = lastPart.trim()
        
        // Look for 6-digit PIN code
        val pinMatch = Regex("(.*?)\\s*(\\d{6})\\s*$").find(trimmed)
        if (pinMatch != null) {
            val stateOnly = pinMatch.groupValues[1].trim()
            val pin = pinMatch.groupValues[2]
            return Pair(stateOnly, pin)
        }
        
        // No PIN found, entire part is state
        return Pair(trimmed, "")
    }

    /**
     * Reconstruct address from components
     */
    fun reconstructAddress(
        streetNumber: String,
        street: String,
        apartment: String,
        city: String,
        state: String,
        zipCode: String,
        country: String
    ): String {
        val streetPart = buildString {
            append(streetNumber.takeIf { it.isNotBlank() } ?: "")
            if (street.isNotBlank()) {
                if (this.isNotEmpty()) append(" ")
                append(street)
            }
            if (apartment.isNotBlank()) {
                if (this.isNotEmpty()) append(", ")
                append("Apt $apartment")
            }
        }

        return buildString {
            if (streetPart.isNotBlank()) append(streetPart)

            if (city.isNotBlank()) {
                if (this.isNotEmpty()) append(", ")
                append(city)
            }

            if (state.isNotBlank() || zipCode.isNotBlank()) {
                if (this.isNotEmpty()) append(", ")
                if (state.isNotBlank()) append(state)
                if (zipCode.isNotBlank()) {
                    if (state.isNotBlank()) append(" ")
                    append(zipCode)
                }
            }

            if (country.isNotBlank()) {
                if (this.isNotEmpty()) append(", ")
                append(country)
            }
        }
    }

    private data class AddressComponents(
        val streetNumber: String = "",
        val street: String = "",
        val apartment: String = "",
        val city: String = "",
        val state: String = "",
        val zipCode: String = "",
        val country: String = ""
    )
}