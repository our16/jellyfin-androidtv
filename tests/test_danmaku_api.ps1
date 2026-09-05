#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Jellyfin Danmaku API 接口测试脚本
#>

$BASE_URL = "http://localhost:8097"
$HEADERS = @{
    "Authorization" = 'MediaBrowser Token="test-token"'
    "X-Emby-Authorization" = 'MediaBrowser Client="Jellyfin for Android TV", Device="androidtv"'
    "Accept" = 'application/json; profile="CamelCase"'
}

$PASS = 0
$FAIL = 0
$RESULTS = @()

function Test-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [int]$ExpectedStatus = 200,
        [object]$Body = $null
    )

    $url = "$BASE_URL$Path"
    $result = [PSCustomObject]@{
        Name   = $Name
        Method = $Method
        Path   = $Path
        Status = "FAIL"
        Code   = 0
        Detail = ""
    }

    try {
        $params = @{
            Uri     = $url
            Method  = $Method
            Headers = $HEADERS
            UseBasicParsing = $true
        }

        if ($Body) {
            $params.Body = ($Body | ConvertTo-Json -Depth 10)
            $params.ContentType = "application/json"
        }

        $response = Invoke-WebRequest @params
        $result.Code = $response.StatusCode

        if ($response.StatusCode -eq $ExpectedStatus) {
            $result.Status = "PASS"
            $script:PASS++
        } else {
            $result.Detail = "Expected $ExpectedStatus, got $($response.StatusCode)"
            $script:FAIL++
        }
    } catch {
        $code = 0
        if ($_.Exception.Response) {
            $code = [int]$_.Exception.Response.StatusCode
        }
        $result.Code = $code

        if ($code -eq $ExpectedStatus) {
            $result.Status = "PASS"
            $script:PASS++
        } else {
            $result.Detail = "Expected $ExpectedStatus, got $code"
            $script:FAIL++
        }
    }

    $script:RESULTS += $result
    $icon = if ($result.Status -eq "PASS") { "[PASS]" } else { "[FAIL]" }
    $color = if ($result.Status -eq "PASS") { "Green" } else { "Red" }
    Write-Host "$icon $($result.Name) - $($result.Method) $($result.Path) -> $($result.Code)" -ForegroundColor $color
    if ($result.Detail) { Write-Host "       $($result.Detail)" -ForegroundColor Yellow }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Jellyfin Danmaku API Interface Tests" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# ===========================================
# 1. 核心接口
# ===========================================
Write-Host "--- 1. Core APIs ---" -ForegroundColor Yellow

Test-Api "Get danmaku info" "GET" "/api/danmaku/test-item-id"
Test-Api "Get danmaku info (not found)" "GET" "/api/danmaku/nonexistent-item" 404
Test-Api "Get danmaku url" "GET" "/api/danmaku/test-item-id/url"
Test-Api "Get danmaku raw" "GET" "/api/danmaku/test-item-id/raw"
Test-Api "Refresh danmaku" "POST" "/api/danmaku/test-item-id/refresh" 202
Test-Api "Delete danmaku cache" "DELETE" "/api/danmaku/test-item-id" 204

# ===========================================
# 2. 搜索接口
# ===========================================
Write-Host ""
Write-Host "--- 2. Search APIs ---" -ForegroundColor Yellow

Test-Api "Search danmaku" "GET" "/api/danmaku/search?keyword=test"
Test-Api "Search danmaku by item" "GET" "/api/danmaku/search/by-item/test-item-id"

# ===========================================
# 3. 弹幕源管理接口
# ===========================================
Write-Host ""
Write-Host "--- 3. Source Management APIs ---" -ForegroundColor Yellow

Test-Api "Get sources" "GET" "/api/danmaku/sources"
Test-Api "Update source" "PUT" "/api/danmaku/sources/bilibili" -Body @{ enabled = $true; priority = 1 }
Test-Api "Test source connection" "POST" "/api/danmaku/sources/bilibili/test"

# ===========================================
# 4. 配置接口
# ===========================================
Write-Host ""
Write-Host "--- 4. Config APIs ---" -ForegroundColor Yellow

Test-Api "Get config" "GET" "/api/danmaku/config"
Test-Api "Update config" "PUT" "/api/danmaku/config" -Body @{ enabled = $true; cacheExpiryDays = 30 }

# ===========================================
# 5. 缓存管理接口
# ===========================================
Write-Host ""
Write-Host "--- 5. Cache Management APIs ---" -ForegroundColor Yellow

Test-Api "Get cache stats" "GET" "/api/danmaku/cache/stats"
Test-Api "Get cache list" "GET" "/api/danmaku/cache"
Test-Api "Cleanup cache" "POST" "/api/danmaku/cache/cleanup"
Test-Api "Clear all cache" "DELETE" "/api/danmaku/cache" 204

# ===========================================
# 6. 弹弹play 兼容接口
# ===========================================
Write-Host ""
Write-Host "--- 6. Dandanplay Compatible APIs ---" -ForegroundColor Yellow

Test-Api "Search anime (v2)" "GET" "/api/v2/search/anime?keyword=test"
Test-Api "Get comment xml (v2)" "GET" "/api/v2/comment/ep001?format=xml"
Test-Api "Get comment json (v2)" "GET" "/api/v2/comment/ep001?format=json"

# ===========================================
# 结果汇总
# ===========================================
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Test Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Total:  $($PASS + $FAIL)"
Write-Host "Passed: $PASS" -ForegroundColor Green
Write-Host "Failed: $FAIL" -ForegroundColor $(if ($FAIL -gt 0) { "Red" } else { "Green" })
Write-Host ""

if ($FAIL -gt 0) {
    Write-Host "Failed tests:" -ForegroundColor Red
    $RESULTS | Where-Object { $_.Status -eq "FAIL" } | ForEach-Object {
        Write-Host "  - $($_.Name) ($($_.Method) $($_.Path)) -> $($_.Code) $($_.Detail)" -ForegroundColor Red
    }
} else {
    Write-Host "All tests passed!" -ForegroundColor Green
}
