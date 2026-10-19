package com.biddrive.app

import com.biddrive.app.data.models.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ModelsSerializationTest {

    private val gson = Gson()

    @Test
    fun testInstantAndAuctionRideRequestSerialization() {
        // Test Instant Ride
        val instantReq = CreateRideRequest(
            pickupLocation = "Koramangala, Bangalore",
            destinationLocation = "Indiranagar, Bangalore",
            pickupLatitude = 12.9357,
            pickupLongitude = 77.6240,
            destinationLatitude = 12.9719,
            destinationLongitude = 77.6412,
            baseFare = 299.0,
            vehicleType = "Auto",
            rideType = "INSTANT"
        )
        val instantJson = gson.toJson(instantReq)
        assertTrue(instantJson.contains("\"rideType\":\"INSTANT\""))
        assertTrue(instantJson.contains("\"pickupLocation\":\"Koramangala, Bangalore\""))

        // Test Auction Ride
        val auctionReq = CreateRideRequest(
            pickupLocation = "Thiruvanchery, Chennai",
            destinationLocation = "Station E Tambaram",
            pickupLatitude = 12.9022,
            pickupLongitude = 80.1472,
            destinationLatitude = 12.9255,
            destinationLongitude = 80.1167,
            baseFare = 260.0,
            vehicleType = "Auto",
            rideType = "AUCTION"
        )
        val auctionJson = gson.toJson(auctionReq)
        assertTrue(auctionJson.contains("\"rideType\":\"AUCTION\""))
        assertTrue(auctionJson.contains("\"baseFare\":260.0"))
    }

    @Test
    fun testRideDtoDeserialization() {
        val json = """
            {
                "id": 21209,
                "passengerId": 1,
                "driverId": 5,
                "pickupLocation": "Koramangala, Bangalore",
                "destinationLocation": "Whitefield, Bangalore",
                "baseFare": 350.0,
                "finalFare": 310.0,
                "status": "ACCEPTED",
                "rideType": "AUCTION",
                "carType": "Auto",
                "otp": "4821",
                "remainingSeconds": 75,
                "driverName": "BASKAR H",
                "driverRating": 4.93
            }
        """.trimIndent()

        val dto = gson.fromJson(json, RideDto::class.java)
        assertEquals(21209, dto.id)
        assertEquals("ACCEPTED", dto.status)
        assertEquals("AUCTION", dto.rideType)
        assertEquals("4821", dto.otp)
        assertEquals(75L, dto.remainingSeconds)
        assertEquals("BASKAR H", dto.driverName)
        assertEquals(4.93, dto.driverRating ?: 0.0, 0.001)
        assertEquals(310.0, dto.finalFare ?: 0.0, 0.001)
    }

    @Test
    fun testBidDtoSerializationAndUnderbidOrdering() {
        val bids = listOf(
            BidDto(id = 1, rideId = 21209, driverId = 1, bidAmount = 280.0, status = "ACTIVE", driverName = "Driver A"),
            BidDto(id = 2, rideId = 21209, driverId = 2, bidAmount = 250.0, status = "ACTIVE", driverName = "Driver B"),
            BidDto(id = 3, rideId = 21209, driverId = 3, bidAmount = 265.0, status = "ACTIVE", driverName = "Driver C")
        )

        // Lowest bid should be Driver B with 250.0
        val lowestBid = bids.minByOrNull { it.bidAmount }
        assertNotNull(lowestBid)
        assertEquals(250.0, lowestBid!!.bidAmount, 0.001)
        assertEquals("Driver B", lowestBid.driverName)

        val json = gson.toJson(lowestBid)
        val deserialized = gson.fromJson(json, BidDto::class.java)
        assertEquals(lowestBid.id, deserialized.id)
        assertEquals(lowestBid.bidAmount, deserialized.bidAmount, 0.001)
    }

    @Test
    fun testAuthAndRegistrationPayloads() {
        val reg = RegisterRequest(
            name = "Rider Test",
            email = "test@biddrive.com",
            password = "password123",
            phoneNumber = "9876543210",
            role = "PASSENGER"
        )
        val regJson = gson.toJson(reg)
        assertTrue(regJson.contains("\"role\":\"PASSENGER\""))

        val auth = AuthResponse(
            token = "jwt-mock-token-xyz",
            role = "PASSENGER",
            userId = 42,
            email = "test@biddrive.com"
        )
        val authJson = gson.toJson(auth)
        val parsed = gson.fromJson(authJson, AuthResponse::class.java)
        assertEquals("jwt-mock-token-xyz", parsed.token)
        assertEquals("PASSENGER", parsed.role)
    }

    @Test
    fun testOtpVerificationPayloads() {
        val verifyReq = VerifyOtpRequest(email = "rider@demo.com", otp = "834921")
        val json = gson.toJson(verifyReq)
        assertTrue(json.contains("\"otp\":\"834921\""))
        assertTrue(json.contains("\"email\":\"rider@demo.com\""))

        val pendingAuth = AuthResponse(
            status = "PENDING_VERIFICATION",
            message = "Code sent",
            testOtp = "834921",
            email = "rider@demo.com"
        )
        val authJson = gson.toJson(pendingAuth)
        val parsed = gson.fromJson(authJson, AuthResponse::class.java)
        assertEquals("PENDING_VERIFICATION", parsed.status)
        assertEquals("834921", parsed.testOtp)
    }
}
