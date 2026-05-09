param(
    [int]$Limit = 50,
    [int]$Offset = 0,
    [int]$Concurrency = 8,
    [switch]$ApplyDb,
    [string]$DbHost,
    [int]$DbPort = 3306,
    [string]$DbUser,
    [string]$DbPassword,
    [string]$DbName
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$ImageGen = Join-Path $env:USERPROFILE ".codex\skills\.system\imagegen\scripts\image_gen.py"
$PromptJsonl = Join-Path $Root "tmp\imagegen\image2-products-50.jsonl"
$OutputDir = Join-Path $Root "output\image2-product-images"

if (-not $env:OPENAI_API_KEY) {
    throw "OPENAI_API_KEY is not set. Set it before running image2 generation."
}

if (-not (Test-Path $PromptJsonl)) {
    throw "Prompt JSONL not found: $PromptJsonl"
}

python $ImageGen generate-batch `
    --input $PromptJsonl `
    --out-dir $OutputDir `
    --concurrency $Concurrency `
    --force

$UploadArgs = @(
    (Join-Path $Root "scripts\upload_image2_products_to_oss_and_db.py"),
    "--images-dir", $OutputDir,
    "--limit", "$Limit",
    "--offset", "$Offset"
)

if ($ApplyDb) {
    $UploadArgs += "--apply-db"
    if ($DbHost) { $UploadArgs += @("--db-host", $DbHost) }
    if ($DbPort) { $UploadArgs += @("--db-port", "$DbPort") }
    if ($DbUser) { $UploadArgs += @("--db-user", $DbUser) }
    if ($DbPassword) { $UploadArgs += @("--db-password", $DbPassword) }
    if ($DbName) { $UploadArgs += @("--db-name", $DbName) }
}

python @UploadArgs
