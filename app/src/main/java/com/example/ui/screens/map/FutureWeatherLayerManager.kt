package com.example.ui.screens.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

object RadarTileFetcher {
    private val semaphore = Semaphore(4) // Rate limit to max 4 concurrent network tile downloads
    private val inFlightTasks = ConcurrentHashMap<String, FutureTask<Bitmap?>>()

    fun isInFlight(cacheKey: String): Boolean {
        return inFlightTasks.containsKey(cacheKey)
    }

    fun getInFlightCount(): Int = inFlightTasks.size

    fun fetchOrDeduplicateTile(
        cacheKey: String,
        fetcher: () -> Bitmap?
    ): Bitmap? {
        RadarDiag.visibleTileRequests.incrementAndGet()

        // 1. Check RAM Cache
        val ramCached = TileRamCache.get(cacheKey)
        if (ramCached != null && !ramCached.isRecycled) {
            RadarDiag.logRamCacheHit(cacheKey)
            return ramCached
        }

        // 2. Check Local Disk Cache
        val diskCached = DiskTileCache.get(cacheKey)
        if (diskCached != null && !diskCached.isRecycled) {
            RadarDiag.logDiskCacheHit(cacheKey)
            TileRamCache.put(cacheKey, diskCached)
            return diskCached
        }

        // 3. Deduplicate in-flight requests (prevent duplicate tile downloads)
        val enqueueTimeMs = System.currentTimeMillis()
        val newTask = FutureTask<Bitmap?> {
            semaphore.acquire()
            val queueWaitMs = System.currentTimeMillis() - enqueueTimeMs
            RadarDiag.recordQueueWait(queueWaitMs)
            RadarDiag.downloadQueueSize.decrementAndGet()
            RadarDiag.concurrentDownloadCount.incrementAndGet()
            try {
                val bitmap = fetcher()
                if (bitmap != null) {
                    TileRamCache.put(cacheKey, bitmap)
                    DiskTileCache.put(cacheKey, bitmap)
                }
                bitmap
            } finally {
                RadarDiag.concurrentDownloadCount.decrementAndGet()
                semaphore.release()
            }
        }

        val existingTask = inFlightTasks.putIfAbsent(cacheKey, newTask)
        if (existingTask != null) {
            RadarDiag.logDuplicateRequest(cacheKey)
            Log.d("SKYSPHERE_TIMELAPSE", "DUPLICATE_TILE_REQUEST key=$cacheKey existingRequest=true")
            Log.d("SKYSPHERE_TIMELAPSE", "TILE_REQUEST_DEDUP key=$cacheKey")
            return try {
                existingTask.get(10, TimeUnit.SECONDS)
            } catch (e: Exception) {
                null
            }
        }

        RadarDiag.uniqueTileRequests.incrementAndGet()
        RadarDiag.actualNetworkDownloads.incrementAndGet()
        RadarDiag.downloadQueueSize.incrementAndGet()

        return try {
            newTask.run()
            newTask.get(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            null
        } finally {
            inFlightTasks.remove(cacheKey)
        }
    }
}

fun org.osmdroid.views.overlay.OverlayManager.refresh() {
    // Extension function for OSMDroid OverlayManager refresh
}

class WeatherTilesOverlay(
    val pTileProvider: MapTileProviderBase,
    val pContext: Context,
    val moduleProvider: MapTileModuleProviderBase?
) : TilesOverlay(pTileProvider, pContext) {

    fun updateFrame(frame: TimeLapseFrame?, mapView: MapView? = null) {
        if (moduleProvider is RainRadarTileModuleProvider) {
            moduleProvider.customRadarFrame = frame?.radarFrame
        } else if (moduleProvider is OwmTileModuleProvider) {
            moduleProvider.currentFrame = frame
        }

        val overlayId = System.identityHashCode(this)
        val timestamp = frame?.radarFrame?.time ?: frame?.timestamp ?: 0L
        val sampleUrl = if (moduleProvider is RainRadarTileModuleProvider && frame?.radarFrame != null) {
            val pZoom = mapView?.zoomLevelDouble?.toInt()?.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM) ?: 5
            frame.radarFrame.buildTileUrl(pZoom, 0, 0)
        } else if (moduleProvider is OwmTileModuleProvider) {
            val pZoom = mapView?.zoomLevelDouble?.toInt()?.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM) ?: 5
            "https://tile.openweathermap.org/map/${moduleProvider.layerEndpoint}/$pZoom/0/0.png"
        } else "N/A"

        Log.d(
            "TimelapsePipeline",
            "FRAME_ADVANCE | FrameIndex: ${frame?.index} | Timestamp: $timestamp | TileURL: $sampleUrl | OverlayID: $overlayId | Layer: ${moduleProvider?.javaClass?.simpleName}"
        )

        val tileSource = when (moduleProvider) {
            is RainRadarTileModuleProvider -> moduleProvider.pTileSource
            is OwmTileModuleProvider -> moduleProvider.pTileSource
            else -> null
        }
        if (tileSource != null) {
            pTileProvider.setTileSource(tileSource)
        }

        mapView?.overlayManager?.refresh()
        RadarDiag.logMapInvalidate("WeatherTilesOverlay.updateFrame")
        mapView?.postInvalidate()

        // Clear osmdroid's tileCache so old frame tiles are not retained
        RadarDiag.logCacheClear("OSMDroid pTileProvider.tileCache", "Frame update (${frame?.index})")
        pTileProvider.clearTileCache()

        // Pre-populate osmdroid's tileCache for visible tiles if already present in RAM/Disk cache
        var tilesSubmitted = 0
        var tilesActuallyRendered = 0
        var missingAtRender = 0

        if (frame != null && mapView != null) {
            try {
                val mapZoom = mapView.zoomLevelDouble.toInt()
                val center = mapView.mapCenter
                val centerLat = center.latitude
                val centerLon = center.longitude

                val providerMaxZoom = if (moduleProvider is RainRadarTileModuleProvider) {
                    FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM
                } else {
                    FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM
                }

                if (mapZoom <= providerMaxZoom) {
                    val (tileXs, tileYs) = RadarPreloader.computeViewportTileBounds(mapView, centerLat, centerLon, mapZoom)
                    tilesSubmitted = tileXs.size * tileYs.size
                    for (x in tileXs) {
                        for (y in tileYs) {
                            val cacheKey = if (moduleProvider is RainRadarTileModuleProvider) {
                                val time = frame.radarFrame?.time ?: frame.timestamp
                                "RainViewer_Radar_${time}_${mapZoom}_${x}_${y}"
                            } else if (moduleProvider is OwmTileModuleProvider) {
                                "${moduleProvider.layerEndpoint}_${mapZoom}_${x}_${y}"
                            } else null

                            if (cacheKey != null) {
                                val bitmap = TileRamCache.get(cacheKey) ?: DiskTileCache.get(cacheKey)
                                if (bitmap != null && bitmap != RadarPreloader.emptyTransparentBitmap && !bitmap.isRecycled) {
                                    tilesActuallyRendered++
                                    val pMapTileIndex = MapTileIndex.getTileIndex(mapZoom, x, y)
                                    val drawable = BitmapDrawable(pContext.resources, bitmap)
                                    pTileProvider.tileCache.putTile(pMapTileIndex, drawable)
                                } else {
                                    missingAtRender++
                                }
                            }
                        }
                    }
                } else {
                    val deltaZ = mapZoom - providerMaxZoom
                    val (tileXs, tileYs) = RadarPreloader.computeViewportTileBounds(mapView, centerLat, centerLon, mapZoom)
                    tilesSubmitted = tileXs.size * tileYs.size
                    for (x in tileXs) {
                        for (y in tileYs) {
                            val parentX = x shr deltaZ
                            val parentY = y shr deltaZ
                            val parentKey = if (moduleProvider is RainRadarTileModuleProvider) {
                                val time = frame.radarFrame?.time ?: frame.timestamp
                                "RainViewer_Radar_${time}_${providerMaxZoom}_${parentX}_${parentY}"
                            } else if (moduleProvider is OwmTileModuleProvider) {
                                "${moduleProvider.layerEndpoint}_${providerMaxZoom}_${parentX}_${parentY}"
                            } else null

                            if (parentKey != null) {
                                val parentBmp = TileRamCache.get(parentKey) ?: DiskTileCache.get(parentKey)
                                if (parentBmp != null && parentBmp != RadarPreloader.emptyTransparentBitmap && !parentBmp.isRecycled) {
                                    try {
                                        val scale = 1 shl deltaZ
                                        val subSize = (256 / scale).coerceAtLeast(1)
                                        val offsetX = ((x and (scale - 1)) * 256 / scale).coerceIn(0, 255)
                                        val offsetY = ((y and (scale - 1)) * 256 / scale).coerceIn(0, 255)
                                        val cropWidth = minOf(subSize, parentBmp.width - offsetX)
                                        val cropHeight = minOf(subSize, parentBmp.height - offsetY)

                                        if (cropWidth > 0 && cropHeight > 0) {
                                            tilesActuallyRendered++
                                            val cropped = Bitmap.createBitmap(parentBmp, offsetX, offsetY, cropWidth, cropHeight)
                                            val scaledBitmap = Bitmap.createScaledBitmap(cropped, 256, 256, true)
                                            val drawable = BitmapDrawable(pContext.resources, scaledBitmap)
                                            val pMapTileIndex = MapTileIndex.getTileIndex(mapZoom, x, y)
                                            pTileProvider.tileCache.putTile(pMapTileIndex, drawable)
                                        } else {
                                            missingAtRender++
                                        }
                                    } catch (e: Exception) {
                                        missingAtRender++
                                    }
                                } else {
                                    missingAtRender++
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("TimelapsePipeline", "Error pre-populating osmdroid tileCache: ${e.localizedMessage}")
            }
        }

        Log.d(
            "SKYSPHERE_TIMELAPSE",
            "FRAME_RENDER timestamp=$timestamp frameIndex=${frame?.index ?: -1} tilesSubmitted=$tilesSubmitted tilesActuallyRendered=$tilesActuallyRendered missingAtRender=$missingAtRender"
        )

        mapView?.postInvalidate()

        Log.d(
            "TimelapsePipeline",
            "MAP_INVALIDATE | FrameIndex: ${frame?.index} | Timestamp: $timestamp | OverlayID: $overlayId | Redraw event dispatched"
        )
        Log.d("RadarDebug", "REDRAW: PostInvalidate called for frame ${frame?.index} with URL sample: $sampleUrl")
    }

    fun setPlaybackActive(active: Boolean) {
        if (moduleProvider is RainRadarTileModuleProvider) {
            moduleProvider.isPlaybackActive = active
        } else if (moduleProvider is OwmTileModuleProvider) {
            moduleProvider.isPlaybackActive = active
        }
    }
}

class FutureWeatherLayerManager(
    private val radarRepository: FutureRadarRepository = FutureRadarRepository()
) {

    companion object {
        const val PROVIDER_MIN_ZOOM = 1
        const val RAIN_RADAR_PROVIDER_MAX_ZOOM = 12
        const val OWM_PROVIDER_MAX_ZOOM = 6
        const val PROVIDER_MAX_ZOOM = 12
        const val OVERLAY_MAX_ZOOM = 20

        val emptyTransparentTile: Drawable by lazy {
            val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            BitmapDrawable(null, bitmap)
        }
    }

    fun createTilesOverlay(
        context: Context,
        layer: MapWeatherLayer,
        radarTimestamp: Long = radarRepository.getFallbackTimestamp(),
        customRadarFrame: RadarFrame? = null
    ): WeatherTilesOverlay? {
        if (layer == MapWeatherLayer.NONE) {
            return null
        }

        DiskTileCache.init(context)
        Log.d("RadarCache", "TileProvider Recreation | Creating TileProvider & Module for Layer = $layer, Timestamp = ${customRadarFrame?.time ?: radarTimestamp}")

        if (layer == MapWeatherLayer.RAIN_RADAR) {
            val dummyTileSource = object : OnlineTileSourceBase(
                "RainViewer_Radar_${customRadarFrame?.time ?: radarTimestamp}",
                PROVIDER_MIN_ZOOM,
                OVERLAY_MAX_ZOOM,
                256,
                ".png",
                arrayOf("https://tilecache.rainviewer.com")
            ) {
                override fun getTileURLString(pMapTileIndex: Long): String = ""
            }

            val moduleProvider = RainRadarTileModuleProvider(dummyTileSource, null, radarRepository, customRadarFrame)
            val providerArray = MapTileProviderArray(dummyTileSource, null, arrayOf(moduleProvider))

            return WeatherTilesOverlay(providerArray, context, moduleProvider).apply {
                loadingBackgroundColor = Color.TRANSPARENT
                loadingLineColor = Color.TRANSPARENT
            }
        }

        val owmApiKey = try {
            val key = BuildConfig.WEATHER_API_KEY
            if (!key.isNullOrBlank() && key != "PLACEholder_WEATHER_API_KEY") key else "f0308472599cabe4521d65850bb6ba22"
        } catch (e: Exception) {
            "f0308472599cabe4521d65850bb6ba22"
        }

        val layerEndpoint = when (layer) {
            MapWeatherLayer.CLOUDS -> "clouds_new"
            MapWeatherLayer.TEMPERATURE -> "temp_new"
            MapWeatherLayer.WIND -> "wind_new"
            MapWeatherLayer.HUMIDITY -> "humidity_new"
            MapWeatherLayer.PRESSURE -> "pressure_new"
            else -> return null
        }

        val dummyTileSource = object : OnlineTileSourceBase(
            "OWM_${layer.name}",
            PROVIDER_MIN_ZOOM,
            OVERLAY_MAX_ZOOM,
            256,
            ".png",
            arrayOf("https://tile.openweathermap.org")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String = ""
        }

        val moduleProvider = OwmTileModuleProvider(
            pTileSource = dummyTileSource,
            pTileCache = null,
            layerEndpoint = layerEndpoint,
            owmApiKey = owmApiKey,
            isCloudsDark = (layer == MapWeatherLayer.CLOUDS)
        )
        val providerArray = MapTileProviderArray(dummyTileSource, null, arrayOf(moduleProvider))

        return WeatherTilesOverlay(providerArray, context, moduleProvider).apply {
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor = Color.TRANSPARENT
        }
    }
}

class RainRadarTileModuleProvider(
    val pTileSource: ITileSource,
    pTileCache: IFilesystemCache?,
    private val radarRepository: FutureRadarRepository,
    @Volatile var customRadarFrame: RadarFrame? = null,
    @Volatile var isPlaybackActive: Boolean = false,
    @Volatile var mapView: MapView? = null
) : MapTileModuleProviderBase(8, 200) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val parentBitmapCache = LruCache<String, Bitmap>(120)

    override fun getUsesDataConnection(): Boolean = true
    override fun getName(): String = "RainRadarTileDownloader"
    override fun getThreadGroupName(): String = "rainradar"
    override fun getMinimumZoomLevel(): Int = FutureWeatherLayerManager.PROVIDER_MIN_ZOOM
    override fun getMaximumZoomLevel(): Int = 20
    override fun setTileSource(pTileSource: ITileSource?) {}

    override fun getTileLoader(): TileLoader {
        return RainTileLoader()
    }

    private fun fetchProviderTileBitmap(zoom: Int, x: Int, y: Int): Pair<Bitmap?, String> {
        val clampedZoom = zoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM)
        var frame = customRadarFrame ?: radarRepository.getLatestRadarFrameSync()
        val cacheKey = "RainViewer_Radar_${frame.time}_${clampedZoom}_${x}_${y}"

        val ramHit = TileRamCache.get(cacheKey)
        if (ramHit != null && !ramHit.isRecycled) {
            Log.d("SKYSPHERE_TIMELAPSE", "FRAME=${frame.time} ZOOM=$clampedZoom X=$x Y=$y RAM playback hit protected_tiles=${PlaybackProtectedCache.size()} ACTION=DISPLAY")
            return Pair(ramHit, "RAM Cache Hit")
        }

        val diskHit = DiskTileCache.get(cacheKey)
        if (diskHit != null && !diskHit.isRecycled) {
            TileRamCache.put(cacheKey, diskHit)
            Log.d("SKYSPHERE_TIMELAPSE", "FRAME=${frame.time} ZOOM=$clampedZoom X=$x Y=$y DISK playback hit ACTION=LOAD_TO_RAM")
            return Pair(diskHit, "Disk Cache Hit")
        }

        if (isPlaybackActive || RadarDiag.isPlaybackActive) {
            Log.d("SKYSPHERE_TIMELAPSE", "FRAME=${frame.time} ZOOM=$clampedZoom X=$x Y=$y RAM=MISS DISK=MISS ACTION=PLAYBACK_MISS_NO_NETWORK")
            return Pair(RadarPreloader.emptyTransparentBitmap, "Playback Miss - Network Blocked")
        }

        Log.d("SKYSPHERE_TIMELAPSE", "FRAME=${frame.time} ZOOM=$clampedZoom X=$x Y=$y NETWORK download KEY=$cacheKey ACTION=NETWORK_DOWNLOAD")

        val bitmap = RadarTileFetcher.fetchOrDeduplicateTile(cacheKey) {
            var tileUrl = frame.buildTileUrl(clampedZoom, x, y)
            RadarApiTracker.logRainViewerRequest(tileUrl)
            var tileBytes: ByteArray? = null
            var httpCode = 0

            try {
                val req = Request.Builder()
                    .url(tileUrl)
                    .header("User-Agent", "SkySphereApp/1.0")
                    .build()
                client.newCall(req).execute().use { response ->
                    httpCode = response.code
                    if (httpCode == 410) {
                        radarRepository.invalidateCache()
                        frame = radarRepository.getLatestRadarFrameSync(forceRefresh = true)
                        tileUrl = frame.buildTileUrl(clampedZoom, x, y)
                        RadarApiTracker.logRainViewerRequest(tileUrl)
                        val retryReq = Request.Builder()
                            .url(tileUrl)
                            .header("User-Agent", "SkySphereApp/1.0")
                            .build()
                        client.newCall(retryReq).execute().use { retryResp ->
                            httpCode = retryResp.code
                            if (retryResp.isSuccessful) {
                                tileBytes = retryResp.body?.bytes()
                            }
                        }
                    } else if (response.isSuccessful) {
                        tileBytes = response.body?.bytes()
                    }
                }
            } catch (e: Exception) {
                Log.e("RainRadarTile", "Network error fetching provider tile [Z=$clampedZoom, X=$x, Y=$y]: ${e.localizedMessage}")
            }

            if (httpCode == 404 || httpCode == 204) {
                RadarPreloader.emptyTransparentBitmap
            } else if (tileBytes != null && tileBytes!!.isNotEmpty()) {
                try {
                    val bmp = BitmapFactory.decodeByteArray(tileBytes, 0, tileBytes!!.size)
                    bmp ?: RadarPreloader.emptyTransparentBitmap
                } catch (e: Exception) {
                    RadarPreloader.emptyTransparentBitmap
                }
            } else if (httpCode == 200 && (tileBytes == null || tileBytes!!.isEmpty())) {
                RadarPreloader.emptyTransparentBitmap
            } else null
        }

        return Pair(bitmap, if (bitmap != null) "Loaded" else "Failed")
    }

    private inner class RainTileLoader : MapTileModuleProviderBase.TileLoader() {
        override fun loadTile(pMapTileIndex: Long): Drawable? {
            val rawZoom = MapTileIndex.getZoom(pMapTileIndex)
            if (rawZoom < FutureWeatherLayerManager.PROVIDER_MIN_ZOOM || rawZoom > 20) {
                return FutureWeatherLayerManager.emptyTransparentTile
            }

            val mapZoom = rawZoom.coerceIn(
                FutureWeatherLayerManager.PROVIDER_MIN_ZOOM,
                20
            )
            val tileX = MapTileIndex.getX(pMapTileIndex)
            val tileY = MapTileIndex.getY(pMapTileIndex)

            Log.d("RadarDraw", "Drawing tile at $tileX,$tileY (zoom=$mapZoom)")

            val providerMaxZoom = FutureWeatherLayerManager.RAIN_RADAR_PROVIDER_MAX_ZOOM

            val drawableResult: Drawable = if (mapZoom <= providerMaxZoom) {
                val (bitmap, _) = fetchProviderTileBitmap(mapZoom, tileX, tileY)
                if (bitmap != null) {
                    BitmapDrawable(null, bitmap)
                } else BitmapDrawable(null, RadarPreloader.emptyTransparentBitmap)
            } else {
                val parentZoom = providerMaxZoom
                val deltaZ = mapZoom - parentZoom
                val parentX = tileX shr deltaZ
                val parentY = tileY shr deltaZ

                val (parentBitmap, _) = fetchProviderTileBitmap(parentZoom, parentX, parentY)
                if (parentBitmap == null || parentBitmap.isRecycled) {
                    BitmapDrawable(null, RadarPreloader.emptyTransparentBitmap)
                } else if (parentBitmap == RadarPreloader.emptyTransparentBitmap) {
                    BitmapDrawable(null, RadarPreloader.emptyTransparentBitmap)
                } else {
                    Log.d("RadarDebug", "RENDER: loadTile returned bitmap with width=${parentBitmap.width} and pixel count=${parentBitmap.byteCount}")

                    try {
                        val scale = 1 shl deltaZ
                        val subSize = (256 / scale).coerceAtLeast(1)
                        val offsetX = ((tileX and (scale - 1)) * 256 / scale).coerceIn(0, 255)
                        val offsetY = ((tileY and (scale - 1)) * 256 / scale).coerceIn(0, 255)

                        val cropWidth = minOf(subSize, parentBitmap.width - offsetX)
                        val cropHeight = minOf(subSize, parentBitmap.height - offsetY)

                        if (cropWidth <= 0 || cropHeight <= 0) {
                            BitmapDrawable(null, RadarPreloader.emptyTransparentBitmap)
                        } else {
                            val cropped = Bitmap.createBitmap(parentBitmap, offsetX, offsetY, cropWidth, cropHeight)
                            val scaledBitmap = Bitmap.createScaledBitmap(cropped, 256, 256, true)
                            BitmapDrawable(null, scaledBitmap)
                        }
                    } catch (e: Exception) {
                        Log.e("RainRadarZoom", "Error cropping/scaling radar tile [MapZoom=$mapZoom, X=$tileX, Y=$tileY]: ${e.localizedMessage}")
                        BitmapDrawable(null, RadarPreloader.emptyTransparentBitmap)
                    }
                }
            }

            RadarDiag.logTileRenderingEvent(tileX, tileY, mapZoom, "RainRadar_${mapZoom}_${tileX}_${tileY}", drawableResult != null)
            return drawableResult
        }
    }
}

class OwmTileModuleProvider(
    val pTileSource: ITileSource,
    pTileCache: IFilesystemCache?,
    val layerEndpoint: String,
    private val owmApiKey: String,
    private val isCloudsDark: Boolean = false,
    @Volatile var isPlaybackActive: Boolean = false,
    @Volatile var currentFrame: TimeLapseFrame? = null
) : MapTileModuleProviderBase(8, 200) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val parentBitmapCache = LruCache<String, Bitmap>(80)

    override fun getUsesDataConnection(): Boolean = true
    override fun getName(): String = "OwmTileDownloader_$layerEndpoint"
    override fun getThreadGroupName(): String = "owmtile"
    override fun getMinimumZoomLevel(): Int = FutureWeatherLayerManager.PROVIDER_MIN_ZOOM
    override fun getMaximumZoomLevel(): Int = FutureWeatherLayerManager.OVERLAY_MAX_ZOOM
    override fun setTileSource(pTileSource: ITileSource?) {}

    override fun getTileLoader(): TileLoader {
        return OwmTileLoader()
    }

    private fun fetchProviderTileBitmap(zoom: Int, x: Int, y: Int): Bitmap? {
        val clampedZoom = zoom.coerceIn(FutureWeatherLayerManager.PROVIDER_MIN_ZOOM, FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM)
        val cacheKey = "${layerEndpoint}_${clampedZoom}_${x}_${y}"

        val ramHit = TileRamCache.get(cacheKey)
        if (ramHit != null && !ramHit.isRecycled) {
            Log.d("SKYSPHERE_TIMELAPSE", "FRAME=${currentFrame?.timestamp ?: 0} ZOOM=$clampedZoom X=$x Y=$y RAM playback hit protected_tiles=${PlaybackProtectedCache.size()} ACTION=DISPLAY")
            return ramHit
        }

        val diskHit = DiskTileCache.get(cacheKey)
        if (diskHit != null && !diskHit.isRecycled) {
            TileRamCache.put(cacheKey, diskHit)
            Log.d("SKYSPHERE_TIMELAPSE", "FRAME=${currentFrame?.timestamp ?: 0} ZOOM=$clampedZoom X=$x Y=$y DISK playback hit ACTION=LOAD_TO_RAM")
            return diskHit
        }

        if (isPlaybackActive || RadarDiag.isPlaybackActive) {
            Log.d("SKYSPHERE_TIMELAPSE", "FRAME=${currentFrame?.timestamp ?: 0} ZOOM=$clampedZoom X=$x Y=$y RAM=MISS DISK=MISS ACTION=PLAYBACK_MISS_NO_NETWORK")
            return RadarPreloader.emptyTransparentBitmap
        }

        Log.d("SKYSPHERE_TIMELAPSE", "FRAME=${currentFrame?.timestamp ?: 0} ZOOM=$clampedZoom X=$x Y=$y NETWORK download KEY=$cacheKey ACTION=NETWORK_DOWNLOAD")

        return RadarTileFetcher.fetchOrDeduplicateTile(cacheKey) {
            val url = "https://tile.openweathermap.org/map/$layerEndpoint/$clampedZoom/$x/$y.png?appid=$owmApiKey"
            RadarApiTracker.logOpenWeatherRequest(url)
            var tileBytes: ByteArray? = null
            var httpCode = 0

            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SkySphereApp/1.0")
                    .build()
                client.newCall(req).execute().use { response ->
                    httpCode = response.code
                    if (response.isSuccessful) {
                        tileBytes = response.body?.bytes()
                    }
                }
            } catch (e: Exception) {
                Log.e("OwmTile", "Network error fetching OWM tile [$layerEndpoint Z=$clampedZoom X=$x Y=$y]: ${e.localizedMessage}")
            }

            if (httpCode == 404 || httpCode == 204) {
                RadarPreloader.emptyTransparentBitmap
            } else if (tileBytes != null && tileBytes!!.isNotEmpty()) {
                try {
                    var bitmap = BitmapFactory.decodeByteArray(tileBytes, 0, tileBytes!!.size)
                    if (bitmap != null && isCloudsDark) {
                        bitmap = applyDarkCloudStyle(bitmap)
                    }
                    bitmap ?: RadarPreloader.emptyTransparentBitmap
                } catch (e: Exception) {
                    Log.e("OwmTile", "Decode error for OWM tile: ${e.localizedMessage}")
                    RadarPreloader.emptyTransparentBitmap
                }
            } else if (httpCode == 200 && (tileBytes == null || tileBytes!!.isEmpty())) {
                RadarPreloader.emptyTransparentBitmap
            } else null
        }
    }

    private fun applyDarkCloudStyle(originalBitmap: Bitmap): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height
        val pixels = IntArray(width * height)
        originalBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val p = pixels[i]
            val a = Color.alpha(p)
            if (a > 0) {
                val alphaBoost = (a * 2.2f).coerceAtMost(255f).toInt()
                val density = a / 255f

                val red = (55 - density * 35).toInt().coerceIn(15, 255)
                val green = (65 - density * 35).toInt().coerceIn(20, 255)
                val blue = (85 - density * 40).toInt().coerceIn(30, 255)

                pixels[i] = Color.argb(alphaBoost, red, green, blue)
            }
        }

        val darkCloudBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        darkCloudBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return darkCloudBitmap
    }

    private inner class OwmTileLoader : MapTileModuleProviderBase.TileLoader() {
        override fun loadTile(pMapTileIndex: Long): Drawable? {
            val mapZoom = MapTileIndex.getZoom(pMapTileIndex).coerceIn(
                FutureWeatherLayerManager.PROVIDER_MIN_ZOOM,
                FutureWeatherLayerManager.OVERLAY_MAX_ZOOM
            )
            val tileX = MapTileIndex.getX(pMapTileIndex)
            val tileY = MapTileIndex.getY(pMapTileIndex)

            val providerMaxZoom = FutureWeatherLayerManager.OWM_PROVIDER_MAX_ZOOM

            val res = if (mapZoom <= providerMaxZoom) {
                val bitmap = fetchProviderTileBitmap(mapZoom, tileX, tileY)
                BitmapDrawable(null, bitmap ?: RadarPreloader.emptyTransparentBitmap)
            } else {
                val deltaZ = mapZoom - providerMaxZoom
                val parentX = tileX shr deltaZ
                val parentY = tileY shr deltaZ

                val parentBitmap = fetchProviderTileBitmap(providerMaxZoom, parentX, parentY)
                if (parentBitmap == null || parentBitmap.isRecycled) {
                    BitmapDrawable(null, RadarPreloader.emptyTransparentBitmap)
                } else if (parentBitmap == RadarPreloader.emptyTransparentBitmap) {
                    BitmapDrawable(null, RadarPreloader.emptyTransparentBitmap)
                } else {
                    try {
                        val scale = 1 shl deltaZ
                        val subSize = (256 / scale).coerceAtLeast(1)
                        val offsetX = ((tileX and (scale - 1)) * 256 / scale).coerceIn(0, 255)
                        val offsetY = ((tileY and (scale - 1)) * 256 / scale).coerceIn(0, 255)

                        val cropWidth = minOf(subSize, parentBitmap.width - offsetX)
                        val cropHeight = minOf(subSize, parentBitmap.height - offsetY)

                        if (cropWidth <= 0 || cropHeight <= 0) BitmapDrawable(null, RadarPreloader.emptyTransparentBitmap)
                        else {
                            val cropped = Bitmap.createBitmap(parentBitmap, offsetX, offsetY, cropWidth, cropHeight)
                            val scaledBitmap = Bitmap.createScaledBitmap(cropped, 256, 256, true)
                            BitmapDrawable(null, scaledBitmap)
                        }
                    } catch (e: Exception) {
                        Log.e("OwmTileZoom", "Error cropping OWM tile: ${e.localizedMessage}")
                        BitmapDrawable(null, RadarPreloader.emptyTransparentBitmap)
                    }
                }
            }
            RadarDiag.logTileRenderingEvent(tileX, tileY, mapZoom, "${layerEndpoint}_${mapZoom}_${tileX}_${tileY}", true)
            return res
        }
    }
}

