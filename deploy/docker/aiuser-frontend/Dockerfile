# ===== 公众端（AI律师用户端）Vue 多阶段构建 =====
# 与管理端 frontend 独立镜像：公众端对外发布，路由与反向代理规则单独维护。
# 阶段1：Node 编译（与管理端保持一致，固定 Node 16 避免 webpack4 的 OpenSSL 兼容问题）
FROM node:16-alpine AS builder
WORKDIR /build
COPY aiuser-ui/package*.json ./
RUN npm config set registry https://registry.npmmirror.com && npm install
COPY aiuser-ui/ ./
RUN npm run build:prod

# 阶段2：Nginx 托管
FROM nginx:1.24-alpine
ENV TZ=Asia/Shanghai
RUN apk add --no-cache openssl \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
COPY deploy/docker/aiuser-frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY deploy/docker/aiuser-frontend/app-locations.conf /etc/nginx/snippets/app-locations.conf
# 复用管理端已验证的 TLS 启动脚本与跳转片段（脚本仅在本容器内改写 snippet）
COPY deploy/docker/frontend/tls-redirect.conf /etc/nginx/snippets/tls-redirect.conf
COPY deploy/docker/frontend/40-tls-setup.sh /docker-entrypoint.d/40-tls-setup.sh
RUN chmod +x /docker-entrypoint.d/40-tls-setup.sh
COPY --from=builder /build/dist /usr/share/nginx/html
EXPOSE 80 443
CMD ["nginx", "-g", "daemon off;"]
