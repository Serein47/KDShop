# 从 docs/issues/*.md 批量创建 GitHub Issue
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/create-issues.ps1 -DryRun
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/create-issues.ps1
# 前置：gh 已安装并执行过 gh auth login

param(
    [string]$Repo = "Serein47/KDShop",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$files = Get-ChildItem (Join-Path $root "docs\issues") -Filter "M*.md" | Sort-Object Name
if (-not $files) { throw "没有找到 docs/issues/M*.md" }

$knownLabels = @{}
$knownMilestones = @{}

function Initialize-Meta {
    if ($DryRun) { return }
    $labels = gh label list --repo $Repo --limit 200 --json name | ConvertFrom-Json
    foreach ($l in $labels) { $knownLabels[$l.name] = $true }
    $ms = gh api "repos/$Repo/milestones" --paginate -q ".[].title"
    foreach ($m in $ms) { if ($m) { $knownMilestones[$m.Trim()] = $true } }
}

function Ensure-Label([string]$name) {
    if ($DryRun -or $knownLabels.ContainsKey($name)) { return }
    $color = switch ($name) { "p0" { "d73a4a" } "p1" { "fbca04" } "p2" { "0e8a16" } default { "ededed" } }
    gh label create $name --repo $Repo --color $color 2>$null | Out-Null
    $knownLabels[$name] = $true
}

function Ensure-Milestone([string]$title) {
    if ($DryRun -or -not $title -or $knownMilestones.ContainsKey($title)) { return }
    gh api --method POST "repos/$Repo/milestones" -f "title=$title" | Out-Null
    $knownMilestones[$title] = $true
}

Initialize-Meta

$existing = @{}
if (-not $DryRun) {
    $json = gh issue list --repo $Repo --limit 300 --state all --json number,title | ConvertFrom-Json
    foreach ($i in $json) { $existing[$i.title] = $i.number }
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

        Ensure-Milestone $milestone
        foreach ($l in $labels) { Ensure-Label $l }

        $tmp = New-TemporaryFile
        Set-Content -Path $tmp -Value $body -Encoding UTF8
        $ghArgs = @("issue", "create", "--repo", $Repo, "--title", $title, "--body-file", $tmp)
        foreach ($l in $labels) { $ghArgs += @("--label", $l) }
        if ($milestone) { $ghArgs += @("--milestone", $milestone) }
        gh @ghArgs | Out-Null
        Remove-Item $tmp -Force
        $created++
        Write-Host ("已创建 " + $title) -ForegroundColor Green
    }
}

Write-Host ("完成：新建 " + $created + " 个，仓库 " + $Repo) -ForegroundColor Yellow
