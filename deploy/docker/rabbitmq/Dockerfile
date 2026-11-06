FROM rabbitmq:3.13.3-management

LABEL maintainer="tuoyu"

ADD ./rabbitmq_delayed_message_exchange-3.13.0.ez /plugins

RUN rabbitmq-plugins enable rabbitmq_delayed_message_exchange

# 沿用官方镜像的入口脚本（以 rabbitmq 用户启动 rabbitmq-server），不要改成直接执行 rabbitmq-server，否则会以 root 运行
