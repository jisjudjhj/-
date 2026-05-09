$ErrorActionPreference = "Stop"

if (-not $env:FLINK_HOME) {
  throw "未检测到 FLINK_HOME，请先设置本机 Flink 安装目录。"
}

$stopScript = Join-Path $env:FLINK_HOME "bin\stop-cluster.bat"
if (-not (Test-Path $stopScript)) {
  throw "未找到停止脚本: $stopScript"
}

Write-Host "Stopping local Flink cluster from $env:FLINK_HOME ..."
& $stopScript
