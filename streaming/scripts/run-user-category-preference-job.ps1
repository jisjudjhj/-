param(
  [string]$MySqlHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { "127.0.0.1" }),
  [int]$MySqlPort = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
  [string]$Database = $(if ($env:DB_NAME) { $env:DB_NAME } else { "ecommerce_recommend" }),
  [string]$Username = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "" }),
  [string]$Password = $(if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "" })
)

$ErrorActionPreference = "Stop"
$runner = Join-Path $PSScriptRoot "run-flink-sql-template.ps1"

& $runner `
  -TemplateFile "..\flink-jobs\user-category-preference-job.sql.template" `
  -JobName "user-category-preference-job" `
  -MySqlHost $MySqlHost `
  -MySqlPort $MySqlPort `
  -Database $Database `
  -Username $Username `
  -Password $Password
