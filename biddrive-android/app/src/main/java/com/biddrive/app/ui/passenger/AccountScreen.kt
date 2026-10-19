package com.biddrive.app.ui.passenger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biddrive.app.data.models.AuthResponse
import com.biddrive.app.ui.components.CashRupeeIcon
import com.biddrive.app.ui.theme.*

@Composable
fun AccountScreen(
    currentUser: AuthResponse?,
    onOpenServerConfig: () -> Unit,
    onLogout: () -> Unit
) {
    var walletBalance by remember { mutableStateOf(150.0) }
    var bidCoins by remember { mutableStateOf(350) }
    var showAddMoneyDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Profile Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(RapidoYellow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = TextOnYellow,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "Rider",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BargainGreenLight
                        ) {
                            Text(
                                text = "VERIFIED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BargainGreen,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = currentUser?.email ?: "user@biddrive.com",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = RapidoAmber, modifier = Modifier.size(13.dp))
                        Text(" 4.95 Rider Rating • Tamil Nadu", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Rapido Wallet & BidCoins Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = RapidoYellowLight,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, RapidoYellow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("BidDrive Wallet Balance", fontSize = 11.sp, color = RapidoOrangeDark, fontWeight = FontWeight.Bold)
                        Text(
                            text = "₹${walletBalance.toInt()}.00",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "⚡ $bidCoins BidCoins available (Save up to ₹35)",
                            fontSize = 11.sp,
                            color = BargainGreenDark,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { showAddMoneyDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = BargainGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Money", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Saved Places Section
        Text("Saved Places", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AccountActionTile(
                icon = Icons.Default.Home,
                title = "Home",
                subtitle = "Thiruvanchery, Chennai",
                onClick = { /* Pre-fills home */ }
            )
            AccountActionTile(
                icon = Icons.Default.Work,
                title = "Work / College",
                subtitle = "SRM Kattankulathur, Potheri",
                onClick = { /* Pre-fills college */ }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Safety & Emergency
        Text("Safety & Support", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AccountActionTile(
                icon = Icons.Default.Shield,
                title = "24x7 Safety Helpline",
                subtitle = "Instant emergency SOS & police assistance",
                onClick = { /* SOS */ }
            )
            AccountActionTile(
                icon = Icons.Default.Tune,
                title = "Backend Server Configuration",
                subtitle = "Configure Spring Boot API base URL",
                onClick = onOpenServerConfig
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // App Version
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "BidDrive Core v1.2.0 • Tamil Nadu Edition",
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Logout Button
        OutlinedButton(
            onClick = { showLogoutConfirmation = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
            border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Log Out of Account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showAddMoneyDialog) {
        AlertDialog(
            onDismissRequest = { showAddMoneyDialog = false },
            title = { Text("Add Money to Wallet", fontWeight = FontWeight.Bold) },
            text = {
                Text("Select an amount to instantly recharge via UPI / Cards:")
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(100, 200, 500).forEach { amount ->
                        Button(
                            onClick = {
                                walletBalance += amount
                                bidCoins += (amount / 2)
                                showAddMoneyDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BargainGreen)
                        ) {
                            Text("+₹$amount")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMoneyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            title = { Text("Confirm Logout", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to log out of your BidDrive rider account?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmation = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AccountActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PureWhite),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                Text(subtitle, fontSize = 11.sp, color = TextSecondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}
