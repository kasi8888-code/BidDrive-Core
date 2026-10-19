package com.biddrive.app.ui.passenger

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biddrive.app.data.locations.LocationUtils
import com.biddrive.app.data.models.BidDto
import com.biddrive.app.data.models.CreateRideRequest
import com.biddrive.app.data.models.RideDto
import com.biddrive.app.data.repository.BidDriveRepository
import com.biddrive.app.data.websocket.BidDriveWebSocket
import com.biddrive.app.ui.components.*
import com.biddrive.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class VehicleOption(
    val id: String,
    val name: String,
    val baseRatePerKm: Double,
    val etaSubtitle: String,
    val category: String,
    val tag: String? = null
)

@Composable
fun PassengerScreen(
    repository: BidDriveRepository
) {
    var activeRide by remember { mutableStateOf<RideDto?>(null) }
    var bidsList by remember { mutableStateOf<List<BidDto>>(emptyList()) }
    var remainingSeconds by remember { mutableStateOf(90L) }
    var selectedBottomTab by remember { mutableStateOf(0) }
    var showServerConfigDialog by remember { mutableStateOf(false) }
    var showWsInspector by remember { mutableStateOf(false) }

    val currentUser by repository.currentUser.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // 1. Dedicated Local 1-Second Countdown Timer for Reverse Auction
    LaunchedEffect(activeRide?.id, activeRide?.status) {
        val rideId = activeRide?.id ?: return@LaunchedEffect
        if (activeRide?.status == "INITIATED" || activeRide?.status == "AUCTION") {
            remainingSeconds = activeRide?.remainingSeconds ?: 90L
            while (remainingSeconds > 0 && (activeRide?.status == "INITIATED" || activeRide?.status == "AUCTION")) {
                delay(1000)
                remainingSeconds--
            }
        }
    }

    // 2. Real-time WebSocket & Sync Polling
    LaunchedEffect(activeRide?.id, activeRide?.status) {
        val rideId = activeRide?.id ?: return@LaunchedEffect
        if (activeRide?.status == "INITIATED" || activeRide?.status == "AUCTION") {
            repository.webSocket.connect()
            repository.webSocket.subscribeAuction(rideId)

            coroutineScope.launch {
                repository.webSocket.incomingBids.collect { incomingBid ->
                    if (incomingBid.rideId == rideId) {
                        bidsList = (listOf(incomingBid) + bidsList.filter { it.id != incomingBid.id })
                            .sortedBy { it.bidAmount }
                    }
                }
            }

            coroutineScope.launch {
                repository.webSocket.auctionEvents.collect { event ->
                    if (event.rideId == rideId) {
                        when (event.eventType) {
                            "AUCTION_MATCHED" -> {
                                activeRide = activeRide?.copy(
                                    status = "MATCHED",
                                    driverId = event.driverId ?: activeRide?.driverId,
                                    finalFare = event.lowestBid ?: activeRide?.finalFare,
                                    otp = event.otp ?: activeRide?.otp ?: "5140"
                                )
                            }
                            "DRIVER_ARRIVING" -> activeRide = activeRide?.copy(status = "DRIVER_ARRIVING")
                            "DRIVER_ARRIVED" -> activeRide = activeRide?.copy(status = "DRIVER_ARRIVED")
                            "TRIP_STARTED" -> activeRide = activeRide?.copy(status = "IN_PROGRESS")
                            "COMPLETED" -> activeRide = activeRide?.copy(status = "COMPLETED")
                        }
                    }
                }
            }

            // Sync poll every 2.5 seconds
            while (activeRide?.status == "INITIATED" || activeRide?.status == "AUCTION") {
                delay(2500)
                repository.getRide(rideId).onSuccess { updated ->
                    if (activeRide?.status == "INITIATED" || activeRide?.status == "AUCTION") {
                        activeRide = updated
                    }
                }
                repository.getBidsForRide(rideId).onSuccess { bids ->
                    if (bids.isNotEmpty()) {
                        bidsList = bids.sortedBy { it.bidAmount }
                    }
                }
            }
        } else if (activeRide != null && activeRide?.status != "COMPLETED" && activeRide?.status != "CANCELLED") {
            while (activeRide?.status != "COMPLETED" && activeRide?.status != "CANCELLED") {
                delay(3000)
                repository.getRide(rideId).onSuccess { updated ->
                    // Do not revert from MATCHED/ACCEPTED back to an unaccepted state
                    if (updated.status != "INITIATED" && updated.status != "AUCTION") {
                        activeRide = updated
                    }
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (activeRide == null) {
                RapidoBottomNavBar(
                    selectedTab = selectedBottomTab,
                    onTabSelected = { selectedBottomTab = it }
                )
            }
        },
        containerColor = PureWhite
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(PureWhite)
        ) {
            if (activeRide == null) {
                // Route between Bottom Tabs: 0 = Home, 1 = Activity, 2 = Account
                when (selectedBottomTab) {
                    0 -> {
                        RapidoPassengerHomeView(
                            repository = repository,
                            onRideCreated = { ride ->
                                activeRide = ride
                                remainingSeconds = ride.remainingSeconds ?: 90L
                                bidsList = emptyList()
                            }
                        )
                    }
                    1 -> {
                        ActivityScreen(
                            onRebookRide = { pickup, drop, vehicle ->
                                selectedBottomTab = 0
                            }
                        )
                    }
                    2 -> {
                        AccountScreen(
                            currentUser = currentUser,
                            onOpenServerConfig = { showServerConfigDialog = true },
                            onLogout = { repository.logout() }
                        )
                    }
                }
            } else if (activeRide?.status == "INITIATED" || activeRide?.status == "AUCTION") {
                // Distinguish between INSTANT booking vs REVERSE AUCTION
                if (activeRide?.rideType == "INSTANT") {
                    RapidoInstantSearchingView(
                        ride = activeRide!!,
                        webSocket = repository.webSocket,
                        onOpenWsInspector = { showWsInspector = true },
                        onCancelRide = {
                            coroutineScope.launch {
                                repository.cancelRide(activeRide!!.id, "Passenger cancelled search").onSuccess {
                                    activeRide = null
                                }
                            }
                        },
                        onSimulateDriverAccept = {
                            coroutineScope.launch {
                                repository.simulateDriverStep(activeRide!!.id).onSuccess { updated ->
                                    activeRide = updated
                                }.onFailure {
                                    // Fallback if offline
                                    activeRide = activeRide?.copy(
                                        status = "ACCEPTED",
                                        driverId = 1,
                                        otp = "4821",
                                        finalFare = activeRide?.baseFare
                                    )
                                }
                            }
                        }
                    )
                } else {
                    RapidoLiveAuctionView(
                        ride = activeRide!!,
                        bids = bidsList,
                        webSocket = repository.webSocket,
                        remainingSeconds = remainingSeconds,
                        onOpenWsInspector = { showWsInspector = true },
                        onAcceptBid = { bidId ->
                            coroutineScope.launch {
                                val acceptedBid = bidsList.firstOrNull { it.id == bidId }
                                val res = repository.acceptBid(bidId)
                                if (res.isSuccess) {
                                    val refreshRes = repository.getRide(activeRide!!.id)
                                    if (refreshRes.isSuccess) {
                                        activeRide = refreshRes.getOrNull()
                                    } else {
                                        activeRide = activeRide?.copy(
                                            status = "MATCHED",
                                            driverId = acceptedBid?.driverId ?: 1,
                                            otp = activeRide?.otp ?: "5140",
                                            finalFare = acceptedBid?.bidAmount ?: activeRide?.baseFare
                                        )
                                    }
                                } else {
                                    activeRide = activeRide?.copy(
                                        status = "MATCHED",
                                        driverId = acceptedBid?.driverId ?: 1,
                                        otp = activeRide?.otp ?: "5140",
                                        finalFare = acceptedBid?.bidAmount ?: activeRide?.baseFare
                                    )
                                }
                            }
                        },
                        onCancelRide = {
                            coroutineScope.launch {
                                repository.cancelRide(activeRide!!.id, "Passenger cancelled auction").onSuccess {
                                    activeRide = null
                                }
                            }
                        },
                        onSimulateDriverBid = {
                            // Dispatches real backend simulation: updates Redis & Postgres, and broadcasts STOMP frame over /topic/auction/{rideId}
                            coroutineScope.launch {
                                repository.simulateBackendBid(activeRide!!.id)
                            }
                        }
                    )
                }
            } else {
                RapidoRideTrackingView(
                    ride = activeRide!!,
                    onCancelRide = {
                        coroutineScope.launch {
                            repository.cancelRide(activeRide!!.id, "Passenger cancelled").onSuccess {
                                activeRide = null
                            }
                        }
                    },
                    onNewRide = {
                        activeRide = null
                    },
                    onAdvanceTrip = {
                        val nextStatus = when (activeRide?.status?.uppercase()) {
                            "MATCHED", "ACCEPTED" -> "ARRIVING"
                            "ARRIVING" -> "ARRIVED"
                            "ARRIVED" -> "IN_PROGRESS"
                            "IN_PROGRESS" -> "COMPLETED"
                            else -> "COMPLETED"
                        }
                        activeRide = activeRide?.copy(status = nextStatus)
                    }
                )
            }
        }

        if (showWsInspector) {
            WebSocketInspectorDialog(
                webSocket = repository.webSocket,
                rideId = activeRide?.id,
                onDismiss = { showWsInspector = false }
            )
        }
    }
}

/**
 * Rapido Passenger Home View with Tamil Nadu Live Search and Dynamic Price Recalculation.
 */
@Composable
fun RapidoPassengerHomeView(
    repository: BidDriveRepository,
    onRideCreated: (RideDto) -> Unit
) {
    var pickupLocation by remember { mutableStateOf("VIT Chennai Campus") }
    var dropLocation by remember { mutableStateOf("Tambaram Railway Station") }
    var pickupLat by remember { mutableStateOf(12.8406) }
    var pickupLon by remember { mutableStateOf(80.1534) }
    var dropLat by remember { mutableStateOf(12.9255) }
    var dropLon by remember { mutableStateOf(80.1167) }
    var distanceKm by remember { mutableStateOf(11.5) }

    var selectedVehicle by remember { mutableStateOf("Auto") }
    var isBargainMode by remember { mutableStateOf(false) }
    var customBargainFare by remember { mutableStateOf("260") }
    var selectedPaymentMethod by remember { mutableStateOf("Cash") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var userRatingGiven by remember { mutableStateOf(5) }

    var showSearchDialog by remember { mutableStateOf(false) }
    var isEditingPickup by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Dynamically calculate fares for all vehicle categories based on distance
    val autoPrice = remember(distanceKm) { LocationUtils.calculateFare("auto", distanceKm) }
    val bikePrice = remember(distanceKm) { LocationUtils.calculateFare("bike", distanceKm) }
    val scootyPrice = remember(distanceKm) { LocationUtils.calculateFare("scooty", distanceKm) }
    val cabEcoPrice = remember(distanceKm) { LocationUtils.calculateFare("cab_economy", distanceKm) }
    val cabPrioPrice = remember(distanceKm) { LocationUtils.calculateFare("cab_priority", distanceKm) }

    val vehicleOptions = remember(distanceKm, autoPrice, bikePrice, scootyPrice, cabEcoPrice, cabPrioPrice) {
        val etaMinutes = (distanceKm * 2.1).toInt().coerceAtLeast(3)
        listOf(
            VehicleOption("bike", "Bike", bikePrice, "$etaMinutes mins away", "Bike", "FASTEST"),
            VehicleOption("scooty", "Scooty", scootyPrice, "${etaMinutes + 1} mins away", "Scooty"),
            VehicleOption("auto", "Auto", autoPrice, "${etaMinutes + 3} mins away", "Auto", "POPULAR"),
            VehicleOption("cab_economy", "Cab Economy", cabEcoPrice, "${etaMinutes + 4} mins away", "Sedan"),
            VehicleOption("cab_priority", "Cab Priority", cabPrioPrice, "${etaMinutes - 2} mins away", "Sedan", "COMFORT")
        )
    }

    val selectedOption = vehicleOptions.find { it.name == selectedVehicle } ?: vehicleOptions[2]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Live Real OpenStreetMap View (Leaflet + OSM Tiles)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        ) {
            RapidoRealMapView(
                modifier = Modifier.fillMaxSize(),
                pickupLat = pickupLat,
                pickupLon = pickupLon,
                pickupName = pickupLocation,
                dropLat = dropLat,
                dropLon = dropLon,
                dropName = dropLocation,
                etaMinutes = (distanceKm * 2.1).toInt().coerceAtLeast(3),
                distanceKm = distanceKm,
                showRoute = true,
                onCurrentLocationDetected = { lat, lon, name ->
                    pickupLat = lat
                    pickupLon = lon
                    pickupLocation = name
                    distanceKm = LocationUtils.calculateDistanceKm(pickupLat, pickupLon, dropLat, dropLon)
                }
            )
        }

        // 2. Dual Pickup & Drop Location Card with 1-Tap Swap and GPS
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .shadow(6.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = PureWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Pickup Selection Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isEditingPickup = true
                            showSearchDialog = true
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(BargainGreen)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pickup Location",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BargainGreen
                        )
                        Text(
                            text = pickupLocation,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                    }
                    // Tap to Detect Current Location
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceMuted,
                        modifier = Modifier.clickable {
                            pickupLat = 12.8406
                            pickupLon = 80.1534
                            pickupLocation = "VIT Chennai Campus (Current GPS)"
                            distanceKm = LocationUtils.calculateDistanceKm(pickupLat, pickupLon, dropLat, dropLon)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Current Location",
                                tint = BargainGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "GPS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BargainGreen
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .width(2.dp)
                            .height(14.dp)
                            .background(BorderSubtle)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // Swap Pickup and Destination
                    IconButton(
                        onClick = {
                            val tempLoc = pickupLocation
                            val tempLat = pickupLat
                            val tempLon = pickupLon
                            pickupLocation = dropLocation
                            pickupLat = dropLat
                            pickupLon = dropLon
                            dropLocation = tempLoc
                            dropLat = tempLat
                            dropLon = tempLon
                            distanceKm = LocationUtils.calculateDistanceKm(pickupLat, pickupLon, dropLat, dropLon)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Swap Locations",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Destination Selection Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isEditingPickup = false
                            showSearchDialog = true
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ErrorRed)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Drop Destination",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed
                        )
                        Text(
                            text = dropLocation,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Destination",
                        tint = RapidoYellowDark,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Route Chips: VIT to Tambaram, Airport, T. Nagar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = RapidoYellowLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, RapidoYellow),
                        modifier = Modifier.clickable {
                            pickupLocation = "VIT Chennai Campus"
                            pickupLat = 12.8406
                            pickupLon = 80.1534
                            dropLocation = "Tambaram Railway Station"
                            dropLat = 12.9255
                            dropLon = 80.1167
                            distanceKm = LocationUtils.calculateDistanceKm(pickupLat, pickupLon, dropLat, dropLon)
                        }
                    ) {
                        Text(
                            text = "🎓 VIT ➔ 🚉 Tambaram",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextOnYellow,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceMuted,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.clickable {
                            dropLocation = "Chennai International Airport (MAA)"
                            dropLat = 12.9941
                            dropLon = 80.1709
                            distanceKm = LocationUtils.calculateDistanceKm(pickupLat, pickupLon, dropLat, dropLon)
                        }
                    ) {
                        Text(
                            text = "✈️ Airport",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceMuted,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.clickable {
                            dropLocation = "T. Nagar Panagal Park"
                            dropLat = 13.0418
                            dropLon = 80.2341
                            distanceKm = LocationUtils.calculateDistanceKm(pickupLat, pickupLon, dropLat, dropLon)
                        }
                    ) {
                        Text(
                            text = "🏬 T. Nagar",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // 2. Service Category Quick Grid (Bike, Auto, Cab Economy, Parcel)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(
                Triple("Bike", "₹${bikePrice.toInt()}", "BIKE"),
                Triple("Auto", "₹${autoPrice.toInt()}", "AUTO"),
                Triple("Cab", "₹${cabEcoPrice.toInt()}", "CAB"),
                Triple("Parcel", "₹49", "PARCEL")
            ).forEach { (name, price, type) ->
                val isSelected = (name == "Auto" && selectedVehicle == "Auto") ||
                                 (name == "Bike" && selectedVehicle == "Bike") ||
                                 (name == "Cab" && selectedVehicle.startsWith("Cab"))
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (name == "Bike") selectedVehicle = "Bike"
                            if (name == "Auto") selectedVehicle = "Auto"
                            if (name == "Cab") selectedVehicle = "Cab Economy"
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) RapidoYellowLight else PureWhite,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) RapidoYellow else BorderSubtle
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (type) {
                            "AUTO" -> AutoRickshawIcon(size = 32.dp)
                            "BIKE" -> MotorcycleIcon(size = 32.dp)
                            "CAB" -> CabEconomyIcon(size = 32.dp)
                            else -> ParcelBoxIcon(size = 32.dp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = price, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = BargainGreen)
                    }
                }
            }
        }

        // 3. Low Coins / Wallet Alert Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFFEF3C7),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Low Rapido coins / wallet balance. Pay with Cash or UPI.",
                    fontSize = 11.sp,
                    color = Color(0xFF92400E),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 4. Vehicle Selection List with Real-time TN Fares
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            vehicleOptions.forEach { option ->
                val isSelected = selectedVehicle == option.name
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { selectedVehicle = option.name },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) RapidoYellowLight else PureWhite,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) RapidoYellow else BorderSubtle
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (option.id) {
                                "auto" -> AutoRickshawIcon(size = 46.dp)
                                "bike" -> MotorcycleIcon(size = 46.dp)
                                "scooty" -> ScootyIcon(size = 46.dp)
                                "cab_priority" -> CabPriorityIcon(size = 46.dp)
                                else -> CabEconomyIcon(size = 46.dp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = option.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TextPrimary
                                    )
                                    if (option.tag != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (option.tag == "FASTEST") BargainGreenLight else RapidoYellowLight
                                        ) {
                                            Text(
                                                text = option.tag,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (option.tag == "FASTEST") BargainGreen else TextOnYellow,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${option.etaSubtitle} • ${distanceKm} km",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "₹${option.baseRatePerKm.toInt()}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = TextPrimary
                            )
                            if (isSelected) {
                                Text(
                                    text = "Selected",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BargainGreen
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Payment Selector Row
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = SurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        selectedPaymentMethod = if (selectedPaymentMethod == "Cash") "UPI / Wallet" else "Cash"
                    }
                ) {
                    CashRupeeIcon(size = 20.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedPaymentMethod,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalOffer,
                        contentDescription = "Offer",
                        tint = BargainGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Offers & Coupons",
                        color = BargainGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 6. Bargain Fare Panel
        AnimatedVisibility(visible = isBargainMode) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = BargainGreenLight,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, BargainGreen)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Bargain Fare (Reverse Auction)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = BargainGreenDark
                            )
                            Text(
                                text = "Set your offer — nearby drivers will compete to underbid it",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        IconButton(onClick = { isBargainMode = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customBargainFare,
                            onValueChange = { customBargainFare = it },
                            label = { Text("Your Offer (₹)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        // Quick Bargain Chips
                        listOf(
                            (selectedOption.baseRatePerKm * 0.9).toInt(),
                            (selectedOption.baseRatePerKm * 0.85).toInt()
                        ).forEach { discountFare ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PureWhite,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BargainGreen),
                                modifier = Modifier.clickable { customBargainFare = discountFare.toString() }
                            ) {
                                Text(
                                    text = "₹$discountFare",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BargainGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = ErrorRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // 7. Dual Action Buttons: "Bargain & Book" vs "Book Auto" (Instant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // "Bargain & Book" (Green #00A854 - initiates Reverse Auction)
            Button(
                onClick = {
                    if (!isBargainMode) {
                        isBargainMode = true
                        customBargainFare = (selectedOption.baseRatePerKm * 0.9).toInt().toString()
                    } else {
                        val fare = customBargainFare.toDoubleOrNull() ?: (selectedOption.baseRatePerKm * 0.9)
                        createRideAction(
                            repository = repository,
                            coroutineScope = coroutineScope,
                            pickup = pickupLocation,
                            drop = dropLocation,
                            pickupLat = pickupLat,
                            pickupLon = pickupLon,
                            dropLat = dropLat,
                            dropLon = dropLon,
                            baseFare = fare,
                            carType = selectedOption.category,
                            rideType = "AUCTION",
                            onLoading = { isLoading = it },
                            onError = { errorMessage = it },
                            onSuccess = onRideCreated
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BargainGreen),
                enabled = !isLoading
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Gavel,
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBargainMode) "Start Auction" else "Bargain & Book",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = PureWhite
                    )
                }
            }

            // "Book Auto" (Yellow #F9C933 - Instant Booking at fixed rate)
            Button(
                onClick = {
                    createRideAction(
                        repository = repository,
                        coroutineScope = coroutineScope,
                        pickup = pickupLocation,
                        drop = dropLocation,
                        pickupLat = pickupLat,
                        pickupLon = pickupLon,
                        dropLat = dropLat,
                        dropLon = dropLon,
                        baseFare = selectedOption.baseRatePerKm,
                        carType = selectedOption.category,
                        rideType = "INSTANT",
                        onLoading = { isLoading = it },
                        onError = { errorMessage = it },
                        onSuccess = onRideCreated
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RapidoYellow),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = TextOnYellow)
                } else {
                    Text(
                        text = "Book ${selectedOption.name}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = TextOnYellow
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 8. Recent Locations (Clickable - pre-fills instantly!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Popular Locations & Hubs",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimary
            )
            Text(
                text = "Search India >",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = RapidoYellowDark,
                modifier = Modifier.clickable {
                    isEditingPickup = false
                    showSearchDialog = true
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple("Koramangala", "Koramangala, Bengaluru, Karnataka", Icons.Default.LocationCity),
                Triple("Whitefield IT Park", "Whitefield, Bengaluru, Karnataka", Icons.Default.Business),
                Triple("Station E Tambaram", "East Tambaram Railway Station, Chennai", Icons.Default.Train),
                Triple("SRM Kattankulathur", "SRM IST University, Potheri", Icons.Default.School),
                Triple("Hitech City / Cyber Towers", "Madhapur, Hyderabad", Icons.Default.BusinessCenter)
            ).forEach { (title, subtitle, icon) ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val matched = LocationUtils.POPULAR_HUBS.find { it.name.contains(title, ignoreCase = true) }
                            if (matched != null) {
                                dropLocation = matched.name
                                dropLat = matched.latitude
                                dropLon = matched.longitude
                                distanceKm = LocationUtils.calculateDistanceKm(pickupLat, pickupLon, dropLat, dropLon)
                            } else {
                                dropLocation = title
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PureWhite),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                            Text(text = subtitle, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 9. "Rate your ride with BASKAR H" Review Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
            color = PureWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(RapidoYellowLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = TextOnYellow)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Rate your ride with BASKAR H",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Auto • 4.93 ⭐ • Yesterday at 6:45 PM",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { userRatingGiven = star }) {
                            Icon(
                                imageVector = if (star <= userRatingGiven) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Star $star",
                                tint = if (star <= userRatingGiven) RapidoAmber else TextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Modal Search Dialog for All Places in Tamil Nadu
    if (showSearchDialog) {
        LocationSearchDialog(
            initialQuery = if (isEditingPickup) pickupLocation else "",
            currentPickupLat = pickupLat,
            currentPickupLon = pickupLon,
            isPickupSelection = isEditingPickup,
            onDismiss = { showSearchDialog = false },
            onLocationSelected = { name, lat, lon, _ ->
                if (isEditingPickup) {
                    pickupLocation = name
                    pickupLat = lat
                    pickupLon = lon
                    distanceKm = LocationUtils.calculateDistanceKm(pickupLat, pickupLon, dropLat, dropLon)
                } else {
                    dropLocation = name
                    dropLat = lat
                    dropLon = lon
                    distanceKm = LocationUtils.calculateDistanceKm(pickupLat, pickupLon, dropLat, dropLon)
                }
                showSearchDialog = false
            }
        )
    }
}

/**
 * Normal Instant Ride Searching Screen (When user clicked "Book Auto").
 * Clearly displays searching nearby drivers without underbidding reverse auction confusion!
 */
@Composable
fun RapidoInstantSearchingView(
    ride: RideDto,
    webSocket: BidDriveWebSocket,
    onOpenWsInspector: () -> Unit,
    onCancelRide: () -> Unit,
    onSimulateDriverAccept: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        WebSocketStatusBadge(
            webSocket = webSocket,
            onClick = onOpenWsInspector,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(RapidoYellowLight),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = RapidoYellow,
                strokeWidth = 4.dp,
                modifier = Modifier.size(90.dp)
            )
            AutoRickshawIcon(size = 46.dp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Searching Nearby Drivers...",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Connecting with nearest ${ride.carType ?: "Auto"} Captain at fixed fare ₹${ride.baseFare.toInt()}",
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(BargainGreen))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Pickup: ${ride.pickupLocation}", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ErrorRed))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Drop: ${ride.destinationLocation}", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = BorderSubtle)
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Guaranteed Ride Fare", fontSize = 12.sp, color = TextSecondary)
                    Text("₹${ride.baseFare.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onSimulateDriverAccept,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RapidoYellow),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = TextOnYellow)
                Spacer(modifier = Modifier.width(4.dp))
                Text("⚡ Accept Ride", color = TextOnYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = onOpenWsInspector,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null, tint = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Inspect WS", color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onCancelRide,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
        ) {
            Text("Cancel Search")
        }
    }
}

/**
 * Reverse Auction Radar Screen with Live Countdown Timer (90s.. 89s.. 88s..)
 */
@Composable
fun RapidoLiveAuctionView(
    ride: RideDto,
    bids: List<BidDto>,
    webSocket: BidDriveWebSocket,
    remainingSeconds: Long,
    onOpenWsInspector: () -> Unit,
    onAcceptBid: (Int) -> Unit,
    onCancelRide: () -> Unit,
    onSimulateDriverBid: () -> Unit
) {
    val lowestBid = bids.minByOrNull { it.bidAmount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .padding(16.dp)
    ) {
        // Auction Header Card with ticking countdown timer and live WS badge
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = PureWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Reverse Auction #${ride.id}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "${ride.carType ?: "Auto"} • Starting Base: ₹${ride.baseFare.toInt()}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CountdownTimerPill(remainingSeconds = remainingSeconds)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WebSocketStatusBadge(webSocket = webSocket, onClick = onOpenWsInspector)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(BargainGreen))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = ride.pickupLocation, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ErrorRed))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = ride.destinationLocation, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Lowest Bid Spotlight Banner
        if (lowestBid != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = BargainGreenLight,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, BargainGreen)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Current Lowest Bid", fontSize = 11.sp, color = BargainGreenDark, fontWeight = FontWeight.Bold)
                        Text(
                            text = "₹${lowestBid.bidAmount.toInt()}",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        val savings = ride.baseFare - lowestBid.bidAmount
                        if (savings > 0) {
                            Text("You save ₹${savings.toInt()}!", fontSize = 12.sp, color = BargainGreen, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    Button(
                        onClick = { onAcceptBid(lowestBid.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = BargainGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Accept ₹${lowestBid.bidAmount.toInt()}", color = PureWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = RapidoYellowDark, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Broadcasting to nearby ${ride.carType ?: "Auto"} drivers...",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Drivers are competing and underbidding each other",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Real Backend Driver Dispatch + STOMP Live Inspector Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onSimulateDriverBid,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BargainGreenDark)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("⚡ Auto-Driver Bid", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = onOpenWsInspector,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Inspect WS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PureWhite)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Received Counter-Bids (${bids.size})",
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(bids) { bid ->
                val isLowest = bid.id == lowestBid?.id
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isLowest) BargainGreenLight else SurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isLowest) 1.5.dp else 1.dp,
                        color = if (isLowest) BargainGreen else BorderSubtle
                    )
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isLowest) BargainGreen else RapidoYellow),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = if (isLowest) PureWhite else TextOnYellow,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(bid.driverName ?: "Captain #${bid.driverId}", fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(
                                    text = if (isLowest) "⭐ Lowest Counter-Offer" else "4.9 ⭐ Captain",
                                    fontSize = 11.sp,
                                    color = if (isLowest) BargainGreenDark else TextSecondary,
                                    fontWeight = if (isLowest) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "₹${bid.bidAmount.toInt()}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isLowest) BargainGreen else TextPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = { onAcceptBid(bid.id) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isLowest) BargainGreen else RapidoYellow
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Accept",
                                    fontSize = 12.sp,
                                    color = if (isLowest) PureWhite else TextOnYellow,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onCancelRide,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
        ) {
            Text("Cancel Auction")
        }
    }
}

/**
 * Ride Tracking Screen with Captain details, Boarding OTP (4821), and Bill Breakdown.
 */
@Composable
fun RapidoRideTrackingView(
    ride: RideDto,
    onCancelRide: () -> Unit,
    onNewRide: () -> Unit,
    onAdvanceTrip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StatusBadge(ride.status)
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = when (ride.status.uppercase()) {
                "MATCHED", "ACCEPTED", "ARRIVING" -> "Captain is on the way!"
                "ARRIVED" -> "Captain has arrived at pickup!"
                "IN_PROGRESS" -> "Trip in progress to destination"
                "COMPLETED" -> "Trip Completed!"
                else -> "Ride Status: ${ride.status}"
            },
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // START TRIP OTP Card
        if (ride.otp != null && (ride.status.uppercase() in listOf("MATCHED", "ACCEPTED", "ARRIVING", "ARRIVED"))) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = RapidoYellowLight,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, RapidoYellow)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "START TRIP OTP",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = RapidoOrangeDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ride.otp,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 6.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Share this 4-digit code with Captain BASKAR H upon boarding",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Live OpenStreetMap Route in Ride Tracking View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            RapidoRealMapView(
                modifier = Modifier.fillMaxSize(),
                pickupLat = ride.pickupLatitude ?: 12.8406,
                pickupLon = ride.pickupLongitude ?: 80.1534,
                pickupName = ride.pickupLocation,
                dropLat = ride.destinationLatitude ?: 12.9255,
                dropLon = ride.destinationLongitude ?: 80.1167,
                dropName = ride.destinationLocation,
                etaMinutes = 15,
                distanceKm = 11.5,
                showRoute = true
            )
        }
        Spacer(modifier = Modifier.height(14.dp))

        // Captain Details Card (BASKAR H, 4.93 ⭐)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(RapidoYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = TextOnYellow)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("BASKAR H", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = RapidoAmber, modifier = Modifier.size(14.dp))
                                Text(" 4.93 • Bajaj RE Auto", fontSize = 11.sp, color = TextSecondary)
                            }
                            Text("TN 11 AB 4821", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }

                    IconButton(
                        onClick = { /* Demo call */ },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(BargainGreenLight)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = BargainGreen)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BorderSubtle)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Agreed Ride Fare", color = TextSecondary, fontSize = 12.sp)
                    Text("₹${(ride.finalFare ?: ride.baseFare).toInt()}", fontWeight = FontWeight.ExtraBold, color = BargainGreen, fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Fare Breakdown Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = PureWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Fare Breakdown", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Base Fare", fontSize = 12.sp, color = TextSecondary)
                    Text("₹${((ride.finalFare ?: ride.baseFare) - 10).toInt().coerceAtLeast(10)}", fontSize = 12.sp, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cancellation Fee Adjustment", fontSize = 12.sp, color = TextSecondary)
                    Text("₹10", fontSize = 12.sp, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = BorderSubtle)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Amount", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                    Text("₹${(ride.finalFare ?: ride.baseFare).toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Simulation Action: Advance Trip Stage
        if (ride.status != "COMPLETED" && ride.status != "CANCELLED") {
            Button(
                onClick = onAdvanceTrip,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RapidoYellow)
            ) {
                Icon(Icons.Default.FastForward, contentDescription = null, tint = TextOnYellow)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "⚡ Advance Captain Trip (${ride.status})",
                    fontWeight = FontWeight.Bold,
                    color = TextOnYellow
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (ride.status == "COMPLETED") {
            Button(
                onClick = onNewRide,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BargainGreen)
            ) {
                Text("Book Another Ride", color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        } else if (ride.status != "CANCELLED") {
            OutlinedButton(
                onClick = onCancelRide,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
            ) {
                Text("Cancel Ride")
            }
        }
    }
}

private fun createRideAction(
    repository: BidDriveRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    pickup: String,
    drop: String,
    pickupLat: Double,
    pickupLon: Double,
    dropLat: Double,
    dropLon: Double,
    baseFare: Double,
    carType: String,
    rideType: String,
    onLoading: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onSuccess: (RideDto) -> Unit
) {
    onLoading(true)
    onError(null)
    coroutineScope.launch {
        val req = CreateRideRequest(
            pickupLocation = pickup,
            destinationLocation = drop,
            pickupLatitude = pickupLat,
            pickupLongitude = pickupLon,
            destinationLatitude = dropLat,
            destinationLongitude = dropLon,
            baseFare = baseFare,
            vehicleType = carType,
            rideType = rideType
        )
        repository.createRide(req).onSuccess { ride ->
            onLoading(false)
            onSuccess(ride)
        }.onFailure { err ->
            onLoading(false)
            onError(err.message ?: "Failed to initiate ride")
        }
    }
}
