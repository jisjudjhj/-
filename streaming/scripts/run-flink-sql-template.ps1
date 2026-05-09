param(
  [Parameter(Mandatory = $true)]
  [string]$TemplateFile,

  [string]$JobName = "",
  [string]$MySqlHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { "127.0.0.1" }),
  [int]$MySqlPort = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
  [string]$Database = $(if ($env:DB_NAME) { $env:DB_NAME } else { "ecommerce_recommend" }),
  [string]$Username = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "" }),
  [string]$Password = $(if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "" })
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Username) -or [string]::IsNullOrWhiteSpace($Password)) {
  throw "未检测到数据库账号或密码，请通过参数或环境变量 DB_USERNAME / DB_PASSWORD 提供。"
}

if (-not $env:FLINK_HOME) {
  throw "未检测到 FLINK_HOME，请先设置本机 Flink 安装目录。"
}

$sqlClient = Join-Path $env:FLINK_HOME "bin\sql-client.bat"
if (-not (Test-Path $sqlClient)) {
  throw "未找到 Flink SQL Client: $sqlClient"
}

if ([System.IO.Path]::IsPathRooted($TemplateFile)) {
  $templatePath = $TemplateFile
} else {
  $templatePath = Join-Path $PSScriptRoot $TemplateFile
}

if (-not (Test-Path $templatePath)) {
  throw "未找到 SQL 模板: $templatePath"
}

$templatePath = (Resolve-Path $templatePath).Path
$template = Get-Content $templatePath -Raw -Encoding UTF8
$sql = $template
$sql = $sql.Replace("__MYSQL_HOST__", $MySqlHost)
$sql = $sql.Replace("__MYSQL_PORT__", [string]$MySqlPort)
$sql = $sql.Replace("__MYSQL_DATABASE__", $Database)
$sql = $sql.Replace("__MYSQL_USERNAME__", $Username)
$sql = $sql.Replace("__MYSQL_PASSWORD__", $Password)

$safeName = if ([string]::IsNullOrWhiteSpace($JobName)) {
  [System.IO.Path]::GetFileNameWithoutExtension($templatePath)
} else {
  $JobName.Trim()
}
$generatedFile = Join-Path $env:TEMP ($safeName + ".generated.sql")
Set-Content -Path $generatedFile -Value $sql -Encoding UTF8

Write-Host "Submitting Flink SQL job: $safeName"
Write-Host "MySQL: $MySqlHost`:$MySqlPort / $Database"

& $sqlClient -f $generatedFile
