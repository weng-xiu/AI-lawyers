#!/bin/sh
# P3 尾项静态收口：生产上线前置检查（不依赖预发环境，部署前即可执行）
#
# 仅在 APP_SECURITY_GUARD=true 时强制校验；未开启时只提示，便于本地快速体验。
# 与后端 ProductionSecurityGuard 口径一致，额外补齐部署侧才可见的变量：
#   - 网关回调签名（对应 P3-⑥：未签名回调不得上线）
#   - 前端构建期 SIP 密码（对应 P3-⑧）
#   - MySQL 初始化 root 口令（对应 P3-①）
#
# 用法： sh deploy/preflight-prod.sh   （或由 deploy.sh 自动调用）
set -u

GUARD="${APP_SECURITY_GUARD:-false}"
FAIL=0
err() { echo "[X] $1"; FAIL=1; }
ok() { echo "[OK] $1"; }

require() {
  name="$1"; value="$2"; min="$3"
  if [ -z "$value" ]; then
    err "$name 未设置"
  elif [ "${#value}" -lt "$min" ]; then
    err "$name 长度不足 ${min} 字节"
  else
    ok "$name 已设置"
  fi
}

reject() {
  name="$1"; value="$2"; bad="$3"
  if [ "$value" = "$bad" ]; then
    err "$name 仍为默认值 $bad"
  fi
}

if [ "$GUARD" != "true" ]; then
  echo "[!] APP_SECURITY_GUARD 未开启：跳过生产强制校验（仅适用于本地/联调）"
  exit 0
fi

echo "== 生产上线前置检查 =="

require TOKEN_SECRET "${TOKEN_SECRET:-}" 32
reject TOKEN_SECRET "${TOKEN_SECRET:-}" "Z2xwLWExMmwtbGF3eWVycy1qd3Qtc2VjcmV0LWtleS0yMDI2LWNoYW5nZS1tZS1pbi1wcm9kLTA5ODc2NTQz"

require DB_PASSWORD "${DB_PASSWORD:-}" 12
reject DB_PASSWORD "${DB_PASSWORD:-}" "root"
if [ "${DB_USERNAME:-}" = "root" ] || [ -z "${DB_USERNAME:-}" ]; then
  err "DB_USERNAME 不得为空或 root，须使用独立业务账号（配合 MYSQL_USER/MYSQL_PASSWORD）"
else
  ok "DB_USERNAME 已使用独立账号"
fi

require REDIS_PASSWORD "${REDIS_PASSWORD:-}" 12

require APP_SECRET_KEY "${APP_SECRET_KEY:-}" 32
reject APP_SECRET_KEY "${APP_SECRET_KEY:-}" "ai-lawyers-dev-secret-change-me-in-production-2026"

if [ "${CALLBACK_SIGN_ENABLED:-false}" != "true" ]; then
  err "CALLBACK_SIGN_ENABLED 必须为 true：网关回调未签名时不得上线（P3-⑥）"
else
  ok "网关回调签名已开启"
fi
require CALLBACK_SIGN_SECRET "${CALLBACK_SIGN_SECRET:-}" 32
reject CALLBACK_SIGN_SECRET "${CALLBACK_SIGN_SECRET:-}" "ai-lawyers-callback-dev-secret-change-me-2026"

reject ESL_PASSWORD "${ESL_PASSWORD:-}" "ClueCon"
require ESL_PASSWORD "${ESL_PASSWORD:-}" 12
reject AMI_PASSWORD "${AMI_PASSWORD:-}" "amp111"
require AMI_PASSWORD "${AMI_PASSWORD:-}" 12

if [ "${CALL_OUTBOUND_SIMULATEANSWER:-true}" = "true" ]; then
  err "CALL_OUTBOUND_SIMULATEANSWER 必须为 false"
else
  ok "外呼模拟接通已关闭"
fi

require MYSQL_ROOT_PASSWORD "${MYSQL_ROOT_PASSWORD:-}" 12
require VUE_APP_SIP_PASSWORD "${VUE_APP_SIP_PASSWORD:-}" 8

if [ "$FAIL" -eq 1 ]; then
  echo ""
  echo "[X] 生产前置检查未通过，请补齐上述变量后重试（参考 deploy/.env.example）"
  exit 1
fi

echo "[OK] 生产前置检查全部通过"
