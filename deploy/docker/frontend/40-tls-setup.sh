#!/bin/sh
# P3-①：TLS 证书准备与 HTTP 跳转开关
# 由官方 nginx 镜像的 /docker-entrypoint.d 在启动 nginx 之前执行。
set -eu

CERT_DIR="${TLS_CERT_DIR:-/etc/nginx/certs}"
CERT_FILE="$CERT_DIR/server.crt"
KEY_FILE="$CERT_DIR/server.key"
SERVER_NAME="${TLS_SERVER_NAME:-localhost}"

mkdir -p "$CERT_DIR"

if [ ! -s "$CERT_FILE" ] || [ ! -s "$KEY_FILE" ]; then
    echo "[tls] 未找到证书，生成自签证书（仅用于本地/联调，生产请挂载正式证书）：CN=$SERVER_NAME"
    openssl req -x509 -nodes -newkey rsa:2048 -days 825 \
        -keyout "$KEY_FILE" -out "$CERT_FILE" \
        -subj "/CN=$SERVER_NAME" \
        -addext "subjectAltName=DNS:$SERVER_NAME" >/dev/null 2>&1
    chmod 600 "$KEY_FILE"
    chmod 644 "$CERT_FILE"
else
    echo "[tls] 使用已有证书：$CERT_FILE"
fi

if [ "${TLS_FORCE_REDIRECT:-false}" = "true" ]; then
    printf 'return 301 https://$host$request_uri;\n' > /etc/nginx/snippets/tls-redirect.conf
    echo "[tls] 已开启 HTTP -> HTTPS 301 跳转"
else
    printf '# TLS_FORCE_REDIRECT=false: 保留 HTTP 直连（默认，便于本地与内网探测）\n' \
        > /etc/nginx/snippets/tls-redirect.conf
    echo "[tls] HTTP 直连保留；设置 TLS_FORCE_REDIRECT=true 可强制跳转 HTTPS"
fi
