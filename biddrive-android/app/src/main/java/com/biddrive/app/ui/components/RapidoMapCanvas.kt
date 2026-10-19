package com.biddrive.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biddrive.app.ui.theme.*

/**
 * Interactive visual map component matching Rapido/Ola/Uber production apps.
 * Renders street grids, pickup/drop pins, active route polylines, and animated nearby drivers.
 */
@Composable
fun RapidoMapCanvas(
    modifier: Modifier = Modifier,
    pickupName: String = "Thiruvanchery, Chennai",
    dropName: String = "Station E Tambaram",
    etaMinutes: Int = 15,
    distanceKm: Double = 8.5,
    showRoute: Boolean = true
) {
    // Infinite pulse animations for nearby driver radar and pickup pin
    val infiniteTransition = rememberInfiniteTransition(label = "MapPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val carOffsetProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CarMovingProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Soft Urban Land Blocks
            drawRoundRect(
                color = Color(0xFFEFF6FF), // Residential / Tech park zone
                topLeft = Offset(w * 0.05f, h * 0.10f),
                size = Size(w * 0.40f, h * 0.35f),
                cornerRadius = CornerRadius(24f, 24f)
            )

            drawRoundRect(
                color = Color(0xFFECFDF5), // Tambaram Green Reserve / Park
                topLeft = Offset(w * 0.55f, h * 0.45f),
                size = Size(w * 0.38f, h * 0.40f),
                cornerRadius = CornerRadius(24f, 24f)
            )

            // 2. Secondary Local Streets (Light Slate)
            val streetStroke = 18f
            val minorStroke = 10f

            // Cross street 1
            drawLine(
                color = Color(0xFFE2E8F0),
                start = Offset(0f, h * 0.30f),
                end = Offset(w, h * 0.32f),
                strokeWidth = streetStroke,
                cap = StrokeCap.Round
            )

            // Cross street 2
            drawLine(
                color = Color(0xFFE2E8F0),
                start = Offset(0f, h * 0.65f),
                end = Offset(w, h * 0.68f),
                strokeWidth = streetStroke,
                cap = StrokeCap.Round
            )

            // Vertical street 1
            drawLine(
                color = Color(0xFFE2E8F0),
                start = Offset(w * 0.28f, 0f),
                end = Offset(w * 0.25f, h),
                strokeWidth = minorStroke,
                cap = StrokeCap.Round
            )

            // Vertical street 2
            drawLine(
                color = Color(0xFFE2E8F0),
                start = Offset(w * 0.72f, 0f),
                end = Offset(w * 0.70f, h),
                strokeWidth = minorStroke,
                cap = StrokeCap.Round
            )

            // 3. Arterial Highway (GST Road / Main Trunk Route)
            val highwayPath = Path().apply {
                moveTo(w * 0.15f, h * 0.90f)
                cubicTo(
                    w * 0.35f, h * 0.65f,
                    w * 0.50f, h * 0.45f,
                    w * 0.82f, h * 0.18f
                )
            }

            // Highway casing (dark border)
            drawPath(
                path = highwayPath,
                color = Color(0xFFCBD5E1),
                style = Stroke(width = 36f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Highway surface
            drawPath(
                path = highwayPath,
                color = PureWhite,
                style = Stroke(width = 28f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Center divider
            drawPath(
                path = highwayPath,
                color = RapidoYellowLight,
                style = Stroke(
                    width = 4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 15f), 0f)
                )
            )

            // 4. Active Route Polyline (Thiruvanchery -> Station E Tambaram)
            if (showRoute) {
                val routePath = Path().apply {
                    moveTo(w * 0.22f, h * 0.78f) // Pickup: Thiruvanchery
                    lineTo(w * 0.28f, h * 0.65f)
                    cubicTo(
                        w * 0.40f, h * 0.58f,
                        w * 0.48f, h * 0.42f,
                        w * 0.65f, h * 0.35f
                    )
                    lineTo(w * 0.78f, h * 0.22f) // Drop: Station E Tambaram
                }

                // Route Outer Glow / Shadow
                drawPath(
                    path = routePath,
                    color = RapidoYellow.copy(alpha = 0.35f),
                    style = Stroke(width = 32f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Route Core Line (Bold Rapido Navy / Amber)
                drawPath(
                    path = routePath,
                    brush = Brush.linearGradient(
                        colors = listOf(BargainGreen, RapidoYellow, RapidoOrange),
                        start = Offset(w * 0.22f, h * 0.78f),
                        end = Offset(w * 0.78f, h * 0.22f)
                    ),
                    style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // 5. Clustered Moving Drivers (Nearby Autos & Bikes)
            // Driver 1: Auto 🛺 moving on GST Road
            val d1X = w * (0.35f + carOffsetProgress * 0.25f)
            val d1Y = h * (0.60f - carOffsetProgress * 0.20f)
            drawCircle(
                color = RapidoYellow.copy(alpha = 0.25f),
                radius = 28f * pulseScale,
                center = Offset(d1X, d1Y)
            )
            drawCircle(
                color = RapidoYellow,
                radius = 16f,
                center = Offset(d1X, d1Y)
            )
            drawCircle(
                color = Color(0xFF1E1E1E),
                radius = 6f,
                center = Offset(d1X, d1Y)
            )

            // Driver 2: Bike 🏍️ at Cross Street
            val d2X = w * (0.65f - carOffsetProgress * 0.15f)
            val d2Y = h * 0.31f
            drawCircle(
                color = BargainGreen.copy(alpha = 0.25f),
                radius = 22f * pulseScale,
                center = Offset(d2X, d2Y)
            )
            drawCircle(
                color = BargainGreen,
                radius = 12f,
                center = Offset(d2X, d2Y)
            )

            // Driver 3: Cab 🚗 near Station
            val d3X = w * 0.85f
            val d3Y = h * (0.28f + carOffsetProgress * 0.10f)
            drawCircle(
                color = CabColor,
                radius = 14f,
                center = Offset(d3X, d3Y)
            )

            // 6. Pickup Location Pin (Thiruvanchery)
            val pickupX = w * 0.22f
            val pickupY = h * 0.78f

            // Pickup radar pulse
            drawCircle(
                color = BargainGreen.copy(alpha = 0.20f),
                radius = 42f * pulseScale,
                center = Offset(pickupX, pickupY)
            )
            drawCircle(
                color = PureWhite,
                radius = 18f,
                center = Offset(pickupX, pickupY)
            )
            drawCircle(
                color = BargainGreen,
                radius = 12f,
                center = Offset(pickupX, pickupY)
            )
            drawCircle(
                color = PureWhite,
                radius = 4f,
                center = Offset(pickupX, pickupY)
            )

            // 7. Destination Pin (Station E Tambaram)
            val dropX = w * 0.78f
            val dropY = h * 0.22f

            drawCircle(
                color = Color(0x33000000),
                radius = 12f,
                center = Offset(dropX + 2f, dropY + 24f)
            )
            // Teardrop Pin Path
            val pinPath = Path().apply {
                moveTo(dropX, dropY + 24f)
                cubicTo(dropX - 16f, dropY + 8f, dropX - 16f, dropY - 14f, dropX, dropY - 14f)
                cubicTo(dropX + 16f, dropY - 14f, dropX + 16f, dropY + 8f, dropX, dropY + 24f)
                close()
            }
            drawPath(pinPath, color = ErrorRed)
            drawCircle(
                color = PureWhite,
                radius = 6f,
                center = Offset(dropX, dropY - 4f)
            )
        }

        // Floating ETA Pill on Route
        if (showRoute) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 10.dp, y = (-20).dp)
                    .shadow(8.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = PureWhite
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(BargainGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ElectricBolt,
                            contentDescription = "Fastest",
                            tint = PureWhite,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$etaMinutes mins",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = " • ${distanceKm} km",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Floating Recenter FAB Button
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .shadow(6.dp, CircleShape),
            shape = CircleShape,
            color = PureWhite
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Recenter",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
