package com.example.ui.screens.map

import android.util.Log

data class RadarTilePlan(
    val frameTimestamp: Long,
    val viewportCandidates: Int,
    val coverageExcluded: Int,
    val criticalKeys: List<String>,
    val backgroundKeys: List<String>,
    val cachedRamCount: Int,
    val cachedDiskCount: Int,
    val networkRequiredKeys: List<String>
)

object RadarTilePlanner {

    fun planFrame(
        layer: MapWeatherLayer,
        frameTimestamp: Long,
        zoom: Int,
        tileXs: List<Int>,
        tileYs: List<Int>
    ): RadarTilePlan {
        if (tileXs.isEmpty() || tileYs.isEmpty()) {
            return RadarTilePlan(
                frameTimestamp = frameTimestamp,
                viewportCandidates = 0,
                coverageExcluded = 0,
                criticalKeys = emptyList(),
                backgroundKeys = emptyList(),
                cachedRamCount = 0,
                cachedDiskCount = 0,
                networkRequiredKeys = emptyList()
            )
        }

        val isRain = (layer == MapWeatherLayer.RAIN_RADAR)
        if (isRain) {
            CoverageTileCache.prefetchCoverageForViewport(zoom, tileXs, tileYs)
        }

        val candidates = mutableListOf<String>()
        val excluded = mutableListOf<String>()
        val critical = mutableListOf<String>()
        val background = mutableListOf<String>()

        var ramCount = 0
        var diskCount = 0
        val networkReq = mutableListOf<String>()

        val endpoint = when (layer) {
            MapWeatherLayer.RAIN_RADAR -> "RainViewer_Radar"
            MapWeatherLayer.CLOUDS -> "clouds_new"
            MapWeatherLayer.TEMPERATURE -> "temp_new"
            MapWeatherLayer.WIND -> "wind_new"
            MapWeatherLayer.HUMIDITY -> "humidity_new"
            MapWeatherLayer.PRESSURE -> "pressure_new"
            else -> "radar"
        }

        for (x in tileXs) {
            for (y in tileYs) {
                val candidateKey = if (isRain) {
                    "RainViewer_Radar_${frameTimestamp}_${zoom}_${x}_${y}"
                } else {
                    "${endpoint}_${zoom}_${x}_${y}"
                }

                candidates.add(candidateKey)

                // Track tiles in areas with reported no radar coverage for diagnostics, but do not exclude required viewport tiles
                if (isRain && CoverageTileCache.hasCoverage(zoom, x, y) == false) {
                    excluded.add(candidateKey)
                }

                val isCenter = RadarPreloader.isCenterTile(x, y, tileXs, tileYs)
                if (isCenter) {
                    critical.add(candidateKey)
                } else {
                    background.add(candidateKey)
                }

                if (TileRamCache.contains(candidateKey)) {
                    ramCount++
                } else if (DiskTileCache.contains(candidateKey)) {
                    diskCount++
                } else {
                    networkReq.add(candidateKey)
                }
            }
        }

        val plan = RadarTilePlan(
            frameTimestamp = frameTimestamp,
            viewportCandidates = candidates.size,
            coverageExcluded = excluded.size,
            criticalKeys = critical,
            backgroundKeys = background,
            cachedRamCount = ramCount,
            cachedDiskCount = diskCount,
            networkRequiredKeys = networkReq
        )

        Log.d(
            "SKYSPHERE_TIMELAPSE",
            "FRAME_PLAN viewportCandidates=${plan.viewportCandidates} coverageExcluded=${plan.coverageExcluded} criticalRequired=${critical.size} backgroundRequired=${background.size} ramHits=$ramCount diskHits=$diskCount networkRequired=${networkReq.size}"
        )

        Log.d(
            "SKYSPHERE_TIMELAPSE",
            "RADAR_TILE_PLAN frame=$frameTimestamp viewportCandidates=${plan.viewportCandidates} coverageExcluded=${plan.coverageExcluded} critical=${critical.size} background=${background.size} cachedRam=$ramCount cachedDisk=$diskCount networkRequired=${networkReq.size}"
        )

        return plan
    }

    /**
     * Plan critical playback keys for active playback window (previous, current, next, lookahead frames).
     */
    fun planPlaybackWindowCriticalKeys(
        layer: MapWeatherLayer,
        activeWindowFrames: List<TimeLapseFrame>,
        zoom: Int,
        tileXs: List<Int>,
        tileYs: List<Int>
    ): Set<String> {
        val criticalWindowKeys = mutableSetOf<String>()
        for (frame in activeWindowFrames) {
            val ts = frame.radarFrame?.time ?: frame.timestamp
            val plan = planFrame(layer, ts, zoom, tileXs, tileYs)
            criticalWindowKeys.addAll(plan.criticalKeys)
        }
        return criticalWindowKeys
    }
}
