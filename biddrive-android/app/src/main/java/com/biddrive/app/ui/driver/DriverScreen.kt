package com.biddrive.app.ui.driver

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biddrive.app.data.models.BidDto
import com.biddrive.app.data.models.RideDto
import com.biddrive.app.data.repository.BidDriveRepository
import com.biddrive.app.ui.components.AutoRickshawIcon
import com.biddrive.app.ui.components.CountdownTimerPill
import com.biddrive.app.ui.components.MotorcycleIcon
import com.biddrive.app.ui.components.StatusBadge
import com.biddrive.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DriverScreen(
    repository: BidDriveRepository
) {
    var driverStatus by remember { mutableStateOf("Available") }
    var availableRides by remember { mutableStateOf<List<RideDto>>(emptyList()) }
    var selectedRideForBidding by remember { mutableStateOf<RideDto?>(null) }
    var activeTrip by remember { mutableStateOf<RideDto?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Polling available rides and active trips
    LaunchedEffect(Unit) {
        while (true) {
            repository.getAvailableRides().onSuccess { rides ->
                availableRides = rides
            }

            // Check if driver has an accepted/in-progress trip
            activeTrip?.id?.let { tripId ->
                repository.getRide(tripId).onSuccess { updated ->
                    activeTrip = updated
                }
            }

            delay(3000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .padding(16.dp)
    ) {
        // Rapido Captain Availability Status Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = PureWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                when (driverStatus) {
                                    "Available" -> BargainGreen
                                    "BUSY" -> RapidoOrange
                                    else -> TextMuted
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Captain Duty Status", fontSize = 11.sp, color = TextSecondary)
                        Text(
                            text = if (driverStatus == "Available") "Online & Ready" else driverStatus,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Available", "Offline").forEach { st ->
                        FilterChip(
                            selected = driverStatus == st,
                            onClick = {
                                driverStatus = st
                                coroutineScope.launch {
                                    repository.updateDriverStatus(st)
                                }
                            },
                            label = {
                                Text(
                                    text = if (st == "Available") "Online" else "Offline",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (st == "Available") BargainGreenLight else SurfaceMuted,
                                selectedLabelColor = if (st == "Available") BargainGreenDark else TextPrimary
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (errorMessage != null) {
            Surface(
                color = ErrorRedLight,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = ErrorRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (successMessage != null) {
            Surface(
                color = BargainGreenLight,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BargainGreen.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = successMessage ?: "",
                    color = BargainGreenDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Active Trip Execution Card (when assigned a ride)
        if (activeTrip != null && activeTrip?.status != "COMPLETED" && activeTrip?.status != "CANCELLED") {
            ActiveCaptainTripCard(
                trip = activeTrip!!,
                onMarkArrived = {
                    coroutineScope.launch {
                        repository.markArrived(activeTrip!!.id).onSuccess {
                            activeTrip = it
                            successMessage = "Marked arrived at pickup!"
                        }
                    }
                },
                onStartTrip = { otp ->
                    coroutineScope.launch {
                        repository.startTrip(activeTrip!!.id, otp).onSuccess {
                            activeTrip = it
                            errorMessage = null
                            successMessage = "Trip started! Drive safely to drop location."
                        }.onFailure { err ->
                            errorMessage = err.message ?: "Invalid OTP entered"
                        }
                    }
                },
                onCompleteTrip = {
                    coroutineScope.launch {
                        repository.completeRide(activeTrip!!.id).onSuccess {
                            activeTrip = it
                            successMessage = "Trip completed! Fare credited to your Captain Wallet."
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Available Rides Live Feed Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Live Ride Requests",
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = RapidoYellowLight
                ) {
                    Text(
                        text = "${availableRides.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextOnYellow,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text("Auto-refreshed", fontSize = 11.sp, color = TextSecondary)
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (availableRides.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AutoRickshawIcon(size = 54.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No live auctions in your radar right now", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("New passenger ride requests will appear here instantly", color = TextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(availableRides) { ride ->
                    CaptainRideAuctionCard(
                        ride = ride,
                        onBidClick = {
                            selectedRideForBidding = ride
                            errorMessage = null
                            successMessage = null
                        }
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet / Dialog for Underbidding
    if (selectedRideForBidding != null) {
        val ride = selectedRideForBidding!!
        var bidAmountText by remember { mutableStateOf("${(ride.baseFare - 10).toInt()}") }
        var isSubmittingBid by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { selectedRideForBidding = null },
            title = {
                Text("Place Underbid #${ride.id}", fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Column {
                    Text(
                        text = "Base Fare: ₹${ride.baseFare.toInt()} • Vehicle: ${ride.carType ?: "Auto"}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Quick Underbid Shortcuts:", fontSize = 11.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15).forEach { discount ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BargainGreenLight,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BargainGreen),
                                modifier = Modifier.clickable {
                                    val current = ride.baseFare
                                    bidAmountText = "${(current - discount).toInt().coerceAtLeast(30)}"
                                }
                            ) {
                                Text(
                                    text = "-₹$discount",
                                    color = BargainGreenDark,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = bidAmountText,
                        onValueChange = { bidAmountText = it },
                        label = { Text("Your Bid Offer (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = bidAmountText.toDoubleOrNull() ?: return@Button
                        isSubmittingBid = true
                        coroutineScope.launch {
                            repository.postBid(ride.id, amount).onSuccess {
                                isSubmittingBid = false
                                selectedRideForBidding = null
                                successMessage = "Counter-bid submitted for ₹${amount.toInt()}!"
                                errorMessage = null
                            }.onFailure { err ->
                                isSubmittingBid = false
                                errorMessage = err.message ?: "Counter-bid rejected"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BargainGreen),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isSubmittingBid
                ) {
                    if (isSubmittingBid) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PureWhite)
                    } else {
                        Text("Send Counter-Bid", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedRideForBidding = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun CaptainRideAuctionCard(
    ride: RideDto,
    onBidClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = PureWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(ride.status)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ride.carType ?: "Auto",
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
                CountdownTimerPill(remainingSeconds = ride.remainingSeconds ?: 90L)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(BargainGreen))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Pickup: ${ride.pickupLocation}", fontSize = 12.sp, color = TextPrimary, maxLines = 1)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ErrorRed))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Drop: ${ride.destinationLocation}", fontSize = 12.sp, color = TextSecondary, maxLines = 1)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Passenger Base Offer", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = "₹${ride.baseFare.toInt()}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                }

                Button(
                    onClick = onBidClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RapidoYellow)
                ) {
                    Icon(Icons.Default.Gavel, contentDescription = null, tint = TextOnYellow, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Counter-Bid", color = TextOnYellow, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun ActiveCaptainTripCard(
    trip: RideDto,
    onMarkArrived: () -> Unit,
    onStartTrip: (String) -> Unit,
    onCompleteTrip: () -> Unit
) {
    var enteredOtp by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = RapidoYellowLight,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, RapidoYellow),
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ASSIGNED TRIP #${trip.id}", fontWeight = FontWeight.ExtraBold, color = RapidoOrangeDark, fontSize = 12.sp)
                StatusBadge(trip.status)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Agreed Fare: ₹${(trip.finalFare ?: trip.baseFare).toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = TextPrimary)
            Text("Pickup: ${trip.pickupLocation}", fontSize = 12.sp, color = TextPrimary)
            Text("Drop: ${trip.destinationLocation}", fontSize = 12.sp, color = TextSecondary)

            Spacer(modifier = Modifier.height(14.dp))

            when (trip.status.uppercase()) {
                "MATCHED", "ACCEPTED", "ARRIVING" -> {
                    Button(
                        onClick = onMarkArrived,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RapidoYellow)
                    ) {
                        Text("I Have Arrived at Pickup", color = TextOnYellow, fontWeight = FontWeight.ExtraBold)
                    }
                }
                "ARRIVED" -> {
                    Column {
                        Text("Enter Passenger's 4-Digit Boarding OTP to Start:", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = enteredOtp,
                                onValueChange = { enteredOtp = it },
                                label = { Text("4-Digit OTP") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { onStartTrip(enteredOtp) },
                                modifier = Modifier.align(Alignment.CenterVertically).height(52.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BargainGreen)
                            ) {
                                Text("Start Trip", color = PureWhite, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                "IN_PROGRESS" -> {
                    Button(
                        onClick = onCompleteTrip,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BargainGreen)
                    ) {
                        Text("Complete Trip & Collect Fare", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
