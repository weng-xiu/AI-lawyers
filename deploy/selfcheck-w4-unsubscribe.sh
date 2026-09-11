#!/bin/bash
# =====================================================================
# W4 短信退订链路上线自检脚本（对应《上线部署操作手册_W4退订与W5上线》§5.3 六步自检）
#
# 覆盖：
#   S1 回复"T"       → 期望 HTTP 200 + code 200（写入退订名单）
#   S2 回复"TD"       → 期望仍 200+code 200；DB 核验同号码 list_type=3 仅 1 条（幂等）
#   S3 管理端黑名单页 → 人工核验（打印操作指引）
#   S4 伪造请求       → 期望 body code=403「上行回调校验失败」（RuoYi AjaxResult 约定：HTTP 恒 200，
#                      业务码在响应体，防短信服务商按非 2xx 触发重试风暴）
#   S5 外呼跳过       → 人工触发后 DB 核验 call_status='4' 且 fail_reason 含 UNSUBSCRIBED
#   S6 短信拒绝       → 人工触发后 DB 核验 ai_sms_log 拒绝留痕
#
# 用法：
#   BASE_URL=https://<域名> SMS_UPSTREAM_TOKEN=<token> \
#     DB_HOST=... DB_USER=... DB_PASS=... DB_NAME=ai-law \
#     ./deploy/selfcheck-w4-unsubscribe.sh [--phone 13800138000]
#
# 鉴权二选一（与生产一致）：
#   1) token 模式（默认）：设置 SMS_UPSTREAM_TOKEN
#   2) 签名模式：设置 CALLBACK_SIGN_ENABLED=true CALLBACK_SIGN_SECRET=<强随机>（需 openssl）
#   若两者都未提供：脚本以 WARN 放行（与后端开发联调行为一致），S4 预期需人工注意。
#
# 运行环境：Linux（GNU date 毫秒时间戳、base64 -w0），生产/CI 适用。
# 退出码：0=全部通过；1=存在 FAIL；2=参数/依赖错误。
# =====================================================================
set -u

# ---------- 配置（环境变量 / 命令行覆盖） ----------
BASE_URL="${BASE_URL:-}"
UPSTREAM_PATH="/lawyers/sms/upstream"
PHONE="13800138000"
MODE="${AUTH_MODE:-}"            # token | sign，自动推断
SECRET="${CALLBACK_SIGN_SECRET:-}"
SIGN_ENABLED="${CALLBACK_SIGN_ENABLED:-false}"
TOKEN="${SMS_UPSTREAM_TOKEN:-}"
CURL_TIMEOUT=15

# 可选 DB 核验（提供 DB_HOST 时启用）
DB_HOST="${DB_HOST:-}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-}"
DB_PASS="${DB_PASS:-}"
DB_NAME="${DB_NAME:-ai-law}"

# ---------- 工具函数 ----------
PASS=0; FAIL=0; WARN=0
step(){ echo ""; echo "===== $1 ====="; }
ok(){ PASS=$((PASS+1)); echo "  [PASS] $1"; }
fail(){ FAIL=$((FAIL+1)); echo "  [FAIL] $1"; }
warn(){ WARN=$((WARN+1)); echo "  [WARN] $1" >&2; }   # 走 stderr，避免被命令替换捕获
die(){ echo "[X] $1"; exit 2; }
has(){ command -v "$1" >/dev/null 2>&1; }

# 解析命令行参数
while [ $# -gt 0 ]; do
  case "$1" in
    --phone) PHONE="${2:?--phone 需要手机号}"; shift 2 ;;
    *) echo "未知参数: $1（支持 --phone <号码>）"; exit 2 ;;
  esac
done

[ -n "$BASE_URL" ] || die "缺少 BASE_URL（如 https://<域名>），用法见脚本头注释。"
has curl || die "需要 curl。"

# 鉴权模式推断
if [ -z "$MODE" ]; then
  if [ "$SIGN_ENABLED" = "true" ] && [ -n "$SECRET" ]; then MODE="sign";
  elif [ -n "$TOKEN" ]; then MODE="token"; fi
fi

echo "目标: $BASE_URL$UPSTREAM_PATH    测试号码: $PHONE    鉴权模式: ${MODE:-未配置(联调放行)}"

# 发送上行请求：自动携带鉴权头；输出一行 "<HTTP码>\t<响应体>"（命令替换在子 shell 内无法回传变量，故用单行输出解析）
upstream_post(){
  local content="$1"; shift
  local ts sign headers=() tmp code body
  case "${MODE:-}" in
    sign)
      has openssl || die "签名模式需要 openssl。"
      ts=$(date +%s%3N)   # GNU date 毫秒时间戳
      sign=$(printf '%s' "${PHONE}|${content}.${ts}" | openssl dgst -sha256 -hmac "$SECRET" -binary | base64 -w0)
      headers=(-H "X-Callback-Timestamp: ${ts}" -H "X-Callback-Sign: ${sign}")
      ;;
    token)
      headers=(-H "X-Upstream-Token: ${TOKEN}")
      ;;
  esac
  tmp=$(mktemp)
  code=$(curl -s -m "$CURL_TIMEOUT" -o "$tmp" -w '%{http_code}' -X POST "$BASE_URL$UPSTREAM_PATH" \
    -H 'Content-Type: application/json' "${headers[@]}" "$@" \
    -d "{\"phone_number\":\"${PHONE}\",\"content\":\"${content}\"}")
  body=$(cat "$tmp" 2>/dev/null)
  rm -f "$tmp"
  printf '%s\t%s' "$code" "$body"
}

# DB 查询：输出查询结果；未配置 DB/无 mysql 客户端时 WARN（stderr）并返回空
db_query(){
  if [ -z "$DB_HOST" ] || [ -z "$DB_USER" ]; then warn "$1"; return 1; fi
  has mysql || { warn "未找到 mysql 客户端，跳过 DB 核验：$1"; return 1; }
  MYSQL_PWD="$DB_PASS" mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -N -B "$DB_NAME" -e "$2" 2>/dev/null
}

echo "======================================================================"

# ---------- S1: 回复 "T" 写入退订名单 ----------
step "S1 回复 \"T\" 写入退订名单（期望 HTTP 200 + code 200）"
R=$(upstream_post "T")
CODE=${R%%$'\t'*}; BODY=${R#*$'\t'}
echo "  HTTP $CODE  body: $BODY"
if [ "$CODE" = "200" ] && echo "$BODY" | grep -q '"code":200'; then
  ok "S1 回复 T 成功写入退订名单"
else
  fail "S1 期望 200+code 200，实际 HTTP=$CODE body=$BODY"
fi

# ---------- S2: 回复 "TD" 幂等（DB 仅 1 条 list_type=3） ----------
step "S2 回复 \"TD\"（期望仍 200+code 200；DB 同号码 list_type=3 仅 1 条=幂等）"
R=$(upstream_post "TD")
CODE=${R%%$'\t'*}; BODY=${R#*$'\t'}
echo "  HTTP $CODE  body: $BODY"
if [ "$CODE" = "200" ] && echo "$BODY" | grep -q '"code":200'; then
  ok "S2 回复 TD 返回 200+code 200"
else
  fail "S2 期望 200+code 200，实际 HTTP=$CODE body=$BODY"
fi
CNT=$(db_query "S2 幂等 DB 核验（提供 DB_HOST/DB_USER/DB_PASS 后自动执行）" \
  "SELECT COUNT(*) FROM ai_call_blacklist WHERE phone_number='${PHONE}' AND list_type=3;")
if [ -n "$CNT" ]; then
  echo "  DB 退订记录数: $CNT"
  [ "$CNT" = "1" ] && ok "S2 幂等：list_type=3 仅 1 条" || fail "S2 幂等失败：期望 1 条，实际 $CNT 条"
fi

# ---------- S3: 管理端人工核验 ----------
step "S3 管理端黑名单页核验（人工）"
echo "  在【外呼管理-黑名单】按号码 ${PHONE} 查询，期望："
echo "    类型=退订名单(list_type=3)、状态=启用；可恢复/删除该记录。"

# ---------- S4: 伪造请求（无鉴权） → body code 403 ----------
step "S4 伪造请求（不带 token/签名，期望 body code=403「上行回调校验失败」）"
if [ -n "$MODE" ]; then
  TMP=$(mktemp)
  CODE=$(curl -s -m "$CURL_TIMEOUT" -o "$TMP" -w '%{http_code}' -X POST "$BASE_URL$UPSTREAM_PATH" \
    -H 'Content-Type: application/json' \
    -d "{\"phone_number\":\"${PHONE}\",\"content\":\"T\"}")
  BODY=$(cat "$TMP" 2>/dev/null); rm -f "$TMP"
  echo "  HTTP $CODE  body: $BODY"
  if echo "$BODY" | grep -q '"code":403'; then
    ok "S4 伪造请求被拒绝（body code=403）"
  else
    fail "S4 期望 body code=403，实际 HTTP=$CODE body=$BODY（检查 CALLBACK_SIGN_ENABLED/SMS_UPSTREAM_TOKEN 是否已注入生产）"
  fi
else
  warn "S4 未配置签名/token，后端联调放行（返回 200+code 200）属预期；生产必须配置其一后重跑本项"
fi

# ---------- S5: 外呼跳过核验 ----------
step "S5 外呼跳过（人工触发 + DB 核验）"
echo "  操作：在【外呼任务】中把 ${PHONE} 加入号码并启动任务（退订名单内）"
echo "  预期：该号码被跳过不拨号；DB 记录 call_status='4'（跳过）、fail_reason 含 UNSUBSCRIBED。"
if [ -n "$DB_HOST" ] && [ -n "$DB_USER" ]; then
  echo "  核验 SQL："
  echo "    SELECT callee_id, call_status, fail_reason FROM ai_outbound_callee"
  echo "     WHERE callee_number='${PHONE}' AND fail_reason LIKE '%UNSUBSCRIBED%' ORDER BY callee_id DESC LIMIT 5;"
  ROWS=$(db_query "" \
    "SELECT CONCAT('callee_id=',callee_id,' call_status=',call_status,' fail_reason=',fail_reason) FROM ai_outbound_callee WHERE callee_number='${PHONE}' AND fail_reason LIKE '%UNSUBSCRIBED%' ORDER BY callee_id DESC LIMIT 5;")
  if [ -n "$ROWS" ]; then
    echo "  DB 命中:"; echo "$ROWS" | sed 's/^/    /'
  else
    warn "S5 尚未在 DB 中找到该号码的 UNSUBSCRIBED 跳过记录（请先人工触发外呼任务后重跑本脚本/本 SQL）"
  fi
else
  warn "S5 未配置 DB 连接，跳过 DB 核验；请用上方 SQL 人工核验"
fi

# ---------- S6: 短信拒绝核验 ----------
step "S6 短信拒绝（人工触发 + DB 核验）"
echo "  操作：在【短信】向 ${PHONE} 下发通知短信"
echo "  预期：发送被拒绝、不重试；ai_sms_log 留痕 fail_reason 非空。"
if [ -n "$DB_HOST" ] && [ -n "$DB_USER" ]; then
  echo "  核验 SQL："
  echo "    SELECT log_id, phone, send_status, fail_reason, content FROM ai_sms_log"
  echo "     WHERE phone='${PHONE}' ORDER BY log_id DESC LIMIT 5;"
  ROWS=$(db_query "" \
    "SELECT CONCAT('log_id=',log_id,' send_status=',send_status,' fail_reason=',IFNULL(fail_reason,'NULL')) FROM ai_sms_log WHERE phone='${PHONE}' ORDER BY log_id DESC LIMIT 5;")
  if [ -n "$ROWS" ]; then
    echo "  DB 命中:"; echo "$ROWS" | sed 's/^/    /'
  else
    warn "S6 尚未在 DB 中找到该号码的短信下发记录（请先人工触发短信后重跑本脚本/本 SQL）"
  fi
else
  warn "S6 未配置 DB 连接，跳过 DB 核验；请用上方 SQL 人工核验"
fi

# ---------- 汇总 ----------
echo ""
echo "======================================================================"
echo "自检汇总: PASS=$PASS  FAIL=$FAIL  WARN=$WARN"
echo "  WARN 说明：S5/S6 需人工触发后复跑；S3 为人工 UI 核验；未配置 DB/鉴权属部署配置问题。"
[ "$FAIL" -gt 0 ] && { echo "结论: 存在 FAIL，请按上方提示修复后重跑。"; exit 1; }
echo "结论: 自动化项全部通过。"
echo "注意: 同号码退订频控默认 10 次/分钟，本脚本每轮 S1/S2 共 2 次写入，短时勿超 5 轮。"
