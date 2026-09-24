# =========================================
# 光影票务后端 - 预构建 JAR 部署 Dockerfile
#
# 本地打包好 JAR 后直接 COPY，跳过服务器编译
# =========================================

FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="guangying-team"
LABEL description="光影票务电影后端服务"

WORKDIR /app

# 直接复制本地打好的 JAR
COPY provider/target/guangying-provider-1.0.0-SNAPSHOT.jar app.jar

# 暴露端口
EXPOSE 8080

# JVM 参数通过环境变量注入
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 启动命令
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
