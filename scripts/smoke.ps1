param()

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$sqlDir = Join-Path $root "backend\src\main\resources\sql"
$chunkDir = Join-Path $sqlDir "chunks"
$schemaFile = Join-Path $sqlDir "schema.sql"
$seedFile = Join-Path $sqlDir "seed.sql"
$generatorFile = Join-Path $root "scripts\generate_seed_chunks.py"

function Read-GeneratorConstant {
    param(
        [string]$Name,
        [int]$Fallback
    )

    $match = Select-String -Path $generatorFile -Pattern "^\s*$Name\s*=\s*(\d+)\s*$" | Select-Object -First 1
    if ($match -and $match.Matches.Count -gt 0) {
        return [int]$match.Matches[0].Groups[1].Value
    }
    return $Fallback
}

if (!(Test-Path $schemaFile)) {
    throw "Missing schema.sql: $schemaFile"
}
if (!(Test-Path $seedFile)) {
    throw "Missing seed.sql: $seedFile"
}
$plannedProductTotal = Read-GeneratorConstant -Name "PLANNED_PRODUCT_TOTAL" -Fallback 0
$normalUserCount = Read-GeneratorConstant -Name "NORMAL_USER_COUNT" -Fallback 0

$seedInfo = Get-Item $seedFile

Write-Host "[smoke] SQL data files are available"
Write-Host "  schema: $schemaFile"
Write-Host "  seed:   $seedFile ($([math]::Round($seedInfo.Length / 1MB, 2)) MB)"
Write-Host "  generator config: products=$plannedProductTotal, normalUsers=$normalUserCount"

if (Test-Path $chunkDir) {
    $productChunks = @(Get-ChildItem $chunkDir -Filter "seed-part-products-*.sql" | Where-Object { $_.Name -notlike "seed-part-products-images-*" } | Sort-Object Name)
    $commerceChunks = @(Get-ChildItem $chunkDir -Filter "seed-part-commerce-*.sql" | Sort-Object Name)
    $baseChunk = Join-Path $chunkDir "seed-part-01-base.sql"
    $extendedChunk = Join-Path $chunkDir "seed-part-extended.sql"

    if (!(Test-Path $baseChunk)) {
        throw "Missing base chunk: $baseChunk"
    }
    if (!(Test-Path $extendedChunk)) {
        throw "Missing extended chunk: $extendedChunk"
    }
    if ($productChunks.Count -eq 0) {
        throw "No product chunks found"
    }
    if ($commerceChunks.Count -eq 0) {
        throw "No commerce chunks found"
    }
    if ($productChunks.Count -ne $commerceChunks.Count) {
        throw "Product chunk count ($($productChunks.Count)) does not match commerce chunk count ($($commerceChunks.Count))"
    }

    $lastProductChunk = $productChunks[-1].BaseName
    $lastChunkMatch = [regex]::Match($lastProductChunk, '(\d+)-(\d+)$')
    if (!$lastChunkMatch.Success) {
        throw "Cannot parse last product chunk range: $lastProductChunk"
    }

    $maxProductId = [int]$lastChunkMatch.Groups[2].Value
    if ($plannedProductTotal -gt 0 -and $maxProductId -lt $plannedProductTotal) {
        throw "Product chunk max id $maxProductId is smaller than planned total $plannedProductTotal"
    }
    Write-Host "  SQL chunks: productChunks=$($productChunks.Count), commerceChunks=$($commerceChunks.Count), maxProductId=$maxProductId"
} else {
    Write-Host "  SQL chunks: not generated, chunk validation skipped. Run scripts/generate_seed_chunks.py when bulk seed chunks are needed."
}
