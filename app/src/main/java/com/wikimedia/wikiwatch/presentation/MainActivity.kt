package com.wikimedia.wikiwatch.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.ImageLoader
import okhttp3.OkHttpClient
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.wikimedia.wikiwatch.R
import com.wikimedia.wikiwatch.data.SearchResult
import com.wikimedia.wikiwatch.data.WikipediaRepository
import com.wikimedia.wikiwatch.presentation.theme.WikiwatchTheme
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent {
            WikiWatchApp()
        }
    }
}

@Composable
fun WikiWatchApp() {
    WikiwatchTheme {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Search()) }
        // Back stack for article history
        val articleBackStack = remember { mutableStateListOf<String>() }
        // Forward stack for articles you went back from
        val articleForwardStack = remember { mutableStateListOf<String>() }
        // Shared ViewModel for map state - scoped to activity level to persist across navigation
        val mapViewModel: NearbyMapViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

        when (val screen = currentScreen) {
            is Screen.Search -> SearchScreen(
                initialQuery = screen.initialQuery,
                previousArticle = screen.previousArticle,
                autoFocus = screen.autoFocus,
                onArticleClick = { result ->
                    articleForwardStack.clear()
                    mapViewModel.pushScreen("Search")
                    mapViewModel.pushScreen("Article")
                    currentScreen = Screen.Article(result.title, Screen.Search())
                },
                onBackToArticle = { title ->
                    currentScreen = Screen.Article(title)
                },
                onNearbyClick = {
                    mapViewModel.pushScreen("Search")
                    mapViewModel.pushScreen("NearbyMap")
                    currentScreen = Screen.NearbyMap()
                },
                onLanguageClick = {
                    currentScreen = Screen.LanguageSelection
                }
            )
            is Screen.Article -> ArticleScreen(
                title = screen.title,
                onBack = { 
                    if (articleBackStack.isNotEmpty()) {
                        // Go back to previous article
                        articleForwardStack.add(screen.title)
                        val previousArticle = articleBackStack.removeLast()
                        currentScreen = Screen.Article(previousArticle, screen.previousScreen)
                    } else {
                        // No article history, go back to previous screen (or search if none)
                        val previousScreen = screen.previousScreen
                        // Check if we're returning to map from article BEFORE popping
                        val isReturningToMap = previousScreen is Screen.NearbyMap
                        when (previousScreen) {
                            is Screen.NearbyMap -> {
                                // When returning to map from article via BACK BUTTON:
                                // - Set initialLat/initialLon to null so map restores full camera position
                                // - Mark that we're NOT opening from coordinates icon
                                mapViewModel.setOpeningFromArticleCoordinates(false)
                                currentScreen = Screen.NearbyMap(
                                    initialLat = null, // Set to null when returning from article via back button
                                    initialLon = null, // Set to null when returning from article via back button
                                    previousScreen = previousScreen.previousScreen // Restore the map's original previousScreen (Search)
                                )
                            }
                            else -> {
                                // Pop for other navigation
                                mapViewModel.popScreen()
                                currentScreen = previousScreen ?: Screen.Search()
                            }
                        }
                    }
                },
                onSwipeToNext = { nextTitle ->
                    articleBackStack.add(screen.title)
                    articleForwardStack.clear()
                    currentScreen = Screen.Article(nextTitle, screen.previousScreen)
                },
                onSearch = { 
                    mapViewModel.clearNavigationStack()
                    currentScreen = Screen.Search(autoFocus = true) 
                },
                onOpenMap = { lat, lon ->
                    // Opening map from article's coordinates icon (not back button)
                    // Mark this so map knows to focus on coordinates but keep zoom level
                    mapViewModel.setOpeningFromArticleCoordinates(true)
                    mapViewModel.pushScreen("Article")
                    mapViewModel.pushScreen("NearbyMap")
                    currentScreen = Screen.NearbyMap(initialLat = lat, initialLon = lon, previousScreen = screen)
                },
                forwardArticle = articleForwardStack.lastOrNull(),
                onForward = {
                    if (articleForwardStack.isNotEmpty()) {
                        articleBackStack.add(screen.title)
                        val forwardArticle = articleForwardStack.removeLast()
                        currentScreen = Screen.Article(forwardArticle, screen.previousScreen)
                    }
                }
            )
            is Screen.NearbyMap -> NearbyMapScreen(
                initialLat = screen.initialLat,
                initialLon = screen.initialLon,
                previousScreen = screen.previousScreen,
                onBack = { 
                    // Pop current screen from navigation stack
                    mapViewModel.popScreen()
                    // Return to previous screen if available, otherwise go to search
                    currentScreen = screen.previousScreen ?: Screen.Search()
                },
                onArticleClick = { title ->
                    articleForwardStack.clear()
                    mapViewModel.pushScreen("NearbyMap")
                    mapViewModel.pushScreen("Article")
                    currentScreen = Screen.Article(title, screen)
                },
                viewModel = mapViewModel // Use shared ViewModel scoped to activity
            )
            is Screen.LanguageSelection -> LanguageSelectionScreen(
                currentLanguageCode = com.wikimedia.wikiwatch.data.WikipediaRepository.getCurrentLanguage(),
                onLanguageSelected = { language ->
                    com.wikimedia.wikiwatch.data.WikipediaRepository.setLanguage(language.languageCode)
                    currentScreen = Screen.Search()
                },
                onBack = { currentScreen = Screen.Search() }
            )
        }
    }
}

sealed class Screen {
    data class Search(val initialQuery: String = "", val previousArticle: String? = null, val autoFocus: Boolean = false) : Screen()
    data class Article(val title: String, val previousScreen: Screen? = null) : Screen()
    data class NearbyMap(val initialLat: Double? = null, val initialLon: Double? = null, val previousScreen: Screen? = null) : Screen()
    object LanguageSelection : Screen()
}

@Composable
fun SearchScreen(
    initialQuery: String = "",
    previousArticle: String? = null,
    autoFocus: Boolean = false,
    onArticleClick: (SearchResult) -> Unit,
    onBackToArticle: (String) -> Unit = {},
    onNearbyClick: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
    viewModel: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var query by remember { mutableStateOf(initialQuery) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val selectedLanguageCode = com.wikimedia.wikiwatch.data.WikipediaRepository.getCurrentLanguage()
    var didYouKnowEntry by remember { mutableStateOf<com.wikimedia.wikiwatch.data.DidYouKnowEntry?>(null) }
    
    // Load DYK feed
    LaunchedEffect(Unit) {
        val entries = com.wikimedia.wikiwatch.data.WikipediaRepository.getDidYouKnow()
        if (entries.isNotEmpty()) {
            didYouKnowEntry = entries.random()
        }
    }
    
    // Trigger search if initial query provided
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotEmpty()) {
            viewModel.search(initialQuery)
        }
    }
    
    // Auto focus keyboard when coming from article search icon
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
        }
    }
    
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    
    // Scroll to first result when search results appear
    LaunchedEffect(results.size) {
        if (results.isNotEmpty() && !isLoading) {
            // Scroll down to show first result (approximately after search bar and other UI elements)
            kotlinx.coroutines.delay(100) // Small delay to ensure layout is complete
            with(density) {
                scrollState.animateScrollTo(200.dp.toPx().toInt())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(top = if (previousArticle != null) 16.dp else 32.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.wikipedia_logo),
            contentDescription = "Wikipedia",
            modifier = Modifier
                .size(48.dp)
                .padding(bottom = 8.dp)
        )
        
        if (previousArticle != null) {
            Chip(
                onClick = { onBackToArticle(previousArticle) },
                label = {
                    Text(
                        text = "← Back to $previousArticle",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = ChipDefaults.secondaryChipColors()
            )
        }

        // Voice input launcher
        val voiceLauncher = rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (spokenText != null) {
                    query = spokenText
                    viewModel.search(spokenText)
                }
            }
        }

        // Search bar with language selector, voice input, and map
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Color(0xFF2A2A2A), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { 
                        viewModel.search(query)
                        focusManager.clearFocus()
                    })
                )
            }
            
            // Language selector - icon + label, just left of voice input
            Row(
                modifier = Modifier
                    .clickable { onLanguageClick() }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_language),
                    contentDescription = "Language",
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = selectedLanguageCode.uppercase().take(2),
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
            
            // Voice input icon inside search bar
            Image(
                painter = painterResource(id = R.drawable.ic_mic),
                contentDescription = "Voice search",
                modifier = Modifier
                    .size(22.dp)
                    .clickable {
                        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search Wikipedia")
                        }
                        voiceLauncher.launch(intent)
                    }
                    .padding(4.dp)
            )
            
            // Map icon inside search bar
            Image(
                painter = painterResource(id = R.drawable.ic_map),
                contentDescription = "Nearby articles",
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier
                    .size(22.dp)
                    .clickable { onNearbyClick() }
                    .padding(4.dp)
            )
            }
        }
        
        // Did You Know entry
        didYouKnowEntry?.let { entry ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .background(Color(0xFF1A1A1A), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "Did you know",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    HtmlText(
                        html = entry.html,
                        onLinkClick = { href ->
                            // Extract article title from href (handles both full URLs and relative paths)
                            val title = when {
                                href.startsWith("http") -> href.substringAfterLast("/").replace("_", " ")
                                href.startsWith("/wiki/") -> href.substringAfter("/wiki/").replace("_", " ")
                                else -> href.replace("_", " ")
                            }
                            if (title.isNotEmpty()) {
                                onArticleClick(com.wikimedia.wikiwatch.data.SearchResult(title, 0, null))
                            }
                        }
                    )
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 16.dp),
                strokeWidth = 2.dp
            )
        }

        if (results.isNotEmpty()) {
            Text(
                text = "${results.size} results",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

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
        
        // Results count with clear button
        if (results.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${results.size} results",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color(0xFF2A2A2A), shape = CircleShape)
                        .clickable {
                            query = ""
                            viewModel.search("")
                            coroutineScope.launch {
                                scrollState.animateScrollTo(0)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Clear",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        
        results.forEach { result ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                // Chip first (background layer)
                Chip(
                    onClick = { onArticleClick(result) },
                    label = {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Spacer(modifier = Modifier.weight(0.25f))
                            Text(
                                text = result.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White,
                                modifier = Modifier.weight(0.75f)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF2A2A2A))
                )
                
                // Circular thumbnail overlay on top of chip
                if (result.thumbnailUrl != null) {
                    SubcomposeAsyncImage(
                        model = result.thumbnailUrl,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.CenterStart)
                            .clip(CircleShape)
                    )
                } else {
                    // Placeholder: Wikipedia W logo on lighter grey circular background
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.CenterStart)
                            .clip(CircleShape)
                            .background(Color(0xFF999999)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.wikipedia_logo),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFFCCCCCC))
                        )
                    }
                }
            }
        }
    }
}
