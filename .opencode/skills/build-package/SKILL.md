---
name: build-package
description: Build and package Jellyfin Android TV release APK with automatic version bumping
license: MIT
compatibility: opencode
metadata:
  audience: developers
  workflow: release
---

## What I do

- Check recent git changes to determine version bump type
- Update version in `gradle.properties`
- Build signed release APK
- Verify APK signature and report output

## When to use me

Trigger when user says: "打包", "构建", "build", "release", "发布", "build release"

## Version Rules

| Change Type | Trigger | Bump | Example |
|-------------|---------|------|---------|
| Bug fix | `fix:`, `chore:` | PATCH +0.0.1 | 1.0.0 → 1.0.1 |
| New feature | `feat:` | MINOR +0.1.0 | 1.0.1 → 1.1.0 |
| Breaking change | `BREAKING` | MAJOR +1.0.0 | 1.1.0 → 2.0.0 |

## Workflow

### Step 1: Check Changes

```powershell
git log --oneline -5
git diff --stat HEAD~1
```

### Step 2: Determine Version

Read current version from `gradle.properties`:

```powershell
Select-String -Path "gradle.properties" -Pattern "jellyfin.version"
```

Apply bump based on last commit type.

### Step 3: Update Version

Edit `gradle.properties`:

```properties
jellyfin.version=X.Y.Z
```

### Step 4: Build Release APK

```powershell
$env:JAVA_HOME = "D:\env\jdk-21.0.12_windows-x64_bin\jdk-21.0.12"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = "C:\Android\Sdk"
./gradlew ":app:assembleRelease" --no-daemon
```

### Step 5: Verify Output

```powershell
$aapt2 = "C:\Android\Sdk\build-tools\36.0.0\aapt2"
$apk = Get-ChildItem "app\build\outputs\apk\release" -Filter "*.apk" | Select-Object -First 1
& $aapt2 dump badging $apk.FullName | Select-String "versionName|versionCode"
Write-Host "APK: $($apk.Name) - $([math]::Round($apk.Length / 1MB, 2)) MB"
```

### Step 6: Commit Version

```powershell
git add gradle.properties
git commit -m "chore: release v{version}"
```

### Step 7: Report

```
✅ 构建完成
📦 app\build\outputs\apk\release\jellyfin-androidtv-v{version}-release.apk
📏 {size} MB | versionCode={code} | versionName={version}
```

## Environment

- Java: `D:\env\jdk-21.0.12_windows-x64_bin\jdk-21.0.12`
- Android SDK: `C:\Android\Sdk`
- Build Tools: `36.0.0`
- Keystore: `../keystore/release.jks` (password: jellyfin123)
