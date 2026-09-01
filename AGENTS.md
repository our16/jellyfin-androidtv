# AGENTS.md

## Project: Jellyfin for Android TV

Jellyfin Android TV 客户端，基于 Kotlin + Compose + Leanback 构建。

## Build Commands

```bash
# 环境变量
$env:JAVA_HOME = "D:\env\jdk-21.0.12_windows-x64_bin\jdk-21.0.12"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = "C:\Android\Sdk"

# Debug 构建
$env:JELLYFIN_VERSION = "1.0.0"
./gradlew :app:assembleDebug

# Release 构建（需要签名）
./gradlew :app:assembleRelease

# 编译检查
./gradlew :app:compileDebugKotlin

# 全量重编译
./gradlew :app:compileDebugKotlin --rerun-tasks
```

## Signing Configuration

Release 签名通过 `gradle.properties` 配置：

```properties
keystore.file=../keystore/release.jks
keystore.password=jellyfin123
signing.key.alias=jellyfin
signing.key.password=jellyfin123
```

- Keystore 路径：`keystore/release.jks`（已 gitignore）
- 生成命令：`keytool -genkeypair -v -keystore keystore/release.jks -alias jellyfin -keyalg RSA -keysize 2048 -validity 10000 -storepass jellyfin123 -keypass jellyfin123 -dname "CN=Jellyfin, OU=Development, O=Jellyfin, L=Unknown, ST=Unknown, C=US"`
- APK 输出：`app/build/outputs/apk/release/jellyfin-androidtv-v{version}-release.apk`

## Version Management

版本通过环境变量或 Gradle 属性设置：

```bash
# 方式一：环境变量
$env:JELLYFIN_VERSION = "1.0.0"

# 方式二：gradle.properties
jellyfin.version=1.0.0
```

版本号格式：`MAJOR.MINOR.PATCH`（如 `1.0.0`）
版本码计算：`MAJOR * 1000000 + MINOR * 10000 + PATCH * 100 + 99`

## Environment

- Java: `D:\env\jdk-21.0.12_windows-x64_bin\jdk-21.0.12`
- Android SDK: `C:\Android\Sdk`
- Build Tools: `36.0.0`
- Kotlin: `2.4.10`
- Compose: `1.12.0`
- Material3: `1.3.1`
- Coil: `3.5.0`
- Koin: `4.2.2`

## Code Conventions

- 使用 `org.jellyfin.androidtv.ui.base.*` 组件（Text, ListButton 等），避免直接使用 Material3
- Compose 函数命名：`Settings*Screen` 用于设置页面
- 路由定义在 `ui/settings/routes.kt`，格式：`const val ROUTE_NAME = "/path"`
- 依赖注入使用 Koin，注册在 `di/AppModule.kt`
- Repository 模式：接口 + Impl，注册在 `AppModule.kt`
- 资源命名：`snake_case`，中文放 `values-zh-rCN/strings.xml`

## Architecture

### UI Plugin System
- `ui/plugin/UiPlugin.kt` — 核心接口
- `ui/plugin/DefaultUiPlugin.kt` — 默认 UI
- `ui/plugin/BilibiliStyleUiPlugin.kt` — Bilibili 风格 UI（stub）
- `ui/plugin/UiPluginRegistry.kt` — 插件注册表
- `ui/plugin/UiPluginPreferences.kt` — 偏好持久化

### Key Files
- `di/AppModule.kt` — Koin 依赖注册
- `ui/browsing/MainActivity.kt` — 主 Activity
- `ui/settings/routes.kt` — 路由定义
- `ui/settings/screen/SettingsMainScreen.kt` — 设置主页
- `data/repository/AppUpdateRepository.kt` — 应用更新

## Server Protocol

API 协议文档：
- `docs/APP_UPDATE_PROTOCOL.md` — 应用更新接口（`/AppUpdate/Check`, `/AppUpdate/Download`）
- `docs/UI_PLUGIN_PROTOCOL.md` — UI 插件协议

## Git Conventions

- Commit 格式：`type: description`
- Types: `feat`, `fix`, `build`, `docs`, `refactor`, `chore`
- 示例：`feat: projector optimization + app update mechanism`
