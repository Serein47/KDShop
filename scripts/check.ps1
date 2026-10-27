# 本地一键检查：后端测试 + 前端类型检查与构建
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check.ps1
param(
    [switch]$SkipFrontend
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$failed = 0

function Step([string]$name, [scriptblock]$action) {
    Write-Host ""
    Write-Host "==> $name" -ForegroundColor Cyan
    & $action
    if ($LASTEXITCODE -ne 0) {
        Write-Host "✗ $name 失败" -ForegroundColor Red
        $script:failed++
    } else {
        Write-Host "✓ $name 通过" -ForegroundColor Green
    }
}

$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Step "后端测试" {
    Push-Location (Join-Path $root "backend")
    try { mvn -B -q test } finally { Pop-Location }
}

if (-not $SkipFrontend) {
    Step "前端构建" {
        Push-Location (Join-Path $root "frontend")
        try { npm run build } finally { Pop-Location }
    }
}

Write-Host ""
if ($failed -gt 0) {
    Write-Host "检查未通过：$failed 项失败" -ForegroundColor Red
    exit 1
}
Write-Host "全部通过" -ForegroundColor Green
