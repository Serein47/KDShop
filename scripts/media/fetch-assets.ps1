# 按 manifest 从 Pexels 拉取演示图片（需要环境变量 PEXELS_API_KEY）
# 用法：
#   $env:PEXELS_API_KEY = "你的 key"
#   powershell -File scripts/media/fetch-assets.ps1            # 全部 30 个商品
#   powershell -File scripts/media/fetch-assets.ps1 -Pilot     # 只跑试点商品
param(
    [switch]$Pilot,
    [string]$Manifest = (Join-Path $PSScriptRoot 'manifest.json'),
    [string]$OutDir = (Join-Path $PSScriptRoot '../../infra/demo-assets/images')
)

$ErrorActionPreference = 'Stop'
$key = $env:PEXELS_API_KEY
if (-not $key) {
    throw '缺少 PEXELS_API_KEY。到 https://www.pexels.com/api/ 免费申请后设置环境变量再运行。'
}

$m = Get-Content $Manifest -Raw -Encoding UTF8 | ConvertFrom-Json
$resolvedPath = Join-Path $PSScriptRoot 'resolved.json'
$resolved = @{}
if (Test-Path $resolvedPath) {
    (Get-Content $resolvedPath -Raw -Encoding UTF8 | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $resolved[$_.Name] = $_.Value }
}

foreach ($p in $m.products) {
    if ($Pilot -and -not $p.pilot) { continue }
    $dir = Join-Path $OutDir "products/$($p.id)"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null

    for ($i = 0; $i -lt $p.count; $i++) {
        $file = Join-Path $dir "$($i + 1).jpg"
        if (Test-Path $file) { continue }

        $query = [uri]::EscapeDataString($p.queries[$i % $p.queries.Count])
        $url = "https://api.pexels.com/v1/search?query=$query&per_page=8&orientation=square"
        $resp = Invoke-RestMethod -Uri $url -Headers @{ Authorization = $key } -TimeoutSec 30

        $photo = $resp.photos | Where-Object { $_.width -ge 800 } | Select-Object -First 1
        if (-not $photo) {
            Write-Warning "没有合适图片：$($p.name) / $query"
            continue
        }

        Invoke-WebRequest -Uri $photo.src.large -OutFile $file -TimeoutSec 60
        $resolved["products/$($p.id)/$($i + 1).jpg"] = [ordered]@{
            productId   = $p.id
            productName = $p.name
            query       = $p.queries[$i % $p.queries.Count]
            photographer = $photo.photographer
            photographerUrl = $photo.photographer_url
            photoUrl    = $photo.url
            license     = 'Pexels License'
        }
        Write-Host "OK  $($p.name) -> products/$($p.id)/$($i + 1).jpg ($($photo.photographer))"
        Start-Sleep -Milliseconds 350
    }
}

$resolved | ConvertTo-Json -Depth 6 | Set-Content $resolvedPath -Encoding UTF8
Write-Host "完成，元数据写入 resolved.json"
