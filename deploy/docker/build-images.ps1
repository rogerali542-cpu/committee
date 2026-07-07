# Build all four images and export images.tar (for sandbox "quick deploy" upload).
# Prereq: Docker Desktop installed & running. First build needs network (deps + base images).
# Usage (run from anywhere):
#   .\deploy\docker\build-images.ps1
#
# NOTE: ASCII-only comments on purpose. Windows PowerShell 5.1 reads UTF-8-without-BOM
# files as GBK; non-ASCII (Chinese) comments get mangled and can break parsing.
# NOTE: gate on $LASTEXITCODE, NOT $?. Native exe stderr flips $? to $false in 5.1
# even when the command succeeded (exit 0).
$ErrorActionPreference = "Continue"
$PLATFORM = "linux/amd64"

# Repo root (this script lives in deploy/docker/)
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $RepoRoot
Write-Host "Repo root: $RepoRoot" -ForegroundColor Cyan

Write-Host "==> [1/5] build backend ywh-backend" -ForegroundColor Green
docker build --platform $PLATFORM -t ywh-backend:latest -f backend/Dockerfile .
$e1 = $LASTEXITCODE

Write-Host "==> [2/5] build voice service ywh-ocr-asr" -ForegroundColor Green
docker build --platform $PLATFORM -t ywh-ocr-asr:latest -f ocr-asr-service/Dockerfile ocr-asr-service
$e2 = $LASTEXITCODE

Write-Host "==> [3/5] build frontend/entry ywh-frontend" -ForegroundColor Green
docker build --platform $PLATFORM -t ywh-frontend:latest -f deploy/docker/frontend.Dockerfile .
$e3 = $LASTEXITCODE

Write-Host "==> [4/5] pull mysql:8.0" -ForegroundColor Green
docker pull --platform $PLATFORM mysql:8.0
$e4 = $LASTEXITCODE

if ($e1 -ne 0 -or $e2 -ne 0 -or $e3 -ne 0 -or $e4 -ne 0) {
    Write-Host "BUILD FAILED: backend=$e1 ocr-asr=$e2 frontend=$e3 mysql=$e4" -ForegroundColor Red
    exit 1
}

Write-Host "==> [5/5] export images.tar" -ForegroundColor Green
docker save ywh-backend:latest ywh-ocr-asr:latest ywh-frontend:latest mysql:8.0 -o deploy/docker/images.tar
if ($LASTEXITCODE -ne 0) { Write-Host "docker save FAILED" -ForegroundColor Red; exit 1 }

$size = "{0:N0} MB" -f ((Get-Item deploy/docker/images.tar).Length / 1MB)
Write-Host "Done: deploy/docker/images.tar ($size)" -ForegroundColor Cyan
Write-Host "Next: see deploy/README (sandbox docker deploy)" -ForegroundColor Cyan
