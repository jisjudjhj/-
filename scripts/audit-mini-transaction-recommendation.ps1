param(
    [string]$MiniRoot = "user-miniprogram---2"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = Resolve-Path "."
$miniPath = Join-Path $root $MiniRoot
$failures = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]
$declaredPages = @()

function Add-Failure([string]$message) {
    $failures.Add($message) | Out-Null
}

function Add-Warning([string]$message) {
    $warnings.Add($message) | Out-Null
}

function Read-Utf8([string]$file) {
    return [System.IO.File]::ReadAllText((Resolve-Path $file), [System.Text.Encoding]::UTF8)
}

function Assert-Contains([string]$file, [string]$pattern, [string]$message) {
    if (-not (Test-Path $file)) {
        Add-Failure "$message (missing file: $file)"
        return
    }
    $content = Read-Utf8 $file
    if ($content -notmatch $pattern) {
        Add-Failure $message
    }
}

if (-not (Test-Path $miniPath)) {
    throw "Mini program root not found: $miniPath"
}

$appJsonPath = Join-Path $miniPath "app.json"
if (-not (Test-Path $appJsonPath)) {
    Add-Failure "app.json missing"
} else {
    $appConfig = Read-Utf8 $appJsonPath | ConvertFrom-Json
    $declaredPages = @($appConfig.pages)
}

$criticalPages = @(
    "cart",
    "checkout",
    "orders",
    "order-detail",
    "refund-apply",
    "wallet",
    "ai-assistant",
    "customer-service",
    "customer-chat",
    "search-result",
    "product-detail"
)

foreach ($page in $criticalPages) {
    $pageDir = Join-Path $miniPath "pages/$page"
    if (-not (Test-Path $pageDir)) {
        Add-Failure "Critical page missing: pages/$page"
        continue
    }
    foreach ($suffix in @("js", "wxml", "wxss", "json")) {
        $file = Join-Path $pageDir "index.$suffix"
        if (-not (Test-Path $file)) {
            Add-Failure "Critical page file missing: pages/$page/index.$suffix"
        }
    }
    $wxml = Join-Path $pageDir "index.wxml"
    if (Test-Path $wxml) {
        $wxmlContent = Read-Utf8 $wxml
        if (-not $wxmlContent.Trim() -or $wxmlContent.Length -lt 80) {
            Add-Failure "Critical page looks empty: pages/$page/index.wxml"
        }
    }
    if ($declaredPages -and $declaredPages -notcontains "pages/$page/index") {
        Add-Failure "Critical page not declared in app.json: pages/$page/index"
    }
}

$jsFiles = Get-ChildItem -Path (Join-Path $miniPath "pages") -Recurse -Filter "*.js"
$jsFiles += Get-Item -Path (Join-Path $miniPath "app.js")
$routeRegex = [regex]'/pages/[A-Za-z0-9_-]+/index'
$declaredRouteSet = @{}
if ($declaredPages) {
    foreach ($route in $declaredPages) {
        $declaredRouteSet[$route] = $true
    }
}

foreach ($file in $jsFiles) {
    $content = Read-Utf8 $file.FullName
    $routes = $routeRegex.Matches($content) | ForEach-Object { $_.Value.TrimStart("/") } | Sort-Object -Unique
    foreach ($route in $routes) {
        $pageDir = Join-Path $miniPath ($route -replace '/index$', '')
        if (-not (Test-Path $pageDir) -and -not $declaredRouteSet.ContainsKey($route)) {
            Add-Failure "Broken navigation route in $($file.FullName): /$route"
        }
    }
    $counts = @{}
    foreach ($route in ($routeRegex.Matches($content) | ForEach-Object { $_.Value })) {
        if (-not $counts.ContainsKey($route)) {
            $counts[$route] = 0
        }
        $counts[$route]++
    }
    foreach ($key in $counts.Keys) {
        if ($counts[$key] -ge 8) {
            Add-Warning "High repeated navigation destination in $($file.Name): $key appears $($counts[$key]) times"
        }
    }
}

$trackerPath = Join-Path $miniPath "utils/recommendation-tracker.js"
$appJsPath = Join-Path $miniPath "app.js"
$searchPath = Join-Path $miniPath "pages/search-result/index.js"
$checkoutPath = Join-Path $miniPath "pages/checkout/index.js"
$ordersPath = Join-Path $miniPath "pages/orders/index.js"
$refundPath = Join-Path $miniPath "pages/refund-apply/index.js"

Assert-Contains $trackerPath "/recommendations/events/batch" "Recommendation events must use batch endpoint"
Assert-Contains $trackerPath "EXPOSURE_DEDUPE_TTL_MS" "Exposure dedupe TTL is missing"
Assert-Contains $trackerPath "loadRetryQueue|saveRetryQueue" "Batch retry queue is missing"
Assert-Contains $trackerPath "flushEvents" "Recommendation event flush function is missing"
Assert-Contains $trackerPath "order.+refund|refund.+order" "Order/refund order-only event support is missing"
Assert-Contains $appJsPath "onHide\(\)[\s\S]*flushEvents" "App onHide must flush recommendation events"
Assert-Contains $searchPath "recommendationToken[\s\S]*search_" "Search result token fallback is missing"
Assert-Contains $searchPath "trackExposures" "Search result exposure tracking is missing"
Assert-Contains $searchPath "trackClick" "Search result click tracking is missing"
Assert-Contains $checkoutPath "normalizeCreatedOrders" "Checkout split-order normalization is missing"
Assert-Contains $checkoutPath "paySplitOrders" "Checkout split-order payment path is missing"
Assert-Contains $checkoutPath "checkOrderPaid" "Checkout duplicate-pay guard is missing"
Assert-Contains $ordersPath "eventType:\s*'order'" "Order completion attribution event is missing"
Assert-Contains $refundPath "/refunds" "Refund apply API call is missing"

$orderServicePath = Join-Path $root "backend/src/main/java/com/ecommerce/service/impl/OrderServiceImpl.java"
$refundControllerPath = Join-Path $root "backend/src/main/java/com/ecommerce/controller/RefundController.java"
Assert-Contains $orderServicePath "public List<Order> createOrders" "Cross-merchant split order entry is missing"
Assert-Contains $orderServicePath "\u5546\u54c1\u5e93\u5b58\u4e0d\u8db3|\u79d2\u6740\u5e93\u5b58\u4e0d\u8db3" "Stock insufficient guard is missing"
Assert-Contains $orderServicePath "\u8ba2\u5355\u5df2\u652f\u4ed8\uff0c\u8bf7\u52ff\u91cd\u590d\u64cd\u4f5c" "Duplicate payment guard is missing"
Assert-Contains $orderServicePath "deductBalance" "Balance payment debit is missing"
Assert-Contains $orderServicePath "\u4f18\u60e0\u5238\u5df2\u8fc7\u671f|\u4f18\u60e0\u5238\u5df2\u88ab\u4f7f\u7528|\u4f18\u60e0\u5238\u5df2\u88ab\u5176\u4ed6\u8ba2\u5355\u5360\u7528" "Coupon invalidation guards are missing"
Assert-Contains $refundControllerPath "emitRefundRecommendationEvents" "Refund recommendation attribution emitter is missing"
Assert-Contains $refundControllerPath "recordRecommendationEventAsync" "Refund attribution async event call is missing"

if ($warnings.Count -gt 0) {
    Write-Host "WARNINGS:"
    $warnings | ForEach-Object { Write-Host " - $_" }
}

if ($failures.Count -gt 0) {
    Write-Host "FAILURES:"
    $failures | ForEach-Object { Write-Host " - $_" }
    exit 1
}

Write-Host "Mini transaction and recommendation audit passed."
