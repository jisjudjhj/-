Param(
    [string]$ConnectUrl = "http://localhost:8083",
    [string[]]$ConnectorFiles = @(
        "F:\IDEAwenjian\大数据电商系统\streaming\connectors\mysql-cdc-user-behavior.json",
        "F:\IDEAwenjian\大数据电商系统\streaming\connectors\mysql-cdc-orders.json",
        "F:\IDEAwenjian\大数据电商系统\streaming\connectors\mysql-cdc-order-item.json",
        "F:\IDEAwenjian\大数据电商系统\streaming\connectors\mysql-cdc-product.json",
        "F:\IDEAwenjian\大数据电商系统\streaming\connectors\mysql-cdc-recommendation-event.json"
    )
)

$ErrorActionPreference = "Stop"

function Register-Connector {
    param([string]$FilePath)

    if (-not (Test-Path $FilePath)) {
        throw "Connector file not found: $FilePath"
    }

    $payload = Get-Content -Path $FilePath -Raw | ConvertFrom-Json
    $name = $payload.name
    $config = $payload.config

    if (-not $name) {
        throw "Connector name missing in: $FilePath"
    }

    Write-Host "==> registering connector: $name"
    try {
        Invoke-RestMethod -Method Get -Uri "$ConnectUrl/connectors/$name" | Out-Null
        Invoke-RestMethod `
            -Method Put `
            -Uri "$ConnectUrl/connectors/$name/config" `
            -ContentType "application/json" `
            -Body ($config | ConvertTo-Json -Depth 20) | Out-Null
        Write-Host "updated: $name"
    } catch {
        Invoke-RestMethod `
            -Method Post `
            -Uri "$ConnectUrl/connectors" `
            -ContentType "application/json" `
            -Body ($payload | ConvertTo-Json -Depth 20) | Out-Null
        Write-Host "created: $name"
    }
}

foreach ($file in $ConnectorFiles) {
    Register-Connector -FilePath $file
}

Write-Host ""
Write-Host "==> connector list"
Invoke-RestMethod -Method Get -Uri "$ConnectUrl/connectors"
