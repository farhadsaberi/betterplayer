package com.jhomlala.better_player

import android.content.Context
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ext.cronet.CronetDataSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSink
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import org.chromium.net.CronetEngine
import java.util.concurrent.Executors

object CacheDataSourceCustomFactory {

    private var instance: CacheDataSource.Factory? = null

    fun getCacheDataSourceFactory(context: Context, maxCacheSize: Long): CacheDataSource.Factory {
        if (instance == null) {
            val cacheReadDataSourceFactory = FileDataSource.Factory()
            val cronetEngine = CronetEngine.Builder(context).build()
            val cronetDataSourceFactory = CronetDataSource.Factory(
                cronetEngine,
                Executors.newSingleThreadExecutor()
            )
            val cache = BetterPlayerCache.createCache(context, maxCacheSize)!!
            instance = CacheDataSource.Factory()
                .setCache(cache)
                .setCacheReadDataSourceFactory(cacheReadDataSourceFactory)
                .setUpstreamDataSourceFactory(cronetDataSourceFactory)
                .setCacheWriteDataSinkFactory(
                    CacheDataSink.Factory().setCache(cache).setFragmentSize(maxCacheSize)
                )
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
        return instance!!

    }

    fun getHlsMediaSource(mediaItem: MediaItem): HlsMediaSource {
        return HlsMediaSource.Factory(instance!!)
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)
    }


}