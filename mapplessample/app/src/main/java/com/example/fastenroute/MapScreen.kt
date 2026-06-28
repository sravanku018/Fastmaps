package com.example.fastenroute

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.OvershootInterpolator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.fastenroute.api.MapplsApi
import com.example.fastenroute.api.Route
import com.example.fastenroute.api.SuggestedLocation
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.mappls.sdk.maps.MapView
import com.mappls.sdk.maps.MapplsMap
import com.mappls.sdk.maps.OnMapReadyCallback
import com.mappls.sdk.maps.annotations.MarkerOptions
import com.mappls.sdk.maps.annotations.PolylineOptions
import com.mappls.sdk.maps.camera.CameraUpdateFactory
import com.mappls.sdk.maps.geometry.LatLng
import com.mappls.sdk.maps.geometry.LatLngBounds
import com.mappls.sdk.maps.location.LocationComponentActivationOptions
import com.mappls.sdk.maps.location.LocationComponentOptions
import com.mappls.sdk.maps.location.modes.CameraMode
import com.mappls.sdk.maps.location.modes.RenderMode
import com.mappls.sdk.maps.annotations.IconFactory
import com.mappls.sdk.maps.offline.OfflineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Destination(val name: String, val address: String, val lat: Double, val lng: Double)
data class RouteInfo(val distanceKm: String, val duration: String, val index: Int)

private const val TAG = "MapScreen"

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var mapplsMap by remember { mutableStateOf<MapplsMap?>(null) }
    var currentLat by remember { mutableDoubleStateOf(17.385) }
    var currentLng by remember { mutableDoubleStateOf(78.4867) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var currentLocationMarker by remember { mutableStateOf<com.mappls.sdk.maps.annotations.Marker?>(null) }

    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SuggestedLocation>>(emptyList()) }
    var selectedDestination by remember { mutableStateOf<Destination?>(null) }
    var routeOptions by remember { mutableStateOf<List<RouteInfo>>(emptyList()) }
    var selectedRouteIndex by remember { mutableIntStateOf(0) }
    var allRoutes by remember { mutableStateOf<List<Route>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isRouting by remember { mutableStateOf(false) }
    var routeError by remember { mutableStateOf<String?>(null) }

    // Track both conditions: permissions granted AND map ready
    var isMapReady by remember { mutableStateOf(false) }
    var locationInitialized by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        Log.d(TAG, "Permission result: fine=${permissions[Manifest.permission.ACCESS_FINE_LOCATION]}")
        // If map is already ready, try to get location now
        if (hasLocationPermission && isMapReady && !locationInitialized) {
            locationInitialized = true
            tryGetFusedLocation(context) { lat, lng ->
                currentLat = lat
                currentLng = lng
                mapplsMap?.let { map ->
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 14.0))
                    // Add animated current location marker with pulsing icon
                    currentLocationMarker?.remove()
                    val locIcon = IconFactory.getInstance(context).fromBitmap(createLocationMarkerBitmap())
                    currentLocationMarker = map.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lng))
                            .title("My Location")
                            .icon(locIcon)
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            hasLocationPermission = true
        }
    }

    // When both permissions and map are ready, get location
    LaunchedEffect(hasLocationPermission, isMapReady) {
        if (hasLocationPermission && isMapReady && !locationInitialized) {
            locationInitialized = true
            tryGetFusedLocation(context) { lat, lng ->
                currentLat = lat
                currentLng = lng
                mapplsMap?.let { map ->
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 14.0))
                    // Add animated current location marker with pulsing icon
                    currentLocationMarker?.remove()
                    val locIcon = IconFactory.getInstance(context).fromBitmap(createLocationMarkerBitmap())
                    currentLocationMarker = map.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lng))
                            .title("My Location")
                            .icon(locIcon)
                    )
                }
            }
        }
    }

    val mapView = remember {
        MapView(activity).also { mv ->
            mv.onCreate(activity.intent?.extras)
            mv.getMapAsync(object : OnMapReadyCallback {
                override fun onMapReady(map: MapplsMap) {
                    Log.d(TAG, "Map ready")
                    mapplsMap = map
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(currentLat, currentLng), 14.0))
                    isMapReady = true

                    // Try to enable Mappls location component (blue dot) if we have permission
                    if (hasLocationPermission) {
                        try {
                            val options = LocationComponentOptions.builder(context)
                                .trackingGesturesManagement(true)
                                .pulseEnabled(true)
                                .pulseColor(Color.parseColor("#2196F3"))
                                .pulseAlpha(0.4f)
                                .pulseMaxRadius(24f)
                                .build()
                            val actOpt = LocationComponentActivationOptions
                                .builder(context, map.style!!)
                                .locationComponentOptions(options)
                                .build()
                            map.locationComponent.activateLocationComponent(actOpt)
                            map.locationComponent.isLocationComponentEnabled = true
                    map.locationComponent.cameraMode = CameraMode.NONE
                    map.locationComponent.renderMode = RenderMode.NORMAL
                } catch (e: Exception) {
                    Log.e(TAG, "Location component setup failed: ${e.message}", e)
                }
            }

            // Configure offline tile caching (max 200MB ambient cache)
            try {
                val offlineManager = OfflineManager.getInstance(context)
                offlineManager.setMaximumAmbientCacheSize(200 * 1024 * 1024L,
                    object : com.mappls.sdk.maps.offline.OfflineManager.FileSourceCallback {
                        override fun onSuccess() {
                            Log.d(TAG, "Ambient cache set to 200MB")
                        }
                        override fun onError(error: String) {
                            Log.e(TAG, "Cache size error: $error")
                        }
                    })
                offlineManager.runPackDatabaseAutomatically(true)
                Log.d(TAG, "Offline cache configured")
            } catch (e: Exception) {
                Log.e(TAG, "Offline cache setup failed: ${e.message}", e)
            }
                }
                override fun onMapError(code: Int, message: String?) {
                    Log.e(TAG, "Map error: code=$code message=$message")
                }
            })
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {}
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(ComposeColor(0xFFE8F5E9))) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .background(ComposeColor.White, RoundedCornerShape(16.dp))
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { newText ->
                    searchText = newText
                    if (newText.length >= 3) {
                        isSearching = true
                        scope.launch {
                            val results = withContext(Dispatchers.IO) { MapplsApi.searchPlace(newText) }
                            searchResults = results
                            isSearching = false
                        }
                    } else { searchResults = emptyList() }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Where to, biker?") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = {
                            searchText = ""; searchResults = emptyList()
                            selectedDestination = null; routeOptions = emptyList(); allRoutes = emptyList()
                            mapplsMap?.clear()
                            currentLocationMarker = null
                            // Re-add current location marker after clearing
                            tryGetFusedLocation(context) { lat, lng ->
                                currentLat = lat
                                currentLng = lng
                                val locIcon = IconFactory.getInstance(context).fromBitmap(createLocationMarkerBitmap())
                                currentLocationMarker = mapplsMap?.addMarker(
                                    MarkerOptions()
                                        .position(LatLng(lat, lng))
                                        .title("My Location")
                                        .icon(locIcon)
                                )
                            }
                        }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            if (isSearching) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }

            if (searchResults.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                    items(searchResults) { suggestion ->
                        ListItem(
                            headlineContent = { Text(suggestion.placeName ?: "") },
                            supportingContent = { Text(suggestion.placeAddress ?: "") },
                            modifier = Modifier.clickable {
                                // Geocode the selected result to get lat/lng (Mappls search doesn't return them)
                                scope.launch {
                                    val geocoded = withContext(Dispatchers.IO) {
                                        MapplsApi.geocodeWithNominatim(
                                            suggestion.placeName ?: "",
                                            suggestion.placeAddress ?: ""
                                        )
                                    }
                                    val lat = geocoded?.first ?: suggestion.latitude ?: 0.0
                                    val lng = geocoded?.second ?: suggestion.longitude ?: 0.0
                                    Log.d(TAG, "Selected destination: ${suggestion.placeName}, lat=$lat, lng=$lng")

                                    if (lat == 0.0 && lng == 0.0) {
                                        routeError = "Could not find location for ${suggestion.placeName}"
                                        searchResults = emptyList()
                                        return@launch
                                    }

                                    val dest = Destination(
                                        name = suggestion.placeName ?: "",
                                        address = suggestion.placeAddress ?: "",
                                        lat = lat,
                                        lng = lng
                                    )
                                    selectedDestination = dest
                                    searchText = dest.name
                                    searchResults = emptyList()
                                    routeError = null
                                    mapplsMap?.clear()
                                    currentLocationMarker = null

                                    // Re-add current location marker
                                    val locIcon = IconFactory.getInstance(context).fromBitmap(createLocationMarkerBitmap())
                                    currentLocationMarker = mapplsMap?.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(currentLat, currentLng))
                                            .title("My Location")
                                            .icon(locIcon)
                                    )

                                    // Add animated destination marker with custom pin icon
                                    val destIcon = IconFactory.getInstance(context).fromBitmap(createDestinationMarkerBitmap())
                                    val destMarker = mapplsMap?.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(dest.lat, dest.lng))
                                            .title(dest.name)
                                            .icon(destIcon)
                                    )
                                    // Animate destination marker with bounce-in effect
                                    destMarker?.let { marker ->
                                        val handler = Handler(Looper.getMainLooper())
                                        val startPos = LatLng(currentLat, currentLng)
                                        marker.position = startPos
                                        var step = 0f
                                    val animator = object : Runnable {
                                        override fun run() {
                                            step += 0.05f
                                            if (step <= 1.0f) {
                                                val t = OvershootInterpolator(2f).getInterpolation(step)
                                                val lat = startPos.latitude + (dest.lat - startPos.latitude) * t
                                                val lng = startPos.longitude + (dest.lng - startPos.longitude) * t
                                                marker.position = LatLng(lat, lng)
                                                handler.postDelayed(this, 16)
                                            } else {
                                                marker.position = LatLng(dest.lat, dest.lng)
                                            }
                                        }
                                    }
                                    handler.post(animator)
                                }
                                isRouting = true
                                val routes = withContext(Dispatchers.IO) {
                                    MapplsApi.getRoutes(currentLat, currentLng, dest.lat, dest.lng)
                                }
                                isRouting = false
                                allRoutes = routes
                                if (routes.isNotEmpty()) {
                                    // Build route options list
                                    routeOptions = routes.mapIndexed { index, route ->
                                        val distMeters = route.summary?.totalDistance?.toDoubleOrNull() ?: 0.0
                                        val timeSeconds = route.summary?.totalTime?.toDoubleOrNull() ?: 0.0
                                        val km = String.format("%.1f", distMeters / 1000.0)
                                        val mins = (timeSeconds / 60.0).toInt()
                                        val label = when (index) {
                                            0 -> "Fastest"
                                            1 -> "Alternative"
                                            else -> "Route ${index + 1}"
                                        }
                                        RouteInfo("${km} km  •  ${mins} min  •  $label", "", index)
                                    }
                                    selectedRouteIndex = 0
                                    // Draw first route
                                    drawRoute(mapplsMap, routes[0], currentLat, currentLng, dest.lat, dest.lng)
                                } else {
                                    routeError = "Could not find a route. Check GPS & try again."
                                    Log.w(TAG, "No routes returned for ${dest.name}")
                                }
                                } // end scope.launch
                            }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                tryGetFusedLocation(context) { lat, lng ->
                    currentLat = lat
                    currentLng = lng
                    mapplsMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15.0)
                    )
                    // Update animated current location marker
                    currentLocationMarker?.remove()
                    val locIcon = IconFactory.getInstance(context).fromBitmap(createLocationMarkerBitmap())
                    currentLocationMarker = mapplsMap?.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lng))
                            .title("My Location")
                            .icon(locIcon)
                    )
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 120.dp).size(48.dp),
            containerColor = ComposeColor.White
        ) { Icon(Icons.Default.MyLocation, contentDescription = "My Location") }

        AnimatedVisibility(
            visible = routeOptions.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .background(ComposeColor.White, RoundedCornerShape(16.dp))
            ) {
                // Destination name header
                Text(
                    selectedDestination?.name ?: "",
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ComposeColor(0xFF333333)
                )

                // Route option chips
                routeOptions.forEachIndexed { index, info ->
                    val isSelected = index == selectedRouteIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedRouteIndex = index
                                if (index < allRoutes.size) {
                                    drawRoute(mapplsMap, allRoutes[index], currentLat, currentLng, selectedDestination?.lat ?: 0.0, selectedDestination?.lng ?: 0.0)
                                    // Re-add markers
                                    val locIcon = IconFactory.getInstance(context).fromBitmap(createLocationMarkerBitmap())
                                    mapplsMap?.addMarker(MarkerOptions().position(LatLng(currentLat, currentLng)).title("My Location").icon(locIcon))
                                    selectedDestination?.let { dest ->
                                        val destIcon = IconFactory.getInstance(context).fromBitmap(createDestinationMarkerBitmap())
                                        mapplsMap?.addMarker(MarkerOptions().position(LatLng(dest.lat, dest.lng)).title(dest.name).icon(destIcon))
                                    }
                                }
                            }
                            .background(
                                if (isSelected) ComposeColor(0xFFE0F2F1) else ComposeColor.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Route number circle
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    if (isSelected) ComposeColor(0xFF00897B) else ComposeColor(0xFFCCCCCC),
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${index + 1}", color = ComposeColor.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(info.distanceKm, fontSize = 15.sp, color = ComposeColor(0xFF333333))
                        Spacer(modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Button(
                                onClick = { },
                                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF00897B)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("GO", fontSize = 13.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (isRouting) {
            Card(modifier = Modifier.align(Alignment.Center).padding(16.dp), shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = ComposeColor(0xFF00897B))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Finding fastest biker route...")
                }
            }
        }

        // Show route error as a snackbar-like toast
        routeError?.let { error ->
            LaunchedEffect(error) {
                kotlinx.coroutines.delay(4000)
                routeError = null
            }
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).padding(bottom = 80.dp),
                containerColor = ComposeColor(0xFFD32F2F),
                contentColor = ComposeColor.White
            ) {
                Text(error)
            }
        }
    }
}

/** Draw a single route on the map */
private fun drawRoute(map: MapplsMap?, route: Route, startLat: Double, startLng: Double, endLat: Double, endLng: Double) {
    map?.clear()
    val steps = route.legs?.flatMap { it.steps ?: emptyList() } ?: emptyList()
    val allPoints = mutableListOf<LatLng>()
    for (step in steps) { step.geometry?.let { allPoints.addAll(decodePolyline(it)) } }
    if (allPoints.isNotEmpty()) {
        map?.addPolyline(
            PolylineOptions().addAll(allPoints)
                .color(Color.parseColor("#00897B")).width(8f)
        )
        val builder = LatLngBounds.Builder()
        builder.include(LatLng(startLat, startLng))
        builder.include(LatLng(endLat, endLng))
        allPoints.forEach { builder.include(it) }
        map?.animateCamera(
            CameraUpdateFactory.newLatLngBounds(builder.build(), 100)
        )
    }
}

/** Create a pulsing blue dot bitmap for current location marker */
private fun createLocationMarkerBitmap(): Bitmap {
    val size = 160
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    // Outer glow ring
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#302196F3")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, size / 2f, glowPaint)

    // Mid ring
    val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#602196F3")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, size / 3f, midPaint)

    // Blue dot body
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF2196F3")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, size / 5f, dotPaint)

    // White center
    val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, size / 10f, centerPaint)

    return bitmap
}

/** Create a teal pin bitmap for destination marker */
private fun createDestinationMarkerBitmap(): Bitmap {
    val w = 120
    val h = 160
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Pin body (teal teardrop)
    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF00897B")
        style = Paint.Style.FILL
        setShadowLayer(6f, 2f, 4f, Color.parseColor("#40000000"))
    }
    val path = Path().apply {
        moveTo(w / 2f, h * 0.05f)
        cubicTo(w * 0.85f, h * 0.05f, w * 0.95f, h * 0.25f, w * 0.95f, h * 0.35f)
        cubicTo(w * 0.95f, h * 0.55f, w / 2f, h * 0.85f, w / 2f, h * 0.85f)
        cubicTo(w / 2f, h * 0.85f, w * 0.05f, h * 0.55f, w * 0.05f, h * 0.35f)
        cubicTo(w * 0.05f, h * 0.25f, w * 0.15f, h * 0.05f, w / 2f, h * 0.05f)
        close()
    }
    canvas.drawPath(path, pinPaint)

    // White inner circle
    val innerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(w / 2f, h * 0.33f, w * 0.22f, innerCirclePaint)

    // Teal dot inside circle
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF00897B")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(w / 2f, h * 0.33f, w * 0.1f, dotPaint)

    return bitmap
}

fun decodePolyline(encoded: String): List<LatLng> {
    val poly = mutableListOf<LatLng>()
    var index = 0; val len = encoded.length; var lat = 0; var lng = 0
    while (index < len) {
        var b: Int; var shift = 0; var result = 0
        do { b = encoded[index++].code - 63; result = result or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
        lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
        shift = 0; result = 0
        do { b = encoded[index++].code - 63; result = result or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
        lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
        poly.add(LatLng(lat / 1E5, lng / 1E5))
    }
    return poly
}

@SuppressLint("MissingPermission")
private fun tryGetFusedLocation(context: android.content.Context, onLocation: (Double, Double) -> Unit) {
    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    try {
        // Try last known first (fast)
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                Log.d(TAG, "Got last known location: ${loc.latitude}, ${loc.longitude}")
                onLocation(loc.latitude, loc.longitude)
            } else {
                // No last known, request fresh location
                Log.d(TAG, "No last known location, requesting fresh fix")
                val cts = CancellationTokenSource()
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { freshLoc ->
                        if (freshLoc != null) {
                            Log.d(TAG, "Got fresh location: ${freshLoc.latitude}, ${freshLoc.longitude}")
                            onLocation(freshLoc.latitude, freshLoc.longitude)
                        } else {
                            Log.w(TAG, "Fresh location also null")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Fresh location request failed: ${e.message}", e)
                    }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Last location request failed: ${e.message}", e)
        }
    } catch (e: SecurityException) {
        Log.e(TAG, "Location permission not granted: ${e.message}", e)
    } catch (e: Exception) {
        Log.e(TAG, "Location request error: ${e.message}", e)
    }
}
