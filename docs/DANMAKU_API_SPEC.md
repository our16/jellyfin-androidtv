# Jellyfin Danmaku API Specification

> 版本: 1.1.0  
> 状态: 已实现（服务端已部署并通过端到端验证）  
> 兼容: jellyfin-plugin-danmu (cxfksword) + 弹弹play 接口规范

## 实现状态 (v1.1.0)

| 接口 | 状态 | 说明 |
|------|------|------|
| `GET /api/danmaku/{itemId}` | ✅ 已验证 | 缓存命中返回 `hasDanmaku:true` + 真实 `danmakuCount`；未命中 404 `DanmakuNotFound` |
| `GET /api/danmaku/{itemId}/url` | ✅ 已验证 | 响应字段已修正为 camelCase（`url`/`expiresAt`/`format`/`fileSize`） |
| `GET /api/danmaku/{itemId}/raw` | ✅ 已验证 | `application/xml`，返回标准 Bilibili XML |
| `GET /api/danmaku/{itemId}/danmaku.{format}` | ✅ 已验证 | 直接下载缓存文件 |
| `POST /api/danmaku/{itemId}/refresh` | ✅ 已实现 | 依赖条目名自动匹配，日期命名的条目（如 `20150425期.HDTV`）匹配率低，建议客户端用 5.2 搜索接口做手动绑定 |
| `GET /api/danmaku/search` | ✅ 已验证 | Bilibili 搜索正常（服务端直连，无需代理） |
| `GET /api/danmaku/search/by-item/{itemId}` | ✅ 已实现 | 按条目名搜索 |
| `GET /api/danmaku/config` / `PUT` | ✅ 已验证 | 默认 `autoMatch:true`，源 `["bilibili","dandanplay"]` |
| `GET /api/danmaku/sources` | ✅ 已验证 | 返回 local(禁用)/bilibili/dandanplay |
| 缓存管理接口 (8.x) | ✅ 已实现 | 缓存目录 `{DataPath}/danmaku-cache/`，文件名 `{itemId}.{xml\|json}`，索引 `cache-index.json`（camelCase 字段） |
| `GET /api/danmaku/{itemId}/protobuf` (4.4) | ❌ 未实现 | 设计稿保留，当前仅 xml/json |
| 弹弹play 兼容接口 (5.3) | ✅ 已实现 | `/api/v2/search/anime` 等 |

**itemId 格式**：路由参数接受带/不带连字符的 Guid；服务端缓存键为 **带连字符** 的 `Guid.ToString()` 格式（如 `2ef335bb-22cc-9727-f8df-ecc1f897e9f7`）。

**响应大小写**：所有 DTO 已通过 `[JsonPropertyName]` 显式声明 camelCase 字段名，客户端按本文档字段名解析即可。

---

## 目录

1. [概述](#1-概述)
2. [认证与授权](#2-认证与授权)
3. [通用约定](#3-通用约定)
4. [核心接口](#4-核心接口)
5. [搜索接口](#5-搜索接口)
6. [弹幕源管理接口](#6-弹幕源管理接口)
7. [配置接口](#7-配置接口)
8. [缓存管理接口](#8-缓存管理接口)
9. [数据模型](#9-数据模型)
10. [错误码](#10-错误码)
11. [客户端集成指南](#11-客户端集成指南)
12. [附录](#12-附录)

---

## 1. 概述

### 1.1 架构

```
┌─────────────────────────────────────────────────────────┐
│                    Jellyfin Server                       │
│  ┌───────────────────────────────────────────────────┐  │
│  │           Danmaku Plugin (核心)                    │  │
│  │                                                   │  │
│  │  ┌─────────────┐  ┌──────────────┐  ┌─────────┐ │  │
│  │  │  API层       │  │  引擎层       │  │ 缓存层  │ │  │
│  │  │ Controller  │  │  SourceMgr   │  │ Cache   │ │  │
│  │  └──────┬──────┘  └──────┬───────┘  └────┬────┘ │  │
│  │         │                │               │       │  │
│  │         │    ┌───────────┴───────────┐   │       │  │
│  │         │    │   弹幕源适配器         │   │       │  │
│  │         │    │   ┌───────────────┐   │   │       │  │
│  │         │    │   │ BilibiliSource│   │   │       │  │
│  │         │    │   │ DandanplaySrc │   │   │       │  │
│  │         │    │   │ CustomSource  │   │   │       │  │
│  │         │    │   └───────────────┘   │   │       │  │
│  │         │    └───────────────────────┘   │       │  │
│  │         │                │               │       │  │
│  │         └────────────────┴───────────────┘       │  │
│  └───────────────────────────────────────────────────┘  │
│                        │                                │
│                        ▼                                │
│              ┌─────────────────┐                        │
│              │  LibraryManager │                        │
│              │  (元数据/媒体)   │                        │
│              └─────────────────┘                        │
└─────────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────┐
│              外部弹幕源 (Bilibili / 弹弹play / ...)     │
└─────────────────────────────────────────────────────────┘
```

### 1.2 弹幕数据流

```
1. 客户端请求弹幕 → /api/danmaku/{itemId}
2. 服务端查找已缓存的弹幕文件
3. 若无缓存，根据媒体元数据匹配外部弹幕源
4. 从外部源下载弹幕数据
5. 转换为统一 XML 格式并缓存
6. 返回弹幕文件 URL 或直接内容
7. 客户端下载 XML → 解析 → 渲染
```

### 1.3 支持的弹幕源

| 源ID | 名称 | 类型 | 说明 |
|------|------|------|------|
| `bilibili` | 哔哩哔哩 | 在线 | 通过 BV/AV 号或 CID 匹配 |
| `dandanplay` | 弹弹play | 在线 | 兼容接口规范 |
| `youku` | 优酷 | 在线 | 通过视频ID匹配 |
| `iqiyi` | 爱奇艺 | 在线 | 通过视频ID匹配 |
| `tencent` | 腾讯视频 | 在线 | 通过视频ID匹配 |
| `mango` | 芒果TV | 在线 | 通过视频ID匹配 |
| `local` | 本地文件 | 本地 | 服务器文件系统中的 XML/JSON 文件 |
| `custom` | 自定义API | 在线 | 用户自定义弹幕源 |

---

## 2. 认证与授权

### 2.1 认证方式

所有 API 端点均需要有效的 Jellyfin 认证 Token。

**请求头格式：**

```
Authorization: MediaBrowser Token="<access_token>"
X-Emby-Authorization: MediaBrowser Client="Jellyfin for Android TV", Device="Android TV", DeviceId="<device_id>", Version="<version>"
```

### 2.2 权限要求

| 操作 | 最低权限 | 说明 |
|------|----------|------|
| 读取弹幕 | 普通用户 | 可播放媒体的用户 |
| 搜索弹幕 | 普通用户 | 可搜索媒体的用户 |
| 管理弹幕源 | 管理员 | 需要管理员权限 |
| 刷新弹幕缓存 | 管理员 | 需要管理员权限 |
| 修改全局配置 | 管理员 | 需要管理员权限 |

---

## 3. 通用约定

### 3.1 基础URL

```
http(s)://<server_host>:<server_port>
```

默认端口: `8096` (HTTP) / `8920` (HTTPS)

### 3.2 请求头

| Header | 必需 | 说明 |
|--------|------|------|
| `Authorization` | 是 | MediaBrowser Token |
| `Accept` | 否 | `application/json; profile="CamelCase"` (默认) 或 `application/json` |
| `Content-Type` | 否 | `application/json` (POST/PUT 请求) |

### 3.3 响应格式

**成功响应：**

```json
{
  "field1": "value1",
  "field2": 123
}
```

**错误响应：**

```json
{
  "errorCode": "ErrorMessage",
  "message": "Detailed error description",
  "statusCode": 404
}
```

### 3.4 分页参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `startIndex` | int | 0 | 起始索引 |
| `limit` | int | 50 | 返回数量上限 (最大 200) |
| `sortBy` | string | `Name` | 排序字段 |
| `sortOrder` | string | `Ascending` | 排序方向: `Ascending` / `Descending` |

### 3.5 时间格式

- 时间戳使用 **Unix 时间戳** (秒)
- 持续时间使用 **毫秒** (ms)
- Jellyfin Ticks: `1 tick = 100 nanoseconds`，转换公式: `ticks / 10000 = ms`

---

## 4. 核心接口

### 4.1 获取弹幕信息

获取指定媒体项目的弹幕元数据 (不包含实际弹幕内容)。

```
GET /api/danmaku/{itemId}
```

**路径参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `itemId` | UUID | 是 | Jellyfin 媒体项目 ID |

**查询参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `mediaSourceId` | string | 否 | 指定媒体源 ID，不指定则返回默认源 |

**响应 (200 OK)：**

```json
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
  "languages": ["zh-CN"],
  "matchConfidence": 0.95
}
```

**响应字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `itemId` | UUID | 媒体项目 ID |
| `mediaSourceId` | string | 媒体源 ID |
| `hasDanmaku` | boolean | 是否有可用弹幕 |
| `danmakuCount` | int | 弹幕条目数量 |
| `source` | string | 弹幕源标识 (`bilibili` / `dandanplay` / `local` / ...) |
| `sourceId` | string | 弹幕源中的视频 ID |
| `sourceCid` | int | 弹幕源中的 CID (Bilibili) |
| `lastUpdated` | long | 上次更新时间戳 (秒) |
| `format` | string | 弹幕格式 (`xml` / `json` / `protobuf`) |
| `fileSize` | long | 弹幕文件大小 (字节) |
| `duration` | int | 视频时长 (毫秒) |
| `languages` | string[] | 弹幕语言列表 |
| `matchConfidence` | float | 匹配置信度 (0.0 - 1.0) |

**错误响应：**

| 状态码 | 错误码 | 说明 |
|--------|--------|------|
| 404 | `ItemNotFound` | 找不到指定的媒体项目 |
| 404 | `DanmakuNotFound` | 该项目无可用弹幕 |
| 401 | `Unauthorized` | 未认证 |
| 403 | `Forbidden` | 无权限访问该项目 |

---

### 4.2 获取弹幕内容 (URL)

获取弹幕文件的下载 URL，客户端自行下载。

```
GET /api/danmaku/{itemId}/url
```

**路径参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `itemId` | UUID | 是 | Jellyfin 媒体项目 ID |

**查询参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `mediaSourceId` | string | 否 | 指定媒体源 ID |
| `format` | string | 否 | 目标格式: `xml` (默认) / `json` |

**响应 (200 OK)：**

```json
{
  "url": "http://192.168.1.100:8096/Danmaku/550e8400/danmaku.xml?token=abc123",
  "expiresAt": 1693503600,
  "format": "xml",
  "fileSize": 524288
}
```

**响应字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `url` | string | 弹幕文件下载 URL (含临时 Token) |
| `expiresAt` | long | URL 过期时间戳 (秒)，默认 24 小时 |
| `format` | string | 文件格式 |
| `fileSize` | long | 文件大小 (字节) |

---

### 4.3 获取弹幕内容 (Raw)

直接返回弹幕文件内容 (适用于小文件)。

```
GET /api/danmaku/{itemId}/raw
```

**路径参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `itemId` | UUID | 是 | Jellyfin 媒体项目 ID |

**查询参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `mediaSourceId` | string | 否 | 指定媒体源 ID |

**响应 (200 OK)：**

- Content-Type: `application/xml` 或 `text/xml`
- Body: XML 弹幕文件内容

```xml
<?xml version="1.0" encoding="UTF-8"?>
<i>
    <chatserver>chat.bilibili.com</chatserver>
    <chatid>145418</chatid>
    <maxlimit>1500</maxlimit>
    <state>0</state>
    <real_name>0</real_name>
    <source>e-r</source>
    <d p="490.191,1,25,16777215,1584278492,0,59417e95,711923911">弹幕内容1</d>
    <d p="120.5,1,25,16777215,1584278500,0,abc123,711923912">弹幕内容2</d>
</i>
```

---

### 4.4 获取弹幕内容 (Protobuf)

返回 Protobuf 格式的弹幕数据 (高效二进制格式)。

```
GET /api/danmaku/{itemId}/protobuf
```

**路径参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `itemId` | UUID | 是 | Jellyfin 媒体项目 ID |

**查询参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `mediaSourceId` | string | 否 | 指定媒体源 ID |
| `segment` | int | 否 | 分段索引 (默认 1，每段 6 分钟) |

**响应 (200 OK)：**

- Content-Type: `application/x-protobuf`
- Body: `DmSegMobileReply` protobuf 消息

**Protobuf 定义：**

```protobuf
syntax = "proto3";
package bilibili.community.service.dm.v1;

message DmSegMobileReply {
    repeated DanmakuElem elems = 1;
}

message DanmakuElem {
    int64 id = 1;           // 弹幕 ID
    int32 progress = 2;     // 出现时间 (毫秒)
    int32 mode = 3;         // 类型: 1-3=滚动, 4=底部, 5=顶部, 6=逆向, 7=高级
    int32 fontsize = 4;     // 字号: 18=小, 25=标准, 36=大
    uint32 color = 5;       // 颜色 (RGB888 十进制)
    string midHash = 6;     // 用户 ID Hash
    string content = 7;     // 弹幕内容
    int64 ctime = 8;        // 发送时间戳
    int32 weight = 9;       // 权重 (0-10)
    string action = 10;     // 动作
    int32 pool = 11;        // 弹幕池: 0=普通, 1=字幕, 2=特殊
    string idStr = 12;      // 弹幕 ID 字符串
    int32 attr = 13;        // 属性位
}
```

---

### 4.5 刷新弹幕

强制重新获取指定媒体项目的弹幕数据。

```
POST /api/danmaku/{itemId}/refresh
```

**路径参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `itemId` | UUID | 是 | Jellyfin 媒体项目 ID |

**请求体 (可选)：**

```json
{
  "source": "bilibili",
  "force": true
}
```

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `source` | string | 否 | 指定弹幕源，不指定则自动匹配 |
| `force` | boolean | 否 | 是否强制刷新 (忽略缓存)，默认 false |

**响应 (202 Accepted)：**

```json
{
  "taskId": "task_abc123",
  "status": "processing",
  "message": "弹幕刷新任务已提交"
}
```

**异步任务状态查询：**

```
GET /api/danmaku/tasks/{taskId}
```

**响应 (200 OK)：**

```json
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
```

---

### 4.6 删除弹幕缓存

删除指定媒体项目的弹幕缓存文件。

```
DELETE /api/danmaku/{itemId}
```

**路径参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `itemId` | UUID | 是 | Jellyfin 媒体项目 ID |

**查询参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `mediaSourceId` | string | 否 | 指定媒体源 ID，不指定则删除所有源 |

**响应 (204 No Content)：**

无响应体。

---

## 5. 搜索接口

### 5.1 全局搜索弹幕

在所有已启用的弹幕源中搜索匹配的弹幕。

```
GET /api/danmaku/search
```

**查询参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `keyword` | string | 是 | 搜索关键词 (标题、番号等) |
| `sources` | string | 否 | 逗号分隔的弹幕源列表，默认全部 |
| `limit` | int | 否 | 返回数量上限，默认 20 |

**响应 (200 OK)：**

```json
{
  "totalResults": 45,
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
```

### 5.2 按媒体项目搜索

根据 Jellyfin 媒体项目自动搜索匹配弹幕。

```
GET /api/danmaku/search/by-item/{itemId}
```

**路径参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `itemId` | UUID | 是 | Jellyfin 媒体项目 ID |

**查询参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `sources` | string | 否 | 逗号分隔的弹幕源列表 |
| `limit` | int | 否 | 返回数量上限，默认 10 |

**响应 (200 OK)：**

返回格式同 5.1，但自动使用媒体元数据进行搜索。

---

### 5.3 弹弹play 兼容接口

#### 5.3.1 搜索动漫

```
GET /api/v2/search/anime
```

**查询参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `keyword` | string | 是 | 搜索关键词 |

**响应 (200 OK)：**

```json
{
  "errorCode": 0,
  "success": true,
  "errorMessage": null,
  "animes": [
    {
      "id": "12345",
      "name": "某科学的超电磁炮S",
      "nameOriginal": "とある科学の超電磁砲S",
      "category": "番剧",
      "year": "2020",
      "episodeSize": 25,
      "site": "bilibili",
      "siteId": "bv1xx411c7mD"
    }
  ]
}
```

#### 5.3.2 搜索剧集

```
GET /api/v2/search/episodes
```

**查询参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `anime` | string | 是 | 动漫 ID 或名称 |

**响应 (200 OK)：**

```json
{
  "errorCode": 0,
  "success": true,
  "errorMessage": null,
  "episodes": [
    {
      "id": "ep_001",
      "commentId": "145418",
      "number": 1,
      "title": "超电磁炮",
      "site": "bilibili"
    }
  ]
}
```

#### 5.3.3 获取影视详情

```
GET /api/v2/bangumi/{bangumiId}
```

**响应 (200 OK)：**

```json
{
  "errorCode": 0,
  "success": true,
  "errorMessage": null,
  "bangumi": {
    "id": "12345",
    "name": "某科学的超电磁炮S",
    "nameOriginal": "とある科学の超電磁砲S",
    "category": "番剧",
    "year": "2020",
    "episodes": [
      {
        "id": "ep_001",
        "commentId": "145418",
        "number": 1,
        "title": "超电磁炮"
      }
    ]
  }
}
```

#### 5.3.4 获取弹幕内容

```
GET /api/v2/comment/{episodeId}
```

**查询参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `format` | string | 否 | 输出格式: `xml` (默认) / `json` |

**响应 (200 OK) - format=xml：**

Content-Type: `application/xml`，返回标准 XML 弹幕。

**响应 (200 OK) - format=json：**

```json
{
  "errorCode": 0,
  "success": true,
  "errorMessage": null,
  "comments": [
    {
      "cid": 711923911,
      "progress": 490191,
      "mode": 1,
      "fontSize": 25,
      "color": 16777215,
      "content": "弹幕内容",
      "sendTime": 1584278492,
      "userId": "59417e95",
      "weight": 6
    }
  ]
}
```

---

## 6. 弹幕源管理接口

### 6.1 获取已配置的弹幕源

```
GET /api/danmaku/sources
```

**响应 (200 OK)：**

```json
{
  "sources": [
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
    },
    {
      "id": "dandanplay",
      "name": "弹弹play",
      "type": "online",
      "enabled": true,
      "priority": 2,
      "supportedFormats": ["xml"],
      "config": {
        "apiEndpoint": "https://www.dandanplay.com"
      },
      "stats": {
        "totalRequests": 567,
        "successRate": 0.95,
        "avgResponseTime": 400,
        "lastError": "Connection timeout",
        "lastErrorTime": 1693400000
      }
    },
    {
      "id": "local",
      "name": "本地文件",
      "type": "local",
      "enabled": false,
      "priority": 0,
      "supportedFormats": ["xml", "json"],
      "config": {
        "scanPaths": ["/media/danmaku", "/config/danmaku"],
        "filePattern": "*.xml"
      },
      "stats": {
        "totalRequests": 0,
        "successRate": 1.0,
        "avgResponseTime": 5,
        "lastError": null,
        "lastErrorTime": null
      }
    }
  ]
}
```

### 6.2 更新弹幕源配置

```
PUT /api/danmaku/sources/{sourceId}
```

**路径参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `sourceId` | string | 是 | 弹幕源 ID |

**请求体：**

```json
{
  "enabled": true,
  "priority": 1,
  "config": {
    "apiEndpoint": "https://api.bilibili.com",
    "useProtobuf": true
  }
}
```

**响应 (200 OK)：**

返回更新后的完整源配置 (同 6.1 格式)。

### 6.3 添加自定义弹幕源

```
POST /api/danmaku/sources/custom
```

**请求体：**

```json
{
  "name": "我的弹幕源",
  "apiEndpoint": "https://my-danmaku-api.example.com",
  "apiKey": "my-api-key",
  "format": "xml",
  "searchPath": "/api/v2/search/anime?keyword={keyword}",
  "commentPath": "/api/v2/comment/{commentId}?format=xml",
  "enabled": true
}
```

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 显示名称 |
| `apiEndpoint` | string | 是 | API 基础 URL |
| `apiKey` | string | 否 | API 密钥 (如需认证) |
| `format` | string | 是 | 弹幕格式: `xml` / `json` |
| `searchPath` | string | 是 | 搜索路径模板，`{keyword}` 为占位符 |
| `commentPath` | string | 是 | 弹幕获取路径模板，`{commentId}` 为占位符 |
| `enabled` | boolean | 否 | 是否启用，默认 true |

**响应 (201 Created)：**

返回创建的源配置 (同 6.1 格式)。

### 6.4 删除自定义弹幕源

```
DELETE /api/danmaku/sources/custom/{sourceId}
```

**响应 (204 No Content)：**

无响应体。

### 6.5 测试弹幕源连接

```
POST /api/danmaku/sources/{sourceId}/test
```

**响应 (200 OK)：**

```json
{
  "success": true,
  "message": "连接成功",
  "responseTime": 250,
  "testResult": {
    "canSearch": true,
    "canFetch": true,
    "sampleResult": {
      "name": "测试弹幕",
      "danmakuCount": 100
    }
  }
}
```

---

## 7. 配置接口

### 7.1 获取全局弹幕配置

```
GET /api/danmaku/config
```

**响应 (200 OK)：**

```json
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
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `enabled` | boolean | 全局弹幕开关 |
| `defaultEnabled` | boolean | 新播放默认开启弹幕 |
| `autoMatch` | boolean | 自动匹配弹幕 |
| `autoMatchSources` | string[] | 自动匹配使用的源列表 |
| `maxCacheSize` | long | 最大缓存大小 (字节) |
| `cacheExpiryDays` | int | 缓存过期天数 |
| `maxDanmakuCount` | int | 单个视频最大弹幕数 |
| `defaultDisplaySettings` | object | 默认显示设置 |
| `updateSettings` | object | 更新策略设置 |

### 7.2 更新全局弹幕配置

```
PUT /api/danmaku/config
```

**请求体：**

```json
{
  "enabled": true,
  "defaultEnabled": true,
  "autoMatch": true,
  "autoMatchSources": ["bilibili", "dandanplay"],
  "maxCacheSize": 1073741824,
  "cacheExpiryDays": 30
}
```

**响应 (200 OK)：**

返回更新后的完整配置 (同 7.1 格式)。

### 7.3 获取用户弹幕偏好

```
GET /api/danmaku/config/user/{userId}
```

**路径参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `userId` | UUID | 是 | 用户 ID |

**响应 (200 OK)：**

```json
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
```

### 7.4 更新用户弹幕偏好

```
PUT /api/danmaku/config/user/{userId}
```

**请求体：**

```json
{
  "danmakuEnabled": true,
  "displaySettings": {
    "fontSize": 25,
    "opacity": 0.8,
    "speed": 1.0,
    "area": 0.7,
    "enabledTypes": [1, 4, 5],
    "blockedColors": [0],
    "blockedUsers": [],
    "blockedWords": []
  }
}
```

**响应 (200 OK)：**

返回更新后的用户偏好 (同 7.3 格式)。

---

## 8. 缓存管理接口

### 8.1 获取缓存统计

```
GET /api/danmaku/cache/stats
```

**响应 (200 OK)：**

```json
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
```

### 8.2 获取缓存列表

```
GET /api/danmaku/cache
```

**查询参数：**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `startIndex` | int | 否 | 起始索引 |
| `limit` | int | 否 | 返回数量上限 |
| `sortBy` | string | 否 | 排序: `cachedAt` / `fileSize` / `itemId` |
| `sortOrder` | string | 否 | `Ascending` / `Descending` |

**响应 (200 OK)：**

```json
{
  "totalRecordCount": 1234,
  "items": [
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
  ]
}
```

### 8.3 清理过期缓存

```
POST /api/danmaku/cache/cleanup
```

**响应 (200 OK)：**

```json
{
  "removedCount": 45,
  "freedSize": 23592960,
  "remainingItems": 1189
}
```

### 8.4 清空全部缓存

```
DELETE /api/danmaku/cache
```

**响应 (204 No Content)：**

无响应体。

---

## 9. 数据模型

### 9.1 DanmakuFileInfo

```json
{
  "itemId": "UUID",
  "mediaSourceId": "string",
  "hasDanmaku": "boolean",
  "danmakuCount": "int",
  "source": "string",
  "sourceId": "string",
  "sourceCid": "int",
  "lastUpdated": "long (timestamp)",
  "format": "string (xml|json|protobuf)",
  "fileSize": "long (bytes)",
  "duration": "int (ms)",
  "languages": "string[]",
  "matchConfidence": "float (0.0-1.0)"
}
```

### 9.2 DanmakuItem

```json
{
  "id": "long",
  "time": "float (seconds)",
  "type": "int (1-9)",
  "fontSize": "int (18|25|36)",
  "color": "int (RGB888 decimal)",
  "timestamp": "long (unix timestamp)",
  "pool": "int (0|1|2)",
  "userIdHash": "string",
  "content": "string",
  "weight": "int (0-10)"
}
```

### 9.3 DanmakuType 枚举

| 值 | 名称 | 说明 |
|----|------|------|
| 1 | ScrollRL | 滚动弹幕 (从右到左) |
| 2 | ScrollRL_2 | 滚动弹幕 (特殊) |
| 3 | ScrollRL_3 | 滚动弹幕 (高级) |
| 4 | Bottom | 底部固定弹幕 |
| 5 | Top | 顶部固定弹幕 |
| 6 | ScrollLR | 滚动弹幕 (从左到右) |
| 7 | Special | 高级弹幕 |
| 8 | Code | 代码弹幕 |
| 9 | BAS | BAS 弹幕 |

### 9.4 DanmakuSource

```json
{
  "id": "string",
  "name": "string",
  "type": "string (online|local|custom)",
  "enabled": "boolean",
  "priority": "int",
  "supportedFormats": "string[]",
  "config": "object",
  "stats": "DanmakuSourceStats"
}
```

### 9.5 DanmakuSourceStats

```json
{
  "totalRequests": "int",
  "successRate": "float (0.0-1.0)",
  "avgResponseTime": "int (ms)",
  "lastError": "string|null",
  "lastErrorTime": "long|null"
}
```

### 9.6 DanmakuDisplaySettings

```json
{
  "fontSize": "int (18|25|36)",
  "opacity": "float (0.0-1.0)",
  "speed": "float (0.5-2.0)",
  "area": "float (0.0-1.0)",
  "enabledTypes": "int[]",
  "blockedColors": "int[]",
  "blockedUsers": "string[]",
  "blockedWords": "string[]",
  "densityLimit": "int (每秒最大弹幕数)"
}
```

### 9.7 弹幕颜色对照表

| 颜色 | HEX | RGB888 (十进制) |
|------|-----|-----------------|
| 红色 | #FE0302 | 16646914 |
| 橘红 | #FF7204 | 16740868 |
| 橘黄 | #FFAA02 | 16755202 |
| 淡黄 | #FFD302 | 16765698 |
| 黄色 | #FFFF00 | 16776960 |
| 草绿 | #A0EE00 | 10546688 |
| 绿色 | #00CD00 | 52480 |
| 墨绿 | #019899 | 104601 |
| 紫色 | #4266BE | 4351678 |
| 青色 | #89D5FF | 9022215 |
| 品红 | #CC0273 | 13369971 |
| 黑色 | #222222 | 2236962 |
| 灰色 | #9B9B9B | 10197915 |
| 白色 | #FFFFFF | 16777215 |

---

## 10. 错误码

### 10.1 HTTP 状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 201 | 已创建 |
| 202 | 已接受 (异步任务) |
| 204 | 成功 (无响应体) |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 冲突 (如重复添加) |
| 429 | 请求过于频繁 |
| 500 | 服务器内部错误 |
| 502 | 外部弹幕源不可达 |
| 503 | 服务暂时不可用 |

### 10.2 业务错误码

| 错误码 | HTTP状态码 | 说明 |
|--------|-----------|------|
| `ItemNotFound` | 404 | 找不到指定的媒体项目 |
| `DanmakuNotFound` | 404 | 该项目无可用弹幕 |
| `SourceNotFound` | 404 | 找不到指定的弹幕源 |
| `InvalidSourceConfig` | 400 | 弹幕源配置无效 |
| `SourceConnectionFailed` | 502 | 弹幕源连接失败 |
| `SourceSearchFailed` | 502 | 弹幕源搜索失败 |
| `SourceDownloadFailed` | 502 | 弹幕源下载失败 |
| `CacheFull` | 507 | 缓存空间不足 |
| `TaskNotFound` | 404 | 找不到指定的异步任务 |
| `TaskFailed` | 500 | 异步任务执行失败 |
| `RateLimited` | 429 | 请求过于频繁 |
| `UnsupportedFormat` | 400 | 不支持的弹幕格式 |
| `CustomSourceExists` | 409 | 自定义弹幕源已存在 |

---

## 11. 客户端集成指南

### 11.1 Android TV 客户端调用流程

```
1. 播放开始
   │
   ├─► 检查 UserPreferences.danmakuEnabled
   │   └─► 如果 false，跳过弹幕加载
   │
   ├─► 调用 GET /api/danmaku/{itemId}
   │   ├─► 成功 → 获取弹幕元数据
   │   └─► 404 → 尝试搜索 GET /api/danmaku/search/by-item/{itemId}
   │
   ├─► 调用 GET /api/danmaku/{itemId}/url
   │   └─► 获取弹幕文件 URL
   │
   ├─► 下载弹幕 XML 文件
   │   └─► 使用 JellyfinDanmakuParser 解析
   │
   ├─► 调用 DanmakuManager.loadDanmaku(parser)
   │   └─► 加载到渲染引擎
   │
   └─► 播放过程中
       ├─► pause → DanmakuManager.pause()
       ├─► resume → DanmakuManager.resume()
       ├─► seek → DanmakuManager.seekTo(position)
       └─► toggle → DanmakuManager.toggleVisibility()
```

### 11.2 Kotlin 客户端示例代码

```kotlin
class DanmakuRepository(
    private val api: ApiClient,
    private val context: Context,
) {
    /**
     * 获取弹幕信息
     */
    suspend fun getDanmakuInfo(
        itemId: UUID,
        mediaSourceId: String? = null,
    ): DanmakuFileInfo? {
        val url = buildString {
            append("${api.baseUrl}/api/danmaku/$itemId")
            mediaSourceId?.let { append("?mediaSourceId=$it") }
        }
        
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", 
                "MediaBrowser Token=\"${api.accessToken}\"")
            connection.setRequestProperty("Accept", 
                "application/json; profile=\"CamelCase\"")
            
            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().readText()
                Json.decodeFromString<DanmakuFileInfo>(json)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get danmaku info")
            null
        }
    }
    
    /**
     * 下载弹幕内容
     */
    suspend fun downloadDanmaku(
        itemId: UUID,
        mediaSourceId: String? = null,
    ): String? {
        val url = buildString {
            append("${api.baseUrl}/api/danmaku/$itemId/raw")
            mediaSourceId?.let { append("?mediaSourceId=$it") }
        }
        
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", 
                "MediaBrowser Token=\"${api.accessToken}\"")
            
            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download danmaku")
            null
        }
    }
    
    /**
     * 搜索弹幕
     */
    suspend fun searchDanmaku(
        keyword: String,
        sources: List<String>? = null,
    ): List<DanmakuSearchResult> {
        val params = buildString {
            append("keyword=${URLEncoder.encode(keyword, "UTF-8")}")
            sources?.let { append("&sources=${it.joinToString(",")}") }
        }
        
        val url = "${api.baseUrl}/api/danmaku/search?$params"
        
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", 
                "MediaBrowser Token=\"${api.accessToken}\"")
            connection.setRequestProperty("Accept", 
                "application/json; profile=\"CamelCase\"")
            
            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().readText()
                val response = Json.decodeFromString<DanmakuSearchResponse>(json)
                response.results
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to search danmaku")
            emptyList()
        }
    }
}
```

### 11.3 错误处理最佳实践

```kotlin
// 1. 重试机制
suspend fun fetchDanmakuWithRetry(
    itemId: UUID,
    maxRetries: Int = 3,
): String? {
    repeat(maxRetries) { attempt ->
        try {
            val info = getDanmakuInfo(itemId) ?: return null
            if (!info.hasDanmaku) return null
            
            val xml = downloadDanmaku(itemId)
            if (xml != null) return xml
        } catch (e: Exception) {
            Timber.w(e, "Attempt ${attempt + 1} failed")
            delay(1000L * (attempt + 1)) // 指数退避
        }
    }
    return null
}

// 2. 降级策略
suspend fun fetchDanmakuWithFallback(
    itemId: UUID,
): String? {
    // 优先使用缓存的 URL
    val urlResponse = downloadDanmakuUrl(itemId)
    if (urlResponse != null) {
        return downloadFromUrl(urlResponse.url)
    }
    
    // 降级到直接获取 Raw
    val rawXml = downloadDanmakuRaw(itemId)
    if (rawXml != null) return rawXml
    
    // 最后尝试搜索
    val searchResults = searchDanmakuByItem(itemId)
    if (searchResults.isNotEmpty()) {
        return downloadDanmakuBySource(searchResults.first())
    }
    
    return null
}
```

---

## 12. 附录

### 12.1 XML 弹幕格式完整示例

```xml
<?xml version="1.0" encoding="UTF-8"?>
<i>
    <chatserver>chat.bilibili.com</chatserver>
    <chatid>145418</chatid>
    <mission>0</mission>
    <maxlimit>1500</maxlimit>
    <state>0</state>
    <real_name>0</real_name>
    <source>e-r</source>
    
    <!-- 滚动弹幕 (type=1) -->
    <d p="0.500,1,25,16777215,1584278492,0,59417e95,711923911">第一秒的弹幕</d>
    <d p="1.200,1,18,16646914,1584278493,0,abc123,711923912">红色小字弹幕</d>
    <d p="5.000,1,36,52480,1584278494,0,def456,711923913">绿色大字弹幕</d>
    
    <!-- 顶部固定弹幕 (type=5) -->
    <d p="10.000,5,25,16777215,1584278495,0,ghi789,711923914">顶部弹幕</d>
    
    <!-- 底部固定弹幕 (type=4) -->
    <d p="15.000,4,25,16777215,1584278496,0,jkl012,711923915">底部弹幕</d>
    
    <!-- 逆向弹幕 (type=6) -->
    <d p="20.000,6,25,16777215,1584278497,0,mno345,711923916">逆向弹幕</d>
</i>
```

### 12.2 JSON 弹幕格式示例

```json
{
  "chatserver": "chat.bilibili.com",
  "chatid": "145418",
  "maxlimit": 1500,
  "state": 0,
  "danmakus": [
    {
      "id": 711923911,
      "time": 0.5,
      "type": 1,
      "fontSize": 25,
      "color": 16777215,
      "timestamp": 1584278492,
      "pool": 0,
      "userIdHash": "59417e95",
      "content": "第一秒的弹幕",
      "weight": 6
    }
  ]
}
```

### 12.3 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.0 | 2026-09-04 | 初始版本 |
| 1.1.0 | 2026-09-06 | 服务端实现并端到端验证；`/url` 响应字段修正为 camelCase；`info` 缓存分支返回真实弹幕数；明确缓存目录布局与 itemId 格式；protobuf 端点标记未实现 |

---

## 文档信息

- **作者**: Jellyfin Android TV 团队
- **最后更新**: 2026-09-04
- **状态**: 设计稿
- **下一步**: 实现服务端插件 + Android TV 客户端集成
