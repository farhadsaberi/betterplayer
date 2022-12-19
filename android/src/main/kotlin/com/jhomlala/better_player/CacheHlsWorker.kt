package com.jhomlala.better_player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.offline.DownloadHelper
import com.google.android.exoplayer2.source.hls.offline.HlsDownloader
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.CacheWriter
import com.jhomlala.better_player.DataSourceUtils.getDataSourceFactory
import com.jhomlala.better_player.DataSourceUtils.getUserAgent
import com.jhomlala.better_player.DataSourceUtils.isHTTP
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.*

/**
 * Cache worker which download part of video and save in cache for future usage. The cache job
 * will be executed in work manager.
 */
class CacheHlsWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    private var hlsDownloader: HlsDownloader? = null
    private var lastCacheReportIndex = -1
    override suspend fun doWork(): Result {
        return coroutineScope {
            val job = async {
                start()
            }

            job.invokeOnCompletion { exception: Throwable? ->
                Log.e(TAG, "Cleanup on completion", exception)
                hlsDownloader?.cancel()
            }

            job.await()
        }
    }

    private fun start(): Result {
        if (runAttemptCount < 6) {
            Log.v(TAG, "CacheHlsWorker Started $runAttemptCount")
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
                        headers[keySplit] =
                            Objects.requireNonNull(data.keyValueMap[key]) as String
                    }
                }
                val uri = Uri.parse(url)
                if (isHTTP(uri)) {

                    val mediaItem = MediaItem.Builder()
                        .setUri(uri)
                        .setStreamKeys(
                            BetterPlayer.cacheStreamKeys
                        )
                        .build()


                    hlsDownloader = HlsDownloader(
                        mediaItem,
                        CacheDataSourceCustomFactory.getCacheDataSourceFactory(
                            context,
                            maxCacheFileSize,
                        )
                    )

                    hlsDownloader?.download { contentLength, bytesDownloaded, percentDownloaded ->
                        if (isStopped) {
                            hlsDownloader?.cancel()
                        }
                        if (percentDownloaded > 100) hlsDownloader?.cancel()
                        if (lastCacheReportIndex != percentDownloaded.toInt()) {
                            lastCacheReportIndex = percentDownloaded.toInt()
                            Log.v(
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
            hlsDownloader?.cancel()
            return Result.failure()
        }
    }

    companion object {
        const val TAG = "CacheHlsWorker"
    }


}