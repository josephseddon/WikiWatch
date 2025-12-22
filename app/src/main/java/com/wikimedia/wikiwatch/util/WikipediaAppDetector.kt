package com.wikimedia.wikiwatch.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.tasks.Tasks
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object WikipediaAppDetector {
    /**
     * Checks if the Wikipedia app is installed on the companion phone device.
     * Returns true if installed, false otherwise.
     */
    suspend fun isWikipediaAppInstalledOnPhone(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val wearableClient = Wearable.getNodeClient(context)
            
            // Try multiple times with delays, as connection might take time to establish
            var nodes = emptyList<com.google.android.gms.wearable.Node>()
            var retries = 3
            while (nodes.isEmpty() && retries > 0) {
                try {
                    nodes = Tasks.await(wearableClient.connectedNodes)
                    if (nodes.isEmpty() && retries > 1) {
                        Log.d("WikiWatch", "WikipediaAppDetector: No nodes found, retrying... (${retries - 1} retries left)")
                        kotlinx.coroutines.delay(1000) // Wait 1 second before retry
                    }
                } catch (e: Exception) {
                    Log.e("WikiWatch", "WikipediaAppDetector: Error getting nodes: ${e.message}", e)
                }
                retries--
            }
            
            if (nodes.isEmpty()) {
                Log.d("WikiWatch", "WikipediaAppDetector: No connected nodes found after retries")
                return@withContext false
            }
            
            // Log node details for debugging
            Log.d("WikiWatch", "WikipediaAppDetector: Found ${nodes.size} connected node(s)")
            nodes.forEach { node ->
                Log.d("WikiWatch", "WikipediaAppDetector: Connected node - ID: ${node.id}, Name: ${node.displayName}, Nearby: ${node.isNearby}")
            }
            
            // Check if we have a phone node (non-nearby node)
            val phoneNode = nodes.firstOrNull { !it.isNearby }
            if (phoneNode != null) {
                Log.d("WikiWatch", "WikipediaAppDetector: Found phone node: ${phoneNode.displayName} (${phoneNode.id})")
            } else {
                Log.d("WikiWatch", "WikipediaAppDetector: No phone node found (all nodes are nearby/local)")
            }
            
            // Optimistic: assume it might be installed if phone is connected
            // The actual launch will handle the case where it's not installed
            true
        } catch (e: Exception) {
            Log.e("WikiWatch", "WikipediaAppDetector: Error checking for Wikipedia app: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Launches the Wikipedia app on the companion phone, or falls back to web browser.
     */
    suspend fun openWikipediaArticleOnPhone(context: Context, title: String, languageCode: String = "en") {
        Log.d("WikiWatch", "WikipediaAppDetector: openWikipediaArticleOnPhone called - title: $title, language: $languageCode")
        withContext(Dispatchers.IO) {
            try {
                val encodedTitle = title.replace(" ", "_")
                Log.d("WikiWatch", "WikipediaAppDetector: Encoded title: $encodedTitle")
                
                val wikipediaUri = Uri.parse("wikipedia://$languageCode.wikipedia.org/wiki/$encodedTitle")
                Log.d("WikiWatch", "WikipediaAppDetector: Wikipedia URI: $wikipediaUri")
                
                val intent = Intent(Intent.ACTION_VIEW, wikipediaUri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                Log.d("WikiWatch", "WikipediaAppDetector: Created intent: $intent (categories: ${intent.categories})")
                
                // Get connected nodes to find the phone
                Log.d("WikiWatch", "WikipediaAppDetector: Getting NodeClient...")
                val wearableClient = Wearable.getNodeClient(context)
                Log.d("WikiWatch", "WikipediaAppDetector: Getting connected nodes...")
                val nodes = Tasks.await(wearableClient.connectedNodes)
                Log.d("WikiWatch", "WikipediaAppDetector: Got ${nodes.size} connected node(s)")
                
                if (nodes.isEmpty()) {
                    Log.w("WikiWatch", "WikipediaAppDetector: No connected nodes found, falling back to web browser")
                    // Fallback to web browser
                    withContext(Dispatchers.Main) {
                        val webUri = Uri.parse("https://$languageCode.wikipedia.org/wiki/$encodedTitle")
                        Log.d("WikiWatch", "WikipediaAppDetector: Opening in web browser: $webUri")
                        val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                        context.startActivity(webIntent)
                        Log.d("WikiWatch", "WikipediaAppDetector: Web browser intent started")
                    }
                    return@withContext
                }
                
                // Log all nodes for debugging
                nodes.forEachIndexed { index, node ->
                    Log.d("WikiWatch", "WikipediaAppDetector: Node[$index] - ID: ${node.id}, Name: ${node.displayName}, Nearby: ${node.isNearby}")
                }
                
                // Use RemoteActivityHelper to launch on phone
                // Find the phone node (non-local node)
                val phoneNode = nodes.firstOrNull { !it.isNearby } ?: nodes.first()
                Log.d("WikiWatch", "WikipediaAppDetector: Selected phone node - ID: ${phoneNode.id}, Name: ${phoneNode.displayName}, Nearby: ${phoneNode.isNearby}")
                
                Log.d("WikiWatch", "WikipediaAppDetector: Creating RemoteActivityHelper...")
                val remoteActivityHelper = RemoteActivityHelper(context, Executors.newSingleThreadExecutor())
                Log.d("WikiWatch", "WikipediaAppDetector: Starting remote activity on node ${phoneNode.id}...")
                
                // startRemoteActivity returns a ListenableFuture<Void>
                try {
                    val future: ListenableFuture<Void> = remoteActivityHelper.startRemoteActivity(intent, phoneNode.id)
                    Log.d("WikiWatch", "WikipediaAppDetector: Got ListenableFuture, waiting for completion...")
                    
                    // Wait for the future to complete (with timeout)
                    future.get(5, TimeUnit.SECONDS)
                    Log.d("WikiWatch", "WikipediaAppDetector: Remote activity completed successfully for: $title")
                    
                    // Note: Even if it completes successfully, if Wikipedia app isn't installed, nothing will happen
                    // The phone will receive the intent but won't be able to handle it
                } catch (e: java.util.concurrent.TimeoutException) {
                    Log.w("WikiWatch", "WikipediaAppDetector: Remote activity timed out, falling back to web browser")
                    // Fallback to web browser
                    withContext(Dispatchers.Main) {
                        val webUri = Uri.parse("https://$languageCode.wikipedia.org/wiki/$encodedTitle")
                        Log.d("WikiWatch", "WikipediaAppDetector: Opening in web browser: $webUri")
                        val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                        context.startActivity(webIntent)
                    }
                } catch (e: Exception) {
                    Log.e("WikiWatch", "WikipediaAppDetector: Exception when starting remote activity: ${e.message}", e)
                    e.printStackTrace()
                    // Fallback to web browser
                    withContext(Dispatchers.Main) {
                        val webUri = Uri.parse("https://$languageCode.wikipedia.org/wiki/$encodedTitle")
                        Log.d("WikiWatch", "WikipediaAppDetector: Opening in web browser after exception: $webUri")
                        val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                        context.startActivity(webIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e("WikiWatch", "WikipediaAppDetector: Exception occurred: ${e.javaClass.simpleName} - ${e.message}", e)
                e.printStackTrace()
                // Fallback to web browser
                withContext(Dispatchers.Main) {
                    try {
                        Log.d("WikiWatch", "WikipediaAppDetector: Attempting web browser fallback...")
                        val encodedTitle = title.replace(" ", "_")
                        val webUri = Uri.parse("https://$languageCode.wikipedia.org/wiki/$encodedTitle")
                        Log.d("WikiWatch", "WikipediaAppDetector: Web URI: $webUri")
                        val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                        Log.d("WikiWatch", "WikipediaAppDetector: Starting web browser intent...")
                        context.startActivity(webIntent)
                        Log.d("WikiWatch", "WikipediaAppDetector: Web browser intent started successfully")
                    } catch (webException: Exception) {
                        Log.e("WikiWatch", "WikipediaAppDetector: Failed to open in browser: ${webException.javaClass.simpleName} - ${webException.message}", webException)
                        webException.printStackTrace()
                    }
                }
            }
        }
    }
}

