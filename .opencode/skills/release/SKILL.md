---
name: release
description: Complete Android TV release workflow - build, upload to server, verify, and manage releases
license: MIT
compatibility: opencode
metadata:
  audience: developers
  workflow: release
---

## What I do

- Build signed release APK
- Upload APK to Jellyfin server
- Verify release is accessible
- Manage releases (list, delete, mark mandatory)

## When to use me

Trigger when user says: "发布", "release", "新版本发布", "发布新版本", "upload release"

## Prerequisites

- Server running at `http://localhost:8096`
- API Token: `be7ba473d4124f56a4a6babe89879b66`
- Version format: `MAJOR.MINOR.PATCH`
- versionCode: `MAJOR×1000000 + MINOR×10000 + PATCH×100`

## Workflow

### Step 1: Determine Version

```powershell
# Read current version
Select-String -Path "gradle.properties" -Pattern "jellyfin.version"

# Ask user for new version if needed
```

### Step 2: Build APK

```powershell
$env:JAVA_HOME = "D:\env\jdk-21.0.12_windows-x64_bin\jdk-21.0.12"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = "C:\Android\Sdk"
$env:JELLYFIN_VERSION = "{version}"

cd F:\github\jellyfin-androidtv
.\gradlew :app:assembleRelease
```

### Step 3: Verify Build

```powershell
$aapt2 = "C:\Android\Sdk\build-tools\36.0.0\aapt2"
$apk = Get-ChildItem "app\build\outputs\apk\release" -Filter "*{version}*" | Select-Object -First 1
& $aapt2 dump badging $apk.FullName | Select-String "versionName|versionCode"
Write-Host "APK: $($apk.Name) - $([math]::Round($apk.Length/1MB, 2)) MB"
```

### Step 4: Upload to Server

```powershell
# Configuration
$token = "be7ba473d4124f56a4a6babe89879b66"
$version = "{version}"
$versionCode = {MAJOR*1000000 + MINOR*10000 + PATCH*100}
$channel = "stable"  # stable / beta / alpha
$apkPath = "F:\github\jellyfin-androidtv\app\build\outputs\apk\release\jellyfin-androidtv-v{version}-release.apk"

# Build multipart/form-data
$boundary = [Guid]::NewGuid().ToString()
$fileName = [IO.Path]::GetFileName($apkPath)
$fileBytes = [IO.File]::ReadAllBytes($apkPath)
Write-Host "Uploading: $([math]::Round($fileBytes.Length/1MB, 2)) MB"

$enc = [Text.Encoding]::GetEncoding("iso-8859-1")
$pre = $enc.GetBytes("--$boundary`r`nContent-Disposition: form-data; name=`"file`"; filename=`"$fileName`"`r`nContent-Type: application/vnd.android.package-archive`r`n`r`n")
$post = $enc.GetBytes("`r`n--$boundary--`r`n")
$ms = New-Object IO.MemoryStream
$ms.Write($pre, 0, $pre.Length)
$ms.Write($fileBytes, 0, $fileBytes.Length)
$ms.Write($post, 0, $post.Length)
$bodyBytes = $ms.ToArray()
$ms.Dispose()

# Upload
$uri = "http://localhost:8096/AppUpdate/Upload?versionString=$version&versionCode=$versionCode&channel=$channel"
$headers = @{
    "X-Emby-Authorization" = 'MediaBrowser Client="curl", Device="PC", DeviceId="release", Version="1.0"'
    "Authorization" = "MediaBrowser Token=`"$token`""
}
$resp = Invoke-WebRequest -Uri $uri -Method Post -Headers $headers -ContentType "multipart/form-data; boundary=$boundary" -Body $bodyBytes -UseBasicParsing
Write-Host "Upload success:"
$resp.Content | ConvertFrom-Json | Format-List
```

### Step 5: Verify Release

```powershell
$token = "be7ba473d4124f56a4a6babe89879b66"
$headers = @{
    "X-Emby-Authorization" = 'MediaBrowser Client="curl", Device="PC", DeviceId="release", Version="1.0"'
    "Authorization" = "MediaBrowser Token=`"$token`""
}

# List all releases
Invoke-WebRequest -Uri "http://localhost:8096/AppUpdate/Releases?channel=$channel" -Headers $headers -UseBasicParsing | Select-Object -ExpandProperty Content

# Simulate client check (old version detects new)
Invoke-WebRequest -Uri "http://localhost:8096/AppUpdate/Check?currentVersionCode=1000100&currentVersion=1.0.1&channel=stable" -Headers $headers -UseBasicParsing | Select-Object -ExpandProperty Content

# Download APK (manual test)
Invoke-WebRequest -Uri "http://localhost:8096/AppUpdate/Download?version=$version" -Headers $headers -OutFile "test.apk" -UseBasicParsing
```

### Step 6: Report

```
✅ 发布完成
📦 Version: {version} (versionCode: {code})
📁 APK: jellyfin-androidtv-v{version}-release.apk
📏 Size: {size} MB
🌐 Server: http://localhost:8096/AppUpdate/Check
```

## API Reference

| Action | Method | Endpoint | Auth |
|--------|--------|----------|------|
| Check update | GET | `/AppUpdate/Check?currentVersionCode=&currentVersion=&channel=` | Token |
| Upload APK | POST | `/AppUpdate/Upload?versionString=&versionCode=&channel=` | Admin |
| Download APK | GET | `/AppUpdate/Download?version=` | Admin |
| List releases | GET | `/AppUpdate/Releases?channel=&limit=&offset=` | Token |
| Create release | POST | `/AppUpdate/Releases` | Admin |
| Update release | PUT | `/AppUpdate/Releases/{id}` | Admin |
| Delete release | DELETE | `/AppUpdate/Releases/{id}` | Admin |

## Channel Types

- `stable` - Production releases
- `beta` - Testing releases
- `alpha` - Experimental releases
