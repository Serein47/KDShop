# 按 manifest 生成演示图片（Pollinations / Flux，免费无需 key）
# 免费额度：约 1 张 / 45 秒 / IP，脚本会重试并放慢节奏
# 用法：powershell -ExecutionPolicy Bypass -File scripts/media/fetch-assets.ps1 -Pilot -MaxPerProduct 1
param(
    [switch]$Pilot,
    [int]$MaxPerProduct = 3,
    [int]$PauseSeconds = 55,
    [int]$MaxAttempts = 6,
    [string]$Manifest = (Join-Path $PSScriptRoot 'manifest.json'),
    [string]$OutDir = (Join-Path $PSScriptRoot '../../infra/demo-assets/images')
)

$ErrorActionPreference = 'Stop'
$style = ', e-commerce catalog photography, high key lighting, sharp focus, no props, no scenery, centered, no text'
$packagingHint = ', retail packaging beside the product'
$m = Get-Content $Manifest -Raw -Encoding UTF8 | ConvertFrom-Json
$resolvedPath = Join-Path $PSScriptRoot 'resolved.json'
$resolved = [ordered]@{}
if (Test-Path $resolvedPath) {
    $old = Get-Content $resolvedPath -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($prop in $old.PSObject.Properties) { $resolved[$prop.Name] = $prop.Value }
}

function Convert-ToWslPath([string]$winPath) { (& wsl -e wslpath -a $winPath).Trim() }

function Remove-Watermark([string]$file) {
    Add-Type -AssemblyName System.Drawing
    $img = [System.Drawing.Image]::FromFile($file)
    $h = [int]($img.Height * 0.92)
    $crop = New-Object System.Drawing.Bitmap $img.Width, $h
    $g = [System.Drawing.Graphics]::FromImage($crop)
    $g.DrawImage($img, (New-Object System.Drawing.Rectangle 0, 0, $img.Width, $h), (New-Object System.Drawing.Rectangle 0, 0, $img.Width, $h), [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose(); $img.Dispose()
    $crop.Save($file, [System.Drawing.Imaging.ImageFormat]::Jpeg)
    $crop.Dispose()
}

$total = 0
foreach ($p in $m.products) {
    if ($Pilot -and -not $p.pilot) { continue }
    $dir = Join-Path $OutDir "products/$($p.id)"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $count = [Math]::Min($MaxPerProduct, $p.count)

    for ($i = 0; $i -lt $count; $i++) {
        $file = Join-Path $dir "$($i + 1).jpg"
        if (Test-Path $file) { continue }

        $extra = if ($p.name -match '礼盒|瓶|包|支装|套装') { $packagingHint } else { '' }
        $prompt = 'isolated product photo on pure white seamless background, ' + $p.queries[$i % $p.queries.Count] + $style + $extra
        $encoded = [uri]::EscapeDataString($prompt)
        $seed = $p.id * 10 + $i
        $url = "https://image.pollinations.ai/prompt/${encoded}?width=800&height=800&nologo=true&model=turbo&seed=$seed"
        $wslFile = Convert-ToWslPath $file

        $ok = $false
        for ($attempt = 1; $attempt -le $MaxAttempts -and -not $ok; $attempt++) {
            $code = (& wsl -e curl -s -L --max-time 150 -o $wslFile -w '%{http_code}' $url).Trim()
            if ($code -eq '200') { $ok = $true }
            else {
                Write-Warning "$($p.name) 第 $attempt 次失败（HTTP $code），等待重试"
                if (Test-Path $file) { Remove-Item $file -Force }
                Start-Sleep -Seconds 45
            }
        }
        if (-not $ok) { Write-Warning "放弃：$($p.name) 第 $($i + 1) 张"; continue }

        Remove-Watermark $file
        $resolved["products/$($p.id)/$($i + 1).jpg"] = [ordered]@{
            productId = $p.id
            productName = $p.name
            prompt = $prompt
            seed = $seed
            model = 'flux via pollinations.ai'
            license = 'AI generated, no third-party copyright'
        }
        $total++
        Write-Host "OK  $($p.name) -> products/$($p.id)/$($i + 1).jpg"
        if ($i -lt $count - 1) { Start-Sleep -Seconds $PauseSeconds }
    }
}

$resolved | ConvertTo-Json -Depth 6 | Set-Content $resolvedPath -Encoding UTF8
Write-Host "完成，本次生成 $total 张"
