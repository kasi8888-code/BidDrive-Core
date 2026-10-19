package com.biddrive.app.ui.passenger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biddrive.app.ui.components.*
import com.biddrive.app.ui.theme.*

data class PastRide(
    val id: Int,
    val date: String,
    val time: String,
    val pickup: String,
    val drop: String,
    val vehicle: String,
    val fare: Double,
    val captain: String,
    val rating: Double,
    val status: String,
    val distanceKm: Double
)

@Composable
fun ActivityScreen(
    onRebookRide: (pickup: String, drop: String, vehicle: String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val pastRides = remember {
        listOf(
            PastRide(
                id = 21208,
                date = "Yesterday",
                time = "6:45 PM",
                pickup = "Thiruvanchery, Chennai",
                drop = "Station E Tambaram",
                vehicle = "Auto",
                fare = 299.0,
                captain = "BASKAR H (TN 11 AB 4821)",
                rating = 4.93,
                status = "COMPLETED",
                distanceKm = 8.5
            ),
            PastRide(
                id = 21194,
                date = "16 Oct 2026",
                time = "9:15 AM",
                pickup = "SRM Kattankulathur",
                drop = "Tambaram West Bus Stand",
                vehicle = "Bike",
                fare = 198.0,
                captain = "KARTIK R (TN 19 C 7890)",
                rating = 4.88,
                status = "COMPLETED",
                distanceKm = 14.2
            ),
            PastRide(
                id = 21150,
                date = "12 Oct 2026",
                time = "11:30 PM",
                pickup = "Chennai International Airport (MAA)",
                drop = "T. Nagar Panagal Park",
                vehicle = "Cab",
                fare = 380.0,
                captain = "SENTHIL V (TN 09 BK 2341)",
                rating = 4.95,
                status = "COMPLETED",
                distanceKm = 13.0
            ),
            PastRide(
                id = 21088,
                date = "08 Oct 2026",
                time = "2:10 PM",
                pickup = "Koyambedu CMBT Bus Terminus",
                drop = "Vadapalani Murugan Temple",
                vehicle = "Auto",
                fare = 140.0,
                captain = "Driver Unassigned",
                rating = 5.0,
                status = "CANCELLED",
                distanceKm = 4.5
            )
        )
    }

    val filteredRides = pastRides.filter {
        when (selectedFilter) {
            "Completed" -> it.status == "COMPLETED"
            "Cancelled" -> it.status == "CANCELLED"
            else -> true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .padding(16.dp)
    ) {
        // Screen Title
        Text(
            text = "My Activity & Rides",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            color = TextPrimary
        )
        Text(
            text = "Past ride history across Tamil Nadu",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )

        // Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Completed", "Cancelled").forEach { filter ->
                val isSelected = selectedFilter == filter
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) RapidoYellowLight else SurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) RapidoYellow else BorderSubtle
                    ),
                    modifier = Modifier.clickable { selectedFilter = filter }
                ) {
                    Text(
                        text = filter,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) TextOnYellow else TextSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (snackbarMessage != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BargainGreenLight,
                border = androidx.compose.foundation.BorderStroke(1.dp, BargainGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(snackbarMessage ?: "", fontSize = 12.sp, color = BargainGreenDark, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { snackbarMessage = null }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = BargainGreenDark)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredRides) { ride ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PureWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Card Header: Date, Vehicle Icon, Status & Fare
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (ride.vehicle) {
                                    "Auto" -> AutoRickshawIcon(size = 36.dp)
                                    "Bike" -> MotorcycleIcon(size = 36.dp)
                                    else -> CabEconomyIcon(size = 36.dp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "${ride.vehicle} Ride • #${ride.id}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${ride.date}, ${ride.time}",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "₹${ride.fare.toInt()}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = if (ride.status == "COMPLETED") BargainGreen else TextPrimary
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (ride.status == "COMPLETED") BargainGreenLight else ErrorRedLight
                                ) {
                                    Text(
                                        text = ride.status,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (ride.status == "COMPLETED") BargainGreen else ErrorRed,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = BorderSubtle)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Pickup & Drop
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(BargainGreen))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = ride.pickup, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ErrorRed))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = ride.drop, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Captain & Rating
                        if (ride.status == "COMPLETED") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = ride.captain, fontSize = 11.sp, color = TextSecondary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = RapidoAmber, modifier = Modifier.size(13.dp))
                                    Text(" ${ride.rating}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Bottom Actions: Download Invoice & Rebook
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (ride.status == "COMPLETED") {
                                OutlinedButton(
                                    onClick = {
                                        snackbarMessage = "Invoice #${ride.id} downloaded successfully to device!"
                                    },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Invoice", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    onRebookRide(ride.pickup, ride.drop, ride.vehicle)
                                },
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RapidoYellow)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = TextOnYellow, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rebook Ride", fontSize = 12.sp, color = TextOnYellow, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
