# ===== 前端 Vue 多阶段构建 =====
# 阶段1：Node 编译
FROM node:16-alpine AS builder
WORKDIR /build
# 优先拷贝 package 依赖文件，利用缓存
COPY ai-ui/package*.json ./
RUN npm config set registry https://registry.npmmirror.com && npm install
# 拷贝源码并打包生产版本（VUE_APP_BASE_API 走 /prod-api）
COPY ai-ui/ ./
# P3-⑧（C8）：SIP 密码不入库不入 git，构建期经 build-arg 注入；缺失时前端回落到开发默认口令
ARG VUE_APP_SIP_PASSWORD=
ENV VUE_APP_SIP_PASSWORD=$VUE_APP_SIP_PASSWORD
RUN npm run build:prod

# 阶段2：Nginx 托管
FROM nginx:1.24-alpine
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
COPY deploy/docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
