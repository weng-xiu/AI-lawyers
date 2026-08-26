#!/bin/bash
# =====================================================================
# 12348 公共法律服务热线 —— 全容器化一键部署（Linux / Docker）
#
# 用法：
#   chmod +x deploy/deploy.sh
#   ./deploy/deploy.sh            # 完整部署（导出库+构建+启动）
#   ./deploy/deploy.sh --skip-dump  # 使用已有 01-init.sql
#   ./deploy/deploy.sh --no-build   # 跳过构建
# =====================================================================
set -e

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$DEPLOY_DIR/.." && pwd)"
INIT_DIR="$DEPLOY_DIR/docker/mysql/init"
DB_NAME="ai-law"; DB_USER="root"; DB_PASS="root"

SKIP_DUMP=0; NO_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --skip-dump) SKIP_DUMP=1 ;;
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

# 2. 导出数据库
step "2/6 导出业务数据库 $DB_NAME"
mkdir -p "$INIT_DIR"
cp "$DEPLOY_DIR/docker/mysql/99-container-trunk-fix.sql" "$INIT_DIR/99-container-trunk-fix.sql"
DUMP="$INIT_DIR/01-init.sql"

if [ "$SKIP_DUMP" -eq 1 ] && [ -f "$DUMP" ]; then
  warn "跳过导出，使用已有 $DUMP"
elif command -v mysqldump >/dev/null 2>&1; then
  echo "正在导出 $DB_NAME ..."
  mysqldump -u"$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 \
    --single-transaction --routines --triggers --events \
    "$DB_NAME" > "$DUMP" 2>/dev/null || warn "mysqldump 报错，请检查 $DUMP"
  ok "数据库已导出：$DUMP"
else
  warn "未找到 mysqldump。"
  [ -f "$DUMP" ] && warn "使用已有 $DUMP" || die "请手动导出 $DB_NAME 到 $DUMP"
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
echo "等待后端 8080（Spring Boot 启动约 30-60 秒）..."
wait_tcp localhost 8080 150 && ok "后端 8080 就绪" || warn "后端超时，docker logs ai12348-backend 查看"
echo "等待前端 80..."
wait_tcp localhost 80 90 && ok "前端 80 就绪" || warn "前端超时"

# 6. 状态
step "6/6 容器状态"
(cd "$DEPLOY_DIR" && docker compose ps)

echo ""
echo "======================================================================"
ok "部署流程完成！"
echo "管理后台: http://localhost    后端接口: http://localhost:8080"
echo "日志: docker compose logs -f backend | freeswitch"
echo "停止: docker compose down"
echo "分机热加载: docker exec ai12348-freeswitch /usr/local/freeswitch/bin/fs_cli -x 'reloadxml'"
echo "======================================================================"
