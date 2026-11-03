# 扫描已生成的图片，生成并执行绑定 SQL（每个商品取实际存在的图）
param(
    [string]$ImagesDir = (Join-Path $PSScriptRoot '../../infra/demo-assets/images/products'),
    [string]$Mysql = 'C:\Program Files\MySQL\MySQL Server 9.6\bin\mysql.exe',
    [string]$DbHost = '127.0.0.1',
    [string]$DbPort = '3307',
    [string]$DbUser = 'kdshop',
    [string]$DbPassword = 'kdshop123',
    [string]$DbName = 'kdshop',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path $ImagesDir)) { throw "没有图片目录：$ImagesDir" }

$statements = @()
foreach ($dir in Get-ChildItem $ImagesDir -Directory | Sort-Object { [int]$_.Name }) {
    $id = [int]$dir.Name
    $files = Get-ChildItem $dir.FullName -Filter *.jpg | Where-Object { $_.Length -gt 5000 } | Sort-Object Name
    if (-not $files) { continue }
    $keys = $files | ForEach-Object { "'products/$id/$($_.Name)'" }
    $main = "products/$id/$($files[0].Name)"
    $statements += "UPDATE products SET main_image='$main', images_json=JSON_ARRAY($($keys -join ',')) WHERE id=$id;"
}

$sql = $statements -join "`n"
Set-Content (Join-Path $PSScriptRoot 'bind-media.sql') $sql -Encoding UTF8
Write-Host "生成 $($statements.Count) 条绑定语句"
if ($DryRun) { Write-Host $sql; exit 0 }

Get-Content (Join-Path $PSScriptRoot 'bind-media.sql') -Raw | & $Mysql "-h$DbHost" "-P$DbPort" "--user=$DbUser" "--password=$DbPassword" $DbName
if ($LASTEXITCODE -ne 0) { throw '绑定失败' }
& $Mysql "-h$DbHost" "-P$DbPort" "--user=$DbUser" "--password=$DbPassword" $DbName -e "SELECT COUNT(*) AS bound FROM products WHERE main_image IS NOT NULL;"
