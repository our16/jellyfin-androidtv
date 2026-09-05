package org.jellyfin.androidtv.data.repository.danmaku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.data.model.danmaku.*
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 弹幕 API 仓库
 * 封装所有与弹幕服务器的 API 交互
 */
interface DanmakuApiRepository {
    suspend fun getDanmakuInfo(itemId: String, mediaSourceId: String? = null): DanmakuFileInfo?
    suspend fun getDanmakuUrl(itemId: String, mediaSourceId: String? = null): DanmakuUrlResponse?
    suspend fun getDanmakuRaw(itemId: String, mediaSourceId: String? = null): String?
    suspend fun refreshDanmaku(itemId: String, source: String? = null, force: Boolean = false): DanmakuTaskResponse?
    suspend fun deleteDanmaku(itemId: String, mediaSourceId: String? = null): Boolean
    suspend fun searchDanmaku(keyword: String, sources: List<String>? = null, limit: Int = 20): DanmakuSearchResponse?
    suspend fun searchDanmakuByItem(itemId: String, sources: List<String>? = null, limit: Int = 10): DanmakuSearchResponse?
    suspend fun getSources(): List<DanmakuSource>?
    suspend fun updateSource(sourceId: String, enabled: Boolean? = null, priority: Int? = null): DanmakuSource?
    suspend fun testSource(sourceId: String): DanmakuSourceTestResult?
    suspend fun getConfig(): DanmakuConfig?
    suspend fun updateConfig(config: DanmakuConfig): DanmakuConfig?
    suspend fun getCacheStats(): DanmakuCacheStats?
    suspend fun cleanupCache(): DanmakuCleanupResult?
}

class DanmakuApiRepositoryImpl(
    private val api: ApiClient,
) : DanmakuApiRepository {

    companion object {
        private const val TAG = "DanmakuApi"
        private const val CONNECT_TIMEOUT = 10_000
        private const val READ_TIMEOUT = 30_000
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun createConnection(urlString: String): HttpURLConnection {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty(
            "Authorization",
            api.accessToken?.let { "MediaBrowser Token=\"$it\"" } ?: ""
        )
        connection.setRequestProperty(
            "X-Emby-Authorization",
            "MediaBrowser Client=\"Jellyfin for Android TV\", Device=\"androidtv\""
        )
        connection.setRequestProperty("Accept", "application/json; profile=\"CamelCase\"")
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT
        return connection
    }

    private suspend fun <T> apiCall(
        urlString: String,
        parser: (String) -> T,
    ): T? = withContext(Dispatchers.IO) {
        try {
            val connection = createConnection(urlString)
            Timber.d(TAG, "API call: $urlString")

            if (connection.responseCode == 200) {
                val body = connection.inputStream.bufferedReader().readText()
                parser(body)
            } else {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: ""
                Timber.w(TAG, "API error ${connection.responseCode}: $error")
                null
            }
        } catch (e: Exception) {
            Timber.e(TAG, "API call failed: $urlString", e)
            null
        }
    }

    private suspend fun apiPost(
        urlString: String,
        body: String? = null,
    ): Int = withContext(Dispatchers.IO) {
        try {
            val connection = createConnection(urlString).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            body?.let {
                connection.outputStream.bufferedWriter().write(it)
            }
            connection.responseCode
        } catch (e: Exception) {
            Timber.e(TAG, "API POST failed: $urlString", e)
            -1
        }
    }

    private suspend fun apiDelete(urlString: String): Int = withContext(Dispatchers.IO) {
        try {
            val connection = createConnection(urlString).apply {
                requestMethod = "DELETE"
            }
            connection.responseCode
        } catch (e: Exception) {
            Timber.e(TAG, "API DELETE failed: $urlString", e)
            -1
        }
    }

    private fun baseUrl(): String = api.baseUrl?.trimEnd('/') ?: ""

    override suspend fun getDanmakuInfo(
        itemId: String,
        mediaSourceId: String?,
    ): DanmakuFileInfo? {
        val params = mediaSourceId?.let { "?mediaSourceId=$it" } ?: ""
        return apiCall("${baseUrl()}/api/danmaku/$itemId$params") { body ->
            json.decodeFromString<DanmakuFileInfo>(body)
        }
    }

    override suspend fun getDanmakuUrl(
        itemId: String,
        mediaSourceId: String?,
    ): DanmakuUrlResponse? {
        val params = mediaSourceId?.let { "?mediaSourceId=$it" } ?: ""
        return apiCall("${baseUrl()}/api/danmaku/$itemId/url$params") { body ->
            json.decodeFromString<DanmakuUrlResponse>(body)
        }
    }

    override suspend fun getDanmakuRaw(
        itemId: String,
        mediaSourceId: String?,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val params = mediaSourceId?.let { "?mediaSourceId=$it" } ?: ""
            val connection = createConnection("${baseUrl()}/api/danmaku/$itemId/raw$params")
            connection.setRequestProperty("Accept", "application/xml")

            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(TAG, "Failed to get raw danmaku", e)
            null
        }
    }

    override suspend fun refreshDanmaku(
        itemId: String,
        source: String?,
        force: Boolean,
    ): DanmakuTaskResponse? {
        val body = buildString {
            append("{")
            source?.let { append("\"source\":\"$it\",") }
            append("\"force\":$force")
            append("}")
        }
        return withContext(Dispatchers.IO) {
            try {
                val connection = createConnection("${baseUrl()}/api/danmaku/$itemId/refresh").apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                connection.outputStream.bufferedWriter().write(body)

                if (connection.responseCode == 202) {
                    val responseBody = connection.inputStream.bufferedReader().readText()
                    json.decodeFromString<DanmakuTaskResponse>(responseBody)
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.e(TAG, "Failed to refresh danmaku", e)
                null
            }
        }
    }

    override suspend fun deleteDanmaku(itemId: String, mediaSourceId: String?): Boolean {
        val params = mediaSourceId?.let { "?mediaSourceId=$it" } ?: ""
        return apiDelete("${baseUrl()}/api/danmaku/$itemId$params") == 204
    }

    override suspend fun searchDanmaku(
        keyword: String,
        sources: List<String>?,
        limit: Int,
    ): DanmakuSearchResponse? {
        val params = buildString {
            append("keyword=${URLEncoder.encode(keyword, "UTF-8")}")
            sources?.let { append("&sources=${it.joinToString(",")}") }
            append("&limit=$limit")
        }
        return apiCall("${baseUrl()}/api/danmaku/search?$params") { body ->
            json.decodeFromString<DanmakuSearchResponse>(body)
        }
    }

    override suspend fun searchDanmakuByItem(
        itemId: String,
        sources: List<String>?,
        limit: Int,
    ): DanmakuSearchResponse? {
        val params = buildString {
            sources?.let { append("sources=${it.joinToString(",")}&") }
            append("limit=$limit")
        }
        return apiCall("${baseUrl()}/api/danmaku/search/by-item/$itemId?$params") { body ->
            json.decodeFromString<DanmakuSearchResponse>(body)
        }
    }

    override suspend fun getSources(): List<DanmakuSource>? {
        data class SourcesResponse(val sources: List<DanmakuSource> = emptyList())
        return apiCall("${baseUrl()}/api/danmaku/sources") { body ->
            json.decodeFromString<SourcesResponse>(body).sources
        }
    }

    override suspend fun updateSource(
        sourceId: String,
        enabled: Boolean?,
        priority: Int?,
    ): DanmakuSource? {
        val body = buildString {
            append("{")
            enabled?.let { append("\"enabled\":$it,") }
            priority?.let { append("\"priority\":$it,") }
            append("}")
        }.trimEnd(',').let { it + "}" }

        return withContext(Dispatchers.IO) {
            try {
                val connection = createConnection("${baseUrl()}/api/danmaku/sources/$sourceId").apply {
                    requestMethod = "PUT"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                connection.outputStream.bufferedWriter().write(body)

                if (connection.responseCode == 200) {
                    val responseBody = connection.inputStream.bufferedReader().readText()
                    json.decodeFromString<DanmakuSource>(responseBody)
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.e(TAG, "Failed to update source", e)
                null
            }
        }
    }

    override suspend fun testSource(sourceId: String): DanmakuSourceTestResult? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = createConnection("${baseUrl()}/api/danmaku/sources/$sourceId/test").apply {
                    requestMethod = "POST"
                }

                if (connection.responseCode == 200) {
                    val body = connection.inputStream.bufferedReader().readText()
                    json.decodeFromString<DanmakuSourceTestResult>(body)
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.e(TAG, "Failed to test source", e)
                null
            }
        }
    }

    override suspend fun getConfig(): DanmakuConfig? {
        return apiCall("${baseUrl()}/api/danmaku/config") { body ->
            json.decodeFromString<DanmakuConfig>(body)
        }
    }

    override suspend fun updateConfig(config: DanmakuConfig): DanmakuConfig? {
        return withContext(Dispatchers.IO) {
            try {
                val body = json.encodeToString(DanmakuConfig.serializer(), config)
                val connection = createConnection("${baseUrl()}/api/danmaku/config").apply {
                    requestMethod = "PUT"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                connection.outputStream.bufferedWriter().write(body)

                if (connection.responseCode == 200) {
                    val responseBody = connection.inputStream.bufferedReader().readText()
                    json.decodeFromString<DanmakuConfig>(responseBody)
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.e(TAG, "Failed to update config", e)
                null
            }
        }
    }

    override suspend fun getCacheStats(): DanmakuCacheStats? {
        return apiCall("${baseUrl()}/api/danmaku/cache/stats") { body ->
            json.decodeFromString<DanmakuCacheStats>(body)
        }
    }

    override suspend fun cleanupCache(): DanmakuCleanupResult? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = createConnection("${baseUrl()}/api/danmaku/cache/cleanup").apply {
                    requestMethod = "POST"
                }

                if (connection.responseCode == 200) {
                    val body = connection.inputStream.bufferedReader().readText()
                    json.decodeFromString<DanmakuCleanupResult>(body)
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.e(TAG, "Failed to cleanup cache", e)
                null
            }
        }
    }
}
