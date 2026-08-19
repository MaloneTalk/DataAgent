# Stage 1: Build (使用 maven 镜像，自带 Maven)
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Maven 依赖默认直连 Maven Central。国内网络拉取易中断导致构建失败时，
# 取消下面整段注释，改用阿里云镜像源：
# RUN mkdir -p /root/.m2 && echo '<settings>\
#   <mirrors>\
#     <mirror>\
#       <id>aliyun</id>\
#       <url>https://maven.aliyun.com/repository/public</url>\
#       <mirrorOf>*</mirrorOf>\
#     </mirror>\
#   </mirrors>\
# </settings>' > /root/.m2/settings.xml

# Copy pom.xml and download dependencies
COPY data-agent-backend/pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY data-agent-backend/src ./src
RUN mvn clean package -DskipTests -B

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
