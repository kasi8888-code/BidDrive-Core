package com.biddrive.app.data.repository

import com.biddrive.app.BidDriveApp
import com.biddrive.app.data.api.BidDriveApi
import com.biddrive.app.data.api.NetworkClient
import com.biddrive.app.data.models.*
import com.biddrive.app.data.websocket.BidDriveWebSocket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BidDriveRepository(
    private val api: BidDriveApi = NetworkClient.api,
    val webSocket: BidDriveWebSocket = BidDriveWebSocket()
) {

    private val prefs = BidDriveApp.prefs

    private val _currentUser = MutableStateFlow<AuthResponse?>(loadUserFromPrefs())
    val currentUser = _currentUser.asStateFlow()

    private val _activeRole = MutableStateFlow(prefs.getString("active_role", "PASSENGER") ?: "PASSENGER")
    val activeRole = _activeRole.asStateFlow()

    fun getAuthHeader(): String {
        val token = _currentUser.value?.token ?: prefs.getString("jwt_token", "") ?: ""
        return if (token.isNotBlank()) "Bearer $token" else ""
    }

    fun switchRole(newRole: String) {
        _activeRole.value = newRole
        prefs.edit().putString("active_role", newRole).apply()
    }

    private fun loadUserFromPrefs(): AuthResponse? {
        val token = prefs.getString("jwt_token", null) ?: return null
        val role = prefs.getString("user_role", "PASSENGER")
        val userId = prefs.getInt("user_id", -1)
        val email = prefs.getString("user_email", "")
        return if (userId != -1) AuthResponse(token, role, userId, email) else null
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            val res = api.login(LoginRequest(email, password))
            if (res.isSuccessful && res.body() != null) {
                val auth = res.body()!!
                _currentUser.value = auth
                auth.role?.let { _activeRole.value = it }
                prefs.edit()
                    .putString("jwt_token", auth.token)
                    .putString("user_role", auth.role)
                    .putInt("user_id", auth.userId ?: -1)
                    .putString("user_email", auth.email)
                    .putString("active_role", auth.role ?: "PASSENGER")
                    .apply()
                Result.success(auth)
            } else {
                Result.failure(Exception(res.errorBody()?.string() ?: "Login failed (${res.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(req: RegisterRequest): Result<AuthResponse> {
        return try {
            val res = api.register(req)
            if (res.isSuccessful && res.body() != null) {
                val auth = res.body()!!
                if (auth.token != null && auth.token.isNotBlank()) {
                    _currentUser.value = auth
                    _activeRole.value = req.role
                    prefs.edit()
                        .putString("jwt_token", auth.token)
                        .putString("user_role", auth.role)
                        .putInt("user_id", auth.userId ?: -1)
                        .putString("user_email", auth.email)
                        .putString("active_role", req.role)
                        .apply()
                }
                Result.success(auth)
            } else {
                val errText = res.errorBody()?.string() ?: "Registration failed (${res.code()})"
                Result.failure(Exception(extractErrorMessage(errText)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(email: String, otp: String): Result<AuthResponse> {
        return try {
            val res = api.verifyOtp(VerifyOtpRequest(email, otp))
            if (res.isSuccessful && res.body() != null) {
                val auth = res.body()!!
                _currentUser.value = auth
                auth.role?.let { _activeRole.value = it }
                prefs.edit()
                    .putString("jwt_token", auth.token)
                    .putString("user_role", auth.role)
                    .putInt("user_id", auth.userId ?: -1)
                    .putString("user_email", auth.email)
                    .putString("active_role", auth.role ?: "PASSENGER")
                    .apply()
                Result.success(auth)
            } else {
                val errText = res.errorBody()?.string() ?: "OTP verification failed"
                Result.failure(Exception(extractErrorMessage(errText)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resendOtp(email: String): Result<AuthResponse> {
        return try {
            val res = api.resendOtp(ResendOtpRequest(email))
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                val errText = res.errorBody()?.string() ?: "Failed to resend code"
                Result.failure(Exception(extractErrorMessage(errText)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractErrorMessage(raw: String): String {
        return try {
            val json = com.google.gson.JsonParser.parseString(raw).asJsonObject
            when {
                json.has("error") -> json.get("error").asString
                json.has("message") -> json.get("message").asString
                json.has("detail") -> json.get("detail").asString
                else -> raw
            }
        } catch (_: Exception) {
            raw
        }
    }

    fun logout() {
        _currentUser.value = null
        prefs.edit().clear().apply()
        webSocket.disconnect()
    }

    suspend fun estimateRide(pLat: Double, pLng: Double, dLat: Double, dLng: Double): Result<EstimateResponse> {
        return try {
            val res = api.estimateRide(pLat, pLng, dLat, dLng)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Failed to estimate fare"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRide(req: CreateRideRequest): Result<RideDto> {
        return try {
            val res = api.createRide(getAuthHeader(), req)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception(res.errorBody()?.string() ?: "Failed to create ride"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRide(rideId: Int): Result<RideDto> {
        return try {
            val res = api.getRideById(getAuthHeader().takeIf { it.isNotBlank() }, rideId)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch ride $rideId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBidsForRide(rideId: Int): Result<List<BidDto>> {
        return try {
            val res = api.getBidsForRide(rideId)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Failed to load bids"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptBid(bidId: Int): Result<BidDto> {
        return try {
            val res = api.acceptBid(getAuthHeader(), bidId)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception(res.errorBody()?.string() ?: "Failed to accept bid"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelRide(rideId: Int, reason: String): Result<RideDto> {
        return try {
            val res = api.cancelRide(getAuthHeader(), rideId, CancelRideRequest(reason))
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Failed to cancel ride"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAvailableRides(): Result<List<RideDto>> {
        return try {
            val res = api.getRides(getAuthHeader(), "INITIATED")
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Failed to load available rides"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun postBid(rideId: Int, bidAmount: Double): Result<BidDto> {
        return try {
            val res = api.postBid(getAuthHeader(), CreateBidRequest(rideId, bidAmount))
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception(res.errorBody()?.string() ?: "Bid rejected (${res.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDriverStatus(status: String): Result<DriverDto> {
        return try {
            val res = api.updateDriverStatus(getAuthHeader(), status)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Failed to update status"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markArriving(rideId: Int): Result<RideDto> = runCatching {
        api.markArriving(getAuthHeader(), rideId).body()!!
    }

    suspend fun markArrived(rideId: Int): Result<RideDto> = runCatching {
        api.markArrived(getAuthHeader(), rideId).body()!!
    }

    suspend fun startTrip(rideId: Int, otp: String): Result<RideDto> {
        return try {
            val res = api.startTrip(getAuthHeader(), rideId, StartTripRequest(otp))
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception(res.errorBody()?.string() ?: "Invalid OTP or could not start"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeRide(rideId: Int): Result<RideDto> = runCatching {
        api.completeRide(getAuthHeader(), rideId).body()!!
    }

    suspend fun simulateBackendBid(rideId: Int): Result<List<BidDto>> = runCatching {
        val res = api.simulateBackendBid(getAuthHeader(), rideId)
        if (res.isSuccessful && res.body() != null) {
            res.body()!!
        } else {
            throw Exception(res.errorBody()?.string() ?: "Simulation failed (${res.code()})")
        }
    }

    suspend fun simulateDriverStep(rideId: Int): Result<RideDto> = runCatching {
        val res = api.simulateDriverStep(getAuthHeader(), rideId)
        if (res.isSuccessful && res.body() != null) {
            res.body()!!
        } else {
            throw Exception(res.errorBody()?.string() ?: "Step simulation failed (${res.code()})")
        }
    }
}
