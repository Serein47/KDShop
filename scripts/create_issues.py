#!/usr/bin/env python3
"""从 docs/issues/*.md 批量创建 GitHub Issue。

在 GitHub Actions 里跑（gh 已预装、token 由 GITHUB_TOKEN 提供）：
    GH_TOKEN=... GH_REPO=owner/repo python3 scripts/create_issues.py
"""
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = os.environ.get("GH_REPO") or ""
if not REPO:
    print("缺少 GH_REPO 环境变量")
    sys.exit(1)

docs = Path("docs/issues")
files = sorted(docs.glob("M*.md"))
if not files:
    print("没有找到 docs/issues/M*.md")
    sys.exit(1)


def gh(*args, check=True):
    proc = subprocess.run(["gh", *args], capture_output=True, text=True)
    if check and proc.returncode != 0:
        print("gh 失败:", " ".join(args), proc.stderr.strip())
    return proc


def existing_titles():
    proc = gh("issue", "list", "--repo", REPO, "--limit", "300",
              "--state", "all", "--json", "title", check=False)
    if proc.returncode != 0:
        return set()
    return {i["title"] for i in json.loads(proc.stdout or "[]")}


def ensure_milestone(title):
    proc = gh("api", f"repos/{REPO}/milestones", "--paginate",
              "-q", ".[].title", check=False)
    if title in set(filter(None, proc.stdout.splitlines())):
        return
    gh("api", "--method", "POST", f"repos/{REPO}/milestones",
       "-f", f"title={title}", check=False)


def ensure_label(name):
    colors = {"p0": "d73a4a", "p1": "fbca04", "p2": "0e8a16"}
    gh("label", "create", name, "--repo", REPO,
       "--color", colors.get(name, "ededed"), "--force", check=False)


taken = existing_titles()
created = 0

for path in files:
    text = path.read_text(encoding="utf-8")
    for chunk in re.split(r"(?m)^## ISSUE-", text):
        m = re.match(r"(\d{2})\s+(.+?)\r?\n", chunk)
        if not m:
            continue
        number, raw_title = m.group(1), m.group(2).strip()
        title = f"[ISSUE-{number}] {raw_title}"
        body = chunk[m.end():].strip()

        milestone = ""
        mm = re.search(r"(?m)^- 里程碑：(.+)$", chunk)
        if mm:
            milestone = mm.group(1).strip()

        labels = []
        lm = re.search(r"(?m)^- 标签：(.+)$", chunk)
        if lm:
            labels = [x.strip().strip("`").strip() for x in lm.group(1).split(",")]
            labels = [x for x in labels if x]

        if title in taken:
            print("跳过（已存在）", title)
            continue

        if milestone:
            ensure_milestone(milestone)
        for label in labels:
            ensure_label(label)

        with tempfile.NamedTemporaryFile("w", suffix=".md", delete=False,
                                         encoding="utf-8") as fh:
            fh.write(body)
            body_file = fh.name

        args = ["issue", "create", "--repo", REPO, "--title", title,
                "--body-file", body_file]
        for label in labels:
            args += ["--label", label]
        if milestone:
            args += ["--milestone", milestone]

        proc = gh(*args, check=False)
        os.unlink(body_file)
        if proc.returncode == 0:
            created += 1
            print("已创建", title)
        else:
            print("失败", title, proc.stderr.strip())

print(f"完成：新建 {created} 个，仓库 {REPO}")
