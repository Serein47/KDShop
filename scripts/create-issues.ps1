# 从 docs/issues/*.md 批量创建 GitHub Issue
# 用法：
#   pwsh scripts/create-issues.ps1 -DryRun      # 只打印，不创建
#   pwsh scripts/create-issues.ps1              # 真正创建
# 前置：gh 已安装并执行过 gh auth login

param(
    [string]$Repo = "Serein47/KDShop",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$files = Get-ChildItem (Join-Path $root "docs\issues") -Filter "M*.md" | Sort-Object Name
if (-not $files) { throw "没有找到 docs/issues/M*.md" }

$existing = @{}
try {
    $json = gh issue list --repo $Repo --limit 200 --json number,title | ConvertFrom-Json
    foreach ($i in $json) { $existing[$i.title] = $i.number }
} catch {
    Write-Warning "读取已有 Issue 失败，将尝试全部创建：$_"
}

$created = 0
foreach ($file in $files) {
    $text = Get-Content $file.FullName -Raw -Encoding UTF8
    $chunks = [regex]::Split($text, '(?m)^## ISSUE-')
    foreach ($chunk in $chunks) {
        if ($chunk -notmatch '^(\d{2})\s+(.+?)\r?\n') { continue }
        $num = $Matches[1]
        $title = "[ISSUE-$num] " + $Matches[2].Trim()
        $body = $chunk.Substring($Matches[0].Length).Trim()

        $milestone = if ($chunk -match '(?m)^- 里程碑：(.+)$') { $Matches[1].Trim() } else { "" }
        $labels = @()
        if ($chunk -match '(?m)^- 标签：(.+)$') {
            $labels = ($Matches[1] -split ',' | ForEach-Object { $_.Trim().Trim([char]96) } | Where-Object { $_ })
        }

        if ($existing.ContainsKey($title)) {
            Write-Host ("跳过（已存在）" + $title) -ForegroundColor DarkGray
            continue
        }

        if ($DryRun) {
            Write-Host ("[dry-run] " + $title + " | 里程碑=" + $milestone + " | 标签=" + ($labels -join ",")) -ForegroundColor Cyan
            continue
        }

        $tmp = New-TemporaryFile
        Set-Content -Path $tmp -Value $body -Encoding UTF8
        $args = @("issue", "create", "--repo", $Repo, "--title", $title, "--body-file", $tmp)
        foreach ($l in $labels) { $args += @("--label", $l) }
        if ($milestone) { $args += @("--milestone", $milestone) }
        gh @args | Out-Null
        Remove-Item $tmp -Force
        $created++
        Write-Host ("已创建 " + $title) -ForegroundColor Green
    }
}

Write-Host ("完成：新建 " + $created + " 个，仓库 " + $Repo) -ForegroundColor Yellow
