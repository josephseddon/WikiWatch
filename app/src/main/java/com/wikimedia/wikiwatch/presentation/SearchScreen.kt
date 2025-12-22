package com.wikimedia.wikiwatch.presentation

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material.*
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.HorizontalPageIndicator
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import okhttp3.OkHttpClient
import com.wikimedia.wikiwatch.R
import com.wikimedia.wikiwatch.data.SearchResult
import com.wikimedia.wikiwatch.data.WikipediaRepository
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    initialQuery: String = "",
    previousArticle: String? = null,
    autoFocus: Boolean = false,
    onArticleClick: (SearchResult) -> Unit,
    onBackToArticle: (String) -> Unit = {},
    onNearbyClick: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
    viewModel: SearchViewModel = viewModel()
) {
    var query by remember { mutableStateOf(initialQuery) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val selectedLanguageCode = WikipediaRepository.getCurrentLanguage()
    var didYouKnowEntries by remember { mutableStateOf<List<com.wikimedia.wikiwatch.data.DidYouKnowEntry>>(emptyList()) }
    var newsEntries by remember { mutableStateOf<List<com.wikimedia.wikiwatch.data.NewsEntry>>(emptyList()) }
    
    // Load DYK feed
    LaunchedEffect(Unit) {
        didYouKnowEntries = WikipediaRepository.getDidYouKnow()
    }
    
    // Load news feed
    LaunchedEffect(Unit) {
        newsEntries = WikipediaRepository.getNews()
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
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    
    // Pager state for horizontal pagination (0 = Search, 1 = DYK, 2 = News)
    val pagerState = rememberPagerState(pageCount = { 3 }, initialPage = 0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000)) // Material 3 background
    ) {
        // Horizontal pager for full-screen tiles
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> {
                    // Search results page - full screen
                    SearchResultsPage(
                        query = query,
                        onQueryChange = { query = it },
                        results = results,
                        isLoading = isLoading,
                        onArticleClick = onArticleClick,
                        onClearSearch = {
                            query = ""
                            viewModel.search("")
                        },
                        onLanguageClick = onLanguageClick,
                        onNearbyClick = onNearbyClick,
                        previousArticle = previousArticle,
                        onBackToArticle = onBackToArticle,
                        selectedLanguageCode = selectedLanguageCode,
                        focusRequester = focusRequester,
                        viewModel = viewModel,
                        context = LocalContext.current
                    )
                }
                1 -> {
                    // Did You Know page - full screen
                    DidYouKnowPage(
                        entries = didYouKnowEntries,
                        onArticleClick = onArticleClick,
                        onBackToSearch = {
                            coroutineScope.launch {
                                pagerState.scrollToPage(0)
                            }
                        }
                    )
                }
                2 -> {
                    // In the News page - full screen
                    InTheNewsPage(
                        entries = newsEntries,
                        onArticleClick = onArticleClick,
                        onBackToSearch = {
                            coroutineScope.launch {
                                pagerState.scrollToPage(0)
                            }
                        }
                    )
                }
            }
        }
        
        
        // Material 3 page indicator overlay at bottom
        HorizontalPageIndicator(
            pagerState = pagerState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
fun SearchResultsPage(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<SearchResult>,
    isLoading: Boolean,
    onArticleClick: (SearchResult) -> Unit,
    onClearSearch: () -> Unit,
    onLanguageClick: () -> Unit,
    onNearbyClick: () -> Unit,
    previousArticle: String?,
    onBackToArticle: (String) -> Unit,
    selectedLanguageCode: String,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    viewModel: SearchViewModel,
    context: Context
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    // Scroll to first result when search results appear
    LaunchedEffect(results.size) {
        if (results.isNotEmpty() && !isLoading) {
            kotlinx.coroutines.delay(100)
            with(density) {
                scrollState.animateScrollTo(0)
            }
        }
    }

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

    // Voice input launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (spokenText != null) {
                onQueryChange(spokenText)
                viewModel.search(spokenText)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            Button(
                onClick = { onBackToArticle(previousArticle) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2C2C2C) // Material 3 surface container
                )
            ) {
                Text(
                    text = "← Back to $previousArticle",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    color = Color(0xFFE0E0E0) // Material 3 onSurface
                )
            }
        }

        // Search bar with language selector, voice input, and map - Material 3 styled
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp) // Material 3 standard height for search bars
                    // Material 3 surface container color (elevated surface)
                    .background(
                        Color(0xFF2C2C2C), // Material 3 surfaceContainerHighest equivalent
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp) // Fully rounded pill shape
                    )
                    .padding(start = 16.dp), // Material 3 padding standards
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search",
                            color = Color(0xFF9E9E9E), // Material 3 onSurfaceVariant color
                            fontSize = 14.sp,
                            style = TextStyle(
                                fontWeight = FontWeight.Normal
                            )
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        textStyle = TextStyle(
                            color = Color(0xFFE0E0E0), // Material 3 onSurface color
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(Color(0xFF6BA5FF)), // Material 3 primary color for cursor
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
                
                // Language selector - pill-shaped button
                Button(
                    onClick = { onLanguageClick() },
                    modifier = Modifier.height(40.dp), // Match search bar height
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C2C2C) // Material 3 surface container
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_language),
                            contentDescription = "Language",
                            modifier = Modifier.size(20.dp),
                            colorFilter = ColorFilter.tint(Color(0xFFE0E0E0)) // Material 3 onSurface
                        )
                        Text(
                            text = selectedLanguageCode.uppercase().take(2),
                            color = Color(0xFFE0E0E0), // Material 3 onSurface
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        
        // Voice input and map buttons below search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Voice input button
            Button(
                onClick = {
                    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search Wikipedia")
                    }
                    voiceLauncher.launch(intent)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2C2C2C) // Material 3 surface container
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_mic),
                        contentDescription = "Voice search",
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(Color(0xFFE0E0E0)) // Material 3 onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Voice",
                        color = Color(0xFFE0E0E0), // Material 3 onSurface
                        fontSize = 12.sp
                    )
                }
            }
            
            // Map button
            Button(
                onClick = { onNearbyClick() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2C2C2C) // Material 3 surface container
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_map),
                        contentDescription = "Nearby articles",
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(Color(0xFFE0E0E0)) // Material 3 onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Map",
                        color = Color(0xFFE0E0E0), // Material 3 onSurface
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 16.dp)
                    .align(Alignment.CenterHorizontally),
                strokeWidth = 2.dp
            )
        }

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
                            onClearSearch()
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
                // Material 3 Button for search result
                Button(
                    onClick = { onArticleClick(result) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C2C2C) // Material 3 surface container
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Circular thumbnail
                        if (result.thumbnailUrl != null) {
                            SubcomposeAsyncImage(
                                model = result.thumbnailUrl,
                                imageLoader = imageLoader,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            // Placeholder: Wikipedia W logo on lighter grey circular background
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF999999)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.wikipedia_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFFCCCCCC))
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = result.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = Color(0xFFE0E0E0), // Material 3 onSurface
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

