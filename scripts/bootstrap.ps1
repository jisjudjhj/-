param()

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot

Write-Host "[bootstrap] workspace: $root"

$checks = @(
    @{ Name = "backend pom"; Path = Join-Path $root "backend\pom.xml" },
    @{ Name = "management package"; Path = Join-Path $root "management-pc\package.json" },
    @{ Name = "miniprogram config"; Path = Join-Path $root "user-miniprogram---2\project.config.json" },
    @{ Name = "python analytics"; Path = Join-Path $root "backend\python_analytics\scheduler.py" },
    @{ Name = "database schema"; Path = Join-Path $root "backend\src\main\resources\sql\schema.sql" }
)

foreach ($check in $checks) {
    if (!(Test-Path $check.Path)) {
        throw "缺少 $($check.Name): $($check.Path)"
    }
    Write-Host "  ok - $($check.Name)"
}

Write-Host "[bootstrap] 基础目录检查通过"
Write-Host "  后端测试:       cd backend; mvn -q test"
Write-Host "  管理端构建:     cd management-pc; npm run build"
Write-Host "  SQL 文件校验:   npm run smoke"
Write-Host "  Python 调度器:  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run-python-analytics.ps1"
