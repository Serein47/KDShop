# 一键应用演示素材：上传 MinIO → 绑定数据库 → 验证
param(
    [string]$SqlFile = (Join-Path $PSScriptRoot 'bind-media.sql'),
    [string]$Mysql = 'C:\Program Files\MySQL\MySQL Server 9.6\bin\mysql.exe',
    [string]$DbHost = '127.0.0.1',
    [string]$DbPort = '3307',
    [string]$DbUser = 'kdshop',
    [string]$DbPassword = 'kdshop123',
    [string]$DbName = 'kdshop'
)

$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'upload-assets.ps1')

Get-Content $SqlFile -Raw -Encoding UTF8 | & $Mysql -h $DbHost -P $DbPort -u$DbUser -p$DbPassword $DbName
if ($LASTEXITCODE -ne 0) { throw '绑定 SQL 执行失败' }

& $Mysql -h $DbHost -P $DbPort -u$DbUser -p$DbPassword $DbName -e "SELECT id, name, main_image, JSON_LENGTH(images_json) AS images FROM products WHERE main_image IS NOT NULL ORDER BY id;"
Write-Host '完成。打开 http://localhost:5180/products/21 查看效果。'
