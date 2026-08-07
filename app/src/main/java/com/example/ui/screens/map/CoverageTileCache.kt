package com.example.ui.screens.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object CoverageTileCache {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    // Map of key "z_x_y" -> Boolean (true if contains radar coverage, false if no coverage)
    private val coverageMap = ConcurrentHashMap<String, Boolean>()
    private val inFlightCoverage = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun hasCoverage(zoom: Int, x: Int, y: Int): Boolean? {
        val key = "${zoom}_${x}_${y}"
        return coverageMap[key]
    }

    fun prefetchCoverageForViewport(zoom: Int, tileXs: List<Int>, tileYs: List<Int>) {
        if (tileXs.isEmpty() || tileYs.isEmpty()) return

        var coveredCount = 0
        var uncoveredCount = 0
        val missingKeys = mutableListOf<Triple<Int, Int, Int>>()

        for (x in tileXs) {
            for (y in tileYs) {
                val res = hasCoverage(zoom, x, y)
                if (res == true) {
                    coveredCount++
                } else if (res == false) {
                    uncoveredCount++
                } else {
                    missingKeys.add(Triple(zoom, x, y))
                }
            }
        }

        if (missingKeys.isNotEmpty()) {
            scope.launch {
                var newCovered = 0
                var newUncovered = 0
                for ((z, x, y) in missingKeys) {
                    val key = "${z}_${x}_${y}"
                    if (!inFlightCoverage.add(key)) continue
                    try {
                        val url = "https://tilecache.rainviewer.com/v2/coverage/0/256/$z/$x/$y/0/0_0.png"
                        val req = Request.Builder().url(url).header("User-Agent", "SkySphereApp/1.0").build()
                        client.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val bytes = resp.body?.bytes()
                                if (bytes != null && bytes.isNotEmpty()) {
                                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    val hasPixels = bmp != null && hasNonTransparentPixels(bmp)
                                    coverageMap[key] = hasPixels
                                    if (hasPixels) newCovered++ else newUncovered++
                                } else {
                                    coverageMap[key] = false
                                    newUncovered++
                                }
                            } else {
                                coverageMap[key] = false
                                newUncovered++
                            }
                        }
                    } catch (e: Exception) {
                        // Network error: don't permanently exclude unless confirmed
                    } finally {
                        inFlightCoverage.remove(key)
                    }
                }

                val totalCandidates = tileXs.size * tileYs.size
                val totalCovered = coveredCount + newCovered
                val totalUncovered = uncoveredCount + newUncovered

                Log.d(
                    "SKYSPHERE_TIMELAPSE",
                    "RADAR_COVERAGE zoom=$zoom candidates=$totalCandidates covered=$totalCovered uncovered=$totalUncovered"
                )
            }
        } else {
            val totalCandidates = tileXs.size * tileYs.size
            Log.d(
                "SKYSPHERE_TIMELAPSE",
                "RADAR_COVERAGE zoom=$zoom candidates=$totalCandidates covered=$coveredCount uncovered=$uncoveredCount"
            )
        }
    }

    private fun hasNonTransparentPixels(bitmap: Bitmap): Boolean {
        if (bitmap.width <= 0 || bitmap.height <= 0) return false
        val stepX = (bitmap.width / 8).coerceAtLeast(1)
        val stepY = (bitmap.height / 8).coerceAtLeast(1)
        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) > 10) {
                    return true
                }
            }
        }
        return false
    }
}
