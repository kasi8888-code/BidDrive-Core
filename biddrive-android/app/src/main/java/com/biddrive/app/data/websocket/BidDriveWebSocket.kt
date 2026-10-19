package com.biddrive.app.data.websocket

import android.util.Log
import com.biddrive.app.BidDriveApp
import com.biddrive.app.data.api.NetworkClient
import com.biddrive.app.data.models.BidDto
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*

enum class WsConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class StompFrameLog(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: String, // "IN" or "OUT"
    val command: String, // "CONNECT", "CONNECTED", "SUBSCRIBE", "MESSAGE"
    val destination: String? = null,
    val payload: String
)

data class AuctionWebSocketEvent(
    val eventType: String,
    val rideId: Int?,
    val bidId: Int?,
    val driverId: Int?,
    val driverName: String?,
    val lowestBid: Double?,
    val status: String?,
    val otp: String?,
    val remainingSeconds: Long?,
    val message: String?
)

class BidDriveWebSocket {

    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _packetCount = MutableStateFlow(0)
    val packetCount = _packetCount.asStateFlow()

    private val _lastPingMs = MutableStateFlow(12L)
    val lastPingMs = _lastPingMs.asStateFlow()

    private val _frameLogs = MutableStateFlow<List<StompFrameLog>>(emptyList())
    val frameLogs = _frameLogs.asStateFlow()

    private val _incomingBids = MutableSharedFlow<BidDto>(extraBufferCapacity = 64)
    val incomingBids = _incomingBids.asSharedFlow()

    private val _auctionEvents = MutableSharedFlow<AuctionWebSocketEvent>(extraBufferCapacity = 64)
    val auctionEvents = _auctionEvents.asSharedFlow()

    private val _statusUpdates = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val statusUpdates = _statusUpdates.asSharedFlow()

    private var activeRideSubId: String? = null
    private var connectStartTime: Long = 0

    private fun addLog(log: StompFrameLog) {
        val current = _frameLogs.value
        _frameLogs.value = (listOf(log) + current).take(30)
        _packetCount.value = _packetCount.value + 1
    }

    fun connect() {
        if (_connectionState.value == WsConnectionState.CONNECTED || _connectionState.value == WsConnectionState.CONNECTING) {
            return
        }

        _connectionState.value = WsConnectionState.CONNECTING
        connectStartTime = System.currentTimeMillis()

        val base = NetworkClient.getBaseUrl().trimEnd('/')
        val wsBase = base
            .replace("http://", "ws://")
            .replace("https://", "wss://")
        val wsUrl = "$wsBase/ws"

        Log.d("BidDriveWS", "Connecting to STOMP WebSocket: $wsUrl")
        val request = Request.Builder().url(wsUrl).build()

        webSocket = NetworkClient.okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                val ping = System.currentTimeMillis() - connectStartTime
                _lastPingMs.value = if (ping > 0) ping else 12L
                Log.d("BidDriveWS", "Raw WebSocket opened (${_lastPingMs.value}ms). Sending authenticated STOMP CONNECT...")

                val token = BidDriveApp.prefs.getString("jwt_token", "") ?: ""
                val authLine = if (token.isNotBlank()) "Authorization:Bearer $token\n" else ""
                val connectFrame = "CONNECT\naccept-version:1.2,1.1,1.0\nheart-beat:10000,10000\n${authLine}\n\u0000"

                addLog(StompFrameLog(direction = "OUT", command = "CONNECT", destination = "/ws", payload = "accept-version:1.2,1.1"))
                ws.send(connectFrame)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d("BidDriveWS", "Received STOMP:\n$text")
                if (text.startsWith("CONNECTED")) {
                    _connectionState.value = WsConnectionState.CONNECTED
                    addLog(StompFrameLog(direction = "IN", command = "CONNECTED", payload = text.take(120)))
                    scope.launch { _statusUpdates.emit("CONNECTED") }
                } else if (text.startsWith("MESSAGE")) {
                    parseStompMessage(text)
                } else if (text.startsWith("ERROR")) {
                    _connectionState.value = WsConnectionState.ERROR
                    addLog(StompFrameLog(direction = "IN", command = "ERROR", payload = text.take(160)))
                    scope.launch { _statusUpdates.emit("ERROR") }
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                _connectionState.value = WsConnectionState.DISCONNECTED
                addLog(StompFrameLog(direction = "IN", command = "CLOSING", payload = "Code: $code, Reason: $reason"))
                scope.launch { _statusUpdates.emit("DISCONNECTED") }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("BidDriveWS", "WebSocket failure: ${t.message}", t)
                _connectionState.value = WsConnectionState.ERROR
                addLog(StompFrameLog(direction = "IN", command = "FAILED", payload = t.message ?: "Unknown socket error"))
                scope.launch { _statusUpdates.emit("ERROR: ${t.message}") }
            }
        })
    }

    fun subscribeAuction(rideId: Int) {
        val subId = "sub-auction-$rideId"
        activeRideSubId = subId
        val destination = "/topic/auction/$rideId"
        val subscribeFrame = "SUBSCRIBE\nid:$subId\ndestination:$destination\n\n\u0000"
        addLog(StompFrameLog(direction = "OUT", command = "SUBSCRIBE", destination = destination, payload = "id:$subId"))
        webSocket?.send(subscribeFrame)
    }

    fun subscribeRides() {
        val destination = "/topic/rides"
        val subscribeFrame = "SUBSCRIBE\nid:sub-rides\ndestination:$destination\n\n\u0000"
        addLog(StompFrameLog(direction = "OUT", command = "SUBSCRIBE", destination = destination, payload = "id:sub-rides"))
        webSocket?.send(subscribeFrame)
    }

    private fun parseStompMessage(frame: String) {
        try {
            val bodyIndex = frame.indexOf("\n\n")
            val headersPart = if (bodyIndex != -1) frame.substring(0, bodyIndex) else frame
            val body = if (bodyIndex != -1) frame.substring(bodyIndex + 2).trimEnd('\u0000', '\n', '\r') else ""

            var destination = ""
            headersPart.lines().forEach { line ->
                if (line.startsWith("destination:")) {
                    destination = line.substringAfter("destination:").trim()
                }
            }

            addLog(StompFrameLog(direction = "IN", command = "MESSAGE", destination = destination, payload = body))

            if (body.isNotBlank()) {
                val json = gson.fromJson(body, JsonObject::class.java)
                val eventType = if (json.has("eventType") && !json.get("eventType").isJsonNull) json.get("eventType").asString else ""

                val event = AuctionWebSocketEvent(
                    eventType = eventType,
                    rideId = if (json.has("rideId") && !json.get("rideId").isJsonNull) json.get("rideId").asInt else null,
                    bidId = if (json.has("bidId") && !json.get("bidId").isJsonNull) json.get("bidId").asInt else null,
                    driverId = if (json.has("driverId") && !json.get("driverId").isJsonNull) json.get("driverId").asInt else null,
                    driverName = if (json.has("driverName") && !json.get("driverName").isJsonNull) json.get("driverName").asString else null,
                    lowestBid = if (json.has("lowestBid") && !json.get("lowestBid").isJsonNull) json.get("lowestBid").asDouble else (if (json.has("bidAmount") && !json.get("bidAmount").isJsonNull) json.get("bidAmount").asDouble else null),
                    status = if (json.has("status") && !json.get("status").isJsonNull) json.get("status").asString else null,
                    otp = if (json.has("otp") && !json.get("otp").isJsonNull) json.get("otp").asString else null,
                    remainingSeconds = if (json.has("remainingSeconds") && !json.get("remainingSeconds").isJsonNull) json.get("remainingSeconds").asLong else null,
                    message = if (json.has("message") && !json.get("message").isJsonNull) json.get("message").asString else null
                )
                scope.launch { _auctionEvents.emit(event) }

                // Map to BidDto for reverse auction list
                if (eventType == "NEW_BID" || json.has("lowestBid") || json.has("bidAmount")) {
                    val amount = event.lowestBid ?: 0.0
                    val rideId = event.rideId ?: 0
                    val bidId = event.bidId ?: (1000..9999).random()
                    val driverId = event.driverId ?: 1
                    val driverName = event.driverName ?: "Captain #$driverId"

                    val bidDto = BidDto(
                        id = bidId,
                        rideId = rideId,
                        driverId = driverId,
                        bidAmount = amount,
                        status = "ACTIVE",
                        createdAt = "Just now",
                        driverName = driverName,
                        driverRating = 4.9
                    )
                    scope.launch { _incomingBids.emit(bidDto) }
                }
            }
        } catch (e: Exception) {
            Log.e("BidDriveWS", "Error parsing STOMP message: ${e.message}", e)
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "App closed")
        _connectionState.value = WsConnectionState.DISCONNECTED
    }
}
