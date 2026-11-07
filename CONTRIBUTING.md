# 参与贡献

感谢你愿意参与 TuoYu-Cloud。本文写的是改代码前需要知道的约定：哪些事情不能做、怎么构建和测试、各个子系统有哪些容易踩的坑。设计上的来龙去脉在 `docs/adr/` 中，本文只给结论。

TuoYu-Cloud 是一个通用的 Java 微服务框架，**同一套代码既可以按微服务部署，也可以按单体部署**。它不面向任何具体业务，MIT 协议，没有商业版。

- 后端（本仓库）与前端 [tuoyu-cloud-frontend](https://gitee.com/tuoyucode/tuoyu-cloud-frontend)。前端有自己的 `CONTRIBUTING.md`。
- 主仓库在 Gitee，GitHub 上的同名仓库与它保持同一份提交历史，CI（GitHub Actions）在 GitHub 上运行。
- 技术栈：JDK 21、Spring Boot 4.1、Spring Cloud 2025.1、Dubbo 3、Sa-Token、MyBatis-Plus、Nacos、Redisson、Warm-Flow。包名 `org.tuoyu`，模块前缀 `tuoyu-`。

## 基本规则

下面几条是硬性要求，CI 与评审都会按它检查。

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

## 构建与检查

需要 JDK 21 与 Maven 3.9。

```bash
mvn clean verify                 # 完整构建，提交前必须通过
mvn clean verify -P extensions   # 改动了可选扩展模块（tuoyu-extensions，默认不参与构建）时
```

- 运行环境不在构建时决定：同一份产物通过 `TUOYU_PROFILES_ACTIVE` 选择环境（默认 `dev`）。
- 资源文件中不要使用 Maven 过滤占位符（`@xxx@` 或 `${project.xxx}`），IDE 中运行时不会过滤，会导致启动失败。需要动态的值时用 Spring 占位符或 logback 的 `<springProperty>`。
- **覆盖率**：JaCoCo 在 verify 阶段生成报告并检查行覆盖率（`tuoyu.coverage.line-minimum`，根 pom 默认 0）。核心模块（tuoyu-auth、tuoyu-gateway、tuoyu-system、tuoyu-common-core / mybatis / tenant / rpc / satoken / security / encrypt）要求不低于 50%。只排除机器生成的代码（Lombok、mapstruct-plus），不要为凑覆盖率排除业务类。surefire 的 argLine 要写成 `@{argLine} ...`。
- **代码检查**：spotless（删除未使用的 import、去掉行尾空白、文件以换行结尾）与 PMD（`tools/pmd/ruleset.xml`，只有缺陷与安全类规则，不检查测试与 `com.*` 下的第三方代码）。格式不符时在仓库根目录执行 `mvn -P format compile`（扩展模块用 `mvn -P extensions,format compile`；直接执行 `mvn spotless:apply` 会在不继承根 pom 的模块上报错）。PMD 需要豁免时用 `@SuppressWarnings("PMD.规则名")` 并写明原因。
- **依赖规则**：模块 `tuoyu-architecture`（只有测试，ArchUnit）读取各模块编译后的类检查依赖规则，放在构建顺序的最后。单独构建某个模块时它会因找不到类而失败，以完整构建为准。
- **端到端测试**：模块 `tuoyu-e2e`，由 profile `e2e` 启用，访问一个已经运行的系统：

  ```bash
  mvn -P e2e -pl tuoyu-e2e verify -Dtuoyu.e2e.base-url=http://localhost:8081
  ```

  管理员口令用 `TUOYU_E2E_PASSWORD` 传入。微服务（经网关）与单体共用同一套测试，被测系统不可达时失败。改动接口路径或返回结构时同步修改。

CI（`.github/workflows/ci.yml`）依次做：规则检查（禁止跳过测试、扫描 `tools/secrets/forbidden-patterns.txt` 中的弱口令与已公开密钥、配置引用的 `TUOYU_*` 变量必须登记在 `.env.example`）、提交说明格式、构建与测试（JDK 21 必过，JDK 25 为观察项）、扩展模块编译、依赖漏洞与协议扫描（CycloneDX + Trivy，高危与严重阻断，忽略项写在 `.trivyignore.yaml` 并注明原因）、镜像构建、两种部署方式的端到端测试。

## 数据库脚本

1.0 支持 MySQL 与 PostgreSQL，由 `TUOYU_DB_TYPE`（`mysql` 默认，或 `postgresql`）选择。设计见 `docs/adr/0004-数据库迁移.md`。

- MySQL、PostgreSQL 由 Flyway 迁移。表结构与种子数据的改动只写成迁移脚本 `V<n>__<小写说明>.sql`，两种数据库各一份（版本号与说明相同），放在负责该库的服务的 `src/main/resources/db/migration/<库名>/mysql` 与 `.../postgresql` 下：
  - `tuoyu-cloud` 库：`tuoyu-system`，库名 `cloud`；
  - `tuoyu-workflow` 库：`tuoyu-workflow`，库名 `workflow`；
  - `tuoyu-job` 库：`tuoyu-snailjob-server`，库名 `job`。
- 库名由服务 `application.yml` 中的 `tuoyu.flyway.database` 指定。按库分目录是为了单体：单体在一个进程中迁移 cloud 与 workflow 两个库（`tuoyu.flyway.additional`）。
- 两种数据库的 `V1__baseline.sql` 与 `deploy/sql` 下对应的全新安装脚本已冻结、逐字一致，不能修改（`MigrationScriptsTest` 校验）。已经发布的迁移脚本也不要再改。
- Oracle 不由 Flyway 管理：修改 `deploy/sql/oracle` 下的全新安装脚本，并在 `deploy/sql/upgrade/<日期>-<说明>/` 新增可重复执行的升级脚本（规则见该目录的 README）。
- `tuoyu-common-flyway` 只有负责迁移的服务依赖。开关 `TUOYU_FLYWAY_ENABLED`（默认开启）；连接其他数据库时跳过；类路径中没有该库的 V1 基线时拒绝启动；固定禁止 clean、不做占位符替换、基线版本为 1。
- 已有库（有表、没有迁移记录表）按基线 1 接入，接入前核对 V1 中的表是否都已存在，缺少时拒绝启动。所以已有库首次启用前，先执行完 `deploy/sql/upgrade` 下的升级脚本。
- `MigrationIntegrationTest` 用 Testcontainers 在真实的 MySQL、PostgreSQL 上执行每个负责服务的迁移，需要 Docker：本地没有 Docker 时跳过，CI 中必须执行。新增迁移脚本后以 CI 的结果为准。

## 两种部署方式

设计见 `docs/adr/0005-RPC抽象与两种部署方式.md`。

- **微服务**：各业务模块单独启动，可执行 jar 为 `target/<模块>-exec.jar`（普通 jar 供单体依赖），Dockerfile 复制它。
- **单体**：`tuoyu-boot/tuoyu-boot-all`（`target/tuoyu-boot-all.jar`，端口 8081，与网关相同），不需要 Nacos，从 `TUOYU_CONFIG_DIR`（默认 `deploy/config/nacos`）读取与 Nacos 相同的配置文件，最后加载单体专用的 `tuoyu-boot-all.yml`。pom 中排除 Dubbo、Nacos、Seata（`MonolithAssemblyTest` 检查）。
- 单体的 URL 前缀表（`tuoyu.monolith.path-prefixes`）与网关路由一一对应（`ModulePathPrefixConsistencyTest`），新增业务模块或网关路由时两边一起改。
- 一个 Controller 在单体中只有一个前缀：system 的 `controller.monitor` 包只挂在 `/monitor` 下（微服务中网关同时转发 `/system`、`/monitor`），前端调用这些接口一律用 `/monitor/...`。
- 单体按模块切换数据源（`tuoyu.monolith.datasource-routes`），访问数据库的模块都要登记。
- SnailJob 服务端在单体部署中作为第二个进程运行（`compose-monolith.yml`）。

## 本地开发

- 执行 `java tools/secrets/GenerateEnv.java` 生成 `.env`。后端在配置加载前读取它，优先级为系统属性 > 环境变量 > `.env`。使用定时任务时，按它打印的语句把 tuoyu-job 库中执行器令牌的占位值改为 `TUOYU_SNAILJOB_TOKEN`，否则执行器注册不上。
- 导入 Nacos 配置：`java tools/nacos/ImportNacosConfig.java`。默认只预览，加 `--apply` 才导入，加 `--overwrite` 才覆盖内容不同的已有配置；使用 v1 接口，兼容 Nacos 2.x。Nacos 与其他项目共用时，导入前先确认命名空间。`--init-admin` 只用于新部署的 Nacos。
- 一键开发环境：`./dev`（Windows 用 `.\dev.ps1`），命令有 `env`、`up`、`down`、`status`、`monolith`、`nacos-import`、`compose <core|full|monolith|extras>`。微服务方式不内嵌注册中心；不想装 Nacos 时用单体。
- IDEA 启动配置在 `.run/` 下（Spring Boot 类型，工作目录为仓库根目录，以便读取 `.env`），另有组合配置 `tuoyu-core-services` 一键启动 system、auth、resource、gateway。项目 SDK 要设为 JDK 21；可选扩展需要在 Maven 面板中勾选 `extensions` profile。Dubbo 端口自动选择，几个服务同时启动偶尔会抢到同一个端口（`Address already in use`），单独重启失败的服务即可。
- **部署**：`deploy/docker` 下三套 compose：`compose-core.yml`（MySQL、Redis、Nacos 与 4 个核心服务）、`compose-full.yml`（引入 core，加上其余服务）、`compose-monolith.yml`（MySQL、Redis、单体、SnailJob 服务端）；附加组件在 `compose-extras.yml`，按 profile 叠加。口令只来自 `.env`，只有 8081 对外。新增服务时同时写 Dockerfile（非 root、`exec java`、`SERVER_PORT`）与 compose 中的健康检查。

## 密钥与配置

- 配置中一律写成 `${TUOYU_XXX}` 占位符，仓库内不放任何明文口令。必填项不给默认值，缺失时服务拒绝启动；可选组件（短信、第三方登录、RabbitMQ 等）用空默认值 `${TUOYU_XXX:}`；用户名、地址等非敏感项可以带默认值，例如 `${TUOYU_DB_USERNAME:root}`。
- 新增变量时，同步登记到 `.env.example` 和 `GenerateEnv.java`，CI 会检查。
- **仓库内的配置默认必须是生产安全的**，开发便利项（例如 `TUOYU_ACTUATOR_EXPOSURE`）只能通过本地 `.env` 开启。`ProductionConfigTest` 按生产模式校验 `deploy/config/nacos`，并检查各服务自带的 `application*.yml`（SnailJob、SnailAI 服务端不导入公共配置，以它为准）。
- **接口加密**（`docs/adr/0001-接口加密.md`）：只有后端持有 RSA 私钥（`TUOYU_API_CRYPTO_PRIVATE_KEY`），前端不持有任何密钥，公钥运行时从 `/auth/crypto/public-key` 获取。需要加密的接口标注 `@ApiEncrypt`，目前有登录、注册、修改密码、重置密码、新增用户、新增租户；请求中有明文口令的新接口都要标注。改动协议时同步修改 ADR 与测试向量 `tuoyu-common-encrypt/src/test/resources/api-crypto/test-vectors.json`。生成或轮换私钥：`java tools/secrets/GenerateEnv.java --api-crypto-key`。
- **Redis 反序列化白名单**（`RedisTypeAllowlist`）：`org.tuoyu.` 下的类型自动放行；其他包加入 `tuoyu.redis.allowed-type-prefixes`；JDK 类型改 `RedisTypeAllowlist` 并补测试。不允许改回放行全部。
- **管理员初始口令**：种子数据中 admin 的口令是公开的，tuoyu-system 启动时若 admin 仍是种子口令则改为 `TUOYU_ADMIN_INITIAL_PASSWORD`，生产环境未配置时拒绝启动。种子数据不放可登录的演示账号。

## 启动自检

`tuoyu-common-selfcheck` 在配置加载完成后、创建任何 Bean 之前执行，所有服务都依赖它。

- 任何环境都检查，未通过即拒绝启动：C1 配置中的 `${TUOYU_XXX}` 都已提供；C2 Nacos 公共配置确实已加载（标记 `tuoyu.config.common-loaded`）。
- 生产检查：prod profile 或 `tuoyu.selfcheck.strict=true` 时未通过即拒绝启动，开发模式下只输出警告。
  - P1 弱口令或已公开的密钥（代码中只保存已公开密钥的 SHA-256，报告中不显示具体值）；
  - P2 Actuator 暴露了全部端点；P3 健康检查展示全部细节；P4 接口文档已开启；P5 网关开启了服务发现自动路由；
  - P6 Nacos 未开启鉴权：公共配置来自 Nacos 时，启动时不带令牌读取一次公共配置（先 v1 接口，不存在时用 v3，超时 3 秒），401 / 403 通过，200 未通过，无法判断时记为不适用并警告；
  - P7 登录图形验证码已关闭（`security.captcha.enabled=false`）。短信、邮箱验证码一律随机生成，没有可固定的配置（`SendCodeGuardTest`）；
  - P8 密码哈希强度低于 10（`tuoyu.auth.password.bcrypt-cost`，默认 10，取值 4 ~ 31，超出范围任何环境都拒绝启动）。保存密码一律用 `PasswordHasher`，不要直接调用 `BCrypt`；
  - P9 开启了完整 SQL 日志（`TUOYU_SQL_LOG_ENABLED`，默认关闭，参数值会写进日志）。
- 只能用 `tuoyu.selfcheck.skip=P4,P5` 按编号跳过，并会在日志中提示；不提供整体关闭的开关。
- 服务启动完成后把结果写入 Redis（`global:selfcheck:<服务名>`），安全中心由 tuoyu-system 按 `tuoyu.home.services` 汇总（同一项取未通过 > 跳过 > 通过 > 不适用），单体只取本进程的结果。新增检查项时，安全中心与首页不需要改。

## 输入输出安全

- **客户端 IP**（`docs/adr/0002-认证加固与可信代理.md`）一律通过 `ServletUtils.getClientIP` 获取，不要自己读转发头。可信代理 `tuoyu.web.trusted-proxies`：各服务默认信任回环与私有网段（`TUOYU_TRUSTED_PROXIES`），网关默认只信任回环（`TUOYU_GATEWAY_TRUSTED_PROXIES`），网关前的反向代理不在本机时必须配置。不允许 `/0`；`none` 表示不信任任何代理。不要配置 `spring.cloud.gateway.server.webmvc.trusted-proxies`。
- **登录失败锁定**：配置在 `tuoyu.auth.*`，锁定键名一律由 `LoginFailureKeys` 生成；验证码统一用 `VerifyCodeService`（一次性）。短信、邮箱验证码接口免登录，发送前都必须先通过图形验证码。
- **上传**（`docs/adr/0003-上传-XSS-脱敏与字段加密.md`）：扩展名白名单 `tuoyu.upload.allowed-extensions` 加文件头校验。新增允许的类型时，在 `FileType` 中登记文件头规则并补测试，同时修改前端 `views/system/oss/data.ts` 的 `UPLOAD_EXTENSIONS`，两处保持一致。上传请求的上限取 `max-request-size` 与 `max-file-size` 中较小的（每次一个文件，网关据此先拒绝）；新增一次上传多个文件的接口时要调整这条规则。
- **XSS**：没有全局输入过滤，普通字段原样保存、由前端输出时转义。富文本字段标注 `@RichText`（jsoup 白名单清洗），前端只对这些字段用 `v-html`。不要再加全局输入过滤。
- **脱敏与字段加密**：脱敏失败时整体掩码；`@EncryptField` 只用于 String，算法为 AES-GCM / SM4-GCM，密钥 `TUOYU_FIELD_ENCRYPT_KEY`（`GenerateEnv --field-key`），加解密失败时拒绝执行；密文不能做等值查询。
- **管理接口不下发密钥**：密钥类字段（含第三方平台的令牌，例如 `SysSocialVo`）在 Vo 中标注 `@JsonIgnore`，另加“是否已设置”的布尔字段（例如 `SysOssConfigVo.accessKeySet`），导出时不带出。修改接口中该字段为空表示不修改。
- **会话标识**：在线用户接口只返回令牌的 SHA-256（`sessionId`），按它定位会话。
- **先删后建的接口**要校验参数是否齐全，不能把 null 当作“清空”。例如角色权限接口要求 `menuIds`、`deptIds` 都不能为 null，空数组才表示清空。
- **Excel 行数上限**：导出最多 `tuoyu.excel.max-export-rows`（默认 50000），导入最多 `tuoyu.excel.max-import-rows`（默认 5000），超限时拒绝。导出用 `ExcelBuilder.query(() -> service.countList(bo), () -> service.queryList(bo), Vo.class)`，先统计、超限时不执行查询；统计写成 `mapper.selectPage(PageParam.countOnly(), wrapper).getTotal()`（分页插件统计时去掉 ORDER BY，不要直接 `selectCount`，带 ORDER BY 的 COUNT 在 PostgreSQL 上报错），服务方法命名为 `countList`；导入传 `MultipartFile`（`ExcelBuilder.read(file, ...)`）。

## 框架对外 API

设计见 `docs/adr/0008-框架对外API.md`。代码生成输出的后端代码就是按这些约定写的，可以作为参考。

- **响应**：Controller 返回 `ApiResult<T>`（`{ code, message, data, traceId }`，成功为 HTTP 200、`code` 0），不继承任何基类。注意 `ApiResult.ok("文字")` 中的文字是 data，只带提示时写 `ApiResult.ok("提示", null)`。Controller 不返回失败的 `ApiResult`。
- **失败**：抛 `ServiceException`，消息写清具体原因（例如“岗位编码已存在”，而不是“操作失败”）。全局异常处理按类别设置 HTTP 状态码：参数校验与业务规则 400、未登录或令牌失效 401、无权限 403、不存在 404、数据重复 409、请求过大 413、重复提交与限流 429、未预期的异常 500（只返回错误编号）。`ServiceException` 带的错误码在 400 ~ 599 之间时作为 HTTP 状态，否则为 400。401 只用于令牌缺失或失效，登录失败（密码错误、验证码错误、账号锁定等）是 400。模块自己的 `@RestControllerAdvice` 用 `ApiErrors` 生成响应，并标 `@Order(ExceptionHandlerOrder.MODULE)`，保证排在全局异常处理之前；提示中不要带异常的原始消息。
- **写操作**：服务方法返回 `void`，失败时抛带原因的 `ServiceException`（例如按 ID 更新影响 0 行时“…不存在”）；不要用 boolean / int 表示成败，Controller 直接返回 `ApiResult.ok()`。
- **分页**：Controller 参数用 `PageParam`（请求参数 `page` 从 1 开始、`pageSize`、可重复的 `sort=属性名,asc|desc`）。单表查询用 `pageParam.toPage(实体.class)`，排序属性按实体映射为列名，不在实体中的返回 400；联表 SQL 用 `toPage(实体.class, "表别名")`；结果不是实体的用 `toPage()`（不支持排序）或 `toPage(Map)` 显式列出可排序的列。可排序的属性不含敏感字段（`select = false`、实体或对应 Vo 中标注 `@JsonIgnore`、`@EncryptField`、`@Sensitive` 的字段，以及名称含 password、secret、token、accessKey 等口令密钥类的字段），否则可借排序结果推出看不到的值；需要更严格时（例如只要求登录的接口）用 `toPage(Map)` 显式白名单。默认排序只在没有传 `sort` 时加（`if (!pageParam.hasSort())`），否则会排在用户的排序前面。结果为 `PageResult<T>`（`items`、`total`），从 MyBatis-Plus 分页转换用 `PageResults.of(page, Vo.class)`。
- **当前用户**：业务代码只用 core 的 `UserContext`（`current()`、`require()`、`userId()`、`isSuperAdmin()` 等）。`LoginHelper` 只在 `tuoyu-common-satoken` 与 `tuoyu-auth` 中处理登录、登出与会话，其他模块不能引用（ArchUnit 检查）；查询其他会话的登录用户用 `LoginSessions`。单元测试中用 `UserContext.setProvider(...)` 替换当前用户，结束时恢复原来的提供者。
- **Mapper 与转换**：Mapper 直接继承 MyBatis-Plus 的 `BaseMapper<实体>`，只处理实体；转换为 Vo 在服务层完成（`BeanMapper.map` / `mapList`，空值安全）。条件用 `QueryBuilder.lambda(实体.class)` 构造后调用 Mapper；批量写入用 `BaseMapper` 自带的 `insert(Collection)`、`updateById(Collection)`。手写 SQL 中返回 Vo 的查询可以保留在 Mapper 中。
- **数据权限**：`@DataPermission` 标在 Mapper 的方法上，只对调用这个方法时执行的 SQL 生效（由 Spring 代理在调用时设置）。MyBatis-Plus `BaseMapper` 的默认方法会在 MyBatis 代理内部转调其他方法，不经过 Spring 代理：例如 `update(Wrapper)` 转调 `update(实体, Wrapper)`，只给后者加注解时前者不受约束。所以带 `@DataPermission` 的 Mapper 要把业务会调用的默认方法也覆盖并加上注解（参考 `SysUserMapper` 的两个 `update`），并用测试确认数据权限生效（参考 `SysUserMapperDataPermissionTest`）。
- **注解**：校验分组 `OnCreate` / `OnUpdate`（不继承 `Default`，没有分组的约束在指定分组时不执行）；操作日志 `@OperationLog(module = "岗位管理", action = AuditAction.CREATE)`（`AuditAction` 的存储值与操作日志表、前端字典一致）；防重复提交 `@PreventResubmit`（重复时 429），只加在写接口上。
- 请求头名等框架约定的常量在 core 的 `HeaderNames` 中，不要另写字符串。

## 跨模块调用

设计见 `docs/adr/0005-RPC抽象与两种部署方式.md`。

- 跨模块调用只能通过 `tuoyu-api-*` 中标注 `@RemoteApi` 的接口。调用方用普通的构造器注入，不加注解：容器中有实现时注入本地调用代理，否则在微服务中注入 Dubbo 引用；单体中缺少实现时拒绝启动。
- 提供方是普通的 `@Service`，由 `tuoyu-common-dubbo` 统一导出。业务模块与 api 中不能出现 Dubbo（`@DubboReference`、`@DubboService`、`@EnableDubbo`、mock、stub），ArchUnit 检查。
- 语义：跨模块调用不参与调用方事务；异常原样到达调用方（Dubbo 中框架异常连同错误码传回，调用方没有的异常只传回错误编号，不带原消息）；参数校验由提供方负责（实现类 `@Validated`、接口参数 `@Valid`）；参数与返回值按值语义使用，不修改对方的对象。
- 结果可有可无的调用（例如推送提示消息、翻译文件地址）用 `RemoteFallback.call` / `run` 降级，名称写成 `提供方模块.用途`。权限、租户、登录、写入数据等调用不能降级。Dubbo 超时为 `TUOYU_DUBBO_TIMEOUT`（默认 3000 毫秒），远程引用固定不重试。
- 当前用户、权限要求、租户分别来自 core 中的 `UserContext`（实现方提供 `UserContextProvider`）、`PermissionContext`、`TenantContext`。需要跨线程、跨服务传递的上下文实现 `ContextPropagator`（只传字符串），`@Async` 与 `ThreadUtils` 的虚拟线程会自动恢复。
- `tuoyu-common-mybatis` 只依赖 core；数据权限 SpEL 中的 `#user` 是 `CurrentUser`。
- 网关与各服务共用 `RequestAuthenticator` 校验登录；免登录白名单只在 `application-common.yml` 的 `security.ignore.whites` 中维护（写完整路径）。只有 same-token 校验开启（请求来自网关）时才采信 `X-Forwarded-Prefix`；单体不校验 same-token，也不采信这个头。
- **接口权限**：每个 Controller 接口必须带 `@SaCheckPermission`、`@SaCheckRole`、`@SaCheckLogin`、`@SaIgnore` 之一（`ControllerAuthAnnotationTest`）。只要求登录的接口标 `@SaCheckLogin` 并在注释中写明原因；免登录接口标 `@SaIgnore`，同时在 `security.ignore.whites` 中写完整路径；`@SaCheckPermission` 的权限码必须在菜单种子数据中，缺少时写迁移脚本补充按钮菜单。
- Warm-Flow 自带的接口（`/warm-flow/**`）没有我们的注解，权限由 `WarmFlowAccessInterceptor` 按路径控制；升级 Warm-Flow 新增接口时要在那里登记规则。
- 流程实例的审批记录、流程图只对流程参与人（发起人、办理人、抄送人）与流程管理员开放（`IFlwInstanceService.checkInstanceReadAccess`）。办理时选择用户用 `/system/user/options`（只要求登录，不含联系方式），不要用用户管理的列表接口。

## 日志追踪与优雅停机

设计见 `docs/adr/0007-日志追踪与优雅停机.md`。

- `tuoyu-common-observability` 负责 traceId（W3C `traceparent`，MDC 键 `traceId`，响应头 `X-Trace-Id`）。跨线程、跨服务透传走 `TracePropagator`，不要自己读写 MDC 中的 traceId。
- 日志格式只在 `logback-pattern.xml` 中定义，各服务（含单体）的 logback 配置最前面 `<include resource="logback-pattern.xml"/>`，不要再自行定义 pattern。
- 停机顺序：先下线（健康检查 503、从 Nacos 注销）→ 等待 `TUOYU_SHUTDOWN_OFFLINE_DELAY`（默认 5 秒）→ 排空请求（`TUOYU_SHUTDOWN_TIMEOUT`，默认 30 秒）→ Dubbo（`TUOYU_DUBBO_SHUTDOWN_WAIT`）→ 其他组件。容器的停止宽限期至少约 55 秒（compose 为 60 秒）。网关负载均衡缓存的有效期必须小于下线等待（`ProductionConfigTest` 检查）。

## 多租户

设计见 `docs/adr/0006-多租户.md`。默认关闭（`TUOYU_TENANT_ENABLED`），开启时必须配置 `TUOYU_TENANT_CONTEXT_SECRET`。

- 表中有 `tenant_id` 的数据按租户隔离，不需要维护表清单。新增需要隔离的表时，表加 `tenant_id varchar(20) not null default 'default'`（迁移脚本），实体继承 `TenantEntity`。插入时框架改写为当前租户，按实体更新不会改动租户；不要手工设置 `tenantId`，也不要用 `lambdaUpdate().set(tenantId, ...)` 这类按 Wrapper 写的更新改租户。
- 平台共享的表（菜单、字典、参数、客户端、存储配置、代码生成、租户与套餐）没有 `tenant_id`。
- 缺少租户时拒绝执行。后台任务、异步代码要明确租户：定时任务用 `@TenantJob`，代码块用 `TenantHelper.dynamic(tenantId, ...)`；确实要跨租户时用 `TenantHelper.ignore(...)` 或 `@TenantIgnore`。
- `scheduledExecutorService` 不透传上下文：提交前取出当前租户，任务中用 `TenantHelper.dynamic` 指定。
- 跨线程透传（`@Async`、`ThreadUtils`）只在本进程内传递、不设有效期；跨服务透传签名并带有效期（`tuoyu.tenant.context-ttl`，默认 60 秒）。
- 用户名在租户内唯一；租户内只能使用套餐中的菜单（读取时取交集，角色授权时服务端校验）。
- 越权测试 `TenantIsolationIntegrationTest`（Testcontainers）在 CI 中必须执行。

## 目录结构

| 目录 | 内容 |
|---|---|
| `tuoyu-framework` | 框架公共组件，子模块的 artifactId 为 `tuoyu-common-*` |
| `tuoyu-api` | 服务间接口契约 |
| `tuoyu-gateway` | 网关 |
| `tuoyu-modules` | 业务模块：auth、system、resource、workflow、gen、job |
| `tuoyu-visual` | 监控与调度控制台 |
| `tuoyu-boot` | 启动器（单体 `tuoyu-boot-all`） |
| `tuoyu-e2e` | 端到端测试 |
| `tuoyu-extensions` | 可选扩展 |
| `tuoyu-architecture` | 依赖规则检查 |
| `deploy` | SQL、Nacos 配置、部署脚本 |
| `tools` | 工具脚本 |
| `docs` | 设计记录（ADR）与升级指南 |

## 前端

前端基于官方 Vben Admin 5 + Ant Design Vue，只使用框架层，业务页面全部原创，接口契约以本仓库的后端代码为准。代码生成器只输出 Vben 风格的页面，目录与前端仓库 `apps/web-antd/src/` 一一对应。菜单种子数据中的 `component` 填前端 `views` 下的页面路径，图标用 Iconify 名称（例如 `lucide:user`）。

## 命名

新写的类按作用命名，不加 `Plus`、`Tuoyu` 之类的前缀（包名 `org.tuoyu` 已经说明归属），也不要与依赖库中的类同名。确实无法避免、且不会同时使用的重名，登记在 ADR 0008 第 9 节。
