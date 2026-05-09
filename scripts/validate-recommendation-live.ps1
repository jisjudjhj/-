param(
    [string]$BaseUrl = $env:ECOMMERCE_BASE_URL,
    [string]$Token = $env:ECOMMERCE_ADMIN_TOKEN,
    [int[]]$UserId = @(),
    [int]$SampleSize = 10,
    [int]$UserPageSize = 80,
    [int]$Limit = 10,
    [double]$MinHitRate = 60.0,
    [int]$MinProducts = 5,
    [int]$LowStockThreshold = 20
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $BaseUrl = "http://localhost:8080"
}

$argsList = @(
    "scripts/validate-recommendation-live.py",
    "--base-url", $BaseUrl,
    "--sample-size", [string]$SampleSize,
    "--user-page-size", [string]$UserPageSize,
    "--limit", [string]$Limit,
    "--min-hit-rate", [string]$MinHitRate,
    "--min-products", [string]$MinProducts,
    "--low-stock-threshold", [string]$LowStockThreshold
)

if (-not [string]::IsNullOrWhiteSpace($Token)) {
    $argsList += @("--token", $Token)
}

foreach ($id in $UserId) {
    $argsList += @("--user-id", [string]$id)
}

Push-Location $projectRoot
try {
    python @argsList
} finally {
    Pop-Location
}
