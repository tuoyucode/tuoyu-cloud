# 贝尔实验室 Spring 官方推荐镜像 JDK下载地址 https://bell-sw.com/pages/downloads/
FROM bellsoft/liberica-openjdk-rocky:21.0.12-cds
# FROM bellsoft/liberica-openjdk-rocky:25.0.4-cds
# FROM findepi/graalvm:java21-native

LABEL maintainer="tuoyu"

# 以非 root 用户运行：UID / GID 固定为 1000，把宿主机目录挂载进容器时按它授权
# 镜像中没有 useradd 时直接写入 /etc/passwd、/etc/group
RUN if command -v useradd > /dev/null; then \
        groupadd --gid 1000 tuoyu && useradd --uid 1000 --gid tuoyu --home-dir /tuoyu --no-create-home --shell /sbin/nologin tuoyu; \
    else \
        echo 'tuoyu:x:1000:' >> /etc/group && echo 'tuoyu:x:1000:1000:tuoyu:/tuoyu:/sbin/nologin' >> /etc/passwd; \
    fi \
    && mkdir -p /tuoyu/gen/logs \
        /tuoyu/gen/temp \
        /tuoyu/skywalking/agent \
    && chown -R 1000:1000 /tuoyu

WORKDIR /tuoyu/gen

ENV SERVER_PORT=9202 LANG=C.UTF-8 LC_ALL=C.UTF-8 JAVA_OPTS=""

EXPOSE ${SERVER_PORT}

# 程序文件属于 root 且只读，运行用户只能写日志与临时目录
COPY ./target/tuoyu-gen-exec.jar ./app.jar

USER 1000:1000

SHELL ["/bin/bash", "-c"]

ENTRYPOINT exec java -Djava.security.egd=file:/dev/./urandom -Dserver.port=${SERVER_PORT} \
           #-Dskywalking.agent.service_name=tuoyu-gen \
           #-javaagent:/tuoyu/skywalking/agent/skywalking-agent.jar \
           -XX:+HeapDumpOnOutOfMemoryError -XX:+UseZGC ${JAVA_OPTS} \
           -jar app.jar
