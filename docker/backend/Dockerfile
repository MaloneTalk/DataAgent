# Stage 1: Build (使用 maven 镜像，自带 Maven)
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Maven 依赖默认直连 Maven Central。国内网络拉取易中断导致构建失败时，
# 取消下面整段注释启用阿里云镜像，并给 mvn 命令加上 -s /root/settings.xml。
# 注意：settings.xml 不能放 /root/.m2（会被下面的 cache mount 覆盖）。
# RUN printf '%s\n' \
#   '<settings>' \
#   '  <mirrors>' \
#   '    <mirror>' \
#   '      <id>aliyun</id>' \
#   '      <url>https://maven.aliyun.com/repository/public</url>' \
#   '      <mirrorOf>*</mirrorOf>' \
#     '</mirror>' \
#   '</mirrors>' \
# '</settings>' > /root/settings.xml

# Copy pom + source and build.
# /root/.m2 通过 BuildKit cache 持久化，跨构建复用依赖，无需额外的 go-offline 步骤。
COPY data-agent-backend/pom.xml .
COPY data-agent-backend/src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Skills 目录（skill.properties 的 filesystem 源）
COPY skills/ /app/skills/

# Add non-root user
RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -D appuser

# Copy the built jar
COPY --from=builder /app/target/*.jar app.jar

# Create log directory
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
