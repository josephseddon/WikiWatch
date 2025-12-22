package com.wikimedia.wikiwatch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalContext
import com.wikimedia.wikiwatch.data.WikipediaLanguage
import com.wikimedia.wikiwatch.data.WikipediaRepository

@Composable
fun LanguageSelectionScreen(
    currentLanguageCode: String,
    onLanguageSelected: (WikipediaLanguage) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var allLanguages by remember { mutableStateOf<List<WikipediaLanguage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var filterQuery by remember { mutableStateOf("") }
    val scrollState = rememberScalingLazyListState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        allLanguages = WikipediaRepository.getLanguages(context)
        isLoading = false
    }

    // Filter languages based on query (ignore trailing spaces)
    val filteredLanguages = remember(filterQuery, allLanguages) {
        val trimmedQuery = filterQuery.trimEnd()
        if (trimmedQuery.isEmpty()) {
            allLanguages
        } else {
            val queryLower = trimmedQuery.lowercase()
            allLanguages.filter { language ->
                language.languageCode.lowercase().contains(queryLower) ||
                language.languageName.lowercase().contains(queryLower) ||
                language.localName.lowercase().contains(queryLower)
            }
        }
    }

    // Auto-scroll to current language when languages are loaded (ignore trailing spaces)
    LaunchedEffect(allLanguages.isNotEmpty(), filterQuery) {
        if (allLanguages.isNotEmpty() && filterQuery.trimEnd().isEmpty()) {
            val currentIndex = filteredLanguages.indexOfFirst { it.languageCode == currentLanguageCode }
            if (currentIndex >= 0) {
                scrollState.scrollToItem(currentIndex)
            }
        }
    }
    
    // Scroll to top when search is initiated (ignore trailing spaces)
    LaunchedEffect(filterQuery) {
        if (filterQuery.trimEnd().isNotEmpty()) {
            scrollState.scrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back button and filter input - centered
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Back button - small round button with arrow
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFF2A2A2A), shape = CircleShape)
                    .clickable { onBack() }
            ) {
                Text(
                    text = "←",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Filter input token
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(36.dp)
                    .background(Color(0xFF2A2A2A), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (filterQuery.isEmpty()) {
                    Text(
                        text = "Find language.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                BasicTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                        }
                    )
                )
            }
        }

        if (isLoading) {
            Text(
                text = "Loading languages...",
                color = Color.Gray,
                fontSize = 11.sp
            )
        } else {
            ScalingLazyColumn(
                state = scrollState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(
                    top = 0.dp,
                    bottom = 16.dp // Add bottom padding to prevent cutoff
                )
            ) {
                items(
                    items = filteredLanguages,
                    key = { it.languageCode }
                ) { language ->
                    Button(
                        onClick = { onLanguageSelected(language) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        colors = if (language.languageCode == currentLanguageCode) {
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6BA5FF) // Material 3 primary color
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2C2C2C) // Material 3 surface container
                            )
                        }
                    ) {
                        Text(
                            text = language.localName,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (language.languageCode == currentLanguageCode) Color.White else Color(0xFFE0E0E0) // Material 3 onSurface
                        )
                    }
                }
            }
        }
    }
}

