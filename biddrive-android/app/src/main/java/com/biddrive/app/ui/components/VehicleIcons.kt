package com.biddrive.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.biddrive.app.ui.theme.*

/**
 * Real, crisp vector icons for Rapido ride options, avoiding dummy placeholders.
 */

@Composable
fun AutoRickshawIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Auto Roof (Iconic Rapido Yellow)
        val roofPath = Path().apply {
            moveTo(w * 0.20f, h * 0.25f)
            cubicTo(w * 0.25f, h * 0.12f, w * 0.65f, h * 0.12f, w * 0.78f, h * 0.22f)
            lineTo(w * 0.88f, h * 0.42f)
            lineTo(w * 0.15f, h * 0.42f)
            close()
        }
        drawPath(roofPath, color = RapidoYellow)
        drawPath(roofPath, color = Color(0xFF1E1E1E), style = Stroke(width = w * 0.035f))

        // Windshield (Light blue glass)
        val windshieldPath = Path().apply {
            moveTo(w * 0.68f, h * 0.26f)
            lineTo(w * 0.84f, h * 0.42f)
            lineTo(w * 0.62f, h * 0.42f)
            lineTo(w * 0.52f, h * 0.26f)
            close()
        }
        drawPath(windshieldPath, color = Color(0xFFBAE6FD))
        drawPath(windshieldPath, color = Color(0xFF1E1E1E), style = Stroke(width = w * 0.025f))

        // Auto Body (Black & Dark Charcoal Lower Cabin)
        val bodyPath = Path().apply {
            moveTo(w * 0.15f, h * 0.42f)
            lineTo(w * 0.88f, h * 0.42f)
            lineTo(w * 0.90f, h * 0.68f)
            lineTo(w * 0.80f, h * 0.72f)
            lineTo(w * 0.15f, h * 0.72f)
            close()
        }
        drawPath(bodyPath, color = Color(0xFF1E293B))

        // Passenger Side Open Entrance
        drawRoundRect(
            color = RapidoYellowLight,
            topLeft = Offset(w * 0.32f, h * 0.38f),
            size = Size(w * 0.24f, h * 0.26f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // Green Lower Stripe (Distinctive CNG / Auto livery)
        drawRect(
            color = BargainGreen,
            topLeft = Offset(w * 0.15f, h * 0.62f),
            size = Size(w * 0.72f, h * 0.08f)
        )

        // Front Headlight (Bright Amber)
        drawCircle(
            color = Color(0xFFF59E0B),
            radius = w * 0.06f,
            center = Offset(w * 0.88f, h * 0.54f)
        )

        // Rear Wheel
        drawCircle(
            color = Color(0xFF0F172A),
            radius = w * 0.13f,
            center = Offset(w * 0.28f, h * 0.75f)
        )
        drawCircle(
            color = Color(0xFF94A3B8),
            radius = w * 0.06f,
            center = Offset(w * 0.28f, h * 0.75f)
        )

        // Front Wheel
        drawCircle(
            color = Color(0xFF0F172A),
            radius = w * 0.11f,
            center = Offset(w * 0.78f, h * 0.76f)
        )
        drawCircle(
            color = Color(0xFF94A3B8),
            radius = w * 0.05f,
            center = Offset(w * 0.78f, h * 0.76f)
        )
    }
}

@Composable
fun MotorcycleIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Rear Wheel
        drawCircle(
            color = Color(0xFF1E293B),
            radius = w * 0.16f,
            center = Offset(w * 0.22f, h * 0.70f)
        )
        drawCircle(
            color = Color(0xFFCBD5E1),
            radius = w * 0.08f,
            center = Offset(w * 0.22f, h * 0.70f)
        )

        // Front Wheel
        drawCircle(
            color = Color(0xFF1E293B),
            radius = w * 0.16f,
            center = Offset(w * 0.80f, h * 0.70f)
        )
        drawCircle(
            color = Color(0xFFCBD5E1),
            radius = w * 0.08f,
            center = Offset(w * 0.80f, h * 0.70f)
        )

        // Bike Frame & Engine (Dark Slate)
        val framePath = Path().apply {
            moveTo(w * 0.22f, h * 0.70f)
            lineTo(w * 0.44f, h * 0.52f)
            lineTo(w * 0.70f, h * 0.40f)
            lineTo(w * 0.80f, h * 0.70f)
        }
        drawPath(framePath, color = Color(0xFF334155), style = Stroke(width = w * 0.07f))

        // Fuel Tank (Rapido Yellow)
        val tankPath = Path().apply {
            moveTo(w * 0.46f, h * 0.42f)
            cubicTo(w * 0.50f, h * 0.30f, w * 0.65f, h * 0.30f, w * 0.68f, h * 0.42f)
            close()
        }
        drawPath(tankPath, color = RapidoYellow)
        drawPath(tankPath, color = Color(0xFF1E1E1E), style = Stroke(width = w * 0.03f))

        // Seat (Black)
        drawRoundRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(w * 0.28f, h * 0.40f),
            size = Size(w * 0.22f, h * 0.09f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // Handlebars & Headlamp
        drawLine(
            color = Color(0xFF0F172A),
            start = Offset(w * 0.66f, h * 0.38f),
            end = Offset(w * 0.64f, h * 0.22f),
            strokeWidth = w * 0.06f
        )
        drawCircle(
            color = Color(0xFFF59E0B),
            radius = w * 0.05f,
            center = Offset(w * 0.75f, h * 0.36f)
        )

        // Helmet (Safety Yellow on the bike)
        drawCircle(
            color = RapidoYellow,
            radius = w * 0.10f,
            center = Offset(w * 0.44f, h * 0.24f)
        )
        drawArc(
            color = Color(0xFF1E293B),
            startAngle = 0f,
            sweepAngle = 90f,
            useCenter = true,
            topLeft = Offset(w * 0.40f, h * 0.20f),
            size = Size(w * 0.12f, h * 0.12f)
        )
    }
}

@Composable
fun ScootyIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Rear Wheel
        drawCircle(
            color = Color(0xFF1E293B),
            radius = w * 0.14f,
            center = Offset(w * 0.24f, h * 0.72f)
        )
        drawCircle(
            color = Color(0xFFCBD5E1),
            radius = w * 0.07f,
            center = Offset(w * 0.24f, h * 0.72f)
        )

        // Front Wheel
        drawCircle(
            color = Color(0xFF1E293B),
            radius = w * 0.14f,
            center = Offset(w * 0.78f, h * 0.72f)
        )
        drawCircle(
            color = Color(0xFFCBD5E1),
            radius = w * 0.07f,
            center = Offset(w * 0.78f, h * 0.72f)
        )

        // Body & Apron (Modern Purple / Lavender Scooty)
        val bodyPath = Path().apply {
            moveTo(w * 0.18f, h * 0.58f)
            lineTo(w * 0.40f, h * 0.58f)
            lineTo(w * 0.48f, h * 0.70f) // Low step-through floorboard
            lineTo(w * 0.62f, h * 0.70f)
            lineTo(w * 0.72f, h * 0.34f) // Front apron
            lineTo(w * 0.64f, h * 0.34f)
            lineTo(w * 0.56f, h * 0.58f)
            close()
        }
        drawPath(bodyPath, color = ScootyPurple)
        drawPath(bodyPath, color = Color(0xFF1E1E1E), style = Stroke(width = w * 0.03f))

        // Rear Body Panel
        drawRoundRect(
            color = ScootyPurple,
            topLeft = Offset(w * 0.16f, h * 0.48f),
            size = Size(w * 0.28f, h * 0.18f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )

        // Seat
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(w * 0.20f, h * 0.42f),
            size = Size(w * 0.26f, h * 0.08f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )

        // Handlebar & Headlight
        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(w * 0.68f, h * 0.34f),
            end = Offset(w * 0.68f, h * 0.20f),
            strokeWidth = w * 0.05f
        )
        drawCircle(
            color = Color(0xFFF59E0B),
            radius = w * 0.05f,
            center = Offset(w * 0.74f, h * 0.24f)
        )
    }
}

@Composable
fun CabEconomyIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Cabin & Roof (Clean White Sedan / Cab)
        val cabinPath = Path().apply {
            moveTo(w * 0.18f, h * 0.48f)
            lineTo(w * 0.32f, h * 0.24f)
            lineTo(w * 0.68f, h * 0.24f)
            lineTo(w * 0.82f, h * 0.48f)
            close()
        }
        drawPath(cabinPath, color = Color(0xFFE2E8F0))
        drawPath(cabinPath, color = Color(0xFF1E293B), style = Stroke(width = w * 0.035f))

        // Windows (Sky Blue tint)
        val windowPath = Path().apply {
            moveTo(w * 0.34f, h * 0.28f)
            lineTo(w * 0.48f, h * 0.28f)
            lineTo(w * 0.48f, h * 0.46f)
            lineTo(w * 0.24f, h * 0.46f)
            close()
        }
        drawPath(windowPath, color = Color(0xFFBAE6FD))

        val frontWindow = Path().apply {
            moveTo(w * 0.52f, h * 0.28f)
            lineTo(w * 0.66f, h * 0.28f)
            lineTo(w * 0.76f, h * 0.46f)
            lineTo(w * 0.52f, h * 0.46f)
            close()
        }
        drawPath(frontWindow, color = Color(0xFFBAE6FD))

        // Car Body (Compact Car Lower Chassis)
        drawRoundRect(
            color = CabColor,
            topLeft = Offset(w * 0.08f, h * 0.46f),
            size = Size(w * 0.84f, h * 0.24f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(w * 0.08f, h * 0.46f),
            size = Size(w * 0.84f, h * 0.24f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
            style = Stroke(width = w * 0.035f)
        )

        // Headlight & Taillight
        drawCircle(color = Color(0xFFF59E0B), radius = w * 0.04f, center = Offset(w * 0.88f, h * 0.52f))
        drawCircle(color = Color(0xFFEF4444), radius = w * 0.04f, center = Offset(w * 0.12f, h * 0.52f))

        // Wheels
        drawCircle(color = Color(0xFF0F172A), radius = w * 0.12f, center = Offset(w * 0.26f, h * 0.70f))
        drawCircle(color = Color(0xFFCBD5E1), radius = w * 0.06f, center = Offset(w * 0.26f, h * 0.70f))

        drawCircle(color = Color(0xFF0F172A), radius = w * 0.12f, center = Offset(w * 0.74f, h * 0.70f))
        drawCircle(color = Color(0xFFCBD5E1), radius = w * 0.06f, center = Offset(w * 0.74f, h * 0.70f))
    }
}

@Composable
fun CabPriorityIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Premium Executive Sedan (Glossy Dark Navy / Charcoal)
        val cabinPath = Path().apply {
            moveTo(w * 0.16f, h * 0.48f)
            lineTo(w * 0.32f, h * 0.22f)
            lineTo(w * 0.72f, h * 0.22f)
            lineTo(w * 0.86f, h * 0.48f)
            close()
        }
        drawPath(cabinPath, color = Color(0xFF1E293B))

        // Privacy Tinted Windows
        val windowPath = Path().apply {
            moveTo(w * 0.34f, h * 0.26f)
            lineTo(w * 0.70f, h * 0.26f)
            lineTo(w * 0.80f, h * 0.46f)
            lineTo(w * 0.22f, h * 0.46f)
            close()
        }
        drawPath(windowPath, color = Color(0xFF0F172A))

        // Sleek Car Body
        drawRoundRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(w * 0.06f, h * 0.46f),
            size = Size(w * 0.88f, h * 0.24f),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f)
        )

        // Gold Priority Crown / Accent Stripe
        drawLine(
            color = RapidoYellow,
            start = Offset(w * 0.10f, h * 0.58f),
            end = Offset(w * 0.90f, h * 0.58f),
            strokeWidth = w * 0.04f
        )

        // Headlight (Cool LED White)
        drawCircle(color = Color(0xFF38BDF8), radius = w * 0.045f, center = Offset(w * 0.90f, h * 0.52f))

        // Premium Alloy Wheels
        drawCircle(color = Color(0xFF020617), radius = w * 0.12f, center = Offset(w * 0.25f, h * 0.70f))
        drawCircle(color = RapidoYellow, radius = w * 0.05f, center = Offset(w * 0.25f, h * 0.70f))

        drawCircle(color = Color(0xFF020617), radius = w * 0.12f, center = Offset(w * 0.75f, h * 0.70f))
        drawCircle(color = RapidoYellow, radius = w * 0.05f, center = Offset(w * 0.75f, h * 0.70f))
    }
}

@Composable
fun ParcelBoxIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Isometric Courier Delivery Box
        val boxPath = Path().apply {
            moveTo(w * 0.50f, h * 0.16f)
            lineTo(w * 0.86f, h * 0.34f)
            lineTo(w * 0.86f, h * 0.74f)
            lineTo(w * 0.50f, h * 0.92f)
            lineTo(w * 0.14f, h * 0.74f)
            lineTo(w * 0.14f, h * 0.34f)
            close()
        }
        drawPath(boxPath, color = Color(0xFFD97706)) // Cardboard brown
        drawPath(boxPath, color = Color(0xFF78350F), style = Stroke(width = w * 0.035f))

        // Top Flaps
        drawLine(
            color = Color(0xFFB45309),
            start = Offset(w * 0.50f, h * 0.16f),
            end = Offset(w * 0.50f, h * 0.54f),
            strokeWidth = w * 0.03f
        )
        drawLine(
            color = Color(0xFFB45309),
            start = Offset(w * 0.14f, h * 0.34f),
            end = Offset(w * 0.50f, h * 0.54f),
            strokeWidth = w * 0.03f
        )
        drawLine(
            color = Color(0xFFB45309),
            start = Offset(w * 0.86f, h * 0.34f),
            end = Offset(w * 0.50f, h * 0.54f),
            strokeWidth = w * 0.03f
        )

        // Rapido Yellow Tape Seal
        drawRoundRect(
            color = RapidoYellow,
            topLeft = Offset(w * 0.42f, h * 0.30f),
            size = Size(w * 0.16f, h * 0.34f),
            cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
        )
    }
}

@Composable
fun CashRupeeIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Rupee Cash Note
        drawRoundRect(
            color = BargainGreen,
            topLeft = Offset(w * 0.05f, h * 0.18f),
            size = Size(w * 0.90f, h * 0.64f),
            cornerRadius = CornerRadius(w * 0.10f, w * 0.10f)
        )
        drawCircle(
            color = PureWhite,
            radius = w * 0.18f,
            center = Offset(w * 0.50f, h * 0.50f)
        )
        // Center Rupee dot
        drawCircle(
            color = BargainGreen,
            radius = w * 0.08f,
            center = Offset(w * 0.50f, h * 0.50f)
        )
    }
}
