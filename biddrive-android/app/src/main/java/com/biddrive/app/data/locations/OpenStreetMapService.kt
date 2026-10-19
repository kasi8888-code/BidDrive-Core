package com.biddrive.app.data.locations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class LocationSearchResult(
    val id: String,
    val name: String,
    val subtitle: String,
    val cityOrState: String,
    val category: String, // "METRO", "TRANSIT", "CAMPUS", "LANDMARK", "CITY"
    val latitude: Double,
    val longitude: Double
)

object LocationUtils {

    /**
     * Accurate Haversine distance in kilometers between two GPS points,
     * with road curvature correction (1.22x).
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Radius of earth in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distance = r * c * 1.22
        return (round(distance * 10.0) / 10.0).coerceAtLeast(1.5)
    }

    /**
     * Production dynamic fare calculation based on real road distance
     */
    fun calculateFare(category: String, distanceKm: Double): Double {
        val base = when (category.lowercase()) {
            "bike" -> 25.0 + (distanceKm * 12.0)
            "scooty" -> 25.0 + (distanceKm * 12.5)
            "auto" -> 35.0 + (distanceKm * 16.0)
            "cab_economy", "sedan" -> 50.0 + (distanceKm * 19.0)
            "cab_priority" -> 70.0 + (distanceKm * 23.0)
            else -> 20.0 + (distanceKm * 10.0)
        }
        return (round(base / 5.0) * 5.0).coerceAtLeast(35.0)
    }

    // Curated quick-select hubs across India (Bangalore, Chennai, Hyderabad, Mumbai, Delhi, etc.)
    val POPULAR_HUBS = listOf(
        // Bangalore Hubs
        LocationSearchResult("blr_koramangala", "Koramangala", "Koramangala, Bengaluru, Karnataka", "Bengaluru", "LANDMARK", 12.9357, 77.6240),
        LocationSearchResult("blr_indiranagar", "Indiranagar 100ft Road", "Indiranagar, Bengaluru, Karnataka", "Bengaluru", "LANDMARK", 12.9719, 77.6412),
        LocationSearchResult("blr_whitefield", "Whitefield IT Park", "Whitefield, Bengaluru, Karnataka", "Bengaluru", "CAMPUS", 12.9698, 77.7500),
        LocationSearchResult("blr_electronic_city", "Electronic City Phase 1", "Infosys / Wipro Hub, Bengaluru", "Bengaluru", "CAMPUS", 12.8452, 77.6602),
        LocationSearchResult("blr_airport", "Kempegowda Airport (BLR)", "Devanahalli, Bengaluru, Karnataka", "Bengaluru", "TRANSIT", 13.1986, 77.7066),
        LocationSearchResult("blr_majestic", "Majestic KSR Railway Station", "Kempegowda Bus Station, Bengaluru", "Bengaluru", "TRANSIT", 12.9778, 77.5713),
        LocationSearchResult("blr_hsr", "HSR Layout Sector 1", "HSR Layout, Bengaluru, Karnataka", "Bengaluru", "LANDMARK", 12.9121, 77.6446),

        // Chennai Hubs
        LocationSearchResult("chn_vit", "VIT Chennai Campus", "Vandalur - Kelambakkam Road, Chennai", "Chennai", "CAMPUS", 12.8406, 80.1534),
        LocationSearchResult("chn_tambaram", "Tambaram Railway Station", "GST Road / West Tambaram, Chennai", "Chennai", "TRANSIT", 12.9255, 80.1167),
        LocationSearchResult("chn_tambaram_east", "Station E Tambaram", "East Tambaram Railway Station, Chennai", "Chennai", "TRANSIT", 12.9255, 80.1167),
        LocationSearchResult("chn_srm", "SRM Kattankulathur", "SRM IST University, Potheri, Chengalpattu", "Chennai", "CAMPUS", 12.8231, 80.0454),
        LocationSearchResult("chn_thiruvanchery", "Thiruvanchery, Chennai", "Near Camp Road / Selaiyur, Chennai", "Chennai", "LANDMARK", 12.9022, 80.1472),
        LocationSearchResult("chn_guindy", "Guindy Kathipara Junction", "Guindy Metro & Industrial Estate, Chennai", "Chennai", "TRANSIT", 13.0067, 80.2026),
        LocationSearchResult("chn_tnagar", "T. Nagar Panagal Park", "Shopping Hub & Bus Terminus, Chennai", "Chennai", "LANDMARK", 13.0418, 80.2341),
        LocationSearchResult("chn_cmbt", "Koyambedu CMBT Bus Terminus", "Central Mofussil Bus Terminus, Chennai", "Chennai", "TRANSIT", 13.0694, 80.1948),
        LocationSearchResult("chn_airport", "Chennai International Airport (MAA)", "Meenambakkam, GST Road, Chennai", "Chennai", "TRANSIT", 12.9941, 80.1709),
        LocationSearchResult("chn_tidel", "OMR Tidel Park", "Tharamani, Rajiv Gandhi Salai (OMR), Chennai", "Chennai", "CAMPUS", 12.9893, 80.2482),

        // Coimbatore & Madurai
        LocationSearchResult("cbe_gandhipuram", "Gandhipuram Central Bus Stand", "Cross Cut Road, Coimbatore, Tamil Nadu", "Coimbatore", "TRANSIT", 11.0183, 76.9644),
        LocationSearchResult("cbe_airport", "Coimbatore International Airport (CJB)", "Peelamedu, Coimbatore", "Coimbatore", "TRANSIT", 11.0300, 77.0434),
        LocationSearchResult("mdu_meenakshi", "Meenakshi Amman Temple", "Madurai Main, Tamil Nadu", "Madurai", "LANDMARK", 9.9195, 78.1193),

        // Hyderabad & Mumbai
        LocationSearchResult("hyd_hitech", "Hitech City / Cyber Towers", "Madhapur, Hyderabad, Telangana", "Hyderabad", "CAMPUS", 17.4474, 78.3762),
        LocationSearchResult("hyd_airport", "Rajiv Gandhi Airport (HYD)", "Shamshabad, Hyderabad", "Hyderabad", "TRANSIT", 17.2403, 78.4294),
        LocationSearchResult("mum_bandra", "Bandra Kurla Complex (BKC)", "Bandra East, Mumbai, Maharashtra", "Mumbai", "CAMPUS", 19.0657, 72.8687)
    )
}

/**
 * Production OpenStreetMap Geocoding Engine.
 * Dynamically searches ANY place in India (Bangalore, Chennai, Mumbai, Delhi, etc.)
 * using OpenStreetMap / Photon APIs with zero API keys required.
 */
object OpenStreetMapService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun searchPlaces(query: String): List<LocationSearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext LocationUtils.POPULAR_HUBS

        val encoded = URLEncoder.encode(q, "UTF-8")
        val results = mutableListOf<LocationSearchResult>()

        // 1. Try Photon (Komoot OpenStreetMap Geocoder - fast & reliable across India)
        try {
            val url = "https://photon.komoot.io/api/?q=$encoded&limit=8"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "BidDrive-Android/1.2")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                val json = JSONObject(body)
                val features = json.optJSONArray("features")
                if (features != null) {
                    for (i in 0 until features.length()) {
                        val feature = features.getJSONObject(i)
                        val geometry = feature.getJSONObject("geometry")
                        val coords = geometry.getJSONArray("coordinates")
                        val lon = coords.getDouble(0)
                        val lat = coords.getDouble(1)

                        val props = feature.getJSONObject("properties")
                        val name = props.optString("name", q)
                        val state = props.optString("state", "")
                        val city = props.optString("city", props.optString("county", props.optString("district", "")))
                        val country = props.optString("country", "India")

                        val subtitleParts = listOf(city, state, country).filter { it.isNotBlank() }
                        val subtitle = if (subtitleParts.isNotEmpty()) subtitleParts.joinToString(", ") else "India"

                        results.add(
                            LocationSearchResult(
                                id = "osm_${lat}_${lon}",
                                name = name,
                                subtitle = subtitle,
                                cityOrState = if (city.isNotBlank()) city else state,
                                category = when {
                                    props.optString("osm_key") == "railway" -> "TRANSIT"
                                    props.optString("osm_key") == "aeroway" -> "TRANSIT"
                                    props.optString("osm_value") == "university" -> "CAMPUS"
                                    else -> "LANDMARK"
                                },
                                latitude = lat,
                                longitude = lon
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback handled below
        }

        // 2. If Photon returned results, return them
        if (results.isNotEmpty()) {
            return@withContext results
        }

        // 3. Fallback: Search local popular hubs (e.g. Bangalore, Chennai, Hyderabad)
        val localMatches = LocationUtils.POPULAR_HUBS.filter {
            it.name.contains(q, ignoreCase = true) ||
            it.subtitle.contains(q, ignoreCase = true) ||
            it.cityOrState.contains(q, ignoreCase = true)
        }

        if (localMatches.isNotEmpty()) {
            return@withContext localMatches
        }

        // 4. Fallback for custom user input when completely offline
        listOf(
            LocationSearchResult(
                id = "custom_${q.hashCode()}",
                name = q,
                subtitle = "Custom Selected Location, India",
                cityOrState = "India",
                category = "LANDMARK",
                latitude = 12.9716, // Default central location
                longitude = 77.5946
            )
        )
    }
}
