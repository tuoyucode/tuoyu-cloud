# 单体（tuoyu-boot-all）镜像
#
# 构建上下文是仓库根目录（不是本模块目录），以便把 deploy/config/nacos 下的配置一起放进镜像：
#   mvn -pl tuoyu-boot/tuoyu-boot-all -am package
#   docker build -f tuoyu-boot/tuoyu-boot-all/Dockerfile -t tuoyu/tuoyu-boot-all:1.0.0-SNAPSHOT .
# 同目录的 Dockerfile.dockerignore 只放行这两样文件，构建上下文很小（需要 BuildKit，Docker 23 起默认开启）。
#
# 单体不需要 Nacos：启动时从 TUOYU_CONFIG_DIR（镜像中为 /app/config）读取与 Nacos 相同的配置文件。
# 修改配置时可以把目录挂载到 /app/config 覆盖镜像中的副本，见 deploy/docker/compose-monolith.yml。

# 贝尔实验室 Spring 官方推荐镜像 JDK下载地址 https://bell-sw.com/pages/downloads/
FROM bellsoft/liberica-openjdk-rocky:21.0.12-cds
# FROM bellsoft/liberica-openjdk-rocky:25.0.4-cds

LABEL maintainer="tuoyu"

# 以非 root 用户运行：UID / GID 固定为 1000，把宿主机目录挂载进容器时按它授权
# 镜像中没有 useradd 时直接写入 /etc/passwd、/etc/group
RUN if command -v useradd > /dev/null; then \
        groupadd --gid 1000 tuoyu && useradd --uid 1000 --gid tuoyu --home-dir /tuoyu --no-create-home --shell /sbin/nologin tuoyu; \
    else \
        echo 'tuoyu:x:1000:' >> /etc/group && echo 'tuoyu:x:1000:1000:tuoyu:/tuoyu:/sbin/nologin' >> /etc/passwd; \
    fi \
    && mkdir -p /app/logs \
        /app/temp \
        /tuoyu/skywalking/agent \
    && chown -R 1000:1000 /tuoyu /app/logs /app/temp

WORKDIR /app

ENV SERVER_PORT=8081 TUOYU_CONFIG_DIR=/app/config LANG=C.UTF-8 LC_ALL=C.UTF-8 JAVA_OPTS=""

EXPOSE ${SERVER_PORT}

# 配置与程序文件属于 root 且只读，运行用户只能写日志与临时目录
COPY deploy/config/nacos/ ./config/
COPY tuoyu-boot/tuoyu-boot-all/target/tuoyu-boot-all.jar ./app.jar

USER 1000:1000

SHELL ["/bin/bash", "-c"]

ENTRYPOINT exec java -Djava.security.egd=file:/dev/./urandom -Dserver.port=${SERVER_PORT} \
           #-Dskywalking.agent.service_name=tuoyu-boot-all \
           #-javaagent:/tuoyu/skywalking/agent/skywalking-agent.jar \
           -XX:+HeapDumpOnOutOfMemoryError -XX:+UseZGC ${JAVA_OPTS} \
           -jar app.jar
