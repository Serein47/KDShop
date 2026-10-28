# KDShop 本地一键启停：WSL 基础设施 + 后端(8080) + 前端(5173)
#
# 用法（任意目录执行均可）：
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\dev.ps1 start
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\dev.ps1 stop
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\dev.ps1 stop -KeepWsl   # 保留 WSL 不关
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\dev.ps1 status
#
# start : 起 WSL(内含 dockerd) → docker compose up -d → 后端 → 前端，并等到真正可用
# stop  : 停前端 → 停后端 → docker compose stop → wsl --shutdown
# status: 只读检查，不起停任何东西
#
# 日志：logs\backend.log / logs\frontend.log（*.log 已在 .gitignore）
# 注意：本机另有原生 MySQL80 占着 3306，本项目一律用 3307，脚本不会碰 3306。

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'stop', 'status')]
    [string]$Action = 'status',

    [switch]$KeepWsl
)

$ErrorActionPreference = 'Stop'
$env:WSL_UTF8 = '1'   # 让 wsl.exe 输出 UTF-8，避免 PS5.1 下 NUL 字节

$root        = Split-Path -Parent $PSScriptRoot
$backendDir  = Join-Path $root 'backend'
$frontendDir = Join-Path $root 'frontend'
$infraDir    = Join-Path $root 'infra'
$logDir      = Join-Path $root 'logs'

$backendPort  = 8080
$frontendPort = 5173
$containers   = @('kdshop-mysql', 'kdshop-redis', 'kdshop-minio', 'kdshop-mailpit')
$jdkPath      = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$distroFallback = 'Ubuntu-22.04'

function Write-Step([string]$Message) { Write-Host "`n==> $Message" -ForegroundColor Cyan }
function Write-Ok([string]$Message)   { Write-Host "  [OK]   $Message" -ForegroundColor Green }
function Write-Warn2([string]$Message) { Write-Host "  [SKIP] $Message" -ForegroundColor Yellow }
function Write-Fail([string]$Message) { Write-Host "  [FAIL] $Message" -ForegroundColor Red }

function Get-PortPids([int]$Port) {
    $found = @()
    foreach ($line in (netstat -ano)) {
        if ($line -match "^\s*TCP\s+\S+:$Port\s+\S+\s+LISTENING\s+(\d+)\s*$") {
            $found += [int]$Matches[1]
        }
    }
    return ($found | Sort-Object -Unique)
}

function Get-WslInfo {
    $eap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $lines = & wsl.exe -l --verbose 2>&1
        if ($LASTEXITCODE -ne 0) { return @{ Distro = $null; State = 'Absent' } }
        foreach ($line in $lines) {
            if ($line -match '^\s*\*\s+(\S+)\s+(\S+)') {
                return @{ Distro = $Matches[1]; State = $Matches[2] }
            }
        }
        return @{ Distro = $distroFallback; State = 'Unknown' }
    } finally { $ErrorActionPreference = $eap }
}

function ConvertTo-WslPath([string]$WindowsPath) {
    # D:\a\b -> /mnt/d/a/b（不把反斜杠交给 wsl.exe 传参，否则会被吞）
    $p = $WindowsPath -replace '\\', '/'
    if ($p -match '^([A-Za-z]):/(.*)$') { return "/mnt/$($Matches[1].ToLower())/$($Matches[2])" }
    return $p
}

function Invoke-Wsl([string]$Command) {
    # 原生命令的 stderr 在 EAP=Stop 下会变成终止错误，这里隔离掉，只看退出码
    $eap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = & wsl.exe -d $script:distro -- bash -lc $Command 2>&1
        return [pscustomobject]@{ Code = $LASTEXITCODE; Out = (($out | ForEach-Object { "$_" }) -join "`n") }
    } finally { $ErrorActionPreference = $eap }
}

function Wait-Until([scriptblock]$Probe, [int]$TimeoutSec, [string]$What) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try { if (& $Probe) { return $true } } catch { }
        Start-Sleep -Seconds 2
    }
    Write-Fail "$What 在 $TimeoutSec 秒内未就绪"
    return $false
}

function Stop-PortOwner([int]$Port, [string]$Label) {
    $pids = Get-PortPids $Port
    if (-not $pids) { Write-Warn2 "$Label 没有在运行"; return }
    foreach ($procId in $pids) {
        Write-Host "  停止 $Label (PID $procId)"
        & taskkill /PID $procId /T /F 2>$null | Out-Null
    }
    $null = Wait-Until { -not (Get-PortPids $Port) } 15 "$Label 端口 $Port 释放"
    if (-not (Get-PortPids $Port)) { Write-Ok "$Label 已停止" }
}

function Start-Infra {
    Write-Step '基础设施：WSL + docker compose'

    if (-not (Test-Path (Join-Path $jdkPath 'bin\java.exe'))) {
        Write-Warn2 "找不到 JDK 21：$jdkPath（后端可能起不来）"
    } else {
        $env:JAVA_HOME = $jdkPath
        $env:PATH = (Join-Path $jdkPath 'bin') + ';' + $env:PATH
    }

    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        Write-Fail 'mvn 不在 PATH，无法启动后端'
        return $false
    }

    $info = Get-WslInfo
    $script:distro = if ($info.Distro) { $info.Distro } else { $distroFallback }
    Write-Host "  WSL 发行版：$($script:distro)（$($info.State)）"

    $dockerReady = Wait-Until { (Invoke-Wsl 'docker info >/dev/null 2>&1').Code -eq 0 } 120 'docker daemon'
    if (-not $dockerReady) { return $false }
    Write-Ok 'docker daemon 就绪'

    $infraWsl = ConvertTo-WslPath $infraDir

    $code = (Invoke-Wsl "cd '$infraWsl' && docker compose --env-file .env up -d").Code
    if ($code -ne 0) { Write-Fail 'docker compose up -d 失败'; return $false }

    $healthy = Wait-Until {
        $all = $true
        foreach ($c in $containers) {
            $status = (Invoke-Wsl "docker inspect -f '{{.State.Health.Status}}' $c").Out.Trim()
            if ($status -ne 'healthy') { $all = $false }
        }
        $all
    } 150 '四个容器 healthy'
    if (-not $healthy) { return $false }
    Write-Ok 'MySQL :3307 / Redis :6380 / MinIO :9000,9001 / Mailpit :1025,8025 全部 healthy'
    return $true
}

function Start-Backend {
    Write-Step '后端：Spring Boot (8080)'
    if (Get-PortPids $backendPort) { Write-Warn2 "8080 已在监听，跳过"; return $true }

    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $out = Join-Path $logDir 'backend.log'
    $err = Join-Path $logDir 'backend.err.log'
    $mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
    Start-Process -FilePath $env:ComSpec `
        -ArgumentList '/c', "$mvn -B -q -Dspring-boot.run.profiles=local spring-boot:run" `
        -WorkingDirectory $backendDir -WindowStyle Hidden `
        -RedirectStandardOutput $out -RedirectStandardError $err | Out-Null

    $up = Wait-Until {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:$backendPort/actuator/health" -UseBasicParsing -TimeoutSec 3
            $r.StatusCode -eq 200
        } catch { $false }
    } 240 '后端 /actuator/health'
    if ($up) { Write-Ok "后端就绪 http://127.0.0.1:$backendPort （日志 logs\backend.log）" }
    return $up
}

function Start-Frontend {
    Write-Step '前端：Vite (5173)'
    if (Get-PortPids $frontendPort) { Write-Warn2 "5173 已在监听，跳过"; return $true }

    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $out = Join-Path $logDir 'frontend.log'
    $err = Join-Path $logDir 'frontend.err.log'
    Start-Process -FilePath $env:ComSpec -ArgumentList '/c', 'npm run dev' `
        -WorkingDirectory $frontendDir -WindowStyle Hidden `
        -RedirectStandardOutput $out -RedirectStandardError $err | Out-Null

    $up = Wait-Until {
        try {
            $r = Invoke-WebRequest -Uri "http://localhost:$frontendPort/" -UseBasicParsing -TimeoutSec 3
            $r.StatusCode -eq 200
        } catch { $false }
    } 120 '前端 http://localhost:5173'
    if ($up) { Write-Ok "前端就绪 http://localhost:$frontendPort （日志 logs\frontend.log）" }
    return $up
}

function Show-Status {
    Write-Step '状态'
    $info = Get-WslInfo
    $script:distro = if ($info.Distro) { $info.Distro } else { $distroFallback }
    Write-Host "  WSL       : $($script:distro) / $($info.State)"

    if ($info.State -eq 'Running') {
        $r = Invoke-Wsl "docker ps --format '{{.Names}} {{.Status}}'"
        if ($r.Code -eq 0 -and $r.Out.Trim()) {
            foreach ($l in ($r.Out -split "`n")) { Write-Host "  容器      : $($l.Trim())" }
        } else {
            Write-Host '  容器      : docker 不可用'
        }
    } else {
        Write-Host '  容器      : WSL 未运行，全部停止'
    }

    foreach ($pair in @(@{ Port = $backendPort; Name = '后端 8080' }, @{ Port = $frontendPort; Name = '前端 5173' })) {
        $pids = Get-PortPids $pair.Port
        if ($pids) {
            $names = foreach ($p in $pids) { (Get-Process -Id $p -ErrorAction SilentlyContinue).ProcessName }
            Write-Host "  $($pair.Name) : 运行中 (PID $($pids -join ',') / $($names -join ','))"
        } else {
            Write-Host "  $($pair.Name) : 已停止"
        }
    }
}

function Invoke-Start {
    $okInfra = Start-Infra
    if (-not $okInfra) { Write-Fail '基础设施未就绪，前后端不启动（避免连不上库白跑）'; return }

    $okBackend = Start-Backend
    $okFrontend = Start-Frontend

    Write-Step '完成'
    Write-Host "  前端      http://localhost:$frontendPort"
    Write-Host "  后端      http://127.0.0.1:$backendPort/actuator/health"
    Write-Host "  MinIO     http://localhost:9001"
    Write-Host "  Mailpit   http://localhost:8025"
    Write-Host "  MySQL     127.0.0.1:3307 (kdshop/kdshop123)"
    Write-Host "  Redis     127.0.0.1:6380"
    if (-not ($okBackend -and $okFrontend)) {
        Write-Fail '有组件未起来，请看 logs\ 下的日志'
        exit 1
    }
}

function Invoke-Stop {
    Write-Step '停止前端与后端'
    Stop-PortOwner $frontendPort '前端 Vite'
    Stop-PortOwner $backendPort '后端 Spring Boot'

    Write-Step '停止容器'
    $info = Get-WslInfo
    $script:distro = if ($info.Distro) { $info.Distro } else { $distroFallback }
    if ($info.State -ne 'Running') {
        Write-Warn2 'WSL 未运行，容器已经是停止状态'
    } else {
        $infraWsl = ConvertTo-WslPath $infraDir
        $code = (Invoke-Wsl "cd '$infraWsl' && docker compose --env-file .env stop").Code
        if ($code -eq 0) { Write-Ok '四个容器已停止（数据保留在命名卷里）' } else { Write-Fail 'docker compose stop 失败' }
    }

    if ($KeepWsl) {
        Write-Warn2 '按 -KeepWsl 保留 WSL 运行'
    } else {
        Write-Step '关闭 WSL'
        $eap = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & wsl.exe --shutdown
        $ErrorActionPreference = $eap
        Start-Sleep -Seconds 2
        $after = Get-WslInfo
        if ($after.State -eq 'Running') { Write-Fail 'WSL 仍在运行' } else { Write-Ok "WSL 已停止（$($after.State)）" }
    }

    Write-Step '收尾检查'
    foreach ($p in @($backendPort, $frontendPort)) {
        if (Get-PortPids $p) { Write-Fail "端口 $p 仍被占用" } else { Write-Ok "端口 $p 已释放" }
    }
    Write-Host '  注：本机原生 MySQL80 占用的 3306 不属于本项目，未做改动。'
}

switch ($Action) {
    'start'  { Invoke-Start }
    'stop'   { Invoke-Stop }
    'status' { Show-Status }
}
