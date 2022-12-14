package com.jhomlala.better_player

import android.content.Context
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSink
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.SimpleCache

internal class CacheDataSourceFactory(
    private val context: Context,
    private val maxCacheSize: Long,
    private val maxFileSize: Long,
    upstreamDataSource: DataSource.Factory?
) : DataSource.Factory {
    private var defaultDatasourceFactory: DefaultDataSource.Factory? = null
    private val betterPlayerCache : SimpleCache =
        BetterPlayerCache.createCache(context, maxCacheSize)!!

    override fun createDataSource(): CacheDataSource {
        return CacheDataSource(
            betterPlayerCache,
            defaultDatasourceFactory?.createDataSource(),
            FileDataSource(),
            CacheDataSink(betterPlayerCache, maxFileSize),
            CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
            null
        )
    }

    fun buildReadOnlyCacheDataSource(): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(betterPlayerCache)
            .setUpstreamDataSourceFactory(defaultDatasourceFactory)
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory().setCache(betterPlayerCache).setFragmentSize(maxFileSize)
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    init {
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        upstreamDataSource?.let {
            defaultDatasourceFactory = DefaultDataSource.Factory(context, upstreamDataSource)
            defaultDatasourceFactory?.setTransferListener(bandwidthMeter)
        }
    }
}