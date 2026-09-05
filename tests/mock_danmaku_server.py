#!/usr/bin/env python3
"""
Jellyfin Danmaku API Mock Server
用于集成测试的模拟服务器

启动方式:
    python mock_danmaku_server.py [--port 8096]

测试方式:
    curl http://localhost:8096/api/danmaku/test-item-id
    curl http://localhost:8096/api/danmaku/test-item-id/url
    curl http://localhost:8096/api/danmaku/test-item-id/raw
    curl http://localhost:8096/api/danmaku/search?keyword=test
    curl http://localhost:8096/api/danmaku/sources
    curl http://localhost:8096/api/danmaku/config
    curl http://localhost:8096/api/danmaku/cache/stats
"""

import json
import sys
import argparse
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
import time
import random

# 测试用的弹幕 XML 数据
TEST_DANMAKU_XML = """<?xml version="1.0" encoding="UTF-8"?>
<i>
    <chatserver>chat.bilibili.com</chatserver>
    <chatid>145418</chatid>
    <mission>0</mission>
    <maxlimit>1500</maxlimit>
    <state>0</state>
    <real_name>0</real_name>
    <source>e-r</source>
    <d p="0.500,1,25,16777215,1584278492,0,59417e95,711923911">这是第一条弹幕</d>
    <d p="1.200,1,18,16646914,1584278493,0,abc123,711923912">红色小字弹幕</d>
    <d p="2.000,1,36,52480,1584278494,0,def456,711923913">绿色大字弹幕</d>
    <d p="3.500,5,25,16777215,1584278495,0,ghi789,711923914">顶部弹幕</d>
    <d p="5.000,4,25,16777215,1584278496,0,jkl012,711923915">底部弹幕</d>
    <d p="7.000,1,25,16777215,1584278497,0,mno345,711923916">弹幕测试</d>
    <d p="10.000,1,25,16777215,1584278498,0,pqr678,711923917">Hello World</d>
    <d p="12.500,1,25,16777215,1584278499,0,stu901,711923918">测试弹幕内容</d>
    <d p="15.000,6,25,16777215,1584278500,0,vwx234,711923919">逆向弹幕</d>
    <d p="20.000,1,25,16777215,1584278501,0,yza567,711923920">最后一条弹幕</d>
</i>"""

# 模拟的媒体项目
MOCK_ITEMS = {
    "test-item-id": {
        "id": "test-item-id",
        "name": "测试视频",
        "hasDanmaku": True,
        "danmakuCount": 10,
        "source": "bilibili",
        "sourceId": "bv1xx411c7mD",
        "sourceCid": 145418,
        "duration": 1440000,
    },
    "no-danmaku-item": {
        "id": "no-danmaku-item",
        "name": "无弹幕视频",
        "hasDanmaku": False,
        "danmakuCount": 0,
    },
    "item-001": {
        "id": "item-001",
        "name": "某科学的超电磁炮S 第1集",
        "hasDanmaku": True,
        "danmakuCount": 2847,
        "source": "bilibili",
        "sourceId": "bv1xx411c7mD",
        "sourceCid": 145418,
        "duration": 1440000,
    },
    "item-002": {
        "id": "item-002",
        "name": "某科学的超电磁炮S 第2集",
        "hasDanmaku": True,
        "danmakuCount": 3156,
        "source": "bilibili",
        "sourceId": "bv1xx411c7mE",
        "sourceCid": 145419,
        "duration": 1440000,
    },
}

# 模拟的搜索结果
MOCK_SEARCH_RESULTS = {
    "某科学的超电磁炮": [
        {
            "source": "bilibili",
            "sourceId": "bv1xx411c7mD",
            "sourceCid": 145418,
            "name": "某科学的超电磁炮S",
            "nameOriginal": "とある科学の超電磁砲S",
            "category": "番剧",
            "year": "2020",
            "episodeNumber": 1,
            "duration": 1440000,
            "matchScore": 0.98,
            "matchSource": "bilibili",
            "availableFormats": ["xml", "protobuf"],
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
            "availableFormats": ["xml"],
        },
    ],
    "test": [
        {
            "source": "bilibili",
            "sourceId": "bv1xx411c7mD",
            "sourceCid": 145418,
            "name": "测试视频",
            "category": "其他",
            "year": "2024",
            "episodeNumber": 1,
            "duration": 600000,
            "matchScore": 0.90,
            "matchSource": "bilibili",
            "availableFormats": ["xml"],
        },
    ],
}

# 模拟的弹幕源
MOCK_SOURCES = [
    {
        "id": "bilibili",
        "name": "哔哩哔哩",
        "type": "online",
        "enabled": True,
        "priority": 1,
        "supportedFormats": ["xml", "protobuf"],
        "config": {
            "apiEndpoint": "https://api.bilibili.com",
            "useProtobuf": True,
        },
        "stats": {
            "totalRequests": 1234,
            "successRate": 0.98,
            "avgResponseTime": 250,
            "lastError": None,
            "lastErrorTime": None,
        },
    },
    {
        "id": "dandanplay",
        "name": "弹弹play",
        "type": "online",
        "enabled": True,
        "priority": 2,
        "supportedFormats": ["xml"],
        "config": {
            "apiEndpoint": "https://www.dandanplay.com",
        },
        "stats": {
            "totalRequests": 567,
            "successRate": 0.95,
            "avgResponseTime": 400,
            "lastError": None,
            "lastErrorTime": None,
        },
    },
]

# 模拟的配置
MOCK_CONFIG = {
    "enabled": True,
    "defaultEnabled": True,
    "autoMatch": True,
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
        "densityLimit": 6,
    },
    "updateSettings": {
        "autoUpdate": True,
        "updateIntervalHours": 24,
        "preferProtobuf": True,
    },
}

# 模拟的缓存统计
MOCK_CACHE_STATS = {
    "totalItems": 1234,
    "totalSize": 536870912,
    "maxSize": 1073741824,
    "usagePercent": 50.0,
    "oldestItem": {
        "itemId": "item-001",
        "cachedAt": int(time.time()) - 86400 * 30,
        "fileSize": 524288,
    },
    "newestItem": {
        "itemId": "item-002",
        "cachedAt": int(time.time()),
        "fileSize": 1048576,
    },
}


class DanmakuAPIHandler(BaseHTTPRequestHandler):
    """弹幕 API 请求处理器"""

    def log_message(self, format, *args):
        """自定义日志格式"""
        print(f"[{time.strftime('%H:%M:%S')}] {args[0]}")

    def send_json(self, data, status=200):
        """发送 JSON 响应"""
        self.send_response(status)
        self.send_header("Content-Type", "application/json; profile=\"CamelCase\"")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(json.dumps(data, ensure_ascii=False).encode("utf-8"))

    def send_xml(self, data, status=200):
        """发送 XML 响应"""
        self.send_response(status)
        self.send_header("Content-Type", "application/xml; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(data.encode("utf-8"))

    def send_error_response(self, status, error_code, message):
        """发送错误响应"""
        self.send_json(
            {"errorCode": error_code, "message": message, "statusCode": status},
            status,
        )

    def do_GET(self):
        """处理 GET 请求"""
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/")
        params = parse_qs(parsed.query)
        parts = path.strip("/").split("/")

        # === Fixed paths first ===

        # GET /api/danmaku/search
        if path == "/api/danmaku/search":
            keyword = params.get("keyword", [""])[0]
            results = []
            for key, items in MOCK_SEARCH_RESULTS.items():
                if key in keyword or keyword in key:
                    results.extend(items)
            self.send_json({"totalResults": len(results), "results": results})

        # GET /api/danmaku/sources
        elif path == "/api/danmaku/sources":
            self.send_json({"sources": MOCK_SOURCES})

        # GET /api/danmaku/config
        elif path == "/api/danmaku/config":
            self.send_json(MOCK_CONFIG)

        # GET /api/danmaku/cache/stats
        elif path == "/api/danmaku/cache/stats":
            self.send_json(MOCK_CACHE_STATS)

        # GET /api/danmaku/cache
        elif path == "/api/danmaku/cache":
            self.send_json({
                "totalRecordCount": MOCK_CACHE_STATS["totalItems"],
                "items": [
                    {
                        "itemId": "item-001",
                        "itemName": "某科学的超电磁炮S 第1集",
                        "source": "bilibili",
                        "sourceId": "bv1xx411c7mD",
                        "cachedAt": MOCK_CACHE_STATS["oldestItem"]["cachedAt"],
                        "expiresAt": int(time.time()) + 86400 * 30,
                        "fileSize": 524288,
                        "danmakuCount": 2847,
                        "format": "xml",
                    },
                    {
                        "itemId": "item-002",
                        "itemName": "某科学的超电磁炮S 第2集",
                        "source": "bilibili",
                        "sourceId": "bv1xx411c7mE",
                        "cachedAt": MOCK_CACHE_STATS["newestItem"]["cachedAt"],
                        "expiresAt": int(time.time()) + 86400 * 30,
                        "fileSize": 1048576,
                        "danmakuCount": 3156,
                        "format": "xml",
                    },
                ],
            })

        # GET /api/v2/search/anime (弹弹play 兼容)
        elif path == "/api/v2/search/anime":
            keyword = params.get("keyword", [""])[0]
            results = []
            for key, items in MOCK_SEARCH_RESULTS.items():
                if key in keyword or keyword in key:
                    results.extend(items)
            self.send_json({
                "errorCode": 0,
                "success": True,
                "errorMessage": None,
                "animes": results,
            })

        # === Parameterized paths ===

        # GET /api/danmaku/search/by-item/{itemId}
        elif len(parts) == 5 and parts[:3] == ["api", "danmaku", "search"] and parts[3] == "by-item":
            item_id = parts[4]
            if item_id in MOCK_ITEMS:
                item = MOCK_ITEMS[item_id]
                results = []
                for key, items in MOCK_SEARCH_RESULTS.items():
                    if item.get("name", "") in key or key in item.get("name", ""):
                        results.extend(items)
                self.send_json({"totalResults": len(results), "results": results})
            else:
                self.send_json({"totalResults": 0, "results": []})

        # GET /api/danmaku/{itemId}/url
        elif len(parts) == 4 and parts[:2] == ["api", "danmaku"] and parts[3] == "url":
            item_id = parts[2]
            if item_id in MOCK_ITEMS and MOCK_ITEMS[item_id].get("hasDanmaku"):
                self.send_json({
                    "url": f"http://localhost:8097/api/danmaku/{item_id}/raw",
                    "expiresAt": int(time.time()) + 86400,
                    "format": "xml",
                    "fileSize": len(TEST_DANMAKU_XML),
                })
            else:
                self.send_error_response(404, "DanmakuNotFound", f"找不到弹幕: {item_id}")

        # GET /api/danmaku/{itemId}/raw
        elif len(parts) == 4 and parts[:2] == ["api", "danmaku"] and parts[3] == "raw":
            item_id = parts[2]
            if item_id in MOCK_ITEMS and MOCK_ITEMS[item_id].get("hasDanmaku"):
                self.send_xml(TEST_DANMAKU_XML)
            else:
                self.send_error_response(404, "DanmakuNotFound", f"找不到弹幕: {item_id}")

        # GET /api/danmaku/{itemId}
        elif len(parts) == 3 and parts[:2] == ["api", "danmaku"]:
            item_id = parts[2]
            if item_id in MOCK_ITEMS:
                item = MOCK_ITEMS[item_id]
                self.send_json({
                    "itemId": item["id"],
                    "mediaSourceId": "source_01",
                    "hasDanmaku": item.get("hasDanmaku", False),
                    "danmakuCount": item.get("danmakuCount", 0),
                    "source": item.get("source", ""),
                    "sourceId": item.get("sourceId", ""),
                    "sourceCid": item.get("sourceCid", 0),
                    "lastUpdated": int(time.time()),
                    "format": "xml",
                    "fileSize": len(TEST_DANMAKU_XML),
                    "duration": item.get("duration", 0),
                    "languages": ["zh-CN"],
                    "matchConfidence": 0.95,
                })
            else:
                self.send_error_response(404, "ItemNotFound", f"找不到项目: {item_id}")

        # GET /api/v2/comment/{episodeId}
        elif len(parts) == 4 and parts[:3] == ["api", "v2", "comment"]:
            episode_id = parts[3]
            format_type = params.get("format", ["xml"])[0]

            if format_type == "json":
                comments = []
                for i in range(10):
                    comments.append({
                        "cid": 711923911 + i,
                        "progress": int((i + 1) * 1000),
                        "mode": 1,
                        "fontSize": 25,
                        "color": 16777215,
                        "content": f"弹幕 {i + 1}",
                        "sendTime": 1584278492 + i,
                        "userId": "59417e95",
                        "weight": 6,
                    })
                self.send_json({
                    "errorCode": 0,
                    "success": True,
                    "errorMessage": None,
                    "comments": comments,
                })
            else:
                self.send_xml(TEST_DANMAKU_XML)

        else:
            self.send_error_response(404, "NotFound", f"未知路径: {path}")

    def do_POST(self):
        """处理 POST 请求"""
        parsed = urlparse(self.path)
        path = parsed.path

        # POST /api/danmaku/{itemId}/refresh
        if path.startswith("/api/danmaku/") and path.endswith("/refresh"):
            item_id = path.split("/")[3]
            task_id = f"task_{random.randint(100000, 999999)}"
            self.send_json(
                {
                    "taskId": task_id,
                    "status": "processing",
                    "message": "弹幕刷新任务已提交",
                },
                202,
            )

        # POST /api/danmaku/sources/{sourceId}/test
        elif path.startswith("/api/danmaku/sources/") and path.endswith("/test"):
            source_id = path.split("/")[4]
            source = next((s for s in MOCK_SOURCES if s["id"] == source_id), None)
            if source:
                self.send_json(
                    {
                        "success": True,
                        "message": "连接成功",
                        "responseTime": random.randint(100, 500),
                        "testResult": {
                            "canSearch": True,
                            "canFetch": True,
                            "sampleResult": {
                                "source": source["id"],
                                "sourceId": "test_id",
                                "name": f"测试{source['name']}",
                                "matchScore": 0.95,
                            },
                        },
                    }
                )
            else:
                self.send_error_response(404, "SourceNotFound", f"找不到源: {source_id}")

        # POST /api/danmaku/cache/cleanup
        elif path == "/api/danmaku/cache/cleanup":
            self.send_json(
                {
                    "removedCount": 45,
                    "freedSize": 23592960,
                    "remainingItems": MOCK_CACHE_STATS["totalItems"] - 45,
                }
            )

        else:
            self.send_error_response(404, "NotFound", f"未知路径: {path}")

    def do_PUT(self):
        """处理 PUT 请求"""
        parsed = urlparse(self.path)
        path = parsed.path

        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length).decode("utf-8") if content_length > 0 else "{}"

        try:
            data = json.loads(body)
        except json.JSONDecodeError:
            data = {}

        # PUT /api/danmaku/config
        if path == "/api/danmaku/config":
            updated_config = {**MOCK_CONFIG, **data}
            MOCK_CONFIG.update(data)
            self.send_json(updated_config)

        # PUT /api/danmaku/sources/{sourceId}
        elif path.startswith("/api/danmaku/sources/"):
            source_id = path.split("/")[4]
            source = next((s for s in MOCK_SOURCES if s["id"] == source_id), None)
            if source:
                source.update(data)
                self.send_json(source)
            else:
                self.send_error_response(404, "SourceNotFound", f"找不到源: {source_id}")

        else:
            self.send_error_response(404, "NotFound", f"未知路径: {path}")

    def do_DELETE(self):
        """处理 DELETE 请求"""
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/")
        parts = path.strip("/").split("/")

        # DELETE /api/danmaku/cache (check before item ID pattern)
        if path == "/api/danmaku/cache":
            self.send_response(204)
            self.end_headers()

        # DELETE /api/danmaku/sources/custom/{sourceId}
        elif len(parts) == 5 and parts[:3] == ["api", "danmaku", "sources"] and parts[3] == "custom":
            self.send_response(204)
            self.end_headers()

        # DELETE /api/danmaku/{itemId}
        elif len(parts) == 3 and parts[:2] == ["api", "danmaku"]:
            item_id = parts[2]
            if item_id in MOCK_ITEMS:
                self.send_response(204)
                self.end_headers()
            else:
                self.send_error_response(404, "ItemNotFound", f"找不到项目: {item_id}")

        else:
            self.send_error_response(404, "NotFound", f"未知路径: {path}")

    def do_OPTIONS(self):
        """处理 CORS 预检请求"""
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()


def run_server(port=8096):
    """启动服务器"""
    server_address = ("", port)
    httpd = HTTPServer(server_address, DanmakuAPIHandler)

    print(f"""
╔══════════════════════════════════════════════════════════════╗
║           Jellyfin Danmaku API Mock Server                  ║
╠══════════════════════════════════════════════════════════════╣
║  Server running on: http://localhost:{port}                   ║
║                                                              ║
║  Test endpoints:                                             ║
║    GET  /api/danmaku/test-item-id                           ║
║    GET  /api/danmaku/test-item-id/url                       ║
║    GET  /api/danmaku/test-item-id/raw                       ║
║    GET  /api/danmaku/search?keyword=test                    ║
║    GET  /api/danmaku/sources                                ║
║    GET  /api/danmaku/config                                 ║
║    GET  /api/danmaku/cache/stats                            ║
║                                                              ║
║  Press Ctrl+C to stop                                        ║
╚══════════════════════════════════════════════════════════════╝
""")

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nServer stopped.")
        httpd.shutdown()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Jellyfin Danmaku API Mock Server")
    parser.add_argument("--port", type=int, default=8096, help="Server port (default: 8096)")
    args = parser.parse_args()

    run_server(args.port)
