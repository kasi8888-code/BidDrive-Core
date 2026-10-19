package com.biddrive.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.biddrive.app.ui.theme.*

/**
 * Interactive Real OpenStreetMap View for BidDrive.
 * Renders real OpenStreetMap tiles, pickup/destination pins, live OSRM highway turn-by-turn routes,
 * on-screen zoom in/out controls (+/-), and current user GPS location with zero paid API keys required.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RapidoRealMapView(
    modifier: Modifier = Modifier,
    pickupLat: Double = 12.8406, // Default: VIT Chennai Campus
    pickupLon: Double = 80.1534,
    pickupName: String = "VIT Chennai Campus",
    dropLat: Double = 12.9255,   // Default: Tambaram Railway Station
    dropLon: Double = 80.1167,
    dropName: String = "Tambaram Railway Station",
    etaMinutes: Int = 18,
    distanceKm: Double = 11.5,
    showRoute: Boolean = true,
    onCurrentLocationDetected: ((Double, Double, String) -> Unit)? = null
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }

    // HTML Template for Leaflet.js with OpenStreetMap Tiles & OSRM Routing
    val htmlData = remember {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                body, html { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background-color: #F8FAFC; }
                #map { width: 100%; height: 100%; }
                .leaflet-control-attribution { display: none !important; }
                .leaflet-control-zoom { display: none !important; }
                
                /* Custom Pulse Markers */
                .pickup-pin {
                    width: 22px; height: 22px;
                    background: #10B981; border: 3px solid #FFFFFF;
                    border-radius: 50%; box-shadow: 0 0 12px rgba(16,185,129,0.8);
                }
                .drop-pin {
                    width: 22px; height: 22px;
                    background: #EF4444; border: 3px solid #FFFFFF;
                    border-radius: 50%; box-shadow: 0 0 12px rgba(239,68,68,0.8);
                }
                .custom-label {
                    background: #FFFFFF; color: #1E293B; font-weight: 800; font-size: 11px;
                    padding: 3px 8px; border-radius: 6px; box-shadow: 0 2px 6px rgba(0,0,0,0.15);
                    border: 1px solid #E2E8F0; white-space: nowrap; font-family: sans-serif;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map', { 
                    zoomControl: false, 
                    attributionControl: false,
                    touchZoom: true,
                    doubleClickZoom: true,
                    scrollWheelZoom: true
                }).setView([12.8800, 80.1350], 12);
                
                // High-performance OpenStreetMap Mirror (100% Free, no watermark, keyless)
                L.tileLayer('https://tile.openstreetmap.de/{z}/{x}/{y}.png', {
                    maxZoom: 19
                }).addTo(map);

                var pickupMarker = null;
                var dropMarker = null;
                var routeLine = null;
                var routeGlow = null;

                function drawRoute(latlngs) {
                    if (routeLine) map.removeLayer(routeLine);
                    if (routeGlow) map.removeLayer(routeGlow);

                    // Polyline glow / outer border
                    routeGlow = L.polyline(latlngs, { color: '#0F172A', weight: 6, opacity: 0.7, lineCap: 'round', lineJoin: 'round' }).addTo(map);
                    // Core Rapido Amber / Green Polyline
                    routeLine = L.polyline(latlngs, { color: '#FFC000', weight: 4, opacity: 1.0, lineCap: 'round', lineJoin: 'round' }).addTo(map);

                    var bounds = L.latLngBounds(latlngs);
                    map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 });
                }

                function updateRoute(pLat, pLon, pName, dLat, dLon, dName, hasRoute) {
                    if (pickupMarker) map.removeLayer(pickupMarker);
                    if (dropMarker) map.removeLayer(dropMarker);

                    var pickupIcon = L.divIcon({
                        className: 'pickup-pin-container',
                        html: '<div class="pickup-pin"></div><div class="custom-label" style="margin-top:4px;">🟢 ' + pName + '</div>',
                        iconSize: [120, 40], iconAnchor: [11, 11]
                    });

                    var dropIcon = L.divIcon({
                        className: 'drop-pin-container',
                        html: '<div class="drop-pin"></div><div class="custom-label" style="margin-top:4px;">🔴 ' + dName + '</div>',
                        iconSize: [120, 40], iconAnchor: [11, 11]
                    });

                    pickupMarker = L.marker([pLat, pLon], { icon: pickupIcon }).addTo(map);
                    dropMarker = L.marker([dLat, dLon], { icon: dropIcon }).addTo(map);

                    if (hasRoute) {
                        // Fetch real highway turn-by-turn route via OSRM
                        var osrmUrl = 'https://router.project-osrm.org/route/v1/driving/' + pLon + ',' + pLat + ';' + dLon + ',' + dLat + '?overview=full&geometries=geojson';
                        fetch(osrmUrl)
                            .then(function(res) { return res.json(); })
                            .then(function(data) {
                                if (data && data.routes && data.routes.length > 0) {
                                    var coords = data.routes[0].geometry.coordinates.map(function(c) {
                                        return [c[1], c[0]]; // OSRM gives [lng, lat], Leaflet wants [lat, lng]
                                    });
                                    drawRoute(coords);
                                } else {
                                    var midLat = (pLat + dLat) / 2 + 0.005;
                                    var midLon = (pLon + dLon) / 2 - 0.005;
                                    drawRoute([[pLat, pLon], [midLat, midLon], [dLat, dLon]]);
                                }
                            })
                            .catch(function(err) {
                                var midLat = (pLat + dLat) / 2 + 0.005;
                                var midLon = (pLon + dLon) / 2 - 0.005;
                                drawRoute([[pLat, pLon], [midLat, midLon], [dLat, dLon]]);
                            });
                    } else {
                        map.setView([pLat, pLon], 14);
                    }
                }

                function centerAt(lat, lon, zoom) {
                    map.flyTo([lat, lon], zoom || 15);
                }

                function zoomIn() {
                    map.zoomIn();
                }

                function zoomOut() {
                    map.zoomOut();
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    // Push coordinates to Leaflet whenever pickup or drop changes
    LaunchedEffect(pickupLat, pickupLon, dropLat, dropLon, isMapLoaded) {
        if (isMapLoaded && webViewRef != null) {
            val safePName = pickupName.replace("'", "\\'")
            val safeDName = dropName.replace("'", "\\'")
            val js = "updateRoute($pickupLat, $pickupLon, '$safePName', $dropLat, $dropLon, '$safeDName', $showRoute);"
            webViewRef?.evaluateJavascript(js, null)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // 1. Live OpenStreetMap WebView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Mobile Safari/537.36 BidDrive/1.0"

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isMapLoaded = true
                            val safePName = pickupName.replace("'", "\\'")
                            val safeDName = dropName.replace("'", "\\'")
                            val js = "updateRoute($pickupLat, $pickupLon, '$safePName', $dropLat, $dropLon, '$safeDName', $showRoute);"
                            view?.evaluateJavascript(js, null)
                        }
                    }
                    loadDataWithBaseURL("https://biddrive.app", htmlData, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            },
            update = { view ->
                if (isMapLoaded) {
                    val safePName = pickupName.replace("'", "\\'")
                    val safeDName = dropName.replace("'", "\\'")
                    val js = "updateRoute($pickupLat, $pickupLon, '$safePName', $dropLat, $dropLon, '$safeDName', $showRoute);"
                    view.evaluateJavascript(js, null)
                }
            }
        )

        // 2. Floating Live ETA Badge on Map
        if (showRoute) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .shadow(8.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = PureWhite
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
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
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = " • $distanceKm km (OpenStreetMap)",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 3. Floating Control Bar (Zoom In, Zoom Out, and GPS Auto-Detect)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom Controls Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PureWhite,
                shadowElevation = 6.dp
            ) {
                Column {
                    // Zoom In Button (+)
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                        color = PureWhite,
                        onClick = {
                            webViewRef?.evaluateJavascript("zoomIn();", null)
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Zoom In",
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(1.dp)
                            .background(BorderSubtle)
                    )

                    // Zoom Out Button (-)
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                        color = PureWhite,
                        onClick = {
                            webViewRef?.evaluateJavascript("zoomOut();", null)
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Zoom Out",
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // My Location (GPS Auto-Detect) FAB
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = PureWhite,
                shadowElevation = 6.dp,
                onClick = {
                    // Fetch GPS or default to VIT Chennai
                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    var detectedLat = 12.8406 // VIT Chennai
                    var detectedLon = 80.1534
                    var detectedName = "VIT Chennai Campus"

                    try {
                        val lastGps = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        val lastNet = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        val best = lastGps ?: lastNet
                        if (best != null && best.latitude != 0.0) {
                            detectedLat = best.latitude
                            detectedLon = best.longitude
                            detectedName = "Current Location (${String.format("%.4f", detectedLat)}, ${String.format("%.4f", detectedLon)})"
                        }
                    } catch (_: Exception) {}

                    onCurrentLocationDetected?.invoke(detectedLat, detectedLon, detectedName)

                    // Smoothly fly camera to detected position
                    webViewRef?.evaluateJavascript("centerAt($detectedLat, $detectedLon, 15);", null)
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Detect Current Location",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
