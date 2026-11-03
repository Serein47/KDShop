# 检测图片背景是否接近纯白（用于筛选需要重跑的商品图）
param(
    [string]$ImagesDir = (Join-Path $PSScriptRoot '../../infra/demo-assets/images/products'),
    [string]$ImagePath,
    [double]$MinLum = 0.90,
    [double]$MaxSat = 0.10
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

function Measure-Image([string]$path) {
    $bmp = [System.Drawing.Bitmap]::FromFile($path)
    try {
        $sum = 0.0; $satSum = 0.0; $count = 0
        $step = [Math]::Max(4, [int]($bmp.Width / 40))
        $coords = New-Object System.Collections.Generic.List[object]
        for ($x = 0; $x -lt $bmp.Width; $x += $step) {
            $coords.Add(@($x, 3)) | Out-Null
            $coords.Add(@($x, ($bmp.Height - 4))) | Out-Null
        }
        for ($y = 0; $y -lt $bmp.Height; $y += $step) {
            $coords.Add(@(3, $y)) | Out-Null
            $coords.Add(@(($bmp.Width - 4), $y)) | Out-Null
        }
        foreach ($pt in $coords) {
            $c = $bmp.GetPixel([int]$pt[0], [int]$pt[1])
            $lum = (0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B) / 255.0
            $mx = [Math]::Max($c.R, [Math]::Max($c.G, $c.B))
            $mn = [Math]::Min($c.R, [Math]::Min($c.G, $c.B))
            $sum += $lum
            if ($mx -gt 0) { $satSum += ($mx - $mn) / [double]$mx }
            $count++
        }
        if ($count -eq 0) { throw "没有采样到像素：$path" }
        return [pscustomobject]@{
            file = $path
            lum = [Math]::Round($sum / $count, 3)
            sat = [Math]::Round($satSum / $count, 3)
        }
    } finally { $bmp.Dispose() }
}

if ($ImagePath) {
    $r = Measure-Image $ImagePath
    $r | Format-List | Out-String
    if ($r.lum -lt $MinLum -or $r.sat -gt $MaxSat) { Write-Host '结论：非白底' } else { Write-Host '结论：接近纯白' }
    exit 0
}

$rows = @()
foreach ($dir in Get-ChildItem $ImagesDir -Directory | Sort-Object { [int]$_.Name }) {
    $f = Join-Path $dir.FullName '1.jpg'
    if (-not (Test-Path $f)) { continue }
    $m = Measure-Image $f
    $rows += [pscustomobject]@{ id = [int]$dir.Name; lum = $m.lum; sat = $m.sat; white = ($m.lum -ge $MinLum -and $m.sat -le $MaxSat) }
}
$rows | Format-Table -AutoSize | Out-String
$bad = $rows | Where-Object { -not $_.white } | ForEach-Object { $_.id }
Write-Host ("非白底：{0} 个 -> {1}" -f $bad.Count, ($bad -join ','))
