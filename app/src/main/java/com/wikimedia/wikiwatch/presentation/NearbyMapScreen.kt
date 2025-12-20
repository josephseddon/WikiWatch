package com.wikimedia.wikiwatch.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestImpl
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.IconFactory
import com.wikimedia.wikiwatch.data.GeoSearchResult
import com.wikimedia.wikiwatch.data.WikipediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Interceptor
import java.net.URL
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import com.wikimedia.wikiwatch.R

fun createCircularBitmap(bitmap: Bitmap, size: Int): Bitmap {
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint().apply {
        isAntiAlias = true
    }
    val rect = Rect(0, 0, size, size)
    val rectF = RectF(rect)
    canvas.drawOval(rectF, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
    canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)
    
    // Add border
    val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = android.graphics.Color.WHITE
    }
    canvas.drawOval(RectF(2f, 2f, size - 2f, size - 2f), borderPaint)
    
    return output
}

fun createBlueLocationPin(context: android.content.Context, size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    // Draw blue circle for the pin head
    val circlePaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#4285F4") // Google Maps blue
    }
    val radius = size / 2f - 2f
    canvas.drawCircle(size / 2f, size / 2f, radius, circlePaint)
    
    // Draw white border
    val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(size / 2f, size / 2f, radius, borderPaint)
    
    // Draw white center dot
    val dotPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
    }
    val dotRadius = size / 6f
    canvas.drawCircle(size / 2f, size / 2f, dotRadius, dotPaint)
    
    return bitmap
}

fun createPlaceholderBitmap(context: android.content.Context, size: Int): Bitmap {
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    
    // Light grey circular background
    val bgPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#CCCCCC")
    }
    canvas.drawOval(RectF(0f, 0f, size.toFloat(), size.toFloat()), bgPaint)
    
    // Draw Wikipedia logo in center
    val drawable = ContextCompat.getDrawable(context, R.drawable.wikipedia_logo)
    drawable?.let {
        val logoSize = (size * 0.6).toInt()
        val offset = (size - logoSize) / 2
        it.setBounds(offset, offset, offset + logoSize, offset + logoSize)
        it.setTint(android.graphics.Color.WHITE)
        it.draw(canvas)
    }
    
    // Add border
    val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = android.graphics.Color.WHITE
    }
    canvas.drawOval(RectF(2f, 2f, size - 2f, size - 2f), borderPaint)
    
    return output
}

fun addCheckmarkToBitmap(bitmap: Bitmap, size: Int): Bitmap {
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    
    // Draw original bitmap
    canvas.drawBitmap(bitmap, 0f, 0f, null)
    
    // Draw green checkmark circle in bottom right
    val checkmarkSize = size / 3f
    val checkmarkX = size - checkmarkSize - 4f
    val checkmarkY = size - checkmarkSize - 4f
    
    // Green circle background
    val circlePaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#4CAF50") // Green
    }
    canvas.drawCircle(checkmarkX + checkmarkSize / 2f, checkmarkY + checkmarkSize / 2f, checkmarkSize / 2f, circlePaint)
    
    // White checkmark
    val checkmarkPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    
    // Draw checkmark (check shape)
    val checkmarkPath = android.graphics.Path()
    val centerX = checkmarkX + checkmarkSize / 2f
    val centerY = checkmarkY + checkmarkSize / 2f
    val checkSize = checkmarkSize * 0.4f
    
    checkmarkPath.moveTo(centerX - checkSize * 0.3f, centerY)
    checkmarkPath.lineTo(centerX - checkSize * 0.1f, centerY + checkSize * 0.3f)
    checkmarkPath.lineTo(centerX + checkSize * 0.3f, centerY - checkSize * 0.2f)
    
    canvas.drawPath(checkmarkPath, checkmarkPaint)
    
    return output
}

// Helper functions to save/load camera position from SharedPreferences
private fun saveCameraPosition(context: Context, position: org.maplibre.android.camera.CameraPosition) {
    val prefs = context.getSharedPreferences("map_state", Context.MODE_PRIVATE)
    val target = position.target
    if (target != null) {
        prefs.edit()
            .putFloat("camera_lat", target.latitude.toFloat())
            .putFloat("camera_lon", target.longitude.toFloat())
            .putFloat("camera_zoom", position.zoom.toFloat()) // Store as Float since SharedPreferences doesn't support Double
            .putFloat("camera_bearing", position.bearing.toFloat())
            .putFloat("camera_tilt", position.tilt.toFloat())
            .apply()
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Saved camera position to SharedPreferences")
    } else {
        android.util.Log.w("WikiWatch", "NearbyMapScreen: Cannot save camera position - target is null")
    }
}

private fun loadSavedCameraPosition(context: Context): org.maplibre.android.camera.CameraPosition? {
    val prefs = context.getSharedPreferences("map_state", Context.MODE_PRIVATE)
    val lat = prefs.getFloat("camera_lat", Float.NaN)
    val lon = prefs.getFloat("camera_lon", Float.NaN)
    // SharedPreferences doesn't have getDouble, so we store zoom as Float
    val zoom = prefs.getFloat("camera_zoom", Float.NaN)
    
    return if (!lat.isNaN() && !lon.isNaN() && !zoom.isNaN()) {
        val bearing = prefs.getFloat("camera_bearing", 0f)
        val tilt = prefs.getFloat("camera_tilt", 0f)
        val position = org.maplibre.android.camera.CameraPosition.Builder()
            .target(org.maplibre.android.geometry.LatLng(lat.toDouble(), lon.toDouble()))
            .zoom(zoom.toDouble())
            .bearing(bearing.toDouble())
            .tilt(tilt.toDouble())
            .build()
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Loaded camera position from SharedPreferences - lat=$lat, lon=$lon, zoom=$zoom")
        position
    } else {
        android.util.Log.d("WikiWatch", "NearbyMapScreen: No saved camera position found in SharedPreferences")
        null
    }
}

@Composable
fun NearbyMapScreen(
    initialLat: Double? = null,
    initialLon: Double? = null,
    previousScreen: Screen? = null,
    onBack: () -> Unit,
    onArticleClick: (String) -> Unit,
    viewModel: NearbyMapViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    // Log when composable is created/recreated
    LaunchedEffect(Unit) {
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Composable created/recreated - previousScreen=${previousScreen?.javaClass?.simpleName}, initialLat=$initialLat, initialLon=$initialLon")
        android.util.Log.d("WikiWatch", "NearbyMapScreen: ViewModel state - articles=${viewModel.nearbyArticles.size}, geosearchDone=${viewModel.initialGeosearchDone}, cameraSetup=${viewModel.initialCameraSetup}, markersAdded=${viewModel.markersInitiallyAdded}")
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Capture context for use in remember blocks (avoid composable call issues)
    val contextForRemember = context
    
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    // Use ViewModel state which persists across navigation
    var nearbyArticles by remember { mutableStateOf(viewModel.nearbyArticles) }
    var thumbnailBitmaps by remember { mutableStateOf(viewModel.thumbnailBitmaps) }
    var isLoading by remember { mutableStateOf(viewModel.nearbyArticles.isEmpty()) }
    
    // Log thumbnail state when restored from ViewModel
    LaunchedEffect(thumbnailBitmaps) {
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Thumbnail bitmaps updated - count=${thumbnailBitmaps.size}, keys=${thumbnailBitmaps.keys.joinToString(", ")}")
    }
    var selectedArticle by remember { mutableStateOf<GeoSearchResult?>(null) }
    
    // Game state - persist across navigation using SharedPreferences
    var isGameActive by remember { 
        mutableStateOf(
            contextForRemember.getSharedPreferences("game_state", Context.MODE_PRIVATE)
                .getBoolean("is_game_active", false)
        )
    }
    var gameScore by remember { 
        mutableStateOf(
            contextForRemember.getSharedPreferences("game_state", Context.MODE_PRIVATE)
                .getInt("game_score", 0)
        )
    }
    var visitedArticles by remember { mutableStateOf(com.wikimedia.wikiwatch.data.GameStateManager.getVisitedArticles(contextForRemember)) }
    var articlesCollectedThisSession by remember { 
        mutableStateOf(
            contextForRemember.getSharedPreferences("game_state", Context.MODE_PRIVATE)
                .getInt("articles_collected_session", 0)
        )
    }
    var showSummary by remember { mutableStateOf(false) }
    var showScoreAnimation by remember { mutableStateOf(false) }
    var recentlyCollectedArticle by remember { mutableStateOf<GeoSearchResult?>(null) }
    
    // Save game state whenever it changes
    LaunchedEffect(isGameActive, gameScore, articlesCollectedThisSession) {
        context.getSharedPreferences("game_state", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_game_active", isGameActive)
            .putInt("game_score", gameScore)
            .putInt("articles_collected_session", articlesCollectedThisSession)
            .apply()
    }
    
    // Use initial coordinates if provided, otherwise use user location
    val searchLocation = remember(initialLat, initialLon, userLocation) {
        if (initialLat != null && initialLon != null) {
            LatLng(initialLat, initialLon)
        } else {
            userLocation
        }
    }
    
    // Track if initial geosearch has been completed to prevent reloading on location updates
    var initialGeosearchDone by remember { mutableStateOf(viewModel.initialGeosearchDone) }
    // Track the last location where geosearch was performed
    var lastGeosearchLocation by remember { mutableStateOf<LatLng?>(null) }
    // Trigger for geosearch when user moves more than 200 meters
    var triggerGeosearch by remember { mutableStateOf(false) }
    // Track if we've done the initial camera setup
    // Use ViewModel state which persists across navigation
    var initialCameraSetup by remember { mutableStateOf(viewModel.initialCameraSetup) }
    
    // Flag to prevent camera idle listener from saving position during restoration
    var isRestoringCamera by remember { mutableStateOf(false) }
    // Store the saved camera position to restore when returning to the map
    // Load from SharedPreferences to persist across composable recreation
    var savedCameraPosition by remember { 
        mutableStateOf<org.maplibre.android.camera.CameraPosition?>(
            loadSavedCameraPosition(contextForRemember)
        )
    }
    
    // Reset geosearch flag and camera setup when initial coordinates change (user navigated to new location)
    LaunchedEffect(initialLat, initialLon) {
        if (initialLat != null && initialLon != null) {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Initial coordinates changed, resetting geosearch flag and camera setup")
            initialGeosearchDone = false
            lastGeosearchLocation = null // Reset last geosearch location
            triggerGeosearch = false // Reset trigger
            initialCameraSetup = false // Reset camera setup so it zooms to article location
            // Don't clear savedCameraPosition - we want to keep it for when user returns via back button
        }
    }
    
    // Handle navigation - sync state when returning from article, reset when first opening
    // Use navigation stack to reliably detect if we're returning from article
    LaunchedEffect(Unit) {
        val isReturning = viewModel.isReturningFromArticle()
        android.util.Log.d("WikiWatch", "NearbyMapScreen: LaunchedEffect - isReturningFromArticle=$isReturning, ViewModel articles=${viewModel.nearbyArticles.size}, ViewModel cameraSetup=${viewModel.initialCameraSetup}")
        
        if (isReturning) {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Detected returning from article via navigation stack")
            // Sync all state from ViewModel when returning - DO NOT reset
            initialGeosearchDone = viewModel.initialGeosearchDone
            initialCameraSetup = viewModel.initialCameraSetup // Sync from ViewModel (should be true)
            nearbyArticles = viewModel.nearbyArticles
            thumbnailBitmaps = viewModel.thumbnailBitmaps
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Synced state from ViewModel - geosearchDone=$initialGeosearchDone, cameraSetup=$initialCameraSetup, articles=${viewModel.nearbyArticles.size}, markersAdded=${viewModel.markersInitiallyAdded}")
            // Pop the Article from stack now that we've checked
            viewModel.popScreen()
        } else {
            // First time opening - reset camera setup
            android.util.Log.d("WikiWatch", "NearbyMapScreen: First time opening, resetting initialCameraSetup")
            initialCameraSetup = false
            viewModel.setInitialCameraSetup(false)
        }
    }
    // Store marker-to-title mapping persistently so it survives recompositions
    val markerToTitleMap = remember { mutableMapOf<Marker, String>() }
    // Store all markers so we can clear them when needed
    val allMarkers = remember { mutableListOf<Marker>() }
    // Store the map instance so we can update markers without reinitializing
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    // Track if style has been loaded (to prevent reloading on marker updates)
    var styleLoaded by remember { mutableStateOf(viewModel.styleLoaded) }
    // Track if markers have been initially added to prevent unnecessary updates when returning
    var markersInitiallyAdded by remember { mutableStateOf(viewModel.markersInitiallyAdded) }
    // Track if marker update is in progress to prevent concurrent updates
    var markerUpdateInProgress by remember { mutableStateOf(false) }
    // Track if map listeners have been set up to prevent duplicate registration
    var mapListenersSetup by remember { mutableStateOf(false) }
    var hasPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val okHttpClient = remember {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                android.util.Log.d("WikiWatch", "NearbyMapScreen: Making HTTP request to ${request.url}")
                val startTime = System.currentTimeMillis()
                try {
                    val modifiedRequest = request.newBuilder()
                        .header("User-Agent", "WikiWatch/1.0 (WearOS App)")
                        .build()
                    val response = chain.proceed(modifiedRequest)
                    val endTime = System.currentTimeMillis()
                    android.util.Log.d("WikiWatch", "NearbyMapScreen: HTTP ${response.code} from ${request.url} in ${endTime - startTime}ms")
                    response
                } catch (e: Exception) {
                    val endTime = System.currentTimeMillis()
                    android.util.Log.e("WikiWatch", "NearbyMapScreen: HTTP request failed to ${request.url} after ${endTime - startTime}ms: ${e.message}", e)
                    throw e
                }
            }
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    // Test network connectivity on startup
    LaunchedEffect(Unit) {
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Testing network connectivity")
        val networkTest = com.wikimedia.wikiwatch.data.WikipediaRepository.testNetwork()
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Network test result: $networkTest")
    }

    // Only request location permission if initial coordinates aren't provided
    LaunchedEffect(Unit, initialLat, initialLon) {
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Initial setup - initialLat=$initialLat, initialLon=$initialLon, hasPermission=$hasPermission")
        if (initialLat == null && initialLon == null && !hasPermission) {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Requesting location permission")
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else if (initialLat != null && initialLon != null) {
            // If we have initial coordinates, we don't need location permission
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Using initial coordinates, skipping location permission")
            // Set isLoading to false once we have coordinates
            isLoading = false
        }
    }

    // Only request location if initial coordinates aren't provided
    LaunchedEffect(hasPermission, initialLat, initialLon) {
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Location effect triggered - hasPermission=$hasPermission, initialLat=$initialLat, initialLon=$initialLon")
        if (hasPermission && initialLat == null && initialLon == null) {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Starting location fetch")
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                // Try lastLocation first
                android.util.Log.d("WikiWatch", "NearbyMapScreen: Requesting lastLocation")
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        android.util.Log.d("WikiWatch", "NearbyMapScreen: Got lastLocation: ${location.latitude}, ${location.longitude}")
                        userLocation = LatLng(location.latitude, location.longitude)
                    } else {
                        // Request current location if lastLocation is null
                        android.util.Log.d("WikiWatch", "NearbyMapScreen: lastLocation is null, requesting current location")
                        val locationRequest = com.google.android.gms.location.CurrentLocationRequest.Builder()
                            .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
                            .build()
                        fusedLocationClient.getCurrentLocation(locationRequest, null)
                            .addOnSuccessListener { currentLocation ->
                                if (currentLocation != null) {
                                    android.util.Log.d("WikiWatch", "NearbyMapScreen: Got current location: ${currentLocation.latitude}, ${currentLocation.longitude}")
                                    userLocation = LatLng(currentLocation.latitude, currentLocation.longitude)
                                } else {
                                    android.util.Log.e("WikiWatch", "NearbyMapScreen: Current location is also null")
                                }
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e("WikiWatch", "NearbyMapScreen: Failed to get current location: ${e.message}", e)
                            }
                    }
                }.addOnFailureListener { e ->
                    android.util.Log.e("WikiWatch", "NearbyMapScreen: Failed to get last location: ${e.message}", e)
                }
            } catch (e: SecurityException) {
                android.util.Log.e("WikiWatch", "NearbyMapScreen: Security exception: ${e.message}", e)
            }
        } else {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Skipping location fetch - hasPermission=$hasPermission, hasInitialCoords=${initialLat != null && initialLon != null}")
        }
    }
    
    // Coroutine scope for animations
    val coroutineScope = rememberCoroutineScope()
    
    // Location updates for game mode and general location tracking
    // Use a regular object instead of remember to avoid composable call issues
    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val lastLocation = result.lastLocation
            if (lastLocation != null) {
                val currentLocation = LatLng(lastLocation.latitude, lastLocation.longitude)
                userLocation = currentLocation
                
                // Check if user has moved more than 200 meters from last geosearch location
                // Only check if we're using actual user location (not initial coordinates)
                if (initialLat == null && initialLon == null && initialGeosearchDone) {
                    val lastGeosearch = lastGeosearchLocation
                    if (lastGeosearch != null) {
                        val distance = com.wikimedia.wikiwatch.data.GameStateManager.calculateDistance(
                            lastGeosearch.latitude,
                            lastGeosearch.longitude,
                            currentLocation.latitude,
                            currentLocation.longitude
                        )
                        if (distance > 200f) {
                            android.util.Log.d("WikiWatch", "NearbyMapScreen: User moved more than 200m, triggering geosearch")
                            triggerGeosearch = true
                        }
                    } else {
                        // First time, set the geosearch location
                        android.util.Log.d("WikiWatch", "NearbyMapScreen: Setting initial geosearch location")
                        lastGeosearchLocation = currentLocation
                    }
                }
                
                // Check SharedPreferences to see if game is still active (works even when composable is removed)
                val gameActive = context.getSharedPreferences("game_state", Context.MODE_PRIVATE)
                    .getBoolean("is_game_active", false)
                
                if (gameActive) {
                    // Check distance to all nearby articles
                    nearbyArticles.forEach { article ->
                        if (!visitedArticles.contains(article.title)) {
                            val distance = com.wikimedia.wikiwatch.data.GameStateManager.calculateDistance(
                                lastLocation.latitude,
                                lastLocation.longitude,
                                article.lat,
                                article.lon
                            )
                            
                            if (distance <= 20f) { // Within 20 meters
                                // Collect article
                                visitedArticles = visitedArticles + article.title
                                com.wikimedia.wikiwatch.data.GameStateManager.addVisitedArticle(context, article.title)
                                gameScore += 5
                                articlesCollectedThisSession += 1
                                
                                // Update SharedPreferences immediately so state persists even if UI is not visible
                                val prefs = context.getSharedPreferences("game_state", Context.MODE_PRIVATE)
                                val currentScore = prefs.getInt("game_score", 0)
                                val currentCollected = prefs.getInt("articles_collected_session", 0)
                                prefs.edit()
                                    .putInt("game_score", currentScore + 5)
                                    .putInt("articles_collected_session", currentCollected + 1)
                                    .apply()
                                
                                // Vibrate (with error handling in case permission is not granted)
                                try {
                                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                                        vibratorManager.defaultVibrator
                                    } else {
                                        @Suppress("DEPRECATION")
                                        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                                    }
                                    
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator.vibrate(200)
                                    }
                                } catch (e: SecurityException) {
                                    android.util.Log.w("WikiWatch", "NearbyMapScreen: Vibration permission not granted: ${e.message}")
                                } catch (e: Exception) {
                                    android.util.Log.w("WikiWatch", "NearbyMapScreen: Error vibrating: ${e.message}")
                                }
                                
                                // Show animation and bottom sheet - use Handler.post to avoid composable call issues
                                showScoreAnimation = true
                                recentlyCollectedArticle = article
                                selectedArticle = article
                                
                                // Hide animation after delay using Handler
                                Handler(Looper.getMainLooper()).postDelayed({
                                    showScoreAnimation = false
                                }, 2000)
                                
                                android.util.Log.d("WikiWatch", "Collected article: ${article.title} at distance: $distance meters")
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Start/stop location updates - run continuously when permission is granted (not just during game)
    // Use DisposableEffect to properly manage lifecycle - location updates will continue
    // even when the screen is not visible (when user navigates to article)
    DisposableEffect(hasPermission, initialLat, initialLon) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        
        // Start location updates if we have permission and aren't using initial coordinates
        // This ensures the user's location is always tracked and displayed on the map
        if (hasPermission && initialLat == null && initialLon == null) {
            try {
                // Remove any existing location updates first to avoid duplicates
                fusedLocationClient.removeLocationUpdates(locationCallback)
                
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000) // Update every 2 seconds
                    .setMinUpdateIntervalMillis(1000)
                    .build()
                
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    android.os.Looper.getMainLooper()
                )
                
                android.util.Log.d("WikiWatch", "Started continuous location updates")
            } catch (e: SecurityException) {
                android.util.Log.e("WikiWatch", "Security exception starting location updates: ${e.message}", e)
            } catch (e: Exception) {
                android.util.Log.e("WikiWatch", "Error starting location updates: ${e.message}", e)
            }
        }
        
        // Cleanup: stop location updates when the effect is disposed
        onDispose {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
                android.util.Log.d("WikiWatch", "Stopped location updates")
            } catch (e: Exception) {
                android.util.Log.e("WikiWatch", "Error stopping location updates: ${e.message}", e)
            }
        }
    }
    
    // Load visited articles on startup
    LaunchedEffect(Unit) {
        visitedArticles = com.wikimedia.wikiwatch.data.GameStateManager.getVisitedArticles(context)
    }

    // Helper function to perform geosearch
    suspend fun performGeosearch(loc: LatLng) {
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Starting geosearch for location: ${loc.latitude}, ${loc.longitude}")
        isLoading = true
        try {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Calling WikipediaRepository.geoSearch")
            nearbyArticles = WikipediaRepository.geoSearch(loc.latitude, loc.longitude)
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Geosearch returned ${nearbyArticles.size} articles")
            
            // Load thumbnails - start with existing thumbnails from ViewModel, then load missing ones
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Starting thumbnail loading for ${nearbyArticles.size} articles")
            val bitmaps = mutableMapOf<String, Bitmap>()
            // Start with existing thumbnails from ViewModel
            bitmaps.putAll(thumbnailBitmaps)
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Starting with ${bitmaps.size} existing thumbnails from ViewModel")
            
            var thumbnailCount = bitmaps.size
            var articlesWithThumbnailUrl = 0
            var loadedCount = 0
            nearbyArticles.forEach { article ->
                if (article.thumbnailUrl != null) {
                    articlesWithThumbnailUrl++
                    // Only load if we don't already have this thumbnail
                    if (!bitmaps.containsKey(article.title)) {
                        try {
                            withContext(Dispatchers.IO) {
                                android.util.Log.d("WikiWatch", "NearbyMapScreen: Loading thumbnail for ${article.title} from ${article.thumbnailUrl}")
                                val request = Request.Builder().url(article.thumbnailUrl!!).build()
                                android.util.Log.d("WikiWatch", "NearbyMapScreen: Executing HTTP request for thumbnail: ${article.thumbnailUrl}")
                                val response = okHttpClient.newCall(request).execute()
                                android.util.Log.d("WikiWatch", "NearbyMapScreen: Received HTTP response ${response.code} for ${article.title}")
                                if (response.isSuccessful) {
                                    response.body?.byteStream()?.let { stream ->
                                        val bitmap = BitmapFactory.decodeStream(stream)
                                        if (bitmap != null) {
                                            bitmaps[article.title] = createCircularBitmap(bitmap, 80)
                                            thumbnailCount++
                                            loadedCount++
                                            android.util.Log.d("WikiWatch", "NearbyMapScreen: Successfully loaded thumbnail for ${article.title}")
                                        } else {
                                            android.util.Log.w("WikiWatch", "NearbyMapScreen: Failed to decode bitmap for ${article.title} - bitmap was null")
                                        }
                                    } ?: run {
                                        android.util.Log.w("WikiWatch", "NearbyMapScreen: Response body was null for ${article.title}")
                                    }
                                } else {
                                    android.util.Log.w("WikiWatch", "NearbyMapScreen: HTTP ${response.code} for ${article.title} - ${response.message}")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("WikiWatch", "NearbyMapScreen: Failed to load thumbnail for ${article.title}: ${e.message}", e)
                        }
                    } else {
                        android.util.Log.d("WikiWatch", "NearbyMapScreen: Thumbnail for ${article.title} already exists, skipping")
                    }
                } else {
                    android.util.Log.d("WikiWatch", "NearbyMapScreen: Article ${article.title} has no thumbnailUrl")
                }
            }
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Loaded $loadedCount new thumbnails, total $thumbnailCount out of $articlesWithThumbnailUrl articles with thumbnail URLs (total articles: ${nearbyArticles.size})")
            thumbnailBitmaps = bitmaps
            isLoading = false
            initialGeosearchDone = true
            lastGeosearchLocation = loc
            // Persist state to ViewModel
            viewModel.setNearbyArticles(nearbyArticles)
            viewModel.setThumbnailBitmaps(bitmaps)
            viewModel.setInitialGeosearchDone(true)
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Geosearch and thumbnail loading complete, isLoading set to false")
        } catch (e: Exception) {
            android.util.Log.e("WikiWatch", "NearbyMapScreen: Error during geosearch: ${e.message}", e)
            isLoading = false
            initialGeosearchDone = true // Mark as done even on error to prevent retry loop
        }
    }
    
    // Use searchLocation (initial coordinates or user location) for geosearch
    // Only do initial geosearch once - don't reload when location updates during active session
    // Also skip if we already have articles loaded (returning from article)
    LaunchedEffect(searchLocation) {
        searchLocation?.let { loc ->
            // Only do geosearch if:
            // 1. We haven't done it yet AND we don't have articles loaded, OR
            // 2. We're using initial coordinates (user explicitly navigated to this location)
            val shouldDoGeosearch = if (initialLat != null && initialLon != null) {
                // Opening from article - always do geosearch
                true
            } else {
                // Not opening from article - only do geosearch if we haven't done it and don't have articles
                !initialGeosearchDone && nearbyArticles.isEmpty()
            }
            
            if (shouldDoGeosearch) {
                android.util.Log.d("WikiWatch", "NearbyMapScreen: Performing geosearch - initialGeosearchDone=$initialGeosearchDone, articlesLoaded=${nearbyArticles.size}, initialLat=$initialLat")
                performGeosearch(loc)
            } else {
                android.util.Log.d("WikiWatch", "NearbyMapScreen: Skipping geosearch - initialGeosearchDone=$initialGeosearchDone, articlesLoaded=${nearbyArticles.size}, initialLat=$initialLat")
                // Make sure isLoading is false if we're skipping geosearch (e.g., when returning)
                if (nearbyArticles.isNotEmpty()) {
                    isLoading = false
                }
            }
        } ?: run {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: searchLocation is null, waiting for location")
        }
    }
    
    // Trigger geosearch when user moves more than 200 meters
    LaunchedEffect(triggerGeosearch, userLocation) {
        if (triggerGeosearch && initialLat == null && initialLon == null) {
            userLocation?.let { currentLocation ->
                android.util.Log.d("WikiWatch", "NearbyMapScreen: Triggering geosearch due to user movement")
                triggerGeosearch = false // Reset trigger
                performGeosearch(currentLocation)
            }
        }
    }


    // Get shared HTTP client with maps.wikimedia.org header interceptor
    val app = context.applicationContext as? com.wikimedia.wikiwatch.WikiWatchApp
    val httpClient = app?.sharedOkHttpClient
    
    // Initialize MapLibre before creating MapView (must be done synchronously)
    // Configure MapLibre to use our HTTP client with headers (like Wikipedia app does)
    // Based on: https://github.com/wikimedia/apps-android-wikipedia/blob/cd917e7fae9d53e1fddf2f6e0ed5295216efd4df/app/src/main/java/org/wikipedia/places/PlacesFragment.kt
    // Use MapView from ViewModel if available (persists across navigation), otherwise create new one
    val mapView = remember(viewModel.mapViewInstance, contextForRemember, httpClient) {
        viewModel.mapViewInstance ?: run {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Creating new MapView instance")
            MapLibre.getInstance(contextForRemember)
            // Set the HTTP client for MapLibre to use (this is the key!)
            // Must be called before creating MapView
            httpClient?.let { HttpRequestImpl.setOkHttpClient(it) }
            MapView(contextForRemember).apply {
                onCreate(Bundle())
                // Ensure the MapView consumes touch events to prevent gesture conflicts
                // This helps prevent pan gestures from being interpreted as swipes
                isClickable = true
                isFocusable = true
            }.also { view ->
                // Store in ViewModel to keep it alive across navigation
                viewModel.mapViewInstance = view
                android.util.Log.d("WikiWatch", "NearbyMapScreen: Stored MapView in ViewModel")
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    android.util.Log.d("WikiWatch", "NearbyMapScreen: Lifecycle ON_START")
                    mapView.onStart()
                }
                Lifecycle.Event.ON_RESUME -> {
                    android.util.Log.d("WikiWatch", "NearbyMapScreen: Lifecycle ON_RESUME")
                    mapView.onResume()
                    // Ensure attribution is disabled when map resumes (can be re-enabled by system)
                    mapInstance?.uiSettings?.setAttributionEnabled(false)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    android.util.Log.d("WikiWatch", "NearbyMapScreen: Lifecycle ON_PAUSE")
                    mapView.onPause()
                }
                Lifecycle.Event.ON_STOP -> {
                    android.util.Log.d("WikiWatch", "NearbyMapScreen: Lifecycle ON_STOP")
                    mapView.onStop()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    android.util.Log.d("WikiWatch", "NearbyMapScreen: Lifecycle ON_DESTROY - destroying MapView")
                    mapView.onDestroy()
                    // Clear from ViewModel when actually destroyed
                    viewModel.mapViewInstance = null
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: DisposableEffect onDispose - NOT destroying MapView (keeping it alive)")
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Camera restoration check - previousScreen=${previousScreen?.javaClass?.simpleName}, initialLat=$initialLat, initialLon=$initialLon")
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Don't call onDestroy here - only pause/stop to keep MapView alive across navigation
            // The MapView will be destroyed when the activity is destroyed (ON_DESTROY lifecycle event)
        }
    }

    // Helper function to setup style images (user location pin, placeholder, thumbnails)
    fun setupStyleImages(
        style: Style,
        context: Context,
        thumbnailBitmaps: Map<String, Bitmap>
    ): Pair<Bitmap, Bitmap> {
        // Add user location pin image to style
        val userLocationPinId = "user_location_pin"
        val userLocationPin = try {
            val pin = createBlueLocationPin(context, 40)
            try {
                style.addImage(userLocationPinId, pin.toDrawable(context.resources))
            } catch (e: Exception) {
                android.util.Log.d("WikiWatch", "User location pin already in style or error: ${e.message}")
            }
            pin
        } catch (e: Exception) {
            android.util.Log.e("WikiWatch", "Failed to create user location pin: ${e.message}", e)
            createBlueLocationPin(context, 40) // Fallback
        }
        
        // Add placeholder image to style
        val placeholderId = "placeholder_marker"
        val placeholderBitmap = try {
            val placeholder = createPlaceholderBitmap(context, 80)
            try {
                style.addImage(placeholderId, placeholder.toDrawable(context.resources))
            } catch (e: Exception) {
                android.util.Log.d("WikiWatch", "Placeholder already in style or error: ${e.message}")
            }
            placeholder
        } catch (e: Exception) {
            android.util.Log.e("WikiWatch", "Failed to create placeholder: ${e.message}", e)
            createPlaceholderBitmap(context, 80)
        }
        
        // Add thumbnail images to style
        thumbnailBitmaps.forEach { (title, bitmap) ->
            val imageId = "marker_${title.hashCode()}"
            try {
                style.addImage(imageId, bitmap.toDrawable(context.resources))
            } catch (e: Exception) {
                android.util.Log.d("WikiWatch", "Image for $title already in style or error: ${e.message}")
            }
        }
        
        return Pair(userLocationPin, placeholderBitmap)
    }
    
    // Helper function to clear existing markers
    fun clearExistingMarkers(
        initializedMap: MapLibreMap,
        allMarkers: MutableList<Marker>,
        markerToTitleMap: MutableMap<Marker, String>
    ) {
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Clearing ${allMarkers.size} previous markers")
        try {
            val markersToRemove = allMarkers.toList()
            markersToRemove.forEach { marker ->
                try {
                    if (marker != null) {
                        initializedMap.removeMarker(marker)
                        mapInstance?.uiSettings?.setAttributionEnabled(false)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("WikiWatch", "NearbyMapScreen: Error removing marker: ${e.message}")
                }
            }
            allMarkers.clear()
            markerToTitleMap.clear()
        } catch (e: Exception) {
            android.util.Log.e("WikiWatch", "NearbyMapScreen: Error clearing markers: ${e.message}", e)
            allMarkers.clear()
            markerToTitleMap.clear()
        }
    }
    
    // Helper function to add user location marker
    fun addUserLocationMarker(
        initializedMap: MapLibreMap,
        context: Context,
        userLocation: LatLng?,
        initialLat: Double?,
        initialLon: Double?,
        userLocationPin: Bitmap,
        allMarkers: MutableList<Marker>,
        markerToTitleMap: MutableMap<Marker, String>
    ) {
        if (initialLat == null && initialLon == null && userLocation != null) {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Adding user location pin")
            val userLocationIcon = IconFactory.getInstance(context).fromBitmap(userLocationPin)
            val userLocationMarker = initializedMap.addMarker(
                MarkerOptions()
                    .position(userLocation)
                    .icon(userLocationIcon)
                    .title("Your Location")
            )
            allMarkers.add(userLocationMarker)
            markerToTitleMap[userLocationMarker] = "Your Location"
        }
    }
    
    // Helper function to add article markers
    fun addArticleMarkers(
        initializedMap: MapLibreMap,
        context: Context,
        nearbyArticles: List<GeoSearchResult>,
        thumbnailBitmaps: Map<String, Bitmap>,
        placeholderBitmap: Bitmap,
        visitedArticles: Set<String>,
        allMarkers: MutableList<Marker>,
        markerToTitleMap: MutableMap<Marker, String>
    ): List<LatLng> {
        val markerPositions = mutableListOf<LatLng>()
        
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Adding ${nearbyArticles.size} article markers")
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Available thumbnails: ${thumbnailBitmaps.keys.joinToString(", ")}")
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Thumbnail count: ${thumbnailBitmaps.size}, Placeholder bitmap: ${placeholderBitmap != null} (${placeholderBitmap.width}x${placeholderBitmap.height})")
        
        nearbyArticles.forEach { article ->
            val position = LatLng(article.lat, article.lon)
            markerPositions.add(position)
            
            val markerOptions = MarkerOptions()
                .position(position)
                .title(article.title)
            
            val hasThumbnail = thumbnailBitmaps.containsKey(article.title)
            val thumbnailBitmap = thumbnailBitmaps[article.title]
            var bitmap = thumbnailBitmap ?: placeholderBitmap
            
            
            if (!hasThumbnail) {
            } else {
            }
            
            // Add green checkmark if article has been visited
            if (visitedArticles.contains(article.title)) {
                bitmap = addCheckmarkToBitmap(bitmap, 80)
            }
            
            try {
                if (bitmap == null) {
                    return@forEach
                }
                if (bitmap.isRecycled) {
                    return@forEach
                }
                val icon = IconFactory.getInstance(context).fromBitmap(bitmap)
                if (icon == null) {
                    return@forEach
                }
                markerOptions.icon(icon)
                
                val marker = initializedMap.addMarker(markerOptions)
                if (marker == null) {
                    return@forEach
                }
                markerToTitleMap[marker] = article.title
                allMarkers.add(marker)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Added ${allMarkers.size} total markers")
        return markerPositions
    }
    
    // Helper function to calculate bounds from marker positions
    fun calculateBounds(markerPositions: List<LatLng>): LatLngBounds {
        val boundsBuilder = LatLngBounds.Builder()
        markerPositions.forEach { position ->
            boundsBuilder.include(position)
        }
        return boundsBuilder.build()
    }
    
    // Helper function to calculate center from marker positions
    fun calculateCenter(markerPositions: List<LatLng>): Pair<Double, Double> {
        var minLat = markerPositions[0].latitude
        var maxLat = markerPositions[0].latitude
        var minLon = markerPositions[0].longitude
        var maxLon = markerPositions[0].longitude
        
        markerPositions.forEach { position ->
            minLat = minOf(minLat, position.latitude)
            maxLat = maxOf(maxLat, position.latitude)
            minLon = minOf(minLon, position.longitude)
            maxLon = maxOf(maxLon, position.longitude)
        }
        
        return Pair((minLat + maxLat) / 2, (minLon + maxLon) / 2)
    }
    
    // Helper function to calculate estimated zoom based on marker positions
    fun calculateEstimatedZoom(markerPositions: List<LatLng>): Double {
        if (markerPositions.isEmpty()) return 15.0
        if (markerPositions.size == 1) return 15.0
        
        var minLat = markerPositions[0].latitude
        var maxLat = markerPositions[0].latitude
        var minLon = markerPositions[0].longitude
        var maxLon = markerPositions[0].longitude
        
        markerPositions.forEach { position ->
            minLat = minOf(minLat, position.latitude)
            maxLat = maxOf(maxLat, position.latitude)
            minLon = minOf(minLon, position.longitude)
            maxLon = maxOf(maxLon, position.longitude)
        }
        
        val latDiff = maxLat - minLat
        val lonDiff = maxLon - minLon
        val maxDiff = maxOf(latDiff, lonDiff)
        return when {
            maxDiff > 0.1 -> 10.0
            maxDiff > 0.05 -> 12.0
            maxDiff > 0.01 -> 14.0
            else -> 16.0
        }
    }
    
    // Helper function to setup camera position
    fun setupCameraPosition(
        initializedMap: MapLibreMap,
        context: Context,
        markerPositions: List<LatLng>,
        loc: LatLng,
        previousScreen: Screen?,
        nearbyArticles: List<GeoSearchResult>,
        initialGeosearchDone: Boolean,
        markersInitiallyAdded: Boolean,
        initialLat: Double?,
        initialLon: Double?,
        initialCameraSetup: Boolean,
        savedCameraPosition: org.maplibre.android.camera.CameraPosition?,
        setIsRestoringCamera: (Boolean) -> Unit,
        setInitialCameraSetup: (Boolean) -> Unit,
        setSavedCameraPosition: (org.maplibre.android.camera.CameraPosition?) -> Unit,
        viewModel: NearbyMapViewModel // Add ViewModel to access state directly
    ): Boolean {
        // Returns true if camera was restored (should return early), false otherwise
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Camera setup - initialCameraSetup=$initialCameraSetup, markerCount=${markerPositions.size}, savedPosition=${savedCameraPosition != null}, initialLat=$initialLat, initialLon=$initialLon")
        
        // FIRST: If returning via back button, restore saved position and skip all other setup
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Camera restoration check - previousScreen=${previousScreen?.javaClass?.simpleName}, initialLat=$initialLat, initialLon=$initialLon")
        
        // Check each condition separately for better debugging
        // Use ViewModel state directly to avoid timing issues with local variable sync
        // Detect returning from article by checking:
        // 1. previousScreen is Screen.Search (we restored it when returning from article)
        //    - When opening from main screen first time, previousScreen is null
        //    - When returning from article, previousScreen is Search (restored)
        // 2. initialLat/initialLon are null (we set them to null when returning)
        // 3. ViewModel has state (articles, camera setup, etc.) indicating we've been here before
        val hasArticles = viewModel.nearbyArticles.isNotEmpty() // Use ViewModel directly
        val geosearchDone = viewModel.initialGeosearchDone // Use ViewModel directly
        val cameraSetup = viewModel.initialCameraSetup // Use ViewModel directly
        val markersAdded = viewModel.markersInitiallyAdded // Use ViewModel directly
        // Detect returning from article using navigation stack (much more reliable!)
        // Check if the last screen in the navigation stack was "Article"
        val isReturningFromArticle = viewModel.isReturningFromArticle() &&
                                    initialLat == null && 
                                    initialLon == null &&
                                    hasArticles && 
                                    geosearchDone && 
                                    cameraSetup &&
                                    markersAdded
        
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Condition breakdown - isReturningFromArticle=${viewModel.isReturningFromArticle()}, initialLat=$initialLat, initialLon=$initialLon, hasArticles=$hasArticles (${viewModel.nearbyArticles.size}), geosearchDone=$geosearchDone, cameraSetup=$cameraSetup, markersAdded=$markersAdded")
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Local state - articles=${nearbyArticles.size}, geosearchDone=$initialGeosearchDone, cameraSetup=$initialCameraSetup, markersAdded=$markersInitiallyAdded")
        android.util.Log.d("WikiWatch", "NearbyMapScreen: ViewModel state - articles=${viewModel.nearbyArticles.size}, geosearchDone=${viewModel.initialGeosearchDone}, cameraSetup=${viewModel.initialCameraSetup}, markersAdded=${viewModel.markersInitiallyAdded}")
        
        android.util.Log.d("WikiWatch", "NearbyMapScreen: Final result - isReturningFromArticle=$isReturningFromArticle")
        
        if (isReturningFromArticle) {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: isReturningFromArticle is TRUE - attempting to restore camera")
            val positionToRestore = savedCameraPosition ?: loadSavedCameraPosition(context)
            android.util.Log.d("WikiWatch", "NearbyMapScreen: positionToRestore=${positionToRestore != null}, savedCameraPosition=${savedCameraPosition != null}, loadedFromPrefs=${positionToRestore != null && savedCameraPosition == null}")
            if (positionToRestore != null) {
                android.util.Log.d("WikiWatch", "NearbyMapScreen: Restoring saved camera position (returning from article) - target=${positionToRestore.target}, zoom=${positionToRestore.zoom}, markerCount=${markerPositions.size}")
                setSavedCameraPosition(positionToRestore)
                setIsRestoringCamera(true)
                initializedMap.cameraPosition = positionToRestore
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    setIsRestoringCamera(false)
                    android.util.Log.d("WikiWatch", "NearbyMapScreen: Camera restoration complete, re-enabling position saving")
                }, 500)
                return true // Indicate that camera was restored, should return early
            } else {
                android.util.Log.d("WikiWatch", "NearbyMapScreen: No saved camera position to restore, will continue with other setup")
            }
        } else {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Not returning (isReturningFromArticle=$isReturningFromArticle), will proceed with normal camera setup")
        }
        
        // SECOND: If opening from article location via coordinates icon, focus on that location but keep zoom level
        if (initialLat != null && initialLon != null && !initialCameraSetup && markerPositions.isNotEmpty()) {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Opening from article location via coordinates icon")
            setInitialCameraSetup(true)
            viewModel.setInitialCameraSetup(true) // Also update ViewModel so it persists
            
            // Get the saved camera position to preserve zoom level
            val savedPosition = savedCameraPosition ?: loadSavedCameraPosition(context)
            val zoomLevel = savedPosition?.zoom ?: 15.0 // Use saved zoom, or default to 15.0
            
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Focusing on article coordinates (lat=$initialLat, lon=$initialLon) with preserved zoom level: $zoomLevel")
            
            // Find the marker for the article at these coordinates (or use the coordinates directly)
            val targetMarker = markerPositions.find { 
                Math.abs(it.latitude - initialLat!!) < 0.0001 && 
                Math.abs(it.longitude - initialLon!!) < 0.0001 
            } ?: markerPositions.firstOrNull() ?: LatLng(initialLat!!, initialLon!!)
            
            val cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                .target(targetMarker)
                .zoom(zoomLevel) // Use preserved zoom level
                .build()
            
            initializedMap.cameraPosition = cameraPosition
            setSavedCameraPosition(cameraPosition)
            saveCameraPosition(context, cameraPosition)
            
            // Reset the flag after using it
            viewModel.setOpeningFromArticleCoordinates(false)
            
            return false
        }
        
        // THIRD: Otherwise, do initial zoom to article markers (first time opening, only if we have markers)
        if (!initialCameraSetup && markerPositions.isNotEmpty()) {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Setting up camera with ${markerPositions.size} marker positions (${nearbyArticles.size} articles)")
            setInitialCameraSetup(true)
            viewModel.setInitialCameraSetup(true) // Also update ViewModel so it persists
            
            val bounds = calculateBounds(markerPositions)
            val (centerLat, centerLon) = calculateCenter(markerPositions)
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Calculated bounds - centerLat=$centerLat, centerLon=$centerLon, markerCount=${markerPositions.size}")
            
            if (markerPositions.size == 1) {
                android.util.Log.d("WikiWatch", "NearbyMapScreen: Only one marker position, using fixed zoom")
                val cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(markerPositions[0])
                    .zoom(15.0)
                    .build()
                initializedMap.cameraPosition = cameraPosition
                setSavedCameraPosition(cameraPosition)
                saveCameraPosition(context, cameraPosition)
            } else {
                android.util.Log.d("WikiWatch", "NearbyMapScreen: Fitting camera to bounds for ${markerPositions.size} marker positions")
                try {
                    android.util.Log.d("WikiWatch", "NearbyMapScreen: Calling easeCamera with bounds")
                    initializedMap.easeCamera(
                        CameraUpdateFactory.newLatLngBounds(bounds, 100),
                        1000
                    )
                    android.util.Log.d("WikiWatch", "NearbyMapScreen: Camera animation started to fit bounds")
                    val (centerLat, centerLon) = calculateCenter(markerPositions)
                    val estimatedZoom = calculateEstimatedZoom(markerPositions)
                    android.util.Log.d("WikiWatch", "NearbyMapScreen: Estimated zoom: $estimatedZoom")
                    val cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                        .target(LatLng(centerLat, centerLon))
                        .zoom(estimatedZoom)
                        .build()
                    setSavedCameraPosition(cameraPosition)
                    saveCameraPosition(context, cameraPosition)
                } catch (e: Exception) {
                    android.util.Log.e("WikiWatch", "NearbyMapScreen: Failed to fit bounds: ${e.message}, using fallback", e)
                    e.printStackTrace()
                    val (centerLat, centerLon) = calculateCenter(markerPositions)
                    val estimatedZoom = calculateEstimatedZoom(markerPositions)
                    android.util.Log.d("WikiWatch", "NearbyMapScreen: Using fallback zoom: $estimatedZoom")
                    val cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                        .target(LatLng(centerLat, centerLon))
                        .zoom(estimatedZoom)
                        .build()
                    initializedMap.cameraPosition = cameraPosition
                    setSavedCameraPosition(cameraPosition)
                    saveCameraPosition(context, cameraPosition)
                }
            }
            return false
        } else if (!initialCameraSetup && markerPositions.isEmpty()) {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: No markers yet, waiting for search results before setting camera")
        } else {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Skipping camera setup - initialCameraSetup=$initialCameraSetup")
        }
        
        return false
    }
    
    
    // Helper function to add markers to map (extracted to avoid code duplication)
    // Note: initialCameraSetup is accessed directly from composable scope
    // This function should only be called when the map is in a stable state
    fun addMarkersToMap(
        initializedMap: MapLibreMap,
        style: Style,
        loc: LatLng,
        context: Context,
        nearbyArticles: List<GeoSearchResult>,
        thumbnailBitmaps: Map<String, Bitmap>,
        visitedArticles: Set<String>,
        initialLat: Double?,
        initialLon: Double?,
        userLocation: LatLng?,
        allMarkers: MutableList<Marker>,
        markerToTitleMap: MutableMap<Marker, String>
    ) {
        try {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: addMarkersToMap called - markers to clear: ${allMarkers.size}, articles: ${nearbyArticles.size}")
            
            // Verify map and style are in valid state
            val currentStyle = initializedMap.style
            if (currentStyle == null || currentStyle != style) {
                android.util.Log.w("WikiWatch", "NearbyMapScreen: Map style mismatch or null, aborting marker update - currentStyle=${currentStyle != null}, styleMatch=${currentStyle == style}")
                return
            }
            
            // Setup style images (user location pin, placeholder, thumbnails)
            val (userLocationPin, placeholderBitmap) = setupStyleImages(style, context, thumbnailBitmaps)
            
            // Clear existing markers
            clearExistingMarkers(initializedMap, allMarkers, markerToTitleMap)
            
            // Add user location marker
            addUserLocationMarker(
                initializedMap, context, userLocation, initialLat, initialLon,
                userLocationPin, allMarkers, markerToTitleMap
            )
            
            // Add article markers and get their positions
            val markerPositions = addArticleMarkers(
                initializedMap, context, nearbyArticles, thumbnailBitmaps,
                placeholderBitmap, visitedArticles, allMarkers, markerToTitleMap
            )
            
            // Setup camera position - returns true if camera was restored (should return early)
            val cameraRestored = setupCameraPosition(
                initializedMap, context, markerPositions, loc, previousScreen,
                nearbyArticles, initialGeosearchDone, markersInitiallyAdded,
                initialLat, initialLon, initialCameraSetup, savedCameraPosition,
                { isRestoringCamera = it }, { initialCameraSetup = it }, { savedCameraPosition = it },
                viewModel // Pass ViewModel to access state directly
            )
            
            if (cameraRestored) {
                // Camera was restored, return early to prevent any other camera setup
                return@addMarkersToMap
            }
            
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Marker and camera setup complete")
        } catch (e: Exception) {
            // Log error but don't crash
            android.util.Log.e("WikiWatch", "NearbyMapScreen: Error in addMarkersToMap: ${e.message}", e)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        if (!hasPermission) {
            Text(
                text = "Location permission required",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (false) { // Disabled loading indicator - removed per user request
            // Only show loading if we're actually loading AND don't have articles yet
            // If we have articles, don't show loading spinner even if isLoading is true (might be from a background update)
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                strokeWidth = 2.dp
            )
        } else {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            ) { view ->
                // Configure the MapView to properly handle touch events
                // This is done in the update block to ensure it's applied after the view is created
                (view as? MapView)?.apply {
                    // Request that parent views don't intercept touch events
                    // This prevents system gestures (like back navigation) from interfering
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                // Ensure attribution is disabled whenever we have access to the MapView
                // This runs on every recomposition, so it will catch any cases where attribution gets re-enabled
                val mapViewInstance = view as? MapView
                mapInstance?.let { map ->
                    map.uiSettings.setAttributionEnabled(false)
                }
                
                // Set up map async callback only once
                // The update block runs on every recomposition, so we guard against multiple calls
                if (!mapListenersSetup) {
                    mapViewInstance?.getMapAsync(object : OnMapReadyCallback {
                        override fun onMapReady(map: MapLibreMap) {
                            // Always disable attribution overlay (even if map was reused)
                            map.uiSettings.setAttributionEnabled(false)
                            
                            // Only set up once - double check to prevent race conditions
                            if (mapInstance == null && !mapListenersSetup) {
                                mapInstance = map
                                mapListenersSetup = true                                
                                // Disable compass and rotation-related gestures to prevent crashes
                                // The compass indicator that appears during rotation can cause SIGSEGV crashes
                                map.uiSettings.setCompassEnabled(false)
                                map.uiSettings.setRotateGesturesEnabled(false)
                                map.uiSettings.setTiltGesturesEnabled(false)
                                map.uiSettings.setAttributionEnabled(false)
                                
                                // Ensure pan and scroll gestures are enabled and properly configured
                                // This helps prevent pan gestures from being misinterpreted as swipes
                                map.uiSettings.setScrollGesturesEnabled(true)
                                map.uiSettings.setZoomGesturesEnabled(true)
                                map.uiSettings.setDoubleTapGesturesEnabled(true)
                                
                                // Configure the MapView to prevent parent views from intercepting touch events
                                // This is critical to prevent system gestures (especially right swipes) from interfering
                                // We use the captured mapViewInstance from the outer scope
                                mapViewInstance?.let { mv ->
                                    try {
                                        mv.parent?.requestDisallowInterceptTouchEvent(true)
                                        // Set a touch listener that requests parent not to intercept while handling touches
                                        mv.setOnTouchListener { v, event ->
                                            // Request parent not to intercept while we're handling the touch
                                            // This prevents system gestures like back navigation from triggering
                                            v.parent?.requestDisallowInterceptTouchEvent(true)
                                            false // Return false to let the MapView handle the event normally
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w("WikiWatch", "NearbyMapScreen: Could not configure touch handling: ${e.message}")
                                    }
                                }                                
                                // Set up click listeners once - they will persist
                                // Use marker.title directly instead of markerToTitleMap to avoid reference issues
                                map.setOnMarkerClickListener { marker ->
                                    val title = marker.title
                                    if (title != null && title.isNotEmpty()) {
                                        // Find the article from the current nearbyArticles list using the title
                                        val article = nearbyArticles.find { it.title == title }
                                        if (article != null) {
                                            selectedArticle = article
                                            true // Consume the event - prevent default info window
                                        } else {
                                            // Fallback: try markerToTitleMap if title doesn't match
                                            val titleFromMap = markerToTitleMap[marker]
                                            if (titleFromMap != null) {
                                                val articleFromMap = nearbyArticles.find { it.title == titleFromMap }
                                                if (articleFromMap != null) {
                                                    selectedArticle = articleFromMap
                                                    true
                                                } else {
                                                    false
                                                }
                                            } else {
                                                false
                                            }
                                        }
                                    } else {
                                        false
                                    }
                                }
                                map.uiSettings.setAttributionEnabled(false)
                                map.addOnMapClickListener { _ ->
                                    selectedArticle = null
                                    true
                                }
                                
                                // Save camera position whenever user stops moving it (pan/zoom complete)
                                // Using idle listener to avoid saving too frequently
                                map.addOnCameraIdleListener {
                                    // Don't save if we're currently restoring the camera position
                                    if (!isRestoringCamera) {
                                        // Save the current camera position so we can restore it when returning to the map
                                        savedCameraPosition = map.cameraPosition
                                        // Also persist to SharedPreferences to survive composable recreation
                                        saveCameraPosition(context, map.cameraPosition)
                                        android.util.Log.d("WikiWatch", "NearbyMapScreen: Saved camera position - target=${map.cameraPosition.target}, zoom=${map.cameraPosition.zoom}")
                                    } else {
                                        android.util.Log.d("WikiWatch", "NearbyMapScreen: Skipping camera position save (restoration in progress)")
                                    }
                                }
                                
                                // Initial style setup - markers will be added/updated in the LaunchedEffect below
                                val styleAsset = "asset://mapstyle.json"
                                map.setStyle(styleAsset) { style ->
                                    // Style is loaded, markers will be added when searchLocation is available
                                }
                                
                                android.util.Log.d("WikiWatch", "NearbyMapScreen: Map listeners setup complete")
                            } else {
                                android.util.Log.w("WikiWatch", "NearbyMapScreen: Map already initialized or listeners already setup, but ensuring attribution is disabled")
                                // Map was already initialized, but ensure attribution is still disabled
                                map.uiSettings.setAttributionEnabled(false)
                                // Also update mapInstance if it's null but we have the map
                                if (mapInstance == null) {
                                    mapInstance = map
                                }
                            }
                        }
                    })
                }
            }
        }
        
        // Ensure attribution is disabled whenever we have access to mapInstance
        LaunchedEffect(mapInstance) {
            mapInstance?.let { map ->
                map.uiSettings.setAttributionEnabled(false)
                android.util.Log.d("WikiWatch", "NearbyMapScreen: Ensured attribution is disabled on map instance")
            }
        }
        
        // Load style once when map is ready (only call setStyle once)
        // Check if style is already loaded on the map instance to avoid reloading when returning
        LaunchedEffect(mapInstance, styleLoaded) {
            mapInstance?.let { initializedMap ->
                // Check if style is already loaded on the map (e.g., when returning from article)
                if (initializedMap.style != null && !styleLoaded) {
                    android.util.Log.d("WikiWatch", "NearbyMapScreen: Map style already loaded, marking as loaded")
                    // Ensure attribution is disabled when style is already loaded
                    initializedMap.uiSettings.setAttributionEnabled(false)
                    styleLoaded = true
                    viewModel.setStyleLoaded(true) // Persist to ViewModel
                } else if (!styleLoaded) {
                    try {
                        android.util.Log.d("WikiWatch", "NearbyMapScreen: Loading map style (one-time)")
                        val styleAsset = "asset://mapstyle.json"
                        initializedMap.setStyle(styleAsset) { style ->
                            android.util.Log.d("WikiWatch", "NearbyMapScreen: Style loaded successfully")
                            // Disable attribution after style loads (style loading can reset UI settings)
                            initializedMap.uiSettings.setAttributionEnabled(false)
                            styleLoaded = true
                            viewModel.setStyleLoaded(true)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("WikiWatch", "NearbyMapScreen: Error loading style: ${e.message}", e)
                    }
                }
            }
        }
        
        // Update markers when data changes (only if map and style are ready)
        // Add debounce to prevent rapid updates during interactions
        // Include userLocation so the user location pin updates when location changes
        LaunchedEffect(mapInstance, styleLoaded, searchLocation, nearbyArticles, thumbnailBitmaps, visitedArticles, userLocation) {
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Marker update LaunchedEffect - mapInstance=${mapInstance != null}, styleLoaded=$styleLoaded, searchLocation=${searchLocation != null}, nearbyArticles=${nearbyArticles.size}, thumbnails=${thumbnailBitmaps.size}, visited=${visitedArticles.size}, updateInProgress=$markerUpdateInProgress, markersInitiallyAdded=$markersInitiallyAdded")
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Thumbnail keys: ${thumbnailBitmaps.keys.joinToString(", ")}")
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Article titles: ${nearbyArticles.map { it.title }.joinToString(", ")}")
            // Check which articles have thumbnails
            val articlesWithThumbnails = nearbyArticles.filter { thumbnailBitmaps.containsKey(it.title) }
            val articlesWithoutThumbnails = nearbyArticles.filter { !thumbnailBitmaps.containsKey(it.title) }
            android.util.Log.d("WikiWatch", "NearbyMapScreen: Articles with thumbnails: ${articlesWithThumbnails.size}, without: ${articlesWithoutThumbnails.size}")
            if (articlesWithoutThumbnails.isNotEmpty()) {
                android.util.Log.d("WikiWatch", "NearbyMapScreen: Articles missing thumbnails: ${articlesWithoutThumbnails.map { "${it.title} (thumbnailUrl=${it.thumbnailUrl})" }.joinToString(", ")}")
            }
            
            // Skip if update is already in progress
            if (markerUpdateInProgress) {
                android.util.Log.d("WikiWatch", "NearbyMapScreen: Marker update already in progress, skipping")
                return@LaunchedEffect
            }
            
            // If markers were already added and we're returning (not opening from article),
            // we should still update if thumbnails are available (they might have loaded after initial marker creation)
            // OR if visitedArticles changed (for checkmarks) OR if we're opening from article
            val hasThumbnails = thumbnailBitmaps.isNotEmpty() && articlesWithThumbnails.isNotEmpty()
            val shouldSkipUpdate = markersInitiallyAdded && 
                                   initialLat == null && 
                                   initialLon == null && 
                                   nearbyArticles.isNotEmpty() && 
                                   allMarkers.isNotEmpty() &&
                                   !hasThumbnails // Don't skip if thumbnails are available now
            
            if (shouldSkipUpdate) {
                android.util.Log.d("WikiWatch", "NearbyMapScreen: Markers already added and we're returning, skipping update to prevent reload (thumbnails: ${thumbnailBitmaps.size}, articles with thumbnails: ${articlesWithThumbnails.size})")
                return@LaunchedEffect
            } else if (hasThumbnails && markersInitiallyAdded) {
                android.util.Log.d("WikiWatch", "NearbyMapScreen: Thumbnails available (${thumbnailBitmaps.size}), updating markers even though they were already added")
            }
            mapInstance?.uiSettings?.setAttributionEnabled(false)
            mapInstance?.let { initializedMap ->
                if (styleLoaded && initializedMap.style != null) {
                    searchLocation?.let { loc ->
                        // Add small delay to debounce rapid updates and let map interactions settle
                        kotlinx.coroutines.delay(150)
                        
                        // Double-check we're still in a valid state
                        if (markerUpdateInProgress || initializedMap.style == null) {
                            android.util.Log.d("WikiWatch", "NearbyMapScreen: State changed during debounce, skipping update")
                            return@LaunchedEffect
                        }
                        
                        try {
                            markerUpdateInProgress = true
                            android.util.Log.d("WikiWatch", "NearbyMapScreen: Updating markers (style already loaded)")
                            val style = initializedMap.style ?: run {
                                markerUpdateInProgress = false
                                return@LaunchedEffect
                            }
                            
                            // LaunchedEffect already runs on UI thread, so we can call directly
                            // But wrap in try-catch for safety
                            android.util.Log.d("WikiWatch", "NearbyMapScreen: Calling addMarkersToMap with ${nearbyArticles.size} articles and ${thumbnailBitmaps.size} thumbnails")
                            android.util.Log.d("WikiWatch", "NearbyMapScreen: Thumbnail keys being passed: ${thumbnailBitmaps.keys.joinToString(", ")}")
                            addMarkersToMap(initializedMap, style, loc, context, nearbyArticles, thumbnailBitmaps, visitedArticles, initialLat, initialLon, userLocation, allMarkers, markerToTitleMap)
                            markersInitiallyAdded = true // Mark that markers have been added
                            viewModel.setMarkersInitiallyAdded(true)
                            android.util.Log.d("WikiWatch", "NearbyMapScreen: Marker update completed successfully")
                        } catch (e: Exception) {
                            android.util.Log.e("WikiWatch", "NearbyMapScreen: Error updating markers: ${e.message}", e)
                            e.printStackTrace()
                        } finally {
                            markerUpdateInProgress = false
                        }
                    } ?: run {
                        android.util.Log.d("WikiWatch", "NearbyMapScreen: searchLocation is null in marker update")
                    }
                } else {
                    android.util.Log.d("WikiWatch", "NearbyMapScreen: Waiting for style to load - styleLoaded=$styleLoaded, style=${initializedMap.style != null}")
                }
            } ?: run {
                android.util.Log.d("WikiWatch", "NearbyMapScreen: mapInstance is null, waiting for map initialization")
            }
        }
        
        // Game UI overlay
        if (isGameActive) {
            // Score counter at bottom center
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .background(Color(0xFF1A1A1A), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Score: $gameScore",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
            
            // End game button with chequered flag icon (green) at middle right
            Chip(
                onClick = {
                    isGameActive = false
                    // Save to SharedPreferences
                    context.getSharedPreferences("game_state", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("is_game_active", false)
                        .apply()
                    // Stop location updates
                    try {
                        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                        fusedLocationClient.removeLocationUpdates(locationCallback)
                        android.util.Log.d("WikiWatch", "Stopped location updates (game ended via button)")
                    } catch (e: Exception) {
                        android.util.Log.e("WikiWatch", "Error stopping location updates: ${e.message}", e)
                    }
                    showSummary = true
                },
                label = { 
                    Icon(
                        painter = painterResource(id = R.drawable.ic_flag_checkered),
                        contentDescription = "End Game",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .height(32.dp),
                colors = ChipDefaults.primaryChipColors(
                    backgroundColor = Color(0xFF4CAF50) // Green
                )
            )
            
            // +5 animation
            if (showScoreAnimation) {
                var animationProgress by remember { mutableStateOf(0f) }
                
                LaunchedEffect(showScoreAnimation) {
                    if (showScoreAnimation) {
                        animationProgress = 0f
                        kotlinx.coroutines.delay(2000)
                        showScoreAnimation = false
                    }
                }
                
                val offsetY by animateFloatAsState(
                    targetValue = -50f,
                    animationSpec = tween(durationMillis = 2000)
                )
                val alpha by animateFloatAsState(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 2000)
                )
                
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = offsetY.dp)
                        .alpha(alpha)
                ) {
                    Text(
                        text = "+5",
                        color = Color(0xFF4CAF50),
                        fontSize = 24.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        } else {
            // Start game button at bottom
            Chip(
                onClick = {
                    isGameActive = true
                    gameScore = 0
                    articlesCollectedThisSession = 0
                    // Save to SharedPreferences
                    context.getSharedPreferences("game_state", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("is_game_active", true)
                        .putInt("game_score", 0)
                        .putInt("articles_collected_session", 0)
                        .apply()
                },
                label = { Text("Start Game", fontSize = 10.sp) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .height(32.dp),
                colors = ChipDefaults.primaryChipColors()
            )
        }
        
        Chip(
            onClick = onBack,
            label = { Text("← Back", fontSize = 10.sp) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
                .height(32.dp),
            colors = ChipDefaults.secondaryChipColors()
        )
        
        // Summary screen
        if (showSummary) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF000000).copy(alpha = 0.9f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Game Summary",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    
                    Text(
                        text = "Articles Collected: $articlesCollectedThisSession",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    
                    Text(
                        text = "Total Score: $gameScore",
                        color = Color(0xFF4CAF50),
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    
                    Chip(
                        onClick = {
                            showSummary = false
                        },
                        label = { Text("Close", fontSize = 10.sp) },
                        modifier = Modifier.height(32.dp),
                        colors = ChipDefaults.primaryChipColors()
                    )
                }
            }
        }
        
        // Bottom sheet for selected article
        androidx.compose.animation.AnimatedVisibility(
            visible = selectedArticle != null,
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedArticle?.let { article ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFF1A1A1A),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Row with thumbnail and text content
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Top
                        ) {
                            // Thumbnail
                            val imageLoader = remember {
                                ImageLoader.Builder(context)
                                    .okHttpClient { okHttpClient }
                                    .build()
                            }
                            
                            if (article.thumbnailUrl != null) {
                                Box(
                                    modifier = Modifier.padding(end = 12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                    ) {
                                        SubcomposeAsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(article.thumbnailUrl)
                                                .crossfade(true)
                                                .build(),
                                            imageLoader = imageLoader,
                                            contentDescription = article.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            } else {
                                // Placeholder space if no thumbnail
                                Spacer(modifier = Modifier.size(48.dp).padding(end = 12.dp))
                            }
                            
                            // Title and description column
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                // "You've bagged" message when game is active and article was just collected
                                if (isGameActive && recentlyCollectedArticle?.title == article.title) {
                                    Text(
                                        text = "You've bagged",
                                        color = Color(0xFF4CAF50),
                                        fontSize = 12.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                                
                                // Title
                                Text(
                                    text = article.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                
                                // Description
                                article.description?.let { description ->
                                    Text(
                                        text = description,
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                        maxLines = 3,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                        
                        // Read more button (smaller)
                        Chip(
                            onClick = { onArticleClick(article.title) },
                            label = { Text("Read more", fontSize = 10.sp) },
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .height(32.dp),
                            colors = ChipDefaults.primaryChipColors()
                        )
                    }
                }
            }
        }
    }
}

