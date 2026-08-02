# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# 先拷贝 pom 以利用依赖缓存
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
# curl 供容器健康检查 (docker-compose healthcheck) 使用
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /build/target/xeflow-backend.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
