# 参与贡献

感谢你愿意参与 TuoYu-Cloud。本文写的是提交代码前需要遵守的约定，CI 与评审都按它检查。

TuoYu-Cloud 是一个通用的 Java 微服务框架，**同一套代码既可以按微服务部署，也可以按单体部署**。它不面向任何具体业务，MIT 协议。

- 后端（本仓库）与前端 [tuoyu-cloud-frontend](https://gitee.com/tuoyucode/tuoyu-cloud-frontend)。前端有自己的 `CONTRIBUTING.md`。
- 主仓库在 Gitee，GitHub 上的同名仓库与它保持同一份提交历史，CI（GitHub Actions）在 GitHub 上运行。
- 技术栈：JDK 21、Spring Boot 4.1、Spring Cloud 2025.1、Dubbo 3、Sa-Token、MyBatis-Plus、Nacos、Redisson、Warm-Flow。包名 `org.tuoyu`，模块前缀 `tuoyu-`。

## 基本规则

1. **新模块原创编写。** `tuoyu-common-rpc`、`tuoyu-common-tenant`、`tuoyu-common-selfcheck`、`tuoyu-common-observability` 与 `tuoyu-boot-*` 必须自己写，不得复制其他开源框架的代码。
2. **改写已有模块要独立实现。** 按新设计重新写，不要对照原有代码逐行改。改写前先补测试。
3. **协议合规。** 不删除、不修改 `LICENSE` 中的版权声明和第三方文件头（例如 Apache 协议头）。随仓库分发或打进发布包、协议不是 MIT 的第三方组件，在 README 的“许可证”一节列出。
4. **第三方包名保持原样。** `org.dromara.warm`、`org.dromara.sms4j`、`org.dromara.mica`、`org.dromara.easyes` 及其 groupId 不改。
5. **默认安全。** 仓库里不放明文密钥，不新增默认口令；配置中的敏感值一律用环境变量占位。
6. **提交与测试。**
   - 提交说明格式为 `类型: 中文描述`，类型取 `feat`、`fix`、`docs`、`refactor`、`test`、`chore`、`build`、`ci`；描述后空一行，再写正文说明做了什么、为什么。CI 检查新增提交的格式（merge 提交放行，不要求 DCO）。
   - 不跳过测试：pom 中不允许 `skipTests`、`-Dmaven.test.skip`，CI 会检查。
   - 导入第三方代码的提交与改名提交分开，不要混在一起。
7. **依赖协议。**
   - 仓库内不分发 GPL、AGPL、LGPL 协议的文件（jar、插件、源码等）。
   - 新增依赖前先确认协议，不引入 GPL、AGPL 依赖。带 FOSS 例外条款的数据库驱动（例如 MySQL Connector/J）可以作为 Maven 依赖使用，但不放进仓库。
   - LGPL 依赖默认不引入。确实无法避免时（例如被必须使用的组件直接依赖、排除后功能失效），只允许未修改、作为 Maven 依赖使用，并且要先经维护者确认、登记在 `tools/ci/license-exceptions.txt` 并写明原因（CI 的协议检查据此放行，未登记的一律阻断）、在 README 的“许可证”一节列出库名、协议与源码地址。能排除的先排除。
   - 目前的 LGPL 例外：`aviator`（SnailJob 服务端的条件表达式引擎）、`simple-http`（JustAuth 的 HTTP 请求层，第三方登录使用）。
8. **框架与业务分开。** `dev` / `master` 只放框架；业务模块只放在 `business` 分支，框架的改动先进 `dev`，再合并到 `business`，不反向合并。详见下一节，CI 会检查。

## 分支

| 分支 | 内容 | 用途 |
|---|---|---|
| `master` | 框架 | 发布版本 |
| `dev` | 框架 | 日常开发，合并到 `master` 后发布 |
| `business` | `dev` 的全部内容加上业务模块 | 业务系统（目前是制造执行 MES）的开发 |

- 框架的改动（框架模块、框架自带的模块、文档与 CI）从 `dev` 开分支、合回 `dev`；在 `business` 上发现框架的缺陷，也回到 `dev` 修。业务的改动从 `business` 开分支、合回 `business`。
- 只能由 `dev` 合并到 `business`（`git merge dev`），不要把 `business` 合并、也不要把其中的提交挑到 `dev` / `master`。一次改动两边都涉及时，拆成两个提交，先在 `dev` 提交框架部分。
- 业务代码放在 `tuoyu-business/` 下；放在其他位置的业务文件、接入业务模块必须改动的框架文件，逐个登记在 `tools/ci/business-scope.txt` 中。新增业务模块时，先在 `dev` 上登记它的路径。
- CI 执行 `tools/ci/check_branch_scope.py` 检查，提交前可以在本地执行 `python tools/ci/check_branch_scope.py <当前分支>`。

## 构建与检查

需要 JDK 21 与 Maven 3.9。

```bash
mvn clean verify                 # 完整构建，提交前必须通过
mvn clean verify -P extensions   # 改动了可选扩展模块（tuoyu-extensions）时
mvn clean verify -P business     # business 分支上改动了业务模块（tuoyu-business）时
mvn -P format compile            # 修正格式；扩展模块用 mvn -P extensions,format compile
```

- 核心模块（tuoyu-auth、tuoyu-gateway、tuoyu-system、tuoyu-common-core / mybatis / tenant / rpc / satoken / security / encrypt）行覆盖率不低于 50%。只排除机器生成的代码，不要为凑覆盖率排除业务类。
- PMD 需要豁免时用 `@SuppressWarnings("PMD.规则名")` 并写明原因。
- 资源文件中不要使用 Maven 过滤占位符（`@xxx@` 或 `${project.xxx}`），需要动态的值时用 Spring 占位符。
- 依赖规则由 `tuoyu-architecture`（ArchUnit）检查，单独构建某个模块时它会失败，以完整构建为准。
- 迁移与多租户越权测试需要 Docker（Testcontainers），本地没有 Docker 时跳过，以 CI 的结果为准。
- 改动接口路径或返回结构时，同步修改端到端测试 `tuoyu-e2e`：`mvn -P e2e -pl tuoyu-e2e verify -Dtuoyu.e2e.base-url=http://localhost:8081`（管理员口令用 `TUOYU_E2E_PASSWORD` 传入）。

## 本地开发

- 执行 `java tools/secrets/GenerateEnv.java` 生成 `.env`，后端在配置加载前读取它。
- 一键开发环境：`./dev`（Windows 用 `.\dev.ps1`），常用命令 `env`、`up`、`down`、`status`、`monolith`、`nacos-import`。
- 微服务方式需要 Nacos（导入配置：`java tools/nacos/ImportNacosConfig.java --apply`）；不想装 Nacos 时用单体 `tuoyu-boot/tuoyu-boot-all`（端口 8081）。
- IDEA 启动配置在 `.run/` 下，项目 SDK 设为 JDK 21。

## 数据库脚本

- 支持 MySQL 与 PostgreSQL（`TUOYU_DB_TYPE`），由 Flyway 迁移。表结构与种子数据的改动写成迁移脚本 `V<n>__<小写说明>.sql`，两种数据库各一份（版本号与说明相同），放在负责该库的服务的 `src/main/resources/db/migration/<库名>/mysql` 与 `.../postgresql` 下。
- `V1__baseline.sql`、`deploy/sql` 下对应的全新安装脚本以及已经发布的迁移脚本不能修改。
- Oracle 不由 Flyway 管理：修改 `deploy/sql/oracle` 下的全新安装脚本，并在 `deploy/sql/upgrade/<日期>-<说明>/` 新增可重复执行的升级脚本。

## 密钥与配置

- 配置中一律写成 `${TUOYU_XXX}` 占位符：必填项不给默认值；可选组件用空默认值 `${TUOYU_XXX:}`；非敏感项可以带默认值。
- 新增变量时，同步登记到 `.env.example` 与 `tools/secrets/GenerateEnv.java`，CI 会检查。
- 仓库中的配置默认必须是生产安全的，开发便利项只在本地 `.env` 中开启。服务启动时会自检，生产环境发现弱口令或不安全的配置时拒绝启动。

## 编码约定

- **响应**：Controller 返回 `ApiResult<T>`；失败时抛 `ServiceException` 并写清具体原因，HTTP 状态码由全局异常处理决定。写操作的服务方法返回 `void`。
- **分页**：参数用 `PageParam`（`page` 从 1 开始、`pageSize`、`sort`），结果为 `PageResult<T>`；可排序的属性不能含敏感字段。
- **当前用户**：业务代码只用 core 的 `UserContext`；`LoginHelper` 只在 `tuoyu-common-satoken` 与 `tuoyu-auth` 中使用。
- **Mapper**：直接继承 `BaseMapper<实体>`，转换为 Vo 在服务层用 `BeanMapper`。带 `@DataPermission` 的 Mapper 要覆盖业务会调用的默认方法并加注解，并用测试确认数据权限生效。
- **接口权限**：每个接口带 `@SaCheckPermission`、`@SaCheckRole`、`@SaCheckLogin`、`@SaIgnore` 之一；免登录接口同时在 `security.ignore.whites` 中写完整路径；权限码必须在菜单数据中。请求中有明文口令的接口标注 `@ApiEncrypt`。
- **跨模块调用**：只通过 `tuoyu-api-*` 中标注 `@RemoteApi` 的接口，调用方普通构造器注入；模块中不出现 Dubbo 的注解。跨模块调用不参与调用方事务，参数校验由提供方负责。
- **多租户**：需要隔离的表加 `tenant_id`、实体继承 `TenantEntity`，不要手工设置租户；后台任务与异步代码用 `@TenantJob` 或 `TenantHelper.dynamic` 指定租户。
- **安全**：客户端 IP 用 `ServletUtils.getClientIP` 获取；管理接口不返回密钥类字段；富文本字段标注 `@RichText`，不加全局输入过滤；Excel 导入导出受行数上限约束。
- **两种部署方式**：单体的 URL 前缀与网关路由一一对应，新增框架模块或网关路由时两边一起改。业务模块在 `META-INF/tuoyu/monolith-module.yml` 中声明单体接入方式，在 `META-INF/tuoyu/menus/<模块>.json` 中声明菜单。
- **日志**：格式只在 `logback-pattern.xml` 中定义；traceId 由框架透传，不要自己读写 MDC 中的 traceId。
- **命名**：新写的类按作用命名，不加 `Plus`、`Tuoyu` 之类的前缀，也不要与依赖库中的类同名。

## 目录结构

| 目录 | 内容 |
|---|---|
| `tuoyu-framework` | 框架公共组件，子模块的 artifactId 为 `tuoyu-common-*` |
| `tuoyu-api` | 服务间接口契约 |
| `tuoyu-gateway` | 网关 |
| `tuoyu-modules` | 框架自带的模块：auth、system、resource、workflow、gen、job |
| `tuoyu-visual` | 监控与调度控制台 |
| `tuoyu-boot` | 启动器（单体 `tuoyu-boot-all`） |
| `tuoyu-e2e` | 端到端测试 |
| `tuoyu-extensions` | 可选扩展 |
| `tuoyu-business` | 业务模块（只在 `business` 分支，`-P business` 启用） |
| `tuoyu-architecture` | 依赖规则检查 |
| `deploy` | SQL、Nacos 配置、部署脚本 |
| `tools` | 工具脚本 |

## 前端

前端基于官方 Vben Admin 5 + Ant Design Vue，业务页面全部原创，接口契约以本仓库的后端代码为准。菜单种子数据中的 `component` 填前端 `views` 下的页面路径，图标用 Iconify 名称（例如 `lucide:user`）。
