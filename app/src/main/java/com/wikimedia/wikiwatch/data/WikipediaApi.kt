package com.wikimedia.wikiwatch.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class SearchResponse(
    val query: QueryResult?
)

data class QueryResult(
    val search: List<SearchResult>?
)

data class SearchResult(
    val title: String,
    val pageid: Long,
    val snippet: String?,
    val thumbnailUrl: String? = null
)

data class SummaryResponse(
    val title: String,
    val extract: String?,
    val thumbnail: Thumbnail?,
    val originalimage: Thumbnail?,
    val content_urls: ContentUrls?
)

data class Thumbnail(
    val source: String?,
    val width: Int?,
    val height: Int?
)

data class ContentUrls(
    val mobile: MobileUrl?
)

data class MobileUrl(
    val page: String?
)

data class PrefixSearchResponse(
    val query: PrefixQueryResult?
)

data class PrefixQueryResult(
    val prefixsearch: List<PrefixSearchResult>?
)

data class PrefixSearchResult(
    val title: String,
    val pageid: Long
)

data class PageImagesResponse(
    val query: PageImagesQuery?
)

data class PageImagesQuery(
    val pages: Map<String, PageImageInfo>?
)

data class PageImageInfo(
    val pageid: Long?,
    val title: String?,
    val thumbnail: PageThumbnail?
)

data class PageThumbnail(
    val source: String?,
    val width: Int?,
    val height: Int?
)

data class PageLinksResponse(
    val query: PageLinksQuery?,
    val `continue`: PageLinksContinue?
)

data class PageLinksContinue(
    val plcontinue: String?
)

data class PageLinksQuery(
    val pages: Map<String, PageLinksInfo>?
)

data class PageLinksInfo(
    val links: List<PageLink>?
)

data class PageLink(
    val title: String
)

data class CoordinatesResponse(
    val query: CoordinatesQuery?
)

data class CoordinatesQuery(
    val pages: Map<String, CoordinatesPage>?
)

data class CoordinatesPage(
    val coordinates: List<Coordinate>?
)

data class Coordinate(
    val lat: Double,
    val lon: Double,
    val globe: String?
)

interface WikipediaApi {
    @GET("w/api.php?action=query&list=prefixsearch&format=json")
    suspend fun prefixSearch(
        @Query("pssearch") query: String,
        @Query("pslimit") limit: Int = 10
    ): PrefixSearchResponse

    @GET("w/api.php?action=query&prop=pageimages&format=json&pithumbsize=150")
    suspend fun getPageImages(
        @Query("titles") titles: String
    ): PageImagesResponse

    @GET("w/api.php?action=query&prop=links&format=json&pllimit=500")
    suspend fun getPageLinks(
        @Query("titles") title: String,
        @Query("plcontinue") continueToken: String? = null
    ): PageLinksResponse

    @GET("w/api.php?action=query&prop=coordinates&format=json")
    suspend fun getCoordinates(
        @Query("titles") title: String
    ): CoordinatesResponse

    @GET("w/api.php?action=query&list=search&format=json")
    suspend fun search(
        @Query("srsearch") query: String,
        @Query("srlimit") limit: Int = 10
    ): SearchResponse

    @GET("w/api.php?action=query&list=search&format=json")
    suspend fun moreLike(
        @Query("srsearch") query: String,
        @Query("srlimit") limit: Int = 5
    ): SearchResponse

    @GET("api/rest_v1/page/summary/{title}")
    suspend fun getSummary(
        @Path("title") title: String
    ): SummaryResponse
}
