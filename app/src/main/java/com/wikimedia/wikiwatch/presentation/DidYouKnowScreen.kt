package com.wikimedia.wikiwatch.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import com.wikimedia.wikiwatch.R
import com.wikimedia.wikiwatch.data.SearchResult

@Composable
fun DidYouKnowPage(
    entries: List<com.wikimedia.wikiwatch.data.DidYouKnowEntry>,
    onArticleClick: (SearchResult) -> Unit,
    onBackToSearch: () -> Unit = {}
) {
    val scrollState = rememberScalingLazyListState()
    
    // Scroll to first item when entries are loaded to center it
    LaunchedEffect(entries.isNotEmpty()) {
        if (entries.isNotEmpty()) {
            scrollState.scrollToItem(0)
        }
    }
    
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            // Loading or no DYK entries
            androidx.wear.compose.material.Text(
                text = "Loading...",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with logo and title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.wikipedia_logo),
                    contentDescription = "Wikipedia",
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onBackToSearch() }
                        .padding(bottom = 4.dp)
                )
                androidx.wear.compose.material.Text(
                    text = "Did you know",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            ScalingLazyColumn(
                state = scrollState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    top = 0.dp,
                    end = 8.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = entries,
                    key = { it.hashCode() }
                ) { entry ->
                    Card(
                        onClick = { /* No action needed for DYK card */ },
                        modifier = Modifier
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1A1A1A)
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                    ) {
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
                                    onArticleClick(SearchResult(title, 0, null))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

