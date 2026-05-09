Param(
    [string]$ConnectUrl = "http://localhost:8083"
)

$scriptPath = "F:\IDEAwenjian\大数据电商系统\streaming\scripts\register-core-cdc-connectors.ps1"
$filePath = "F:\IDEAwenjian\大数据电商系统\streaming\connectors\mysql-cdc-order-item.json"

powershell -NoProfile -ExecutionPolicy Bypass -File $scriptPath `
  -ConnectUrl $ConnectUrl `
  -ConnectorFiles $filePath
