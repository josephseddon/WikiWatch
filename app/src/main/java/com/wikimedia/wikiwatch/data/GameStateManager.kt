package com.wikimedia.wikiwatch.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object GameStateManager {
    private const val PREFS_NAME = "wikiwatch_game_state"
    private const val KEY_VISITED_ARTICLES = "visited_articles"
    
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun getVisitedArticles(context: Context): Set<String> {
        val prefs = getSharedPreferences(context)
        val json = prefs.getString(KEY_VISITED_ARTICLES, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<Set<String>>() {}.type
                Gson().fromJson(json, type) ?: emptySet()
            } catch (e: Exception) {
                Log.e("WikiWatch", "Error loading visited articles: ${e.message}", e)
                emptySet()
            }
        } else {
            emptySet()
        }
    }
    
    fun addVisitedArticle(context: Context, articleTitle: String) {
        val prefs = getSharedPreferences(context)
        val visited = getVisitedArticles(context).toMutableSet()
        visited.add(articleTitle)
        val json = Gson().toJson(visited)
        prefs.edit().putString(KEY_VISITED_ARTICLES, json).apply()
        Log.d("WikiWatch", "Added visited article: $articleTitle. Total: ${visited.size}")
    }
    
    fun clearVisitedArticles(context: Context) {
        val prefs = getSharedPreferences(context)
        prefs.edit().remove(KEY_VISITED_ARTICLES).apply()
        Log.d("WikiWatch", "Cleared visited articles")
    }
    
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0] // Distance in meters
    }
}


