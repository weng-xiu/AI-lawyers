#!/bin/bash
# =====================================================================
# 12348 公共法律服务热线 —— 全容器化一键部署（Linux / Docker）
#
# 用法：
#   chmod +x deploy/deploy.sh
#   ./deploy/deploy.sh                 # 完整部署（使用仓库基线 01-init.sql + 构建 + 启动）
#   ./deploy/deploy.sh --dump-from-local  # 从本机已有 ai-law 库导出，覆盖仓库基线（迁移场景）
#   ./deploy/deploy.sh --no-build      # 跳过构建
#   ./deploy/deploy.sh --skip-dump     # 兼容旧参数：现已默认不导出
# =====================================================================
set -e

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$DEPLOY_DIR/.." && pwd)"
INIT_DIR="$DEPLOY_DIR/docker/mysql/init"
DB_NAME="ai-law"; DB_USER="root"; DB_PASS="root"

DUMP_FROM_LOCAL=0; SKIP_DUMP=0; NO_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --dump-from-local) DUMP_FROM_LOCAL=1 ;;
    --skip-dump)  SKIP_DUMP=1 ;;
    --no-build)  NO_BUILD=1 ;;
  esac
done

step(){ echo ""; echo "========== $1 =========="; }
ok(){ echo "[OK] $1"; }
warn(){ echo "[!] $1"; }
die(){ echo "[X] $1"; exit 1; }

# 1. 检查 Docker
step "1/6 检查 Docker 环境"
command -v docker >/dev/null 2>&1 || die "未安装 docker。"
docker info >/dev/null 2>&1 || die "Docker 未运行，请先启动 Docker。"
docker compose version >/dev/null 2>&1 || die "需要 Docker Compose v2。"
ok "Docker 与 Compose 可用"

# 2. 准备初始化 SQL
step "2/6 准备数据库初始化脚本"
mkdir -p "$INIT_DIR"
cp "$DEPLOY_DIR/docker/mysql/99-container-trunk-fix.sql" "$INIT_DIR/99-container-trunk-fix.sql"
DUMP="$INIT_DIR/01-init.sql"

if [ "$DUMP_FROM_LOCAL" -eq 1 ]; then
  [ "$SKIP_DUMP" -eq 1 ] && warn "--skip-dump 与 --dump-from-local 同时指定，按 --dump-from-local 执行"
  command -v mysqldump >/dev/null 2>&1 || die "未找到 mysqldump，无法执行 --dump-from-local。"
  echo "正在从本机 $DB_NAME 导出（会覆盖仓库基线 $DUMP）..."
  TMP_DUMP="$DUMP.tmp"
  rm -f "$TMP_DUMP"
  if ! mysqldump -u"$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 \
      --single-transaction --routines --triggers --events \
      "$DB_NAME" > "$TMP_DUMP" 2>/dev/null || [ ! -s "$TMP_DUMP" ]; then
    rm -f "$TMP_DUMP"
    die "从本机导出 $DB_NAME 失败，仓库基线未被修改。"
  fi
  mv "$TMP_DUMP" "$DUMP"
  ok "已从本机导出并覆盖基线：$DUMP ($(wc -c < "$DUMP") 字节)"
else
  [ -f "$DUMP" ] || die "缺少仓库基线 $DUMP。请从仓库恢复该文件，或使用 --dump-from-local 从本机导出。"
  [ "$SKIP_DUMP" -eq 1 ] && warn "已默认使用仓库基线，--skip-dump 参数不再需要"
  ok "使用仓库基线：$DUMP（$(grep -c '^CREATE TABLE' "$DUMP" || true) 张表，$(wc -c < "$DUMP") 字节）"
fi

# 3. 构建镜像
if [ "$NO_BUILD" -eq 0 ]; then
  step "3/6 构建后端/前端镜像（首次较慢）"
  (cd "$DEPLOY_DIR" && docker compose build)
  ok "镜像构建完成"
else
  step "3/6 跳过构建（--no-build）"
fi

# 4. 启动
step "4/6 启动全部容器"
(cd "$DEPLOY_DIR" && docker compose up -d)
ok "已下发启动命令"

# 5. 等待就绪
step "5/6 等待服务就绪"
wait_tcp(){ local host=$1 port=$2 timeout=$3; local start=$(date +%s);
  while [ $(( $(date +%s)-start )) -lt $timeout ]; do
    if (echo >/dev/tcp/$host/$port) >/dev/null 2>&1; then return 0; fi
    sleep 2
  done; return 1; }

echo "等待 MySQL/Redis..."
wait_tcp localhost 3306 90; wait_tcp localhost 6379 60
echo "等待后端容器启动（P3-⑫：8080 仅容器内网，约 30-60 秒）..."
backend_ready=0
for i in $(seq 1 50); do
  if [ "$(docker inspect -f '{{.State.Running}}' ai12348-backend 2>/dev/null)" = "true" ]; then
    backend_ready=1; break
  fi
  sleep 3
done
[ "$backend_ready" = "1" ] && ok "后端容器已运行（端口仅容器内网）" || warn "后端容器未运行，docker logs ai12348-backend 查看"
echo "等待前端 80..."
wait_tcp localhost 80 90 && ok "前端 80 就绪" || warn "前端超时"

# 6. 状态
step "6/6 容器状态"
(cd "$DEPLOY_DIR" && docker compose ps)

echo ""
echo "======================================================================"
ok "部署流程完成！"
echo "管理后台: http://localhost    后端接口: 仅容器内网 :8080（经前端 /prod-api 访问）"
echo "日志: docker compose logs -f backend | freeswitch"
echo "停止: docker compose down"
echo "分机热加载: docker exec ai12348-freeswitch /usr/local/freeswitch/bin/fs_cli -x 'reloadxml'"
echo "======================================================================"
