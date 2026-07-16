param(
    [ValidateSet("windows", "linux", "darwin")]
    [string]$TargetOs = "windows",
    [ValidateSet("amd64", "arm64")]
    [string]$TargetArch = "amd64"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $projectRoot "vendor\open-code-review"
$resourceRoot = Join-Path $projectRoot "src\main\resources\ocr\$TargetOs-$TargetArch"
$extension = if ($TargetOs -eq "windows") { ".exe" } else { "" }
$binary = Join-Path $resourceRoot "opencodereview$extension"

if (-not (Get-Command go -ErrorAction SilentlyContinue)) {
    throw "未找到 Go。构建 OCR 源码需要 Go 1.25.5 或更高版本。"
}
if (-not (Test-Path (Join-Path $sourceRoot "go.mod"))) {
    throw "未找到 vendor/open-code-review 源码。"
}

New-Item -ItemType Directory -Path $resourceRoot -Force | Out-Null
$env:GOOS = $TargetOs
$env:GOARCH = $TargetArch
$env:CGO_ENABLED = "0"
$commit = (Get-Content (Join-Path $sourceRoot "UPSTREAM_COMMIT") -Raw).Trim().Substring(0, 7)
$buildDate = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$ldflags = "-s -w -X main.Version=v1.7.9 -X main.GitCommit=$commit -X main.BuildDate=$buildDate"

Push-Location $sourceRoot
try {
    go build -trimpath -ldflags $ldflags -o $binary ./cmd/opencodereview
    if ($LASTEXITCODE -ne 0) {
        throw "OCR 构建失败。"
    }
} finally {
    Pop-Location
}

$hash = (Get-FileHash -Algorithm SHA256 $binary).Hash.ToLowerInvariant()
Set-Content -Path "$binary.sha256" -Value $hash -Encoding ascii
$sourceStream = [System.IO.File]::OpenRead($binary)
$targetStream = [System.IO.File]::Create("$binary.gz")
try {
    $gzipStream = New-Object System.IO.Compression.GZipStream($targetStream, [System.IO.Compression.CompressionMode]::Compress)
    try {
        $sourceStream.CopyTo($gzipStream)
    } finally {
        $gzipStream.Dispose()
    }
} finally {
    $sourceStream.Dispose()
    $targetStream.Dispose()
}
Remove-Item -LiteralPath $binary -Force
Write-Host "OCR 已生成：$binary.gz"
