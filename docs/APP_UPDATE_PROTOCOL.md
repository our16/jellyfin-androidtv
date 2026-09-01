# Jellyfin Android TV App Update Protocol

## Overview

This document defines the server-side API protocol for app version management and update distribution. The Android TV client checks for updates via these endpoints and downloads APKs when updates are available.

## Base URL

All endpoints are relative to the Jellyfin server base URL:
```
https://your-server.com
```

---

## API Endpoints

### 1. Check for Update

**Endpoint:** `GET /AppUpdate/Check`

**Purpose:** Client calls this to check if a newer app version is available.

**Headers:**
```
Authorization: MediaBrowser Token="<access_token>"
Client: Jellyfin for Android TV
Version: <current_app_version>
DeviceId: <device_id>
```

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `currentVersionCode` | int | Yes | Current client version code (MAJOR×1000000 + MINOR×10000 + PATCH×100) |
| `currentVersion` | string | Yes | Current client version string (e.g. "1.0.0") |
| `channel` | string | No | Release channel: `stable` (default), `beta`, `alpha` |

**Response: `200 OK`**

```json
{
  "updateAvailable": true,
  "appVersion": "1.2.0",
  "appVersionCode": 1020000,
  "minVersion": "1.0.0",
  "releaseDate": "2026-09-01T00:00:00Z",
  "changelog": {
    "zh-CN": "新增 Bilibili 风格界面，优化 4K 投影仪性能",
    "en": "Added Bilibili-style UI, optimized 4K projector performance"
  },
  "downloadUrl": "https://releases.example.com/jellyfin-androidtv-1.2.0.apk",
  "downloadSize": 52428800,
  "checksum": "sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
  "mandatory": false,
  "minServerVersion": "10.9.0",
  "releaseNotes": "https://releases.example.com/notes/1.2.0.html"
}
```

**Response when no update:**
```json
{
  "updateAvailable": false
}
```

**Field Definitions:**

| Field | Type | Description |
|-------|------|-------------|
| `updateAvailable` | boolean | Whether an update is available |
| `appVersion` | string | New version string (e.g. "1.2.0") |
| `appVersionCode` | int | Numeric version code for Android version comparison |
| `minVersion` | string | Minimum client version required. If client is below this, force update |
| `releaseDate` | string | ISO 8601 release date |
| `changelog` | object | Key-value pairs of locale → changelog text |
| `downloadUrl` | string | Direct download URL for the APK file |
| `downloadSize` | long | File size in bytes |
| `checksum` | string | Format: `sha256:<hex_digest>` |
| `mandatory` | boolean | If true, client blocks usage until updated |
| `minServerVersion` | string | Minimum server version this client supports |
| `releaseNotes` | string | Optional URL to full release notes page |

---

### 2. Download APK

**Endpoint:** `GET /AppUpdate/Download`

**Purpose:** Download the APK binary. Client uses Android `DownloadManager` to fetch from `downloadUrl`.

**Headers:**
```
Authorization: MediaBrowser Token="<access_token>"
```

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `version` | string | Yes | Target version to download (e.g. "1.2.0") |

**Response: `200 OK`**
- Content-Type: `application/vnd.android.package-archive`
- Content-Length: `<file_size>`
- Body: APK binary stream

**Alternative:** The `downloadUrl` in the Check response can point to a CDN/external URL. In that case, the server does not need to implement this endpoint.

---

### 3. Get Release List (Optional)

**Endpoint:** `GET /AppUpdate/Releases`

**Purpose:** List available releases for display or debugging.

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `channel` | string | No | Filter by channel: `stable`, `beta`, `alpha` |
| `limit` | int | No | Max results (default: 10) |
| `offset` | int | No | Pagination offset |

**Response: `200 OK`**
```json
{
  "releases": [
    {
      "appVersion": "1.2.0",
      "appVersionCode": 1020000,
      "releaseDate": "2026-09-01T00:00:00Z",
      "channel": "stable",
      "changelog": {
        "zh-CN": "...",
        "en": "..."
      },
      "downloadSize": 52428800,
      "mandatory": false
    },
    {
      "appVersion": "1.1.0",
      "appVersionCode": 1010000,
      "releaseDate": "2026-08-01T00:00:00Z",
      "channel": "stable",
      "changelog": {
        "zh-CN": "...",
        "en": "..."
      },
      "downloadSize": 50331648,
      "mandatory": false
    }
  ]
}
```

---

## Server-Side Implementation Guide

### Data Model

```sql
-- App releases table
CREATE TABLE app_releases (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    version_string VARCHAR(32) NOT NULL,      -- "1.2.0"
    version_code INTEGER NOT NULL,            -- 1020000
    channel VARCHAR(16) NOT NULL DEFAULT 'stable',  -- stable/beta/alpha
    release_date TIMESTAMP NOT NULL,
    changelog JSON,                           -- {"zh-CN": "...", "en": "..."}
    download_url TEXT NOT NULL,
    file_size BIGINT NOT NULL,
    checksum VARCHAR(128) NOT NULL,           -- "sha256:..."
    mandatory BOOLEAN DEFAULT FALSE,
    min_version VARCHAR(32),                  -- minimum client version
    min_server_version VARCHAR(32),           -- minimum server version
    release_notes_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(version_string, channel)
);
```

### API Implementation Pseudocode

```python
# GET /AppUpdate/Check
def check_update(request):
    current_version_code = request.query.get('currentVersionCode')
    channel = request.query.get('channel', 'stable')
    
    # Find latest release for this channel with higher version code
    latest = db.query("""
        SELECT * FROM app_releases 
        WHERE channel = ? AND version_code > ?
        ORDER BY version_code DESC 
        LIMIT 1
    """, channel, current_version_code)
    
    if not latest:
        return {"updateAvailable": False}
    
    return {
        "updateAvailable": True,
        "appVersion": latest.version_string,
        "appVersionCode": latest.version_code,
        "minVersion": latest.min_version,
        "releaseDate": latest.release_date.isoformat(),
        "changelog": latest.changelog,
        "downloadUrl": latest.download_url,
        "downloadSize": latest.file_size,
        "checksum": latest.checksum,
        "mandatory": latest.mandatory,
        "minServerVersion": latest.min_server_version,
        "releaseNotes": latest.release_notes_url,
    }
```

### Version Code Format

```
MAJOR * 1000000 + MINOR * 10000 + PATCH * 100 + PRE_RELEASE_BUILD

Examples:
  1.0.0        → 1000000
  1.2.0        → 1020000
  1.2.3        → 1020300
  0.0.0-dev.1  → 99 (pre-release default)
```

### Checksum Verification

Server generates APK checksum during upload:
```bash
sha256sum jellyfin-androidtv-1.2.0.apk
# Output: abcdef1234567890... jellyfin-androidtv-1.2.0.apk

# Store as: "sha256:abcdef1234567890..."
```

Client verifies after download:
```kotlin
val expectedHash = updateInfo.checksum.removePrefix("sha256:")
val actualHash = file.sha256()
if (actualHash != expectedHash) {
    // Reject download, show error
}
```

---

## Client Integration

### Client Call Flow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  App Start  │────▶│ Check Update│────▶│ Show Dialog │
└─────────────┘     └─────────────┘     └─────────────┘
                           │                    │
                           ▼                    ▼
                    ┌─────────────┐     ┌─────────────┐
                    │ No Update   │     │ User Confirm│
                    │ → Continue  │     └─────────────┘
                    └─────────────┘           │
                                              ▼
                                       ┌─────────────┐
                                       │  Download   │
                                       └─────────────┘
                                              │
                                              ▼
                                       ┌─────────────┐
                                       │  Verify SHA │
                                       └─────────────┘
                                              │
                                              ▼
                                       ┌─────────────┐
                                       │  Install APK│
                                       └─────────────┘
```

### Client Settings Entry Point

The Android TV client adds an "App Update" entry in Settings:
```
Settings
├── Accounts
├── Customization
│   ├── Theme
│   ├── Clock
│   └── UI Plugin
├── Playback
├── App Update        ← NEW
│   ├── Current version: 1.0.0
│   ├── [Check for updates]
│   └── (shows update info when available)
├── About
```

### Mandatory Update Flow

When `mandatory: true`:
1. Client shows blocking dialog: "A required update is available. Please update to continue."
2. Dialog cannot be dismissed
3. App does not proceed until update is installed
4. Used for critical security patches or breaking API changes

---

## Error Handling

| HTTP Code | Client Behavior |
|-----------|-----------------|
| 200 | Parse response normally |
| 401 | Not authenticated, skip check |
| 403 | Forbidden, skip check |
| 404 | Endpoint not implemented, skip silently |
| 500 | Server error, retry later |
| Timeout | Skip, check again next time |

Client should:
- Never crash on update check failure
- Log errors via Timber for debugging
- Allow manual retry via Settings → App Update
