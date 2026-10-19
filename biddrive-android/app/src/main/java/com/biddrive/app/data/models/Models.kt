package com.biddrive.app.data.models

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class RegisterRequest(
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("phoneNumber") val phoneNumber: String,
    @SerializedName("role") val role: String,
    @SerializedName("licensePlate") val licensePlate: String? = null,
    @SerializedName("carType") val carType: String? = null
)

data class AuthResponse(
    @SerializedName("token") val token: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("userId") val userId: Int? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("testOtp") val testOtp: String? = null
)

data class VerifyOtpRequest(
    @SerializedName("email") val email: String,
    @SerializedName("otp") val otp: String
)

data class ResendOtpRequest(
    @SerializedName("email") val email: String
)

data class RideDto(
    @SerializedName("id") val id: Int,
    @SerializedName("passengerId") val passengerId: Int,
    @SerializedName("driverId") val driverId: Int? = null,
    @SerializedName("pickupLocation") val pickupLocation: String,
    @SerializedName("destinationLocation") val destinationLocation: String,
    @SerializedName("pickupLatitude") val pickupLatitude: Double? = null,
    @SerializedName("pickupLongitude") val pickupLongitude: Double? = null,
    @SerializedName("destinationLatitude") val destinationLatitude: Double? = null,
    @SerializedName("destinationLongitude") val destinationLongitude: Double? = null,
    @SerializedName("baseFare") val baseFare: Double,
    @SerializedName("finalFare") val finalFare: Double? = null,
    @SerializedName("status") val status: String,
    @SerializedName("rideType") val rideType: String = "AUCTION",
    @SerializedName("vehicleType") val vehicleType: String? = null,
    @SerializedName("carType") val carType: String? = null,
    @SerializedName("otp") val otp: String? = null,
    @SerializedName("remainingSeconds") val remainingSeconds: Long? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("driverName") val driverName: String? = null,
    @SerializedName("driverPhone") val driverPhone: String? = null,
    @SerializedName("driverRating") val driverRating: Double? = null
)

data class CreateRideRequest(
    @SerializedName("pickupLocation") val pickupLocation: String,
    @SerializedName("destinationLocation") val destinationLocation: String,
    @SerializedName("pickupLatitude") val pickupLatitude: Double,
    @SerializedName("pickupLongitude") val pickupLongitude: Double,
    @SerializedName("destinationLatitude") val destinationLatitude: Double,
    @SerializedName("destinationLongitude") val destinationLongitude: Double,
    @SerializedName("baseFare") val baseFare: Double,
    @SerializedName("vehicleType") val vehicleType: String,
    @SerializedName("rideType") val rideType: String = "AUCTION"
)

data class BidDto(
    @SerializedName("id") val id: Int,
    @SerializedName("rideId") val rideId: Int,
    @SerializedName("driverId") val driverId: Int,
    @SerializedName("bidAmount") val bidAmount: Double,
    @SerializedName("status") val status: String = "ACTIVE",
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("driverName") val driverName: String? = null,
    @SerializedName("driverRating") val driverRating: Double? = null
)

data class CreateBidRequest(
    @SerializedName("rideId") val rideId: Int,
    @SerializedName("bidAmount") val bidAmount: Double
)

data class DriverDto(
    @SerializedName("id") val id: Int,
    @SerializedName("userId") val userId: Int? = null,
    @SerializedName("licensePlate") val licensePlate: String? = null,
    @SerializedName("carType") val carType: String? = null,
    @SerializedName("status") val status: String? = "Available",
    @SerializedName("rating") val rating: Double? = 5.0,
    @SerializedName("currentLat") val currentLat: Double? = null,
    @SerializedName("currentLng") val currentLng: Double? = null
)

data class StartTripRequest(
    @SerializedName("otp") val otp: String
)

data class CancelRideRequest(
    @SerializedName("cancellationReason") val cancellationReason: String
)

data class EstimateResponse(
    @SerializedName("distanceKm") val distanceKm: Double,
    @SerializedName("suggestedBaseFare") val suggestedBaseFare: Double
)

data class LiveAuctionUpdate(
    val rideId: Int,
    val lowestBid: Double,
    val lowestDriverId: Int? = null,
    val remainingSeconds: Long = 90L,
    val bids: List<BidDto> = emptyList()
)
