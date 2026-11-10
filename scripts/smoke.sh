#!/usr/bin/env bash
# 部署后冒烟：BASE_URL=http://8.134.215.195 bash scripts/smoke.sh
set -eu
BASE="${BASE_URL:-http://8.134.215.195}"
API="$BASE/api/v1"
JAR=$(mktemp)
trap 'rm -f "$JAR"' EXIT

fail() { echo "FAIL: $1"; exit 1; }
ok() { echo "PASS: $1"; }

code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

[ "$(code "$BASE/")" = "200" ] || fail "首页"
ok "首页 200"

[ "$(code "$BASE/api/v1/categories")" = "200" ] || fail "类目接口"
ok "类目接口 200"

CSRF=$(curl -s -c "$JAR" "$API/auth/csrf" | sed 's/.*"token":"\([^"]*\)".*/\1/')
[ -n "$CSRF" ] || fail "CSRF"
ok "CSRF 下发"

curl -s -b "$JAR" -c "$JAR" -H "X-XSRF-TOKEN: $CSRF" -H 'Content-Type: application/json' \
  -d '{"email":"customer@kdshop.dev","password":"Demo@123456"}' "$API/auth/login" | grep -q '"code":0' || fail "登录"
ok "顾客登录"

curl -s -b "$JAR" "$API/auth/me" | grep -q '"email":"customer@kdshop.dev"' || fail "当前用户"
ok "当前用户"

curl -s "$API/products?size=1" | grep -q '"items"' || fail "商品列表"
ok "商品列表"

curl -s -b "$JAR" "$API/coupons" | grep -q '"couponId"' || fail "优惠券"
ok "优惠券"

curl -s -b "$JAR" "$API/orders?page=1&size=1" | grep -q '"items"' || fail "订单"
ok "订单列表"

echo "全部通过：$BASE"
