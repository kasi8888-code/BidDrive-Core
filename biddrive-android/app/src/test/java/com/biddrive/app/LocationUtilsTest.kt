package com.biddrive.app

import com.biddrive.app.data.locations.LocationUtils
import org.junit.Assert.*
import org.junit.Test

class LocationUtilsTest {

    @Test
    fun testHaversineDistanceCalculation() {
        // Thiruvanchery (12.9022, 80.1472) to Tambaram East (12.9255, 80.1167)
        val distance = LocationUtils.calculateDistanceKm(12.9022, 80.1472, 12.9255, 80.1167)
        assertTrue("Distance should be between 4 and 10 km", distance in 4.0..10.0)

        // Bangalore: Koramangala (12.9357, 77.6240) to Whitefield (12.9698, 77.7500)
        val blrDistance = LocationUtils.calculateDistanceKm(12.9357, 77.6240, 12.9698, 77.7500)
        assertTrue("Bangalore distance should be between 14 and 22 km", blrDistance in 14.0..22.0)

        // Zero distance should clamp to minimum safe pickup distance (1.5 km)
        val zeroDist = LocationUtils.calculateDistanceKm(12.9022, 80.1472, 12.9022, 80.1472)
        assertEquals(1.5, zeroDist, 0.01)
    }

    @Test
    fun testFareCalculation() {
        val dist8km = 8.5

        val autoFare = LocationUtils.calculateFare("auto", dist8km)
        val bikeFare = LocationUtils.calculateFare("bike", dist8km)
        val cabFare = LocationUtils.calculateFare("cab_economy", dist8km)
        val cabPriorityFare = LocationUtils.calculateFare("cab_priority", dist8km)

        // Bike should be cheapest
        assertTrue("Bike fare should be cheaper than Auto", bikeFare < autoFare)
        // Auto should be cheaper than Cab
        assertTrue("Auto fare should be cheaper than Cab", autoFare < cabFare)
        // Cab Economy should be cheaper than Cab Priority
        assertTrue("Cab Economy should be cheaper than Priority", cabFare < cabPriorityFare)

        // All fares should be rounded to multiples of 5
        assertEquals(0.0, autoFare % 5.0, 0.001)
        assertEquals(0.0, bikeFare % 5.0, 0.001)
        assertEquals(0.0, cabFare % 5.0, 0.001)
    }

    @Test
    fun testPopularHubsCoverage() {
        val hubs = LocationUtils.POPULAR_HUBS
        assertTrue("Should have at least 15 curated hubs", hubs.size >= 15)

        val hasBangalore = hubs.any { it.cityOrState.contains("Bengaluru", ignoreCase = true) }
        val hasChennai = hubs.any { it.cityOrState.contains("Chennai", ignoreCase = true) }
        val hasHyderabad = hubs.any { it.cityOrState.contains("Hyderabad", ignoreCase = true) }

        assertTrue("Should include Bangalore hubs", hasBangalore)
        assertTrue("Should include Chennai hubs", hasChennai)
        assertTrue("Should include Hyderabad hubs", hasHyderabad)
    }
}
