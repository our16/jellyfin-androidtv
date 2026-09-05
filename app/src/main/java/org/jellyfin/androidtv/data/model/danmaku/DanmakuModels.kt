package org.jellyfin.androidtv.data.model.danmaku

import kotlinx.serialization.Serializable

/**
 * 弹幕文件元数据信息
 */
@Serializable
data class DanmakuFileInfo(
    val itemId: String = "",
    val mediaSourceId: String = "",
    val hasDanmaku: Boolean = false,
    val danmakuCount: Int = 0,
    val source: String = "",
    val sourceId: String = "",
    val sourceCid: Int = 0,
    val lastUpdated: Long = 0,
    val format: String = "xml",
    val fileSize: Long = 0,
    val duration: Int = 0,
    val languages: List<String> = emptyList(),
    val matchConfidence: Float = 0f,
)

/**
 * 弹幕文件下载 URL 响应
 */
@Serializable
data class DanmakuUrlResponse(
    val url: String = "",
    val expiresAt: Long = 0,
    val format: String = "xml",
    val fileSize: Long = 0,
)

/**
 * 异步任务响应
 */
@Serializable
data class DanmakuTaskResponse(
    val taskId: String = "",
    val status: String = "pending",
    val message: String = "",
)

/**
 * 异步任务状态
 */
@Serializable
data class DanmakuTaskStatus(
    val taskId: String = "",
    val status: String = "pending",
    val progress: Int = 0,
    val result: DanmakuTaskResult? = null,
    val createdAt: Long = 0,
    val completedAt: Long = 0,
)

@Serializable
data class DanmakuTaskResult(
    val danmakuCount: Int = 0,
    val source: String = "",
    val fileSize: Long = 0,
)

/**
 * 弹幕搜索结果
 */
@Serializable
data class DanmakuSearchResponse(
    val totalResults: Int = 0,
    val results: List<DanmakuSearchResult> = emptyList(),
)

@Serializable
data class DanmakuSearchResult(
    val source: String = "",
    val sourceId: String = "",
    val sourceCid: Int = 0,
    val name: String = "",
    val nameOriginal: String = "",
    val category: String = "",
    val year: String = "",
    val episodeNumber: Int = 0,
    val episodeTitle: String = "",
    val duration: Int = 0,
    val matchScore: Float = 0f,
    val matchSource: String = "",
    val availableFormats: List<String> = emptyList(),
    val thumbnailUrl: String = "",
)

/**
 * 弹幕源配置
 */
@Serializable
data class DanmakuSource(
    val id: String = "",
    val name: String = "",
    val type: String = "online",
    val enabled: Boolean = true,
    val priority: Int = 0,
    val supportedFormats: List<String> = emptyList(),
    val config: DanmakuSourceConfig? = null,
    val stats: DanmakuSourceStats? = null,
)

@Serializable
data class DanmakuSourceConfig(
    val apiEndpoint: String = "",
    val useProtobuf: Boolean = false,
    val scanPaths: List<String> = emptyList(),
    val filePattern: String = "*.xml",
)

@Serializable
data class DanmakuSourceStats(
    val totalRequests: Int = 0,
    val successRate: Float = 1f,
    val avgResponseTime: Int = 0,
    val lastError: String? = null,
    val lastErrorTime: Long? = null,
)

/**
 * 弹幕显示设置
 */
@Serializable
data class DanmakuDisplaySettings(
    val fontSize: Int = 25,
    val opacity: Float = 0.8f,
    val speed: Float = 1.0f,
    val area: Float = 0.7f,
    val enabledTypes: List<Int> = listOf(1, 4, 5),
    val blockedColors: List<Int> = emptyList(),
    val blockedUsers: List<String> = emptyList(),
    val blockedWords: List<String> = emptyList(),
    val densityLimit: Int = 6,
)

/**
 * 弹幕全局配置
 */
@Serializable
data class DanmakuConfig(
    val enabled: Boolean = true,
    val defaultEnabled: Boolean = true,
    val autoMatch: Boolean = true,
    val autoMatchSources: List<String> = listOf("bilibili", "dandanplay"),
    val maxCacheSize: Long = 1073741824,
    val cacheExpiryDays: Int = 30,
    val maxDanmakuCount: Int = 5000,
    val defaultDisplaySettings: DanmakuDisplaySettings = DanmakuDisplaySettings(),
    val updateSettings: DanmakuUpdateSettings? = null,
)

@Serializable
data class DanmakuUpdateSettings(
    val autoUpdate: Boolean = true,
    val updateIntervalHours: Int = 24,
    val preferProtobuf: Boolean = true,
)

/**
 * 用户弹幕偏好
 */
@Serializable
data class DanmakuUserPreference(
    val userId: String = "",
    val danmakuEnabled: Boolean = true,
    val displaySettings: DanmakuDisplaySettings = DanmakuDisplaySettings(),
)

/**
 * 缓存统计
 */
@Serializable
data class DanmakuCacheStats(
    val totalItems: Int = 0,
    val totalSize: Long = 0,
    val maxSize: Long = 0,
    val usagePercent: Float = 0f,
    val oldestItem: DanmakuCacheItem? = null,
    val newestItem: DanmakuCacheItem? = null,
)

@Serializable
data class DanmakuCacheItem(
    val itemId: String = "",
    val cachedAt: Long = 0,
    val fileSize: Long = 0,
)

/**
 * 缓存列表响应
 */
@Serializable
data class DanmakuCacheListResponse(
    val totalRecordCount: Int = 0,
    val items: List<DanmakuCacheEntry> = emptyList(),
)

@Serializable
data class DanmakuCacheEntry(
    val itemId: String = "",
    val itemName: String = "",
    val source: String = "",
    val sourceId: String = "",
    val cachedAt: Long = 0,
    val expiresAt: Long = 0,
    val fileSize: Long = 0,
    val danmakuCount: Int = 0,
    val format: String = "xml",
)

/**
 * 缓存清理结果
 */
@Serializable
data class DanmakuCleanupResult(
    val removedCount: Int = 0,
    val freedSize: Long = 0,
    val remainingItems: Int = 0,
)

/**
 * 连接测试结果
 */
@Serializable
data class DanmakuSourceTestResult(
    val success: Boolean = false,
    val message: String = "",
    val responseTime: Int = 0,
    val testResult: DanmakuSourceTestDetail? = null,
)

@Serializable
data class DanmakuSourceTestDetail(
    val canSearch: Boolean = false,
    val canFetch: Boolean = false,
    val sampleResult: DanmakuSearchResult? = null,
)
