package com.biddrive.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biddrive.app.ui.theme.*

@Composable
fun AppHeaderBar(
    currentRole: String,
    userEmail: String?,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PureWhite,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Rapido Yellow Brand Avatar
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(RapidoYellow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (currentRole == "DRIVER") Icons.Default.DirectionsCar else Icons.Default.TwoWheeler,
                        contentDescription = "App Icon",
                        tint = TextOnYellow,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "BidDrive",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (currentRole == "DRIVER") RapidoOrange.copy(alpha = 0.15f) else RapidoYellowLight
                        ) {
                            Text(
                                text = if (currentRole == "DRIVER") "CAPTAIN" else "RIDER",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (currentRole == "DRIVER") RapidoOrangeDark else TextOnYellow,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = userEmail ?: "User",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Server Config",
                        tint = TextSecondary
                    )
                }
                IconButton(onClick = onLogoutClick) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Logout",
                        tint = ErrorRed
                    )
                }
            }
        }
    }
}

@Composable
fun RapidoBottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PureWhite,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val items = listOf(
                Pair("Home", Icons.Default.Home),
                Pair("Activity", Icons.Default.ReceiptLong),
                Pair("Account", Icons.Default.Person)
            )

            items.forEachIndexed { index, pair ->
                val isSelected = selectedTab == index
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = pair.second,
                        contentDescription = pair.first,
                        tint = if (isSelected) TextPrimary else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = pair.first,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) TextPrimary else TextMuted
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(RapidoYellow)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (bgColor, textColor) = when (status.uppercase()) {
        "INITIATED", "AUCTION" -> Pair(RapidoYellowLight, RapidoOrangeDark)
        "MATCHED", "ACCEPTED", "ARRIVING" -> Pair(Color(0xFFE0F2FE), Color(0xFF0284C7))
        "ARRIVED" -> Pair(Color(0xFFFEF3C7), Color(0xFFD97706))
        "IN_PROGRESS" -> Pair(BargainGreenLight, BargainGreen)
        "COMPLETED" -> Pair(BargainGreenLight, BargainGreenDark)
        "CANCELLED", "TIMEOUT" -> Pair(ErrorRedLight, ErrorRed)
        else -> Pair(SurfaceMuted, TextSecondary)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.uppercase(),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CountdownTimerPill(remainingSeconds: Long) {
    val isUrgent = remainingSeconds <= 15
    val pillColor = if (isUrgent) ErrorRed else BargainGreen
    val pillBg = if (isUrgent) ErrorRedLight else BargainGreenLight

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(pillBg)
            .border(1.dp, pillColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Timer,
            contentDescription = "Timer",
            tint = pillColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${remainingSeconds}s reverse auction",
            color = pillColor,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}
