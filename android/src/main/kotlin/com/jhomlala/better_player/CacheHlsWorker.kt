package com.jhomlala.better_player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.offline.DownloadHelper
import com.google.android.exoplayer2.source.hls.offline.HlsDownloader
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException
import com.google.android.exoplayer2.upstream.cache.CacheWriter
import com.jhomlala.better_player.DataSourceUtils.getDataSourceFactory
import com.jhomlala.better_player.DataSourceUtils.getUserAgent
import com.jhomlala.better_player.DataSourceUtils.isHTTP
import java.util.*

/**
 * Cache worker which download part of video and save in cache for future usage. The cache job
 * will be executed in work manager.
 */
class CacheHlsWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    private var hlsDownloader: HlsDownloader? = null
    private var lastCacheReportIndex = -1
    override fun doWork(): Result {
        if (runAttemptCount < 6) {
            Log.e(TAG, "CacheHlsWorker Started $runAttemptCount")
            try {
                val data = inputData
                val url = data.getString(BetterPlayerPlugin.URL_PARAMETER)
                val maxCacheSize = data.getLong(BetterPlayerPlugin.MAX_CACHE_SIZE_PARAMETER, 0)
                val maxCacheFileSize =
                    data.getLong(BetterPlayerPlugin.MAX_CACHE_FILE_SIZE_PARAMETER, 0)
                val headers: MutableMap<String, String> = HashMap()
                for (key in data.keyValueMap.keys) {
                    if (key.contains(BetterPlayerPlugin.HEADER_PARAMETER)) {
                        val keySplit =
                            key.split(BetterPlayerPlugin.HEADER_PARAMETER.toRegex())
                                .toTypedArray()[0]
                        headers[keySplit] = Objects.requireNonNull(data.keyValueMap[key]) as String
                    }
                }
                val uri = Uri.parse(url)
                if (isHTTP(uri)) {
                    val userAgent = getUserAgent(headers)
                    val dataSourceFactory = getDataSourceFactory(userAgent, headers)
                    val cacheDataSourceFactory = CacheDataSourceFactory(
                        context,
                        maxCacheSize,
                        maxCacheFileSize,
                        dataSourceFactory
                    )
                    val mediaItem = MediaItem.Builder()
                        .setUri(uri)
                        .setStreamKeys(
                            BetterPlayer.cacheStreamKeys
                        )
                        .build()

                    hlsDownloader = HlsDownloader(
                        mediaItem,
                        cacheDataSourceFactory.buildReadOnlyCacheDataSource()
                    )


                    hlsDownloader?.download { contentLength, bytesDownloaded, percentDownloaded ->
                        if (percentDownloaded > 100) hlsDownloader?.cancel()
                        if (lastCacheReportIndex != percentDownloaded.toInt()) {
                            lastCacheReportIndex = percentDownloaded.toInt()
                            Log.e(
                                TAG,
                                "CacheHlsWorker Downloaded: bytesDownloaded: $bytesDownloaded, percentDownloaded: $percentDownloaded"
                            )
                        }

                    }
                } else {
                    Log.e(TAG, "Preloading only possible for remote data sources")
                    return Result.failure()
                }
            } catch (exception: Exception) {
                Log.e(TAG, exception.toString())
                return if (exception is HttpDataSourceException) {
                    Log.e(TAG, "Error Worker And Retry $exception")
                    Result.retry()
                } else {
                    Log.e(TAG, "Error Worker$exception")
                    Result.failure()
                }
            }
            Log.e(TAG, "Finish")
            return Result.success()
        } else {
            return Result.failure()
        }

    }

    override fun onStopped() {
        try {
            Log.e(TAG, "onStopped WorkManger")
            hlsDownloader?.cancel()
            super.onStopped()
        } catch (exception: Exception) {
            Log.e(TAG, exception.toString())
        }
    }

    companion object {
        private const val TAG = "CacheHlsWorker"
    }


}