# ===== 后端 Spring Boot 多阶段构建 =====
# 阶段1：Maven 编译打包（JDK 17）
FROM maven:3.9-eclipse-temurin-17 AS builder
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

# 阶段2：运行镜像（JDK 17）
FROM eclipse-temurin:17-jre
LABEL maintainer="12348-hotline"
WORKDIR /app
# 时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
# 上传文件目录
RUN mkdir -p /ruoyi/uploadPath
COPY --from=builder /build/ai-admin/target/ai-admin.jar /app/ai-admin.jar

# C2/C5：容器健康检查。运行镜像仅含 JRE（无 curl/wget，且 JDK17 已移除 Nashorn 不能用 jrunscript），
# 改用 Java 17 单文件源码运行（java HealthCheck.java，无需 javac/第三方包）探测 readiness 探针；
# HTTP 200 且 JSON status=UP 才健康。readiness 组含 readinessState + drain：排空时自动
# OUT_OF_SERVICE，配合负载均衡摘流量。脚本退出码已在 JDK17 实测：UP=0 / 503或连不通=1。
COPY docker/backend/HealthCheck.java /app/HealthCheck.java

EXPOSE 8080
# C2/C5：readiness 健康检查（启动宽限期覆盖 Spring 容器初始化；连续 3 次失败标记 unhealthy）
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=3 \
  CMD java /app/HealthCheck.java || exit 1
# 容器内通过环境变量覆盖数据库/Redis/ESL 连接（见 docker-compose.yml）
ENTRYPOINT ["java", "-Xms256m", "-Xmx1024m", "-XX:+UseG1GC", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/ai-admin.jar"]
