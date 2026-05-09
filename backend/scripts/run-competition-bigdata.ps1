param(
    [string]$SnapshotDate = "",
    [string]$OutputMode = "oss",
    [string]$OssPrefix = "competition/bigdata",
    [string]$SparkMaster = "local[*]",
    [int]$ShufflePartitions = 4,
    [int]$BehaviorWindowDays = 30,
    [int]$OrderWindowDays = 90,
    [switch]$SkipAiBrief
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Resolve-Path (Join-Path $scriptDir "..")
$pythonScript = Join-Path $backendDir "python_analytics\spark_jobs\run_competition_warehouse.py"
$outputDir = Join-Path $backendDir "output\spark_competition"

if (-not (Test-Path $pythonScript)) {
    throw "未找到比赛产物 Python 脚本: $pythonScript"
}

if ([string]::IsNullOrWhiteSpace($SnapshotDate)) {
    $SnapshotDate = (Get-Date).ToString("yyyy-MM-dd")
}

$ossUpload = if ($OutputMode -eq "oss") { "true" } else { "false" }

$args = @(
    $pythonScript,
    "--snapshot-date", $SnapshotDate,
    "--output-dir", $outputDir,
    "--spark-master", $SparkMaster,
    "--shuffle-partitions", [string][Math]::Max($ShufflePartitions, 1),
    "--use-mock-data", "false",
    "--oss-upload", $ossUpload,
    "--oss-prefix", $OssPrefix
)

if ($BehaviorWindowDays -ne 30 -or $OrderWindowDays -ne 90 -or $SkipAiBrief.IsPresent) {
    Write-Host "[competition-wrapper] 当前 Python 入口暂未消费 BehaviorWindowDays/OrderWindowDays/SkipAiBrief，已忽略这些参数。"
}

Push-Location (Split-Path -Parent $pythonScript)
try {
    & python @args
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
