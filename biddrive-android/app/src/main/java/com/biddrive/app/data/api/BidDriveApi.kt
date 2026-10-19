package com.biddrive.app.data.api

import com.biddrive.app.data.models.*
import retrofit2.Response
import retrofit2.http.*

interface BidDriveApi {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<AuthResponse>

    @POST("api/auth/resend-otp")
    suspend fun resendOtp(@Body request: ResendOtpRequest): Response<AuthResponse>

    @GET("api/rides/estimate")
    suspend fun estimateRide(
        @Query("pickupLat") pickupLat: Double,
        @Query("pickupLng") pickupLng: Double,
        @Query("destinationLat") destinationLat: Double,
        @Query("destinationLng") destinationLng: Double
    ): Response<EstimateResponse>

    @POST("api/rides")
    suspend fun createRide(
        @Header("Authorization") token: String,
        @Body request: CreateRideRequest
    ): Response<RideDto>

    @GET("api/rides")
    suspend fun getRides(
        @Header("Authorization") token: String?,
        @Query("status") status: String? = null
    ): Response<List<RideDto>>

    @GET("api/rides/{id}")
    suspend fun getRideById(
        @Header("Authorization") token: String?,
        @Path("id") rideId: Int
    ): Response<RideDto>

    @GET("api/rides/{id}/auction")
    suspend fun getLiveAuction(
        @Path("id") rideId: Int
    ): Response<Map<String, Any>>

    @DELETE("api/rides/{id}")
    suspend fun cancelRideByDelete(
        @Header("Authorization") token: String,
        @Path("id") rideId: Int
    ): Response<Unit>

    @PUT("api/rides/{id}/cancel")
    suspend fun cancelRide(
        @Header("Authorization") token: String,
        @Path("id") rideId: Int,
        @Body request: CancelRideRequest? = null
    ): Response<RideDto>

    @POST("api/rides/{id}/retry-auction")
    suspend fun retryAuction(
        @Header("Authorization") token: String,
        @Path("id") rideId: Int,
        @Query("newBaseFare") newBaseFare: Double? = null
    ): Response<RideDto>

    @POST("api/rides/{id}/convert-to-instant")
    suspend fun convertToInstant(
        @Header("Authorization") token: String,
        @Path("id") rideId: Int
    ): Response<RideDto>

    @POST("api/bids")
    suspend fun postBid(
        @Header("Authorization") token: String,
        @Body request: CreateBidRequest
    ): Response<BidDto>

    @GET("api/bids/ride/{rideId}")
    suspend fun getBidsForRide(
        @Path("rideId") rideId: Int
    ): Response<List<BidDto>>

    @PUT("api/bids/{bidId}/accept")
    suspend fun acceptBid(
        @Header("Authorization") token: String,
        @Path("bidId") bidId: Int
    ): Response<BidDto>

    @GET("api/drivers/{id}")
    suspend fun getDriver(
        @Path("id") driverId: Int
    ): Response<DriverDto>

    @PUT("api/drivers/status")
    suspend fun updateDriverStatus(
        @Header("Authorization") token: String,
        @Query("status") status: String
    ): Response<DriverDto>

    @PUT("api/rides/{id}/arriving")
    suspend fun markArriving(
        @Header("Authorization") token: String,
        @Path("id") rideId: Int
    ): Response<RideDto>

    @POST("api/rides/{id}/simulate-bids")
    suspend fun simulateBackendBid(
        @Header("Authorization") token: String,
        @Path("id") rideId: Int
    ): Response<List<BidDto>>

    @POST("api/rides/{id}/simulate-driver-step")
    suspend fun simulateDriverStep(
        @Header("Authorization") token: String,
        @Path("id") rideId: Int
    ): Response<RideDto>

    @PUT("api/rides/{id}/arrived")
    suspend fun markArrived(
        @Header("Authorization") token: String,
        @Path("id") rideId: Int
    ): Response<RideDto>

    @POST("api/rides/{id}/start")
    suspend fun startTrip(
        @Header("Authorization") token: String,
        @Path("id") rideId: Int,
        @Body request: StartTripRequest
    ): Response<RideDto>

    @PUT("api/rides/{id}/complete")
    suspend fun completeRide(
        @Header("Authorization") token: String,
        @Path("id") rideId: Int
    ): Response<RideDto>
}
