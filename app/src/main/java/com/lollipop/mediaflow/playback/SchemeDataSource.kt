package com.lollipop.mediaflow.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException

/**
 * 按 URI scheme 分派的数据源。
 *
 * 对齐计划第三节「缓存与去重」：
 * - **远程**（http / https / ...）走调用方传入的 `cachedDataSource`（即 [FlowMediaCache] 的
 *   [androidx.media3.datasource.cache.CacheDataSource]）；
 * - **本地**（file / content / android.resource / asset / rawresource）走 `localDataSource`
 *   （[androidx.media3.datasource.DefaultDataSource]），**不写缓存**——本地文件预缓存没有意义，
 *   只会白占空间。
 *
 * 整体交给 `DefaultMediaSourceFactory.setDataSourceFactory(...)`，因此页面对此无感知。
 */
@OptIn(UnstableApi::class)
class SchemeDataSource(
    private val cachedDataSource: DataSource,
    private val localDataSource: DataSource
) : BaseDataSource(/* isContentEncrypted= */ false) {

    private var current: DataSource? = null

    override fun open(dataSpec: DataSpec): Long {
        val dataSource = if (isLocalScheme(dataSpec.uri)) {
            localDataSource
        } else {
            cachedDataSource
        }
        current = dataSource
        transferInitializing(dataSpec)
        return dataSource.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val dataSource = current ?: throw IOException("DataSource is not opened")
        val read = dataSource.read(buffer, offset, length)
        if (read > 0) {
            bytesTransferred(read)
        }
        return read
    }

    override fun getUri(): Uri? {
        return current?.uri
    }

    override fun close() {
        val dataSource = current ?: return
        current = null
        try {
            dataSource.close()
        } finally {
            transferEnded()
        }
    }

    /** 每层数据源各自实例化，保证 [SchemeDataSource] 无共享可变状态。 */
    class Factory(
        private val cachedFactory: DataSource.Factory,
        private val localFactory: DataSource.Factory
    ) : DataSource.Factory {

        override fun createDataSource(): DataSource {
            return SchemeDataSource(
                cachedDataSource = cachedFactory.createDataSource(),
                localDataSource = localFactory.createDataSource()
            )
        }
    }

    companion object {

        /** 本地 scheme 白名单：命中的走本地数据源，不写缓存。 */
        private val LocalSchemes = setOf(
            "file",
            "content",
            "android.resource",
            "asset",
            "rawresource"
        )

        /** 是否为本地（无需缓存）URI。 */
        fun isLocalScheme(uri: Uri): Boolean {
            return LocalSchemes.contains(uri.scheme?.lowercase())
        }
    }
}
