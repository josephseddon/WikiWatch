package com.wikimedia.wikiwatch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.wikimedia.wikiwatch.R
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.wikimedia.wikiwatch.data.SearchResult
import com.wikimedia.wikiwatch.data.WikipediaRepository
import okhttp3.OkHttpClient

@Composable
fun ArticleScreen(
    title: String,
    onBack: () -> Unit,
    onSwipeToNext: (String) -> Unit,
    onLinkClick: (String) -> Unit = onSwipeToNext,
    onSearch: () -> Unit = {},
    onOpenMap: (Double, Double) -> Unit = { _, _ -> },
    forwardArticle: String? = null,
    onForward: () -> Unit = {}
) {
    var summary by remember { mutableStateOf<String?>(null) }
    var thumbnailUrl by remember { mutableStateOf<String?>(null) }
    var pageLinks by remember { mutableStateOf<List<String>>(emptyList()) }
    var relatedArticles by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var coordinates by remember { mutableStateOf<com.wikimedia.wikiwatch.data.Coordinate?>(null) }
    var currentIndex by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    
    // Scroll to hide search bar on load
    LaunchedEffect(title) {
        scrollState.scrollTo(200)
    }

    LaunchedEffect(title) {
        isLoading = true
        val response = WikipediaRepository.getSummary(title)
        summary = response?.extract
        thumbnailUrl = response?.thumbnail?.source ?: response?.originalimage?.source
        android.util.Log.d("WikiWatch", "Thumbnail URL: $thumbnailUrl")
        pageLinks = WikipediaRepository.getPageLinks(title)
        coordinates = WikipediaRepository.getCoordinates(title)
        relatedArticles = WikipediaRepository.moreLike(title)
        currentIndex = 0
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .pointerInput(relatedArticles) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -50 && relatedArticles.isNotEmpty()) {
                        val nextArticle = relatedArticles.getOrNull(currentIndex)
                        if (nextArticle != null) {
                            currentIndex = (currentIndex + 1) % relatedArticles.size
                            onSwipeToNext(nextArticle.title)
                        }
                    } else if (dragAmount > 50) {
                        onBack()
                    }
                }
            }
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                strokeWidth = 2.dp
            )
        } else {
            // Calculate gradient alpha based on scroll position - starts dark, fades as you scroll up
            val gradientAlpha = ((scrollState.value - 200) / 300f).coerceIn(0f, 0.7f)
            // Toolbar fades in as you scroll up
            val toolbarAlpha = (1f - (scrollState.value / 200f)).coerceIn(0f, 1f)
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                // Toolbar hidden above - scroll up to reveal
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 24.dp)
                        .alpha(toolbarAlpha),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    Text(
                        text = "←",
                        color = Color.White,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clickable { onBack() }
                            .padding(8.dp)
                    )
                    
                    // Search button in token
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF2A2A2A), shape = androidx.compose.foundation.shape.CircleShape)
                            .clickable { onSearch() },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = "Search",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    // Forward button (darkened when no forward article)
                    Text(
                        text = "→",
                        color = if (forwardArticle != null) Color.White else Color.DarkGray,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clickable(enabled = forwardArticle != null) { onForward() }
                            .padding(8.dp)
                    )
                }
                
                if (thumbnailUrl != null) {
                    val context = LocalContext.current
                    val imageLoader = remember {
                        ImageLoader.Builder(context)
                            .okHttpClient {
                                OkHttpClient.Builder()
                                    .addInterceptor { chain ->
                                        val request = chain.request().newBuilder()
                                            .header("User-Agent", "WikiWatch/1.0 (WearOS App)")
                                            .build()
                                        chain.proceed(request)
                                    }
                                    .build()
                            }
                            .build()
                    }
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        imageLoader = imageLoader,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .requiredWidth(LocalConfiguration.current.screenWidthDp.dp)
                            .height(120.dp)
                            .offset(y = (-16).dp),
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                        },
                        error = { state ->
                            android.util.Log.e("WikiWatch", "Image error: ${state.result.throwable}")
                            Text("Image failed", color = Color.Red, fontSize = 10.sp)
                        }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Center
                    )
                    if (coordinates != null) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_map_pin),
                            contentDescription = "Open in Maps",
                            tint = Color(0xFF6BA5FF),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(18.dp)
                                .clickable {
                                    onOpenMap(coordinates!!.lat, coordinates!!.lon)
                                }
                        )
                    }
                }
                LinkedText(
                    text = summary ?: "No content available",
                    links = pageLinks,
                    onLinkClick = onLinkClick
                )
                
                // Related articles horizontal scroll
                if (relatedArticles.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp)
                            .height(1.dp)
                            .background(Color.Gray.copy(alpha = 0.3f))
                    )
                    Text(
                        text = "Related",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .requiredWidth(LocalConfiguration.current.screenWidthDp.dp)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val context = LocalContext.current
                        val imageLoader = remember {
                            ImageLoader.Builder(context)
                                .okHttpClient {
                                    OkHttpClient.Builder()
                                        .addInterceptor { chain ->
                                            val request = chain.request().newBuilder()
                                                .header("User-Agent", "WikiWatch/1.0 (WearOS App)")
                                                .build()
                                            chain.proceed(request)
                                        }
                                        .build()
                                }
                                .build()
                        }
                        
                        relatedArticles.take(4).forEach { article ->
                            Column(
                                modifier = Modifier
                                    .width(80.dp)
                                    .clickable { onLinkClick(article.title) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (article.thumbnailUrl != null) {
                                    SubcomposeAsyncImage(
                                        model = article.thumbnailUrl,
                                        imageLoader = imageLoader,
                                        contentDescription = article.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.placeholder),
                                        contentDescription = article.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                    )
                                }
                                Text(
                                    text = article.title,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
            
            // Gradient overlay from title area that fades as you scroll up
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.25f)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = gradientAlpha),
                                Color.Transparent
                            ),
                            startY = 50f
                        )
                    )
            )
            
            // Bottom gradient
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.25f)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = gradientAlpha)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
fun LinkedText(
    text: String,
    links: List<String>,
    onLinkClick: (String) -> Unit
) {
    val annotatedString = remember(text, links) {
        buildAnnotatedString {
            var currentIndex = 0
            val textLower = text.lowercase()
            
            // Sort links by length descending to match longer phrases first
            val sortedLinks = links.sortedByDescending { it.length }
            
            // Find all link occurrences
            data class LinkMatch(val start: Int, val end: Int, val link: String)
            val matches = mutableListOf<LinkMatch>()
            
            fun isWordBoundary(index: Int): Boolean {
                if (index < 0 || index >= text.length) return true
                val char = text[index]
                return !char.isLetterOrDigit()
            }
            
            for (link in sortedLinks) {
                val linkLower = link.lowercase()
                var searchStart = 0
                while (true) {
                    val index = textLower.indexOf(linkLower, searchStart)
                    if (index == -1) break
                    
                    val endIndex = index + link.length
                    // Check word boundaries
                    val startsAtBoundary = isWordBoundary(index - 1)
                    val endsAtBoundary = isWordBoundary(endIndex)
                    
                    if (startsAtBoundary && endsAtBoundary) {
                        // Check if this range overlaps with existing matches
                        val overlaps = matches.any { 
                            (index < it.end && endIndex > it.start)
                        }
                        if (!overlaps) {
                            matches.add(LinkMatch(index, endIndex, link))
                        }
                    }
                    searchStart = index + 1
                }
            }
            
            // Sort matches by start position
            matches.sortBy { it.start }
            
            // Build the annotated string
            for (match in matches) {
                if (match.start > currentIndex) {
                    append(text.substring(currentIndex, match.start))
                }
                pushStringAnnotation(tag = "link", annotation = match.link)
                withStyle(style = SpanStyle(color = Color(0xFF6BA5FF))) {
                    append(text.substring(match.start, match.end))
                }
                pop()
                currentIndex = match.end
            }
            
            if (currentIndex < text.length) {
                append(text.substring(currentIndex))
            }
        }
    }
    
    ClickableText(
        text = annotatedString,
        style = TextStyle(
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "link", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onLinkClick(annotation.item)
                }
        }
    )
}

