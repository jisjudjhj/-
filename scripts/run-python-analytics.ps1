param(
    [switch]$RunOnce,
    [string]$SnapshotDate = "",
    [string]$Jobs = "analytics,kmeans,recommendation",
    [int]$IntervalMinutes = 60,
    [int]$K = 3,
    [string]$AutoK = "false",
    [int]$MinK = 2,
    [int]$MaxK = 6,
    [string]$PythonExe = ""
)

$ErrorActionPreference = "Stop"
$env:PYTHONIOENCODING = "utf-8"

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$analyticsDir = Join-Path $projectRoot "backend\python_analytics"
$scheduler = Join-Path $analyticsDir "scheduler.py"

if (-not (Test-Path $scheduler)) {
    throw "Python analytics scheduler not found: $scheduler"
}

if ([string]::IsNullOrWhiteSpace($PythonExe)) {
    $PythonExe = "python"
}

$argsList = @(
    $scheduler,
    "--jobs", $Jobs,
    "--interval-minutes", [string]$IntervalMinutes,
    "--k", [string]$K,
    "--auto-k", $AutoK,
    "--min-k", [string]$MinK,
    "--max-k", [string]$MaxK
)

if ($RunOnce) {
    $argsList += "--run-once"
}

if (-not [string]::IsNullOrWhiteSpace($SnapshotDate)) {
    $argsList += @("--snapshot-date", $SnapshotDate)
}

Write-Host "[run-python-analytics] projectRoot=$projectRoot"
Write-Host "[run-python-analytics] analyticsDir=$analyticsDir"
Write-Host "[run-python-analytics] jobs=$Jobs snapshotDate=$SnapshotDate runOnce=$RunOnce"

Push-Location $analyticsDir
try {
    & $PythonExe @argsList
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
