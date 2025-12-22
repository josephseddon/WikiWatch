package com.wikimedia.wikiwatch.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object WikipediaRepository {
    private var currentLanguageCode = "en"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            Log.d("WikiWatch", "WikipediaRepository: Making request to ${request.url}")
            val startTime = System.currentTimeMillis()
            try {
                val response = chain.proceed(request)
                val endTime = System.currentTimeMillis()
                Log.d("WikiWatch", "WikipediaRepository: Response ${response.code} from ${request.url} in ${endTime - startTime}ms")
                response
            } catch (e: Exception) {
                val endTime = System.currentTimeMillis()
                Log.e("WikiWatch", "WikipediaRepository: Request failed to ${request.url} after ${endTime - startTime}ms: ${e.message}", e)
                throw e
            }
        }
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "WikiWatch/1.0 (WearOS App; contact@example.com)")
                .build()
            chain.proceed(request)
        }
        .build()

    private fun getApi(languageCode: String = currentLanguageCode): WikipediaApi {
        return Retrofit.Builder()
            .baseUrl("https://$languageCode.wikipedia.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WikipediaApi::class.java)
    }
    
    private val api: WikipediaApi
        get() = getApi()
    
    fun setLanguage(languageCode: String) {
        currentLanguageCode = languageCode
    }
    
    fun getCurrentLanguage(): String = currentLanguageCode
    
    suspend fun getLanguages(context: Context): List<WikipediaLanguage> = withContext(Dispatchers.IO) {
        try {
            Log.d("WikiWatch", "Loading languages from assets")
            val inputStream = context.assets.open("wikipedia-languages.json")
            val json = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()
            
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<WikipediaLanguage>>() {}.type
            val languages = gson.fromJson<List<WikipediaLanguage>>(json, type) ?: emptyList()
            
            Log.d("WikiWatch", "Loaded ${languages.size} languages from assets")
            languages
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("WikiWatch", "Failed to load languages from assets: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            // Try prefix search first
            val prefixResponse = api.prefixSearch(query)
            val prefixResults = prefixResponse.query?.prefixsearch
            val results = if (!prefixResults.isNullOrEmpty()) {
                Log.d("WikiWatch", "PrefixSearch for '$query': ${prefixResults.size} results")
                prefixResults.map { SearchResult(it.title, it.pageid, null) }
            } else {
                // Fall back to full text search
                val response = api.search(query)
                Log.d("WikiWatch", "Search for '$query': ${response.query?.search?.size ?: 0} results")
                response.query?.search ?: emptyList()
            }
            
            // Fetch thumbnails using the same API properties as the lead image (REST API summary)
            if (results.isNotEmpty()) {
                val resultsWithThumbnails = results.map { result ->
                    try {
                        val encodedTitle = result.title.replace(" ", "_")
                        val summaryResponse = getApi().getSummary(encodedTitle)
                        val thumbnailUrl = summaryResponse?.thumbnail?.source ?: summaryResponse?.originalimage?.source
                        result.copy(thumbnailUrl = thumbnailUrl)
                    } catch (e: Exception) {
                        Log.e("WikiWatch", "Failed to get thumbnail for ${result.title}: ${e.message}")
                        result
                    }
                }
                resultsWithThumbnails
            } else {
                results
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("WikiWatch", "Search error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun moreLike(title: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val response = api.moreLike("morelike:$title")
            Log.d("WikiWatch", "MoreLike for '$title': ${response.query?.search?.size ?: 0} results")
            val results = response.query?.search ?: emptyList()
            
            // Fetch thumbnails for results
            if (results.isNotEmpty()) {
                val titles = results.joinToString("|") { it.title }
                val imagesResponse = api.getPageImages(titles)
                val thumbnails = imagesResponse.query?.pages?.values?.associate { 
                    it.title to it.thumbnail?.source 
                } ?: emptyMap()
                
                results.map { result ->
                    result.copy(thumbnailUrl = thumbnails[result.title])
                }
            } else {
                results
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("WikiWatch", "MoreLike error: ${e.message}", e)
            emptyList()
        }
    }

    fun getArticleUrl(title: String): String {
        val encodedTitle = title.replace(" ", "_")
        return "https://en.wikipedia.org/api/rest_v1/page/mobile-html/$encodedTitle"
    }

    suspend fun getSummary(title: String): SummaryResponse? = withContext(Dispatchers.IO) {
        try {
            val encodedTitle = title.replace(" ", "_")
            api.getSummary(encodedTitle)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("WikiWatch", "Summary error: ${e.message}", e)
            null
        }
    }

    suspend fun getCoordinates(title: String): Coordinate? = withContext(Dispatchers.IO) {
        try {
            // URL encode the title for the API call (same as getSummary)
            val encodedTitle = title.replace(" ", "_")
            val response = api.getCoordinates(encodedTitle)
            val coordinate = response.query?.pages?.values?.firstOrNull()?.coordinates?.firstOrNull()
            if (coordinate?.globe == "earth") {
                Log.d("WikiWatch", "Coordinates for '$title': ${coordinate.lat}, ${coordinate.lon}, globe: ${coordinate.globe}")
                coordinate
            } else {
                Log.d("WikiWatch", "Coordinates for '$title': globe is '${coordinate?.globe}', not 'earth'")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("WikiWatch", "Coordinates error for '$title': ${e.message}", e)
            null
        }
    }

    suspend fun getPageLinks(title: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val allLinks = mutableListOf<String>()
            var continueToken: String? = null
            
            do {
                val response = api.getPageLinks(title, continueToken)
                val links = response.query?.pages?.values?.firstOrNull()?.links?.map { it.title }
                if (links != null) {
                    allLinks.addAll(links)
                }
                continueToken = response.`continue`?.plcontinue
                Log.d("WikiWatch", "Fetched ${links?.size ?: 0} links, continue: $continueToken")
            } while (continueToken != null)
            
            Log.d("WikiWatch", "Total links for '$title': ${allLinks.size}")
            allLinks
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("WikiWatch", "Links error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun geoSearch(lat: Double, lon: Double): List<GeoSearchResult> = withContext(Dispatchers.IO) {
        Log.d("WikiWatch", "WikipediaRepository: geoSearch coroutine started on ${Thread.currentThread().name}")
        try {
            Log.d("WikiWatch", "WikipediaRepository: Starting geoSearch for $lat|$lon")
            
            // Add timeout to prevent hanging
            val response = withTimeout(30000L) { // 30 second timeout
                Log.d("WikiWatch", "WikipediaRepository: Calling api.geoSearch")
                api.geoSearch("$lat|$lon")
            }
            
            Log.d("WikiWatch", "WikipediaRepository: api.geoSearch completed, parsing response")
            val pagesMap = response.query?.pages
            Log.d("WikiWatch", "WikipediaRepository: Pages map size: ${pagesMap?.size ?: 0}")
            
            if (pagesMap == null || pagesMap.isEmpty()) {
                Log.d("WikiWatch", "WikipediaRepository: No pages found in geosearch response")
                return@withContext emptyList()
            }
            
            // Log details about each page to debug coordinate parsing
            pagesMap.values.forEach { page ->
                val coordsSize = page.coordinates?.size ?: 0
                val hasCoords = coordsSize > 0
                Log.d("WikiWatch", "WikipediaRepository: Page '${page.title}' (pageid: ${page.pageid}) - coordinates list: ${if (page.coordinates == null) "null" else "not null"}, size: $coordsSize, has thumbnail: ${page.thumbnail != null}")
                if (hasCoords) {
                    page.coordinates?.forEachIndexed { index, coord ->
                        Log.d("WikiWatch", "WikipediaRepository:   Coordinate[$index]: lat=${coord.lat}, lon=${coord.lon}, globe=${coord.globe}")
                    }
                } else {
                    Log.w("WikiWatch", "WikipediaRepository:   WARNING: No coordinates found for '${page.title}' (pageid: ${page.pageid}) - coordinates field is ${if (page.coordinates == null) "null" else "empty list"}")
                }
            }
            
            // Convert pages to GeoSearchResult list
            // With generator=geosearch, all data (coordinates, thumbnails, descriptions) is in pages
            val results = pagesMap.values.mapNotNull { page ->
                if (page.title == null) {
                    Log.w("WikiWatch", "WikipediaRepository: Skipping page with null title (pageid: ${page.pageid})")
                    return@mapNotNull null
                }
                
                val coord = page.coordinates?.firstOrNull()
                if (coord != null) {
                    // Distance not provided by generator, so we'll calculate it if needed later
                    GeoSearchResult(
                        pageid = page.pageid ?: 0L,
                        title = page.title,
                        lat = coord.lat,
                        lon = coord.lon,
                        dist = 0.0, // Distance not provided by generator
                        thumbnailUrl = page.thumbnail?.source,
                        description = page.description
                    )
                } else {
                    Log.w("WikiWatch", "WikipediaRepository: Skipping page '${page.title}' (pageid: ${page.pageid}) - coordinates are missing (coordinates field: ${if (page.coordinates == null) "null" else "empty"})")
                    null
                }
            }
            
            val articlesWithThumbnails = results.count { it.thumbnailUrl != null }
            Log.d("WikiWatch", "WikipediaRepository: GeoSearch complete with ${results.size} articles, ${articlesWithThumbnails} have thumbnails")
            if (articlesWithThumbnails > 0) {
                Log.d("WikiWatch", "WikipediaRepository: Sample article with thumbnail: ${results.firstOrNull { it.thumbnailUrl != null }?.title}")
            }
            results
        } catch (e: TimeoutCancellationException) {
            Log.e("WikiWatch", "WikipediaRepository: GeoSearch timed out after 30 seconds", e)
            emptyList()
        } catch (e: CancellationException) {
            Log.e("WikiWatch", "WikipediaRepository: GeoSearch was cancelled", e)
            throw e
        } catch (e: Exception) {
            Log.e("WikiWatch", "WikipediaRepository: GeoSearch error: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun testNetwork(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("WikiWatch", "WikipediaRepository: Testing network connectivity...")
            val testClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            
            val request = okhttp3.Request.Builder()
                .url("https://www.wikipedia.org")
                .build()
            
            Log.d("WikiWatch", "WikipediaRepository: Making test request to https://www.wikipedia.org")
            val response = testClient.newCall(request).execute()
            val success = response.isSuccessful
            Log.d("WikiWatch", "WikipediaRepository: Network test result: $success (code: ${response.code}, message: ${response.message})")
            response.close()
            success
        } catch (e: Exception) {
            Log.e("WikiWatch", "WikipediaRepository: Network test failed: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }

    suspend fun getDidYouKnow(): List<com.wikimedia.wikiwatch.data.DidYouKnowEntry> = withContext(Dispatchers.IO) {
        try {
            Log.d("WikiWatch", "WikipediaRepository: Starting getDidYouKnow")
            // Add timeout to prevent hanging on DNS resolution failures
            val response = withTimeout(10000L) { // 10 second timeout
                api.getDidYouKnow()
            }
            Log.d("WikiWatch", "WikipediaRepository: Fetched ${response.size} DYK entries")
            response
        } catch (e: TimeoutCancellationException) {
            Log.e("WikiWatch", "WikipediaRepository: getDidYouKnow timed out after 10 seconds", e)
            emptyList()
        } catch (e: CancellationException) {
            Log.e("WikiWatch", "WikipediaRepository: getDidYouKnow was cancelled", e)
            throw e
        } catch (e: Exception) {
            Log.e("WikiWatch", "WikipediaRepository: getDidYouKnow error: ${e.message}", e)
            emptyList()
        }
    }
    
    suspend fun getNews(): List<com.wikimedia.wikiwatch.data.NewsEntry> = withContext(Dispatchers.IO) {
        try {
            Log.d("WikiWatch", "WikipediaRepository: Starting getNews")
            // Get today's date for the featured feed
            val calendar = java.util.Calendar.getInstance()
            val year = calendar.get(java.util.Calendar.YEAR).toString()
            val month = String.format("%02d", calendar.get(java.util.Calendar.MONTH) + 1) // Month is 0-based
            val day = String.format("%02d", calendar.get(java.util.Calendar.DAY_OF_MONTH))
            
            val response = withTimeout(10000L) { // 10 second timeout
                api.getNews(year, month, day)
            }
            val newsEntries = response.news ?: emptyList()
            Log.d("WikiWatch", "WikipediaRepository: Fetched ${newsEntries.size} news entries")
            newsEntries
        } catch (e: TimeoutCancellationException) {
            Log.e("WikiWatch", "WikipediaRepository: getNews timed out after 10 seconds", e)
            emptyList()
        } catch (e: CancellationException) {
            Log.e("WikiWatch", "WikipediaRepository: getNews was cancelled", e)
            throw e
        } catch (e: Exception) {
            Log.e("WikiWatch", "WikipediaRepository: getNews error: ${e.message}", e)
            emptyList()
        }
    }
}

