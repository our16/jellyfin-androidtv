# Skill: Build & Package

## Trigger
When user says: "打包", "构建", "build", "release", "发布"

## Workflow

### Step 1: Check Changes
```bash
git log --oneline -5
git diff --stat HEAD~1
```

### Step 2: Determine Version Bump
Read `gradle.properties` for current version, then:

| Last Commit Type | Bump | Example |
|------------------|------|---------|
| `fix:`, `chore:` | PATCH +0.0.1 | 1.0.1 → 1.0.2 |
| `feat:` | MINOR +0.1.0 | 1.0.1 → 1.1.0 |
| `BREAKING` | MAJOR +1.0.0 | 1.0.1 → 2.0.0 |

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

### Step 5: Verify & Report
```powershell
$aapt2 = "C:\Android\Sdk\build-tools\36.0.0\aapt2"
$apk = Get-ChildItem "app\build\outputs\apk\release" -Filter "*.apk" | Select-Object -First 1
& $aapt2 dump badging $apk.FullName | Select-String "versionName|versionCode"
Write-Host "APK: $($apk.Name) - $([math]::Round($apk.Length / 1MB, 2)) MB"
```

### Step 6: Commit Version Bump
```bash
git add gradle.properties
git commit -m "chore: release v{version}"
```

### Step 7: Output
```
✅ 构建完成
📦 app\build\outputs\apk\release\jellyfin-androidtv-v{version}-release.apk
📏 {size} MB | versionCode={code} | versionName={version}
```
