package com.wikimedia.wikiwatch.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.wikimedia.wikiwatch.data.GeoSearchResult
import com.wikimedia.wikiwatch.data.WikipediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import androidx.core.graphics.drawable.toBitmap
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

@Composable
fun NearbyMapScreen(
    onBack: () -> Unit,
    onArticleClick: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var nearbyArticles by remember { mutableStateOf<List<GeoSearchResult>>(emptyList()) }
    var thumbnailBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedArticle by remember { mutableStateOf<GeoSearchResult?>(null) }
    var hasPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val okHttpClient = remember {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "WikiWatch/1.0 (WearOS App)")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                // Try lastLocation first
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        android.util.Log.d("WikiWatch", "Got location: ${location.latitude}, ${location.longitude}")
                        userLocation = LatLng(location.latitude, location.longitude)
                    } else {
                        // Request current location if lastLocation is null
                        android.util.Log.d("WikiWatch", "lastLocation is null, requesting current location")
                        val locationRequest = com.google.android.gms.location.CurrentLocationRequest.Builder()
                            .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
                            .build()
                        fusedLocationClient.getCurrentLocation(locationRequest, null)
                            .addOnSuccessListener { currentLocation ->
                                if (currentLocation != null) {
                                    android.util.Log.d("WikiWatch", "Got current location: ${currentLocation.latitude}, ${currentLocation.longitude}")
                                    userLocation = LatLng(currentLocation.latitude, currentLocation.longitude)
                                } else {
                                    android.util.Log.e("WikiWatch", "Current location is also null")
                                }
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e("WikiWatch", "Failed to get current location: ${e.message}")
                            }
                    }
                }.addOnFailureListener { e ->
                    android.util.Log.e("WikiWatch", "Failed to get last location: ${e.message}")
                }
            } catch (e: SecurityException) {
                android.util.Log.e("WikiWatch", "Security exception: ${e.message}")
            }
        }
    }

    LaunchedEffect(userLocation) {
        userLocation?.let { loc ->
            isLoading = true
            nearbyArticles = WikipediaRepository.geoSearch(loc.latitude, loc.longitude)
            
            // Load thumbnails
            val bitmaps = mutableMapOf<String, Bitmap>()
            nearbyArticles.forEach { article ->
                article.thumbnailUrl?.let { url ->
                    try {
                        withContext(Dispatchers.IO) {
                            val request = Request.Builder().url(url).build()
                            val response = okHttpClient.newCall(request).execute()
                            response.body?.byteStream()?.let { stream ->
                                val bitmap = BitmapFactory.decodeStream(stream)
                                if (bitmap != null) {
                                    bitmaps[article.title] = createCircularBitmap(bitmap, 80)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Skip failed thumbnails
                    }
                }
            }
            thumbnailBitmaps = bitmaps
            isLoading = false
        }
    }


    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
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
        } else if (isLoading || userLocation == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                strokeWidth = 2.dp
            )
        } else {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            ) { view ->
                view.getMapAsync { googleMap ->
                    userLocation?.let { loc ->
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(loc, 15f))
                        
                        // Add markers for nearby articles with thumbnails
                        googleMap.clear()
                        val placeholderBitmap = createPlaceholderBitmap(context, 80)
                        nearbyArticles.forEach { article ->
                            val markerOptions = MarkerOptions()
                                .position(LatLng(article.lat, article.lon))
                                .title(article.title)
                            
                            // Use thumbnail bitmap if available, otherwise use placeholder
                            val bitmap = thumbnailBitmaps[article.title] ?: placeholderBitmap
                            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                            
                            val marker = googleMap.addMarker(markerOptions)
                            marker?.tag = article.title
                        }
                        
                        googleMap.setOnMarkerClickListener { marker ->
                            val title = marker.tag as? String
                            if (title != null) {
                                selectedArticle = nearbyArticles.find { it.title == title }
                                true
                            } else {
                                false
                            }
                        }
                        
                        googleMap.setOnMapClickListener {
                            selectedArticle = null
                        }
                    }
                    googleMap.uiSettings.isZoomControlsEnabled = false
                }
            }
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
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Thumbnail
                        val imageLoader = remember {
                            ImageLoader.Builder(context)
                                .okHttpClient { okHttpClient }
                                .build()
                        }
                        
                        article.thumbnailUrl?.let { url ->
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(url)
                                    .crossfade(true)
                                    .build(),
                                imageLoader = imageLoader,
                                contentDescription = article.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                            )
                        }
                        
                        // Title
                        Text(
                            text = article.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        
                        // Description
                        article.description?.let { description ->
                            Text(
                                text = description,
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        
                        // Read more button
                        Chip(
                            onClick = { onArticleClick(article.title) },
                            label = { Text("Read more", fontSize = 12.sp) },
                            modifier = Modifier.padding(top = 8.dp),
                            colors = ChipDefaults.primaryChipColors()
                        )
                    }
                }
            }
        }
    }
}

