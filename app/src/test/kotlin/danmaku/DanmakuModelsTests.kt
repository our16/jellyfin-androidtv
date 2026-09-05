package org.jellyfin.androidtv.danmaku

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.data.model.danmaku.*

class DanmakuModelsTests : FunSpec({

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    test("DanmakuFileInfo deserialization") {
        val jsonStr = """
        {
            "itemId": "550e8400-e29b-41d4-a716-446655440000",
            "mediaSourceId": "source_01",
            "hasDanmaku": true,
            "danmakuCount": 2847,
            "source": "bilibili",
            "sourceId": "bv1xx411c7mD",
            "sourceCid": 145418,
            "lastUpdated": 1693500000,
            "format": "xml",
            "fileSize": 524288,
            "duration": 1440000,
            "languages": ["zh-CN", "en"],
            "matchConfidence": 0.95
        }
        """.trimIndent()

        val info = json.decodeFromString<DanmakuFileInfo>(jsonStr)
        info.itemId shouldBe "550e8400-e29b-41d4-a716-446655440000"
        info.mediaSourceId shouldBe "source_01"
        info.hasDanmaku shouldBe true
        info.danmakuCount shouldBe 2847
        info.source shouldBe "bilibili"
        info.sourceId shouldBe "bv1xx411c7mD"
        info.sourceCid shouldBe 145418
        info.lastUpdated shouldBe 1693500000
        info.format shouldBe "xml"
        info.fileSize shouldBe 524288
        info.duration shouldBe 1440000
        info.languages shouldBe listOf("zh-CN", "en")
    }

    test("DanmakuFileInfo default values") {
        val info = DanmakuFileInfo()
        info.itemId shouldBe ""
        info.hasDanmaku shouldBe false
        info.danmakuCount shouldBe 0
        info.source shouldBe ""
        info.languages shouldBe emptyList()
    }

    test("DanmakuUrlResponse deserialization") {
        val jsonStr = """
        {
            "url": "http://192.168.1.100:8096/Danmaku/550e8400/danmaku.xml?token=abc123",
            "expiresAt": 1693503600,
            "format": "xml",
            "fileSize": 524288
        }
        """.trimIndent()

        val response = json.decodeFromString<DanmakuUrlResponse>(jsonStr)
        response.url shouldBe "http://192.168.1.100:8096/Danmaku/550e8400/danmaku.xml?token=abc123"
        response.expiresAt shouldBe 1693503600
        response.format shouldBe "xml"
        response.fileSize shouldBe 524288
    }

    test("DanmakuSearchResponse deserialization") {
        val jsonStr = """
        {
            "totalResults": 2,
            "results": [
                {
                    "source": "bilibili",
                    "sourceId": "bv1xx411c7mD",
                    "sourceCid": 145418,
                    "name": "某科学的超电磁炮S 第1集",
                    "nameOriginal": "とある科学の超電磁砲S 第1話",
                    "category": "番剧",
                    "year": "2020",
                    "episodeNumber": 1,
                    "episodeTitle": "超电磁炮",
                    "duration": 1440000,
                    "matchScore": 0.98,
                    "matchSource": "bilibili",
                    "availableFormats": ["xml", "protobuf"],
                    "thumbnailUrl": "https://i0.hdslb.com/bfs/archive/xxx.jpg"
                },
                {
                    "source": "dandanplay",
                    "sourceId": "12345",
                    "name": "某科学的超电磁炮S",
                    "category": "Anime",
                    "year": "2020",
                    "episodeNumber": 1,
                    "duration": 1440000,
                    "matchScore": 0.95,
                    "matchSource": "dandanplay",
                    "availableFormats": ["xml"]
                }
            ]
        }
        """.trimIndent()

        val response = json.decodeFromString<DanmakuSearchResponse>(jsonStr)
        response.totalResults shouldBe 2
        response.results shouldHaveSize 2
        response.results[0].source shouldBe "bilibili"
        response.results[0].name shouldBe "某科学的超电磁炮S 第1集"
        response.results[1].source shouldBe "dandanplay"
    }

    test("DanmakuSource deserialization") {
        val jsonStr = """
        {
            "id": "bilibili",
            "name": "哔哩哔哩",
            "type": "online",
            "enabled": true,
            "priority": 1,
            "supportedFormats": ["xml", "protobuf"],
            "config": {
                "apiEndpoint": "https://api.bilibili.com",
                "useProtobuf": true
            },
            "stats": {
                "totalRequests": 1234,
                "successRate": 0.98,
                "avgResponseTime": 250,
                "lastError": null,
                "lastErrorTime": null
            }
        }
        """.trimIndent()

        val source = json.decodeFromString<DanmakuSource>(jsonStr)
        source.id shouldBe "bilibili"
        source.name shouldBe "哔哩哔哩"
        source.type shouldBe "online"
        source.enabled shouldBe true
        source.priority shouldBe 1
        source.supportedFormats shouldBe listOf("xml", "protobuf")
        source.config?.apiEndpoint shouldBe "https://api.bilibili.com"
        source.config?.useProtobuf shouldBe true
        source.stats?.totalRequests shouldBe 1234
        source.stats?.avgResponseTime shouldBe 250
        source.stats?.lastError shouldBe null
    }

    test("DanmakuConfig deserialization") {
        val jsonStr = """
        {
            "enabled": true,
            "defaultEnabled": true,
            "autoMatch": true,
            "autoMatchSources": ["bilibili", "dandanplay"],
            "maxCacheSize": 1073741824,
            "cacheExpiryDays": 30,
            "maxDanmakuCount": 5000,
            "defaultDisplaySettings": {
                "fontSize": 25,
                "opacity": 0.8,
                "speed": 1.0,
                "area": 0.7,
                "enabledTypes": [1, 4, 5],
                "blockedColors": [],
                "blockedUsers": [],
                "blockedWords": [],
                "densityLimit": 6
            },
            "updateSettings": {
                "autoUpdate": true,
                "updateIntervalHours": 24,
                "preferProtobuf": true
            }
        }
        """.trimIndent()

        val config = json.decodeFromString<DanmakuConfig>(jsonStr)
        config.enabled shouldBe true
        config.defaultEnabled shouldBe true
        config.autoMatch shouldBe true
        config.autoMatchSources shouldBe listOf("bilibili", "dandanplay")
        config.maxCacheSize shouldBe 1073741824
        config.cacheExpiryDays shouldBe 30
        config.maxDanmakuCount shouldBe 5000
        config.defaultDisplaySettings.fontSize shouldBe 25
        config.defaultDisplaySettings.opacity shouldBe 0.8f
        config.defaultDisplaySettings.speed shouldBe 1.0f
        config.defaultDisplaySettings.area shouldBe 0.7f
        config.defaultDisplaySettings.enabledTypes shouldBe listOf(1, 4, 5)
        config.defaultDisplaySettings.densityLimit shouldBe 6
        config.updateSettings?.autoUpdate shouldBe true
        config.updateSettings?.updateIntervalHours shouldBe 24
        config.updateSettings?.preferProtobuf shouldBe true
    }

    test("DanmakuCacheStats deserialization") {
        val jsonStr = """
        {
            "totalItems": 1234,
            "totalSize": 536870912,
            "maxSize": 1073741824,
            "usagePercent": 50.0,
            "oldestItem": {
                "itemId": "550e8400-e29b-41d4-a716-446655440000",
                "cachedAt": 1690000000,
                "fileSize": 524288
            },
            "newestItem": {
                "itemId": "660e8400-e29b-41d4-a716-446655440001",
                "cachedAt": 1693500000,
                "fileSize": 1048576
            }
        }
        """.trimIndent()

        val stats = json.decodeFromString<DanmakuCacheStats>(jsonStr)
        stats.totalItems shouldBe 1234
        stats.totalSize shouldBe 536870912
        stats.maxSize shouldBe 1073741824
        stats.usagePercent shouldBe 50.0f
        stats.oldestItem?.itemId shouldBe "550e8400-e29b-41d4-a716-446655440000"
        stats.newestItem?.itemId shouldBe "660e8400-e29b-41d4-a716-446655440001"
    }

    test("DanmakuDisplaySettings default values") {
        val settings = DanmakuDisplaySettings()
        settings.fontSize shouldBe 25
        settings.opacity shouldBe 0.8f
        settings.speed shouldBe 1.0f
        settings.area shouldBe 0.7f
        settings.enabledTypes shouldBe listOf(1, 4, 5)
        settings.blockedColors shouldBe emptyList()
        settings.blockedUsers shouldBe emptyList()
        settings.blockedWords shouldBe emptyList()
        settings.densityLimit shouldBe 6
    }

    test("DanmakuTaskResponse deserialization") {
        val jsonStr = """
        {
            "taskId": "task_abc123",
            "status": "processing",
            "message": "弹幕刷新任务已提交"
        }
        """.trimIndent()

        val response = json.decodeFromString<DanmakuTaskResponse>(jsonStr)
        response.taskId shouldBe "task_abc123"
        response.status shouldBe "processing"
        response.message shouldBe "弹幕刷新任务已提交"
    }

    test("DanmakuCleanupResult deserialization") {
        val jsonStr = """
        {
            "removedCount": 45,
            "freedSize": 23592960,
            "remainingItems": 1189
        }
        """.trimIndent()

        val result = json.decodeFromString<DanmakuCleanupResult>(jsonStr)
        result.removedCount shouldBe 45
        result.freedSize shouldBe 23592960
        result.remainingItems shouldBe 1189
    }

    test("DanmakuSourceTestResult deserialization") {
        val jsonStr = """
        {
            "success": true,
            "message": "连接成功",
            "responseTime": 250,
            "testResult": {
                "canSearch": true,
                "canFetch": true,
                "sampleResult": {
                    "source": "bilibili",
                    "sourceId": "bv1xx411c7mD",
                    "name": "测试弹幕",
                    "matchScore": 0.95
                }
            }
        }
        """.trimIndent()

        val result = json.decodeFromString<DanmakuSourceTestResult>(jsonStr)
        result.success shouldBe true
        result.message shouldBe "连接成功"
        result.responseTime shouldBe 250
        result.testResult?.canSearch shouldBe true
        result.testResult?.canFetch shouldBe true
        result.testResult?.sampleResult?.source shouldBe "bilibili"
    }

    test("DanmakuUserInfo deserialization") {
        val jsonStr = """
        {
            "userId": "550e8400-e29b-41d4-a716-446655440000",
            "danmakuEnabled": true,
            "displaySettings": {
                "fontSize": 25,
                "opacity": 0.8,
                "speed": 1.0,
                "area": 0.7,
                "enabledTypes": [1, 4, 5],
                "blockedColors": [0],
                "blockedUsers": ["user_hash_1"],
                "blockedWords": ["广告", "推广"]
            }
        }
        """.trimIndent()

        val pref = json.decodeFromString<DanmakuUserPreference>(jsonStr)
        pref.userId shouldBe "550e8400-e29b-41d4-a716-446655440000"
        pref.danmakuEnabled shouldBe true
        pref.displaySettings.fontSize shouldBe 25
        pref.displaySettings.blockedUsers shouldBe listOf("user_hash_1")
        pref.displaySettings.blockedWords shouldBe listOf("广告", "推广")
    }

    test("DanmakuCacheEntry deserialization") {
        val jsonStr = """
        {
            "itemId": "550e8400-e29b-41d4-a716-446655440000",
            "itemName": "某科学的超电磁炮S 第1集",
            "source": "bilibili",
            "sourceId": "bv1xx411c7mD",
            "cachedAt": 1693500000,
            "expiresAt": 1696092000,
            "fileSize": 524288,
            "danmakuCount": 2847,
            "format": "xml"
        }
        """.trimIndent()

        val entry = json.decodeFromString<DanmakuCacheEntry>(jsonStr)
        entry.itemId shouldBe "550e8400-e29b-41d4-a716-446655440000"
        entry.itemName shouldBe "某科学的超电磁炮S 第1集"
        entry.source shouldBe "bilibili"
        entry.fileSize shouldBe 524288
        entry.danmakuCount shouldBe 2847
    }

    test("DanmakuTaskStatus deserialization") {
        val jsonStr = """
        {
            "taskId": "task_abc123",
            "status": "completed",
            "progress": 100,
            "result": {
                "danmakuCount": 2847,
                "source": "bilibili",
                "fileSize": 524288
            },
            "createdAt": 1693500000,
            "completedAt": 1693500005
        }
        """.trimIndent()

        val status = json.decodeFromString<DanmakuTaskStatus>(jsonStr)
        status.taskId shouldBe "task_abc123"
        status.status shouldBe "completed"
        status.progress shouldBe 100
        status.result?.danmakuCount shouldBe 2847
        status.result?.source shouldBe "bilibili"
        status.createdAt shouldBe 1693500000
        status.completedAt shouldBe 1693500005
    }

    test("DanmakuSearchResult serialization roundtrip") {
        val original = DanmakuSearchResult(
            source = "bilibili",
            sourceId = "bv1xx411c7mD",
            sourceCid = 145418,
            name = "测试视频",
            nameOriginal = "テスト動画",
            category = "番剧",
            year = "2024",
            episodeNumber = 1,
            episodeTitle = "第1集",
            duration = 1440000,
            matchScore = 0.98f,
            matchSource = "bilibili",
            availableFormats = listOf("xml", "protobuf"),
            thumbnailUrl = "https://example.com/thumb.jpg",
        )

        val serialized = json.encodeToString(DanmakuSearchResult.serializer(), original)
        val deserialized = json.decodeFromString<DanmakuSearchResult>(serialized)

        deserialized.source shouldBe original.source
        deserialized.sourceId shouldBe original.sourceId
        deserialized.name shouldBe original.name
        deserialized.matchScore shouldBe original.matchScore
        deserialized.availableFormats shouldBe original.availableFormats
    }

    test("DanmakuConfig serialization roundtrip") {
        val original = DanmakuConfig(
            enabled = true,
            defaultEnabled = false,
            autoMatch = true,
            autoMatchSources = listOf("bilibili"),
            maxCacheSize = 2147483648,
            cacheExpiryDays = 60,
            maxDanmakuCount = 10000,
            defaultDisplaySettings = DanmakuDisplaySettings(
                fontSize = 36,
                opacity = 0.9f,
                speed = 1.2f,
                area = 0.8f,
                enabledTypes = listOf(1, 5),
                blockedColors = listOf(0),
                blockedUsers = listOf("user1"),
                blockedWords = listOf("广告"),
                densityLimit = 10,
            ),
            updateSettings = DanmakuUpdateSettings(
                autoUpdate = false,
                updateIntervalHours = 48,
                preferProtobuf = false,
            ),
        )

        val serialized = json.encodeToString(DanmakuConfig.serializer(), original)
        val deserialized = json.decodeFromString<DanmakuConfig>(serialized)

        deserialized.enabled shouldBe original.enabled
        deserialized.defaultEnabled shouldBe original.defaultEnabled
        deserialized.autoMatch shouldBe original.autoMatch
        deserialized.maxCacheSize shouldBe original.maxCacheSize
        deserialized.defaultDisplaySettings.fontSize shouldBe original.defaultDisplaySettings.fontSize
        deserialized.updateSettings?.autoUpdate shouldBe original.updateSettings?.autoUpdate
    }
})
