# ===== 后端 Spring Boot 多阶段构建 =====
# 阶段1：Maven 编译打包
FROM maven:3.8.6-openjdk-8 AS builder
WORKDIR /build
# 先拷贝 pom 利用缓存加速依赖下载
COPY pom.xml .
COPY ai-common/pom.xml ai-common/
COPY ai-framework/pom.xml ai-framework/
COPY ai-system/pom.xml ai-system/
COPY ai-quartz/pom.xml ai-quartz/
COPY ai-generator/pom.xml ai-generator/
COPY ai-admin/pom.xml ai-admin/
RUN mvn -q dependency:go-offline -B || true
# 拷贝全部源码并打包（跳过测试）
COPY . .
RUN mvn -q clean package -DskipTests -B

# 阶段2：运行镜像
FROM openjdk:8-jre
LABEL maintainer="12348-hotline"
WORKDIR /app
# 时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
# 上传文件目录
RUN mkdir -p /ruoyi/uploadPath
COPY --from=builder /build/ai-admin/target/ai-admin.jar /app/ai-admin.jar

EXPOSE 8080
# 容器内通过环境变量覆盖数据库/Redis/ESL 连接（见 docker-compose.yml）
ENTRYPOINT ["java", "-Xms256m", "-Xmx1024m", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/ai-admin.jar"]
