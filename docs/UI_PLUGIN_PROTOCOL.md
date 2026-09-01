# Jellyfin Android TV UI Plugin Protocol

## Overview

This document defines how the Android TV client communicates UI plugin information to the server. The server can use this to understand which UI the client is running and potentially offer server-side configuration.

## Client → Server: Plugin Info Header

Every API request from the client includes a custom header indicating the active UI plugin:

```
X-UI-Plugin: bilibili
```

Or for the default UI:
```
X-UI-Plugin: default
```

**Values:**

| Plugin ID | Description |
|-----------|-------------|
| `default` | Original Jellyfin UI (Leanback + Compose hybrid) |
| `bilibili` | Bilibili-style modern UI (pure Compose) |
| `<custom>` | Any custom UI plugin ID |

---

## Server → Client: Plugin Configuration (Future)

If the server wants to configure UI plugins per-device or per-user:

**Endpoint:** `GET /Plugins/UI/Config`

**Headers:**
```
Authorization: MediaBrowser Token="<token>"
X-UI-Plugin: <plugin_id>
```

**Response:**
```json
{
  "pluginId": "bilibili",
  "enabled": true,
  "config": {
    "homeLayout": "hero-first",
    "maxHeroItems": 1,
    "cardStyle": "compact",
    "showContinueWatching": true,
    "showLatestAdded": true
  }
}
```

This is **optional** — the client works without server-side plugin configuration.

---

## Server-Side Plugin Registration (Future)

If the server supports distributing UI plugins:

**Endpoint:** `POST /Plugins/UI/Register`

**Body:**
```json
{
  "pluginId": "bilibili",
  "name": "Bilibili Style UI",
  "version": "1.0.0",
  "description": "Modern Bilibili-style interface",
  "minAppVersion": "1.0.0",
  "downloadUrl": "https://plugins.example.com/bilibili-ui-1.0.0.zip",
  "checksum": "sha256:..."
}
```

This enables a plugin marketplace where users can browse and install different UIs.
