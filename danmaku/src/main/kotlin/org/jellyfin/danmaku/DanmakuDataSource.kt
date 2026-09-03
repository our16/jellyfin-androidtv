package org.jellyfin.danmaku

import master.flame.danmaku.danmaku.parser.IDataSource
import java.io.InputStream

/**
 * Data source for danmaku data.
 * Supports loading from strings, input streams, or URLs.
 */
class DanmakuDataSource : IDataSource<Any> {

    private var data: Any? = null
    private var inputStream: InputStream? = null

    /**
     * Load danmaku data from a string.
     */
    fun loadFromString(xmlContent: String): DanmakuDataSource {
        data = xmlContent
        inputStream = xmlContent.byteInputStream(Charsets.UTF_8)
        return this
    }

    /**
     * Load danmaku data from an input stream.
     */
    fun loadFromStream(stream: InputStream): DanmakuDataSource {
        data = stream
        inputStream = stream
        return this
    }

    override fun data(): Any {
        return inputStream ?: data ?: ""
    }

    override fun release() {
        inputStream?.close()
        inputStream = null
        data = null
    }

    /**
     * Get the size of the data source.
     */
    fun size(): Long {
        return when (val d = data) {
            is String -> d.length.toLong()
            is InputStream -> d.available().toLong()
            else -> 0
        }
    }
}
