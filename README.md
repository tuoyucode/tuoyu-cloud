# TuoYu-Cloud

> 生产就绪的开源 Java 微服务框架：默认安全、质量可验证，单体与微服务一体，云原生，完全开源。

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![JDK](https://img.shields.io/badge/JDK-21-green.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-blue.svg)
![Status](https://img.shields.io/badge/status-预览版-orange.svg)

TuoYu-Cloud 是一个通用的企业级后台开发框架，提供权限、组织、工作流、代码生成、文件存储、消息推送等开箱即用的基础能力。**同一套代码可以按单体架构部署，也可以按微服务架构部署**，团队可以随业务规模平滑演进。

> ⚠️ **项目处于预览阶段（1.0.0-alpha）**，接口和目录结构仍可能调整，暂不建议用于生产环境。

**使用手册**：[https://docs.ctoolhub.com](https://docs.ctoolhub.com)

---

## 设计目标

| 目标 | 说明 |
|---|---|
| **默认安全** | 仓库内不放明文密钥；生产环境启动时自检弱口令、默认密钥、敏感端点暴露等问题，发现即拒绝启动 |
| **单体与微服务一体** | 单体模式下服务间直接在进程内调用；微服务模式下使用 Dubbo。业务代码不需要改动 |
| **可插拔多租户** | 默认关闭、零侵入；开启后覆盖数据库、缓存、会话、异步与定时任务、跨服务调用 |
| **质量可验证** | 持续集成必跑测试，不允许跳过；架构规则自动检查；两种部署方式都有端到端测试；数据库变更用 Flyway 管理 |
| **云原生** | 容器化部署、健康检查、日志 traceId 贯穿网关与各服务、优雅停机 |
| **完全开源** | MIT 协议，没有商业版，也没有付费功能 |

## 功能

- 权限：用户、角色、菜单、部门、岗位，按钮级权限，部门数据权限
- 认证：账号密码、短信、邮箱、社交登录、小程序；支持多客户端，每个客户端可独立配置 Token 时效
- 工作流：流程设计、审批、驳回、转办、委派、加签、减签、抄送、催办
- 代码生成：根据表结构一键生成后端代码和前端页面，支持多数据源
- 资源：对象存储（兼容 S3）、短信、邮件、SSE / WebSocket 消息推送
- 数据处理：注解式脱敏、字段加解密、字典翻译、Excel 导入导出
- 运维：服务监控、分布式任务调度、接口文档
- 安全：启动自检、接口加密（RSA-OAEP-256 + AES-256-GCM）、登录失败锁定、上传文件头校验、安全中心
- 部署：单体（`tuoyu-boot-all`，不需要 Nacos）与微服务两种方式，三套 docker compose，一键开发环境 `./dev`
- 多租户：租户与租户套餐管理，默认关闭
- 数据库：MySQL 与 PostgreSQL，Flyway 自动迁移

## 技术栈

| 类别 | 选型 |
|---|---|
| 基础 | JDK 21、Spring Boot 4.1、Spring Cloud 2025.1 |
| 注册与配置 | Nacos 2.x（单体部署不需要） |
| 服务调用 | Apache Dubbo 3 |
| 网关 | Spring Cloud Gateway（Servlet） |
| 认证鉴权 | Sa-Token |
| 数据访问 | MyBatis-Plus、dynamic-datasource、Flyway；MySQL 8、PostgreSQL |
| 缓存与锁 | Redis、Redisson |
| 工作流 | Warm-Flow |
| 任务调度 | SnailJob |

## 目录结构

```
tuoyu-cloud
├── tuoyu-framework   框架公共组件（tuoyu-common-*）
├── tuoyu-api         服务间接口契约
├── tuoyu-gateway     网关
├── tuoyu-modules     框架自带的模块（auth / system / resource / workflow / gen / job）
├── tuoyu-visual      监控与调度控制台
├── tuoyu-boot        单体启动器（tuoyu-boot-all）
├── tuoyu-e2e         端到端测试
├── tuoyu-extensions  可选扩展（AI 等），默认不参与构建
├── tuoyu-architecture 依赖规则检查
├── deploy            SQL、Nacos 配置、docker compose
└── tools             开发工具脚本
```

## 快速开始

**环境要求**：JDK 21、Maven 3.9、MySQL 8 或 PostgreSQL（设置 `TUOYU_DB_TYPE=postgresql`）、Redis 6 及以上；微服务方式另需 Nacos 2.x（已验证 2.5），单体方式不需要

```bash
# 构建并运行测试
mvn clean verify

# 连同可选扩展一起构建
mvn clean verify -P extensions
```

数据库表结构由服务启动时自动迁移（Flyway，只需先建好空库）；也可以用 `deploy/sql` 下的全新安装脚本手工初始化。Nacos 配置位于 `deploy/config/nacos`。

- **单体**：启动 `tuoyu-boot/tuoyu-boot-all`（端口 8081），不需要 Nacos，直接读取 `deploy/config/nacos` 下的配置文件；
- **微服务**：导入 Nacos 配置（见下文）后，启动 tuoyu-system、tuoyu-auth、tuoyu-resource、tuoyu-gateway 四个核心服务，网关端口 8081；
- **一键开发环境**：`./dev`（Windows 用 `.\dev.ps1`），`./dev up` 准备 MySQL、Redis、Nacos（有 Docker 时用 compose 启动），`./dev monolith` 构建并启动单体，`./dev help` 查看全部命令；
- **docker compose**：核心、完整、单体三套编排，以及 Nginx 等附加组件，见 [deploy/docker/README.md](deploy/docker/README.md)。

### 配置密钥

仓库中**不包含任何口令和密钥**，全部通过环境变量注入；缺少必填项时，服务会拒绝启动。

本地开发时，用下面的命令生成 `.env`：

```bash
java tools/secrets/GenerateEnv.java
```

- 生成的 `.env` 包含随机强口令、JWT 密钥和接口加密私钥。后端服务启动时会自动读取它；如果同时设置了真实环境变量，以环境变量为准。
- 使用已有的数据库、Redis、Nacos 时，请把 `.env` 中对应的地址和口令改成实际值。如果 Nacos 与其他项目共用，建议新建独立的命名空间，并设置 `TUOYU_NACOS_NAMESPACE`。
- 初始化数据库后，按工具输出的提示执行一条 `UPDATE` 语句，同步任务调度的令牌。
- 管理员账号为 `admin`，首次启动时口令设置为 `.env` 中的 `TUOYU_ADMIN_INITIAL_PASSWORD`（种子数据中的口令不能使用）。登录后可在个人中心修改。
- 接口加密只需要后端私钥（`TUOYU_API_CRYPTO_PRIVATE_KEY`）。前端运行时从 `/auth/crypto/public-key` 获取公钥，不需要配置任何密钥。
- 浏览器只在 HTTPS 或 localhost 下提供加密所需的 WebCrypto。内网没有 HTTPS 时，可以设置 `TUOYU_API_CRYPTO_REQUIRE_HTTPS=false`，前端改用纯 JavaScript 实现加密；这种方式只能防止被动窃听，条件允许时请配置 HTTPS（`deploy/docker/nginx/conf/nginx.conf` 中有示例）。
- 文件存储（OSS）的访问密钥需要在管理界面“文件管理”的存储配置中填写。

全部变量及说明见 [.env.example](.env.example)。生产环境请改用真实的环境变量或密钥管理服务注入，不要使用 `.env` 文件。

### 导入 Nacos 配置

微服务方式需要先把 `deploy/config/nacos` 下的配置导入 Nacos（支持 Nacos 2.x）。脚本读取 `.env` 中的 Nacos 地址、命名空间和账号：

```bash
java tools/nacos/ImportNacosConfig.java                      # 预览，不写入
java tools/nacos/ImportNacosConfig.java --apply              # 导入：新建不存在的配置，已存在的不覆盖
java tools/nacos/ImportNacosConfig.java --apply --overwrite  # 导入，并覆盖内容不同的已有配置
```

如果 Nacos 与其他项目共用，请先新建独立的命名空间，再设置 `TUOYU_NACOS_NAMESPACE`，避免覆盖其他项目的配置。

**仓库中的配置更新后**（例如拉取代码后 `deploy/config/nacos` 有变化），需要把改动同步到 Nacos：

1. 预览：执行 `java tools/nacos/ImportNacosConfig.java`，确认显示为“已存在且内容不同”的只有本次改动涉及的文件；
2. 确认后覆盖导入：执行 `java tools/nacos/ImportNacosConfig.java --apply --overwrite`；
3. 重启相关服务。

如果没有同步，启动自检可能会报 `[C2] 未能从 Nacos 加载公共配置`，原因是 Nacos 中的 `application-common.yml` 仍是旧版本，缺少加载标记。

注意：`--overwrite` 会用仓库中的版本覆盖 Nacos 中的配置。如果在 Nacos 控制台手工修改过配置，请先把修改同步回仓库，否则会被覆盖。

### 启动自检

每个服务启动时会先做自检，未通过就拒绝启动，并一次列出全部问题和修复方式：

| 检查项 | 内容 | 开发环境 | 生产环境（`TUOYU_PROFILES_ACTIVE=prod`） |
|---|---|---|---|
| C1 | 必填的环境变量都已提供 | 拒绝启动 | 拒绝启动 |
| C2 | 已加载公共配置 | 拒绝启动 | 拒绝启动 |
| P1 | 没有弱口令或已公开的默认密钥 | 警告 | 拒绝启动 |
| P2 ~ P5 | Actuator 未全量暴露、健康检查不展示细节、接口文档已关闭、网关未开启自动路由 | 警告 | 拒绝启动 |
| P6 | Nacos 已开启鉴权 | 警告 | 拒绝启动 |
| P7 | 登录图形验证码已开启 | 警告 | 拒绝启动 |
| P8 | 密码哈希强度不低于 10 | 警告 | 拒绝启动 |
| P9 | 没有开启完整 SQL 日志 | 警告 | 拒绝启动 |

仓库中的配置默认就是生产安全的。生成工具会在本地 `.env` 中打开接口文档等开发便利项，生产环境不要设置这些变量。确需跳过某项检查时，只能按编号跳过，例如 `tuoyu.selfcheck.skip=P4`。各服务的自检结果汇总在管理界面“系统监控 → 安全中心”。

## 相关仓库

- 前端：[tuoyu-cloud-frontend](https://gitee.com/tuoyucode/tuoyu-cloud-frontend)

## 参与贡献

开发约定（基本规则、分支、构建与检查、数据库脚本、编码约定）见 [CONTRIBUTING.md](CONTRIBUTING.md)。

- 提交信息格式：`类型: 中文描述`，类型可取 `feat`、`fix`、`docs`、`refactor`、`test`、`chore`、`build`、`ci`
- 提交 Pull Request 前，请确保 `mvn clean verify` 通过。CI 会在每次提交和每个 Pull Request 上自动运行全部测试，测试不允许跳过

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

以下第三方组件使用其他协议，未经修改：

| 组件 | 协议 | 位置与用途 |
|---|---|---|
| [ip2region](https://github.com/lionsoul2014/ip2region) 离线 IP 地址库 | Apache-2.0 | `tuoyu-common-web` 中的 `ip2region_v4.xdb`，随仓库分发 |
| [RabbitMQ 延迟消息插件](https://github.com/rabbitmq/rabbitmq-delayed-message-exchange) | MPL-2.0 | `deploy/docker/rabbitmq`，随仓库分发，可选组件 |
| [aviator](https://github.com/killme2008/aviator) | LGPL | SnailJob 服务端的条件表达式引擎，作为依赖打进可执行 jar 与镜像，可按 LGPL 替换 |
| [simple-http](https://github.com/xkcoding/simple-http) | LGPL-3.0 | 第三方登录的 HTTP 请求层，作为依赖打进可执行 jar 与镜像，可按 LGPL 替换 |

少量源文件改自其他开源项目（Apache-2.0），文件头中保留了原始的版权与协议声明。其他通过 Maven 引入的依赖，协议见各自的发布包。
