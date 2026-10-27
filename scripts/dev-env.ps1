# 本地开发环境变量（点源使用： . .\scripts\dev-env.ps1）
# 目的：把 JAVA_HOME 固定到 JDK 21，避免机器上多个 JDK 互相干扰。

$jdk21 = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'

if (-not (Test-Path (Join-Path $jdk21 'bin\java.exe'))) {
    Write-Warning "找不到 JDK 21：$jdk21（请先安装 Temurin 21）"
} else {
    $env:JAVA_HOME = $jdk21
    if (-not ($env:PATH -split ';' | Where-Object { $_ -eq (Join-Path $jdk21 'bin') })) {
        $env:PATH = (Join-Path $jdk21 'bin') + ';' + $env:PATH
    }
    Write-Host "JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Green
    & (Join-Path $jdk21 'bin\java.exe') -version
}
