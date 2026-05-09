$ErrorActionPreference = "Stop"

if (-not $env:FLINK_HOME) {
  throw "未检测到 FLINK_HOME，请先设置本机 Flink 安装目录。"
}

$startScript = Join-Path $env:FLINK_HOME "bin\start-cluster.bat"
if (-not (Test-Path $startScript)) {
  throw "未找到启动脚本: $startScript"
}

Write-Host "Starting local Flink cluster from $env:FLINK_HOME ..."
& $startScript

Write-Host "Flink UI: http://localhost:8081"
