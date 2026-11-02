# 把本地演示图片同步到 MinIO，并开放公共只读。
# 默认通过 WSL 调 docker（本项目基础设施跑在 WSL 里）；Windows 侧有原生 docker 时加 -Native。
param(
    [string]$Container = 'kdshop-minio',
    [string]$Bucket = 'kdshop-media',
    [string]$LocalDir = (Join-Path $PSScriptRoot '../../infra/demo-assets/images'),
    [switch]$Native
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path $LocalDir)) { throw "找不到素材目录：$LocalDir" }

if ($Native) {
    $docker = 'docker'
    $src = (Resolve-Path $LocalDir).Path
} else {
    $docker = 'wsl -e docker'
    $win = (Resolve-Path $LocalDir).Path
    $src = (& wsl -e wslpath -a $win).Trim()
}

function Invoke-Docker([string]$arguments) {
    Invoke-Expression ("$docker $arguments") | Out-Null
}

Invoke-Docker "exec $Container mc alias set local http://127.0.0.1:9000 kdshop kdshop123"
Invoke-Docker "exec $Container mc mb --ignore-existing local/$Bucket"
Invoke-Docker "exec $Container mc anonymous set download local/$Bucket"

if ($Native) {
    Invoke-Docker ("cp " + $src + "/. " + $Container + ":/tmp/assets")
} else {
    & wsl -e docker exec $Container rm -rf /tmp/assets
    & wsl -e docker cp "$src/." "$($Container):/tmp/assets"
}

Invoke-Docker "exec $Container mc cp --recursive --force /tmp/assets/ local/$Bucket/"
Invoke-Docker "exec $Container rm -rf /tmp/assets"

Write-Host "已同步到 $Bucket"
Write-Host "公共地址：http://localhost:9000/$Bucket/"
