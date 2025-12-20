package com.wikimedia.wikiwatch.presentation

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.wikimedia.wikiwatch.data.GeoSearchResult
import org.maplibre.android.maps.MapView

class NearbyMapViewModel : ViewModel() {
    // Persist map state across navigation
    var nearbyArticles: List<GeoSearchResult> = emptyList()
        private set
    
    var thumbnailBitmaps: Map<String, Bitmap> = emptyMap()
        private set
    
    var initialGeosearchDone: Boolean = false
        private set
    
    var styleLoaded: Boolean = false
        private set
    
    var markersInitiallyAdded: Boolean = false
        private set
    
    var initialCameraSetup: Boolean = false
        private set
    
    // Keep MapView instance alive across navigation
    var mapViewInstance: MapView? = null
        set(value) {
            field = value
            android.util.Log.d("WikiWatch", "NearbyMapViewModel: MapView instance ${if (value != null) "set" else "cleared"}")
        }
    
    fun setNearbyArticles(articles: List<GeoSearchResult>) {
        nearbyArticles = articles
    }
    
    fun setThumbnailBitmaps(bitmaps: Map<String, Bitmap>) {
        thumbnailBitmaps = bitmaps
    }
    
    fun setInitialGeosearchDone(done: Boolean) {
        initialGeosearchDone = done
    }
    
    fun setStyleLoaded(loaded: Boolean) {
        styleLoaded = loaded
    }
    
    fun setMarkersInitiallyAdded(added: Boolean) {
        markersInitiallyAdded = added
    }
    
    fun setInitialCameraSetup(setup: Boolean) {
        initialCameraSetup = setup
    }
    
    fun clearState() {
        nearbyArticles = emptyList()
        thumbnailBitmaps = emptyMap()
        initialGeosearchDone = false
        styleLoaded = false
        markersInitiallyAdded = false
        initialCameraSetup = false
    }
}

