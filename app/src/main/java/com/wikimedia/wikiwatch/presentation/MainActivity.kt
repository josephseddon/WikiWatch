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
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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

        when (val screen = currentScreen) {
            is Screen.Search -> SearchScreen(
                initialQuery = screen.initialQuery,
                previousArticle = screen.previousArticle,
                onArticleClick = { result ->
                    currentScreen = Screen.Article(result.title)
                },
                onBackToArticle = { title ->
                    currentScreen = Screen.Article(title)
                }
            )
            is Screen.Article -> ArticleScreen(
                title = screen.title,
                onBack = { currentScreen = Screen.Search() },
                onSwipeToNext = { nextTitle ->
                    currentScreen = Screen.Article(nextTitle)
                },
                onSearch = { query ->
                    currentScreen = Screen.Search(query, screen.title)
                },
                onOpenMap = { lat, lon ->
                    currentScreen = Screen.Map(lat, lon, screen.title, screen.title)
                }
            )
            is Screen.Map -> MapScreen(
                lat = screen.lat,
                lon = screen.lon,
                title = screen.title,
                onBack = { currentScreen = Screen.Article(screen.previousArticle) }
            )
        }
    }
}

sealed class Screen {
    data class Search(val initialQuery: String = "", val previousArticle: String? = null) : Screen()
    data class Article(val title: String) : Screen()
    data class Map(val lat: Double, val lon: Double, val title: String, val previousArticle: String) : Screen()
}

@Composable
fun SearchScreen(
    initialQuery: String = "",
    previousArticle: String? = null,
    onArticleClick: (SearchResult) -> Unit,
    onBackToArticle: (String) -> Unit = {},
    viewModel: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var query by remember { mutableStateOf(initialQuery) }
    
    // Trigger search if initial query provided
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotEmpty()) {
            viewModel.search(initialQuery)
        }
    }
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(Color(0xFF2A2A2A), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Search Wikipedia...",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { 
                    viewModel.search(query)
                    focusManager.clearFocus()
                })
            )
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
        
        results.forEach { result ->
            Chip(
                onClick = { onArticleClick(result) },
                label = {
                    Text(
                        text = result.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.Black
                    )
                },
                icon = {
                    if (result.thumbnailUrl != null) {
                        SubcomposeAsyncImage(
                            model = result.thumbnailUrl,
                            imageLoader = imageLoader,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.placeholder),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                colors = ChipDefaults.chipColors(backgroundColor = Color.White)
            )
        }
    }
}
