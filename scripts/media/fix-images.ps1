# 针对个别生成错误的商品，用指定提示词重跑（flux 模型，主体还原度更好）
param(
    [string]$FixList = (Join-Path $PSScriptRoot 'fix-list.json'),
    [string]$OutDir = (Join-Path $PSScriptRoot '../../infra/demo-assets/images/products'),
    [int]$PauseSeconds = 50
)
$ErrorActionPreference = 'Stop'
$style = ', e-commerce catalog photography, high key lighting, no people, no text, centered'
$items = Get-Content $FixList -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($item in $items) {
    $dir = Join-Path $OutDir "$($item.id)"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $file = Join-Path $dir '1.jpg'
    $prompt = $item.prompt + $style
    $encoded = [uri]::EscapeDataString($prompt)
    $url = "https://image.pollinations.ai/prompt/${encoded}?width=800&height=800&nologo=true&model=flux&seed=$($item.id)"
    $wslFile = (& wsl -e wslpath -a $file).Trim()
    $ok = $false
    for ($attempt = 1; $attempt -le 6 -and -not $ok; $attempt++) {
        $code = (& wsl -e curl -s -L --max-time 150 -o $wslFile -w '%{http_code}' $url).Trim()
        if ($code -eq '200') { $ok = $true } else { Write-Warning "商品 $($item.id) 第 $attempt 次失败（$code）"; Start-Sleep -Seconds 45 }
    }
    if (-not $ok) { Write-Warning "放弃商品 $($item.id)"; continue }
    Add-Type -AssemblyName System.Drawing
    $img = [System.Drawing.Image]::FromFile($file)
    $h = [int]($img.Height * 0.92)
    $crop = New-Object System.Drawing.Bitmap $img.Width, $h
    $g = [System.Drawing.Graphics]::FromImage($crop)
    $g.DrawImage($img, (New-Object System.Drawing.Rectangle 0, 0, $img.Width, $h), (New-Object System.Drawing.Rectangle 0, 0, $img.Width, $h), [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose(); $img.Dispose()
    $crop.Save($file, [System.Drawing.Imaging.ImageFormat]::Jpeg); $crop.Dispose()
    Write-Host "OK 商品 $($item.id)"
    Start-Sleep -Seconds $PauseSeconds
}
