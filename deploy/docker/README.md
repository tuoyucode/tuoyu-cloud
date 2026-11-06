# docker compose 部署

本目录提供三套 compose，以及一个可叠加的附加组件文件：

| 文件 | 内容 | 适用场景 |
|---|---|---|
| `compose-core.yml` | MySQL、Redis、Nacos + 网关、认证、系统、资源 | 微服务方式的最小部署；本地开发时只启动其中的中间件 |
| `compose-full.yml` | core 的全部内容（`include`）+ 工作流、代码生成、SnailJob 服务端与执行器、监控中心、MinIO | 微服务方式的完整部署 |
| `compose-monolith.yml` | MySQL、Redis、单体（`tuoyu-boot-all`）+ SnailJob 服务端 | 单体方式，不需要 Nacos |
| `compose-extras.yml` | 按 profile 开启的附加组件：PostgreSQL、ELK、SkyWalking、Prometheus / Grafana、RocketMQ、RabbitMQ、Kafka、Seata、ShardingSphere-Proxy、Nginx、AI 扩展 | 叠加在 full（或 core）之上，不能单独使用 |

共同约定：

- **bridge 网络**：服务之间用容器名访问（`mysql`、`redis`、`nacos`、`tuoyu-system` 等），不使用 host 网络，也不使用 `privileged`；
- **健康检查**：所有服务都有健康检查，依赖关系使用 `depends_on: condition: service_healthy`。Java 服务的检查请求 `/actuator/health`（带监控账号），要求返回 `UP`；
- **内存限制**：每个容器都有 `deploy.resources.limits.memory`，Java 服务的堆内存按容器上限的 70% 计算（`-XX:MaxRAMPercentage=70.0`）；
- **非 root**：本项目的镜像都以 UID / GID 1000 运行，程序文件属于 root 且只读；
- **口令全部来自 `.env`**：compose 文件中不保存任何口令，缺少必填项时 `docker compose` 直接报错；
- **端口**：只有网关 / 单体的 `8081`（以及 Nginx 的 80 / 443）对外开放，其余端口只绑定 `127.0.0.1`，供本机调试与运维使用；
- **项目名都是 `tuoyu`**：三套 compose 共用同一组数据卷（`tuoyu_mysql-data` 等），在微服务与单体之间切换时数据不用迁移。

## 准备

需要 Docker 24 及以上（Compose v2.20 及以上，`include` 需要它），所有命令都在**仓库根目录**执行。

```bash
# 1. 生成 .env（已有时跳过）。compose 的变量替换与容器的环境变量都读取它
java tools/secrets/GenerateEnv.java        # 或 ./dev env（Windows：.\dev.ps1 env）

# 2. 构建可执行 jar（镜像从各模块的 target 目录复制，构建会运行测试）
mvn clean package
```

`.env` 中面向本机开发的地址（`TUOYU_DB_HOST=localhost` 等）不用改：compose 会把中间件地址覆盖为容器名。

## 微服务：core / full

```bash
docker compose --env-file .env -f deploy/docker/compose-core.yml up -d --build
docker compose --env-file .env -f deploy/docker/compose-full.yml up -d --build
```

启动顺序由健康检查保证：

1. MySQL 首次启动（数据卷为空）时执行 `mysql/init`，创建 `tuoyu-cloud`、`tuoyu-workflow`、`tuoyu-job` 三个库；表由各负责服务启动时的 Flyway 迁移创建；
2. Nacos 2.x 单机模式、开启鉴权。一次性任务 `nacos-init` 用 `.env` 中的 `TUOYU_NACOS_PASSWORD` 初始化管理员 `nacos` 的口令，并导入 `deploy/config/nacos` 下的配置（只新建不存在的配置，不覆盖已有配置）；
3. 各服务在 MySQL、Redis 健康且 `nacos-init` 成功后启动；网关在认证、系统、资源都健康后启动；
4. full 中的一次性任务 `snailjob-token-init` 在 SnailJob 服务端健康（`tuoyu-job` 库已迁移）后，把执行器令牌的占位值替换为 `.env` 中的 `TUOYU_SNAILJOB_TOKEN`，之后再启动执行器 `tuoyu-job`。

访问地址：

| 地址 | 说明 |
|---|---|
| `http://<主机>:8081` | 网关，前端的接口地址 |
| `http://127.0.0.1:8848/nacos` | Nacos 控制台，账号 `nacos`，口令为 `TUOYU_NACOS_PASSWORD` |
| `http://127.0.0.1:9100` | 监控中心（full），账号为 `TUOYU_MONITOR_USERNAME` / `TUOYU_MONITOR_PASSWORD` |
| `http://127.0.0.1:8800/snail-job` | SnailJob 管理界面（full / monolith） |
| `http://127.0.0.1:9001` | MinIO 控制台（full），账号为 `TUOYU_MINIO_ACCESS_KEY` / `TUOYU_MINIO_SECRET_KEY` |

注意：

- compose 中的 Nacos 专供本项目使用：命名空间固定为 `public`、账号固定为 `nacos`（覆盖 `.env` 中的 `TUOYU_NACOS_NAMESPACE`、`TUOYU_NACOS_USERNAME`）；
- 修改了 `deploy/config/nacos` 下的配置后，覆盖导入再重启相关服务：`./dev nacos-import`（预览）→ `./dev nacos-import --apply --overwrite`（本机 `.env` 中的 `TUOYU_NACOS_SERVER` 为 `127.0.0.1:8848` 即指向 compose 中的 Nacos）；
- 重新生成过 `.env` 而数据卷还在时，数据库、Redis、Nacos 中保存的仍是旧口令：要么恢复旧的 `.env`，要么删除数据卷重新初始化（`docker compose -p tuoyu down -v`，会清空数据）；
- SnailJob 管理界面的账号是 `admin`，口令为 `.env` 中的 `TUOYU_SNAILJOB_ADMIN_PASSWORD`：SnailJob 初始化数据中的默认口令 `admin` 会在服务端启动时改为它（没有设置时生产环境拒绝启动）；管理界面只绑定在本机回环地址上；
- MinIO 的接口端口只绑定本机。浏览器需要直接访问文件地址时，把 `9000` 改为对外开放，并在管理界面“文件配置”中把服务端地址填为浏览器能访问的地址。

## 单体：monolith

```bash
mvn -pl tuoyu-boot/tuoyu-boot-all,tuoyu-visual/tuoyu-snailjob-server -am package
docker compose --env-file .env -f deploy/docker/compose-monolith.yml up -d --build
```

- 单体不需要 Nacos：从 `TUOYU_CONFIG_DIR`（容器中为 `/app/config`）读取与 Nacos 相同的配置文件。镜像中带有一份副本，compose 再把仓库的 `deploy/config/nacos` 只读挂载上去，修改后重启即可生效；
- 单体镜像的构建上下文是仓库根目录，`tuoyu-boot/tuoyu-boot-all/Dockerfile.dockerignore` 只放行配置目录与 jar（需要 BuildKit，Docker 23 起默认开启）；
- 对外端口与网关相同（`8081`），前端访问的地址与微服务方式一致；单体在 compose 网络中也有别名 `tuoyu-gateway`，Nginx 在两种方式下使用同一个上游地址；
- 定时任务：目前按“SnailJob 服务端作为第二个进程随单体一起部署”编写，默认启用。SnailJob 服务端不连接 Nacos，改为直接读取挂载的 `datasource.yml` 与 `tuoyu-snailjob-server.yml`；单体中的执行器直接连接 `tuoyu-snailjob-server`。不需要定时任务时只启动前三个服务：
  `docker compose --env-file .env -f deploy/docker/compose-monolith.yml up -d mysql redis tuoyu-boot-all`

### 在两种部署方式之间切换

三套 compose 的项目名相同、共用数据卷。切换前先停掉当前这套，启动另一套时加 `--remove-orphans` 清理上一套留下的容器：

```bash
docker compose -p tuoyu down
docker compose --env-file .env -f deploy/docker/compose-monolith.yml up -d --build --remove-orphans
```

## 附加组件：extras

叠加在 full 之上，用 profile 选择要启动的组件（可以写多个 `--profile`）：

```bash
docker compose --env-file .env -f deploy/docker/compose-full.yml -f deploy/docker/compose-extras.yml \
  --profile metrics --profile elk up -d
```

| profile | 组件 | 说明 |
|---|---|---|
| `postgres` | PostgreSQL 14.2 | 只提供数据库（首次启动时建三个库）。服务改用 PostgreSQL：在自己的覆盖文件中把各服务的 `TUOYU_DB_TYPE`、`TUOYU_DB_HOST`、`TUOYU_DB_PORT` 改为 `postgresql`、`postgres`、`5432` |
| `elk` | Elasticsearch、Kibana、Logstash | 需要在服务中启用 `tuoyu-common-logstash`，并把 `TUOYU_LOGSTASH_ADDRESS` 设为 `logstash:4560`。Elasticsearch 未开启认证，只在 compose 网络与本机回环地址可访问；Linux 主机需要 `vm.max_map_count >= 262144` |
| `skywalking` | SkyWalking OAP、UI | 存储使用 Elasticsearch（自动一起启动）。探针需另行挂载，并在服务的 Dockerfile 中打开 `-javaagent` |
| `metrics` | Prometheus、Grafana | 先执行 GenerateEnv 生成 `prometheus/monitor.password`；Prometheus 通过监控中心发现各服务，抓取账号固定为 `tuoyu`（与 `TUOYU_MONITOR_USERNAME` 的默认值一致） |
| `rocketmq` | 名称服务、Broker、控制台 | `rocketmq/broker1/conf/broker.conf` 的 `brokerIP1` 为容器名；容器外的客户端访问时改为宿主机地址 |
| `rabbitmq` | RabbitMQ 3.13 + 延迟消息插件 | 账号为 `TUOYU_RABBITMQ_USERNAME`（默认 `tuoyu`）/ `TUOYU_RABBITMQ_PASSWORD`；服务端需设置 `TUOYU_RABBITMQ_HOST=rabbitmq` |
| `kafka` | Kafka 4.3（KRaft 单节点） | 对外宣告的地址为 `kafka:9092`；容器外的客户端访问时修改 `KAFKA_ADVERTISED_LISTENERS` |
| `seata` | Seata 2.6 | 默认 file 模式；`seata/conf/application.yml` 中的 Nacos、MySQL、Redis 地址已改为容器名 |
| `sharding` | ShardingSphere-Proxy 5.4 | 数据库驱动自行下载放入 `shardingproxy/ext-lib`（见该目录的 README） |
| `nginx` | Nginx 1.31 | 前端构建产物放在 `nginx/html`，证书放在 `nginx/cert`；`/prod-api/` 反向代理到 `tuoyu-gateway:8081`（微服务网关或单体） |
| `ai` | tuoyu-ai、tuoyu-snailai-server | 需要 full，并先用 `mvn clean package -P extensions` 构建；`tuoyu-ai` 库需要手工导入；Docling、PaddleOCR 默认通过宿主机端口访问 |

### 使用 Nginx 时的客户端 IP

网关只采信可信代理发来的 `X-Forwarded-For`，默认只信任回环地址（`TUOYU_GATEWAY_TRUSTED_PROXIES`）。compose 中 Nginx 在另一个容器里，地址是 compose 网络中的私有地址，不在默认的可信范围内，网关会把 Nginx 的地址当作客户端地址：日志中的 IP 都相同，按 IP 的登录失败锁定也会把所有用户算在一起。

开启 `nginx` profile 时，在 `.env` 中把网关的可信代理设为 compose 网络的网段（不要使用 `/0`）：

```bash
docker network inspect tuoyu_default --format '{{range .IPAM.Config}}{{.Subnet}}{{end}}'   # 例如 172.18.0.0/16
# .env
TUOYU_GATEWAY_TRUSTED_PROXIES=127.0.0.0/8,::1/128,172.18.0.0/16
```

compose 网络中的其他容器也会因此被网关信任，所以不要把不受控的容器加入这个网络。

## 本地开发：只启动中间件

在 IDE 中运行各服务时，只需要 core 中的中间件：

```bash
./dev up            # Windows：.\dev.ps1 up
# 等同于
docker compose --env-file .env -f deploy/docker/compose-core.yml up -d mysql redis nacos nacos-init
```

中间件端口绑定在本机（MySQL 3306、Redis 6379、Nacos 8848 / 9848），`.env` 中的 `localhost` / `127.0.0.1` 地址直接可用。没有 Docker 时，`./dev up` 会检查本机已有的 MySQL、Redis、Nacos 能否连接。

## 常用命令

```bash
./dev compose full ps                         # 查看容器与健康状态
./dev compose full logs -f tuoyu-system       # 查看日志（文件日志在数据卷 tuoyu_logs 中，按服务名分目录）
./dev down                                    # 停止并删除容器，保留数据卷
docker compose -p tuoyu down -v               # 同时删除数据卷（清空全部数据）
```
