param(
    [switch]$SkipMavenTest
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

Write-Host "[audit] projectRoot=$projectRoot"
Push-Location $projectRoot
try {
    python "scripts/audit-recommendation-logic.py"
    python -m py_compile `
        "scripts/audit-recommendation-logic.py" `
        "scripts/validate-recommendation-live.py" `
        "backend/python_analytics/recommendation_builder.py" `
        "backend/python_analytics/feature_builder.py" `
        "backend/python_analytics/kmeans_trainer.py"

    if (-not $SkipMavenTest) {
        Push-Location "backend"
        try {
            mvn -q test
        } finally {
            Pop-Location
        }
    }
    Write-Host "[audit] all checks passed"
} finally {
    Pop-Location
}
