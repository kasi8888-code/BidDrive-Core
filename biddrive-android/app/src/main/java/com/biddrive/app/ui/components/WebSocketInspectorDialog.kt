package com.biddrive.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.biddrive.app.data.api.NetworkClient
import com.biddrive.app.data.websocket.BidDriveWebSocket
import com.biddrive.app.data.websocket.StompFrameLog
import com.biddrive.app.data.websocket.WsConnectionState
import com.biddrive.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WebSocketInspectorDialog(
    webSocket: BidDriveWebSocket,
    rideId: Int? = null,
    onDismiss: () -> Unit
) {
    val connectionState by webSocket.connectionState.collectAsState()
    val packetCount by webSocket.packetCount.collectAsState()
    val lastPingMs by webSocket.lastPingMs.collectAsState()
    val frameLogs by webSocket.frameLogs.collectAsState()

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = PureWhite,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    when (connectionState) {
                                        WsConnectionState.CONNECTED -> BargainGreen
                                        WsConnectionState.CONNECTING -> RapidoOrange
                                        WsConnectionState.ERROR -> ErrorRed
                                        WsConnectionState.DISCONNECTED -> Color.Gray
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "STOMP WebSocket Live Inspector",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Telemetry Stats Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F172A))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "STATUS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = connectionState.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (connectionState) {
                                WsConnectionState.CONNECTED -> Color(0xFF34D399)
                                WsConnectionState.CONNECTING -> Color(0xFFFBBF24)
                                else -> Color(0xFFF87171)
                            }
                        )
                    }

                    Column {
                        Text(
                            text = "LATENCY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "${lastPingMs}ms",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }

                    Column {
                        Text(
                            text = "PACKETS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "$packetCount frames",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }

                    Column {
                        Text(
                            text = "PROTOCOL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "STOMP 1.2",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Active Endpoints Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Broker Endpoint: ws://${NetworkClient.getBaseUrl().replace("http://", "").replace("https://", "").trimEnd('/')}/ws",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF475569)
                        )
                        if (rideId != null) {
                            Text(
                                text = "Subscribed Topic: /topic/auction/$rideId",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = BargainGreenDark
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Live STOMP Frame Feed (${frameLogs.size} recent frames)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Frame logs list
                if (frameLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Waiting for STOMP WebSocket traffic...\nBids and events will stream here live.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(frameLogs, key = { it.id }) { log ->
                            FrameLogCard(log = log, timeStr = timeFormatter.format(Date(log.timestamp)))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Reconnect / Close row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { webSocket.connect() },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reconnect",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reconnect Socket", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Done", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FrameLogCard(log: StompFrameLog, timeStr: String) {
    val isIncoming = log.direction == "IN"
    val badgeColor = if (isIncoming) Color(0xFF10B981) else Color(0xFF3B82F6)
    val badgeBg = if (isIncoming) Color(0xFFECFDF5) else Color(0xFFEFF6FF)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${log.direction} • ${log.command}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                    if (!log.destination.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = log.destination,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Text(
                    text = timeStr,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF64748B)
                )
            }

            if (log.payload.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = log.payload,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

@Composable
fun WebSocketStatusBadge(
    webSocket: BidDriveWebSocket,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionState by webSocket.connectionState.collectAsState()
    val pingMs by webSocket.lastPingMs.collectAsState()
    val packetCount by webSocket.packetCount.collectAsState()

    val dotColor = when (connectionState) {
        WsConnectionState.CONNECTED -> BargainGreen
        WsConnectionState.CONNECTING -> RapidoOrange
        WsConnectionState.ERROR -> ErrorRed
        WsConnectionState.DISCONNECTED -> Color.Gray
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "WS: ${connectionState.name}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PureWhite
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "• ${pingMs}ms",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
            if (packetCount > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "• $packetCount msgs",
                    fontSize = 10.sp,
                    color = Color(0xFF38BDF8)
                )
            }
        }
    }
}
