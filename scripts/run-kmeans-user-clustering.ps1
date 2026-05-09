param(
    [string]$SnapshotDate = "",
    [int]$K = 3,
    [string]$AutoK = "false",
    [int]$MinK = 2,
    [int]$MaxK = 6,
    [string]$PythonExe = ""
)

$ErrorActionPreference = "Stop"

$runner = Join-Path $PSScriptRoot "run-python-analytics.ps1"
if (-not (Test-Path $runner)) {
    throw "Python analytics runner not found: $runner"
}

$argsList = @(
    "-RunOnce",
    "-Jobs", "analytics,kmeans,recommendation",
    "-K", [string]$K,
    "-AutoK", $AutoK,
    "-MinK", [string]$MinK,
    "-MaxK", [string]$MaxK
)

if (-not [string]::IsNullOrWhiteSpace($SnapshotDate)) {
    $argsList += @("-SnapshotDate", $SnapshotDate)
}

if (-not [string]::IsNullOrWhiteSpace($PythonExe)) {
    $argsList += @("-PythonExe", $PythonExe)
}

Write-Host "[run-kmeans-user-clustering] Delegating to run-python-analytics.ps1"
& $runner @argsList
exit $LASTEXITCODE
