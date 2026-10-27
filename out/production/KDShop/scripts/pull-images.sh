#!/usr/bin/env bash
# 拉取本地基础设施镜像，按顺序尝试多个来源并统一打回标准名前缀。
# 用法：bash scripts/pull-images.sh
set -uo pipefail

pull() {
  local target="$1"; shift
  if docker image inspect "$target" >/dev/null 2>&1; then
    echo "✓ $target 已存在"
    return 0
  fi
  for src in "$@"; do
    echo "→ 尝试 $src"
    if docker pull "$src"; then
      if [ "$src" != "$target" ]; then docker tag "$src" "$target"; fi
      echo "✓ $target 就绪（来源 $src）"
      return 0
    fi
  done
  echo "✗ $target 拉取失败，请换一个镜像源"
  return 1
}

fail=0
pull mysql:8 mysql:8 || fail=1
pull redis:7 redis:7 || fail=1
pull minio/minio:latest docker.1panel.live/minio/minio:latest quay.io/minio/minio:latest || fail=1
pull axllent/mailpit:latest docker.m.daocloud.io/axllent/mailpit:latest docker.1panel.live/axllent/mailpit:latest || fail=1

exit $fail
