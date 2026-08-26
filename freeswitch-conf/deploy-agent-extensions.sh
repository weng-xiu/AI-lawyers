#!/bin/bash
# FreeSWITCH 律师坐席分机（1003-1006）一键部署（Linux / Docker）
set -e
CONTAINER_NAME="freeswitch"
FS_CLI="/usr/local/freeswitch/bin/fs_cli"
CONF_DIR="/usr/local/freeswitch/conf/directory/default"
EXTENSIONS=("1003:张律师(婚姻)" "1004:李律师(婚姻)" "1005:王律师(合同)" "1006:赵律师(合同)")
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

CID=$(docker ps --filter "name=freeswitch" --filter "status=running" --format '{{.Names}}' | head -n1)
[ -z "$CID" ] && { echo "[X] 未找到运行中的 freeswitch 容器"; exit 1; }
echo "[OK] 容器: $CID"

echo "[*] 拷贝分机配置..."
for entry in "${EXTENSIONS[@]}"; do
  IFS=':' read -r ext name <<< "$entry"
  docker cp "$SCRIPT_DIR/directory/default/$ext.xml" "$CID:$CONF_DIR/$ext.xml"
  echo "    $ext.xml -> $name"
done

echo "[*] reloadxml ..."
docker exec "$CID" "$FS_CLI" -x "reloadxml"
docker exec "$CID" "$FS_CLI" -x "sofia profile internal restart reloadxml" || true
sleep 2
for entry in "${EXTENSIONS[@]}"; do
  IFS=':' read -r ext name <<< "$entry"
  echo "[OK] 分机 $ext（$name）"
done
echo "[OK] 部署完成。"
