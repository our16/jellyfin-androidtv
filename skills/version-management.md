# Skill: Version Management for Jellyfin Android TV

## Version Numbering Rules

Semantic Versioning: `MAJOR.MINOR.PATCH`

| Change Type | Version Bump | Example |
|-------------|--------------|---------|
| Bug fix, crash fix, minor tweak | PATCH +0.0.1 | 1.0.0 → 1.0.1 |
| New feature, UI improvement, performance | MINOR +0.1.0 | 1.0.1 → 1.1.0 |
| Major redesign, breaking changes | MAJOR +1.0.0 | 1.1.0 → 2.0.0 |

## Examples

```
1.0.0 → 1.0.1  (crash fix, typo fix, icon change)
1.0.1 → 1.1.0  (new plugin system, new settings screen)
1.1.0 → 2.0.0  (complete UI rewrite, breaking API changes)
```

## Build Commands

```bash
# Set version in gradle.properties
jellyfin.version=X.Y.Z

# Build release APK
$env:JELLYFIN_VERSION = "X.Y.Z"
./gradlew :app:assembleRelease

# Verify version
aapt2 dump badging app\build\outputs\apk\release\*.apk | findstr versionName
```

## Version Code Calculation

```
MAJOR * 1000000 + MINOR * 10000 + PATCH * 100 + 99

1.0.0 → 1000099
1.0.1 → 1000199
1.1.0 → 1010099
2.0.0 → 2000099
```

## Commit Before Build

Always commit changes before bumping version:
```bash
git add -A && git commit -m "fix: description"
# Then bump version and build
```
