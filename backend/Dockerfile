# ---- build：Maven 编译打包(JDK 21；跳过测试——测试依赖 Testcontainers/Docker) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# 容器内挂阿里云 Maven 镜像:容器是干净环境用不到宿主 ~/.m2,默认走 Maven Central 国内很慢
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src ./src
RUN mvn -q -e -B -DskipTests clean package

# ---- run：JRE 运行(装 curl 供健康检查) ----
FROM eclipse-temurin:21-jre
# apt 换阿里云源加速(eclipse-temurin 基于 Ubuntu):只换域名保留版本代号,
# 兼容 sources.list 与 noble(24.04)的 ubuntu.sources 两种格式;文件不存在时忽略
RUN sed -i \
        -e 's|http://archive.ubuntu.com|https://mirrors.aliyun.com|g' \
        -e 's|http://security.ubuntu.com|https://mirrors.aliyun.com|g' \
        /etc/apt/sources.list /etc/apt/sources.list.d/ubuntu.sources 2>/dev/null || true \
    && apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
