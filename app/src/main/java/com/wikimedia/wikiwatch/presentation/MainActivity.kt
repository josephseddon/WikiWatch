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
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
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


