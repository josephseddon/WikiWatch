package com.wikimedia.wikiwatch.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object WikipediaRepository {
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "WikiWatch/1.0 (WearOS App; contact@example.com)")
                .build()
            chain.proceed(request)
        }
        .build()

    private val api: WikipediaApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://en.wikipedia.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WikipediaApi::class.java)
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
            val response = api.getCoordinates(title)
            val coordinate = response.query?.pages?.values?.firstOrNull()?.coordinates?.firstOrNull()
            if (coordinate?.globe == "earth") {
                Log.d("WikiWatch", "Coordinates for '$title': ${coordinate.lat}, ${coordinate.lon}")
                coordinate
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("WikiWatch", "Coordinates error: ${e.message}", e)
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
}

