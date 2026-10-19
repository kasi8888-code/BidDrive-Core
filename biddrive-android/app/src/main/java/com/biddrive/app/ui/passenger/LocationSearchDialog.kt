package com.biddrive.app.ui.passenger

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.biddrive.app.data.locations.LocationSearchResult
import com.biddrive.app.data.locations.LocationUtils
import com.biddrive.app.data.locations.OpenStreetMapService
import com.biddrive.app.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSearchDialog(
    initialQuery: String = "",
    currentPickupLat: Double = 12.9022,
    currentPickupLon: Double = 80.1472,
    isPickupSelection: Boolean = false,
    onDismiss: () -> Unit,
    onLocationSelected: (name: String, lat: Double, lon: Double, distKm: Double) -> Unit
) {
    var searchQuery by remember { mutableStateOf(initialQuery) }
    var selectedCityFilter by remember { mutableStateOf("All India") }
    var searchResults by remember { mutableStateOf<List<LocationSearchResult>>(LocationUtils.POPULAR_HUBS) }
    var isSearching by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val cityFilters = listOf("All India", "Bengaluru", "Chennai", "Hyderabad", "Mumbai", "Coimbatore")

    // Live OpenStreetMap Geocoding with 300ms debounce
    LaunchedEffect(searchQuery) {
        searchJob?.cancel()
        if (searchQuery.isBlank()) {
            searchResults = LocationUtils.POPULAR_HUBS
            isSearching = false
        } else {
            isSearching = true
            searchJob = coroutineScope.launch {
                delay(300) // 300ms debounce to avoid spamming network while typing
                val liveResults = OpenStreetMapService.searchPlaces(searchQuery)
                searchResults = liveResults
                isSearching = false
            }
        }
    }

    val filteredResults = remember(searchResults, selectedCityFilter) {
        if (selectedCityFilter == "All India") {
            searchResults
        } else {
            searchResults.filter {
                it.cityOrState.contains(selectedCityFilter, ignoreCase = true) ||
                it.subtitle.contains(selectedCityFilter, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = PureWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Header with Back Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = if (isPickupSelection) "Set Pickup Location" else "Search Any Destination",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Powered by OpenStreetMap (Bangalore, Chennai, All India)",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Input with Live OpenStreetMap Indicator
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Koramangala, Whitefield, Tambaram, Airport...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = RapidoYellowDark)
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = RapidoYellow,
                                strokeWidth = 2.dp
                            )
                        } else if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RapidoYellow,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quick City Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(cityFilters) { city ->
                        val isSelected = selectedCityFilter == city
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) RapidoYellowLight else SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) RapidoYellow else BorderSubtle
                            ),
                            modifier = Modifier.clickable { selectedCityFilter = city }
                        ) {
                            Text(
                                text = city,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) TextOnYellow else TextSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom Location Option
                if (searchQuery.isNotBlank() && filteredResults.none { it.name.equals(searchQuery.trim(), ignoreCase = true) }) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BargainGreenLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BargainGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onLocationSelected(searchQuery.trim(), 12.9716, 77.5946, 12.0)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AddLocationAlt, contentDescription = null, tint = BargainGreen)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Use Custom Address: \"${searchQuery.trim()}\"",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = BargainGreenDark
                                )
                                Text("Tap to select this destination directly", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text(
                    text = if (searchQuery.isBlank()) "Popular Hubs in India" else "OpenStreetMap Search Results (${filteredResults.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Results List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredResults) { loc ->
                        val distanceKm = LocationUtils.calculateDistanceKm(
                            currentPickupLat, currentPickupLon,
                            loc.latitude, loc.longitude
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLocationSelected(loc.name, loc.latitude, loc.longitude, distanceKm)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (loc.category) {
                                                    "TRANSIT" -> Color(0xFFE0F2FE)
                                                    "CAMPUS" -> BargainGreenLight
                                                    else -> RapidoYellowLight
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when (loc.category) {
                                                "TRANSIT" -> Icons.Default.DirectionsTransit
                                                "CAMPUS" -> Icons.Default.School
                                                else -> Icons.Default.Place
                                            },
                                            contentDescription = null,
                                            tint = when (loc.category) {
                                                "TRANSIT" -> Color(0xFF0284C7)
                                                "CAMPUS" -> BargainGreen
                                                else -> RapidoOrangeDark
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = loc.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = loc.subtitle,
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = PureWhite,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Text(
                                        text = "${distanceKm} km",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
