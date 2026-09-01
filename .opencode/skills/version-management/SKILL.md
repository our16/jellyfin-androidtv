---
name: version-management
description: Version numbering rules and semantic versioning for Jellyfin Android TV
license: MIT
compatibility: opencode
metadata:
  audience: developers
  workflow: versioning
---

## What I do

- Define semantic versioning rules
- Calculate version codes
- Guide version bump decisions

## When to use me

Trigger when user asks about: "版本", "version", "bump", "升级版本"

## Version Format

```
MAJOR.MINOR.PATCH
```

| Type | Rule | Example |
|------|------|---------|
| PATCH | Bug fixes, crash fixes, minor tweaks | 1.0.0 → 1.0.1 |
| MINOR | New features, UI improvements, performance | 1.0.1 → 1.1.0 |
| MAJOR | Major redesign, breaking changes | 1.1.0 → 2.0.0 |

## Version Code Calculation

```
MAJOR * 1000000 + MINOR * 10000 + PATCH * 100 + 99
```

| Version | Code |
|---------|------|
| 1.0.0 | 1000099 |
| 1.0.1 | 1000199 |
| 1.1.0 | 1010099 |
| 2.0.0 | 2000099 |

## Configuration

Version is set in `gradle.properties`:

```properties
jellyfin.version=1.0.1
```

Or via environment variable:

```powershell
$env:JELLYFIN_VERSION = "1.0.1"
```

## Build Command

```powershell
$env:JELLYFIN_VERSION = "X.Y.Z"
./gradlew :app:assembleRelease
```
