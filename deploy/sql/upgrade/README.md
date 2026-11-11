# 数据库升级脚本

`deploy/sql` 下的 `tuoyu-*.sql` 是**全新安装**用的完整脚本。已经按旧版本初始化过的数据库，按日期顺序执行本目录下的升级脚本。

## MySQL、PostgreSQL：Flyway 迁移（2026-10-28 起）

接入 Flyway 后，MySQL 与 PostgreSQL 的表结构与种子数据改动**只写成迁移脚本**，不再在本目录新增这两种数据库的升级脚本，也不再修改 `deploy/sql` 下它们的全新安装脚本：

| 库 | 负责迁移的服务 | 迁移脚本目录（`mysql` 与 `postgresql` 各一套） |
|---|---|---|
| `tuoyu-cloud` | `tuoyu-system` | `tuoyu-modules/tuoyu-system/src/main/resources/db/migration/cloud/<数据库>` |
| `tuoyu-workflow` | `tuoyu-workflow` | `tuoyu-modules/tuoyu-workflow/src/main/resources/db/migration/workflow/<数据库>` |
| `tuoyu-job` | `tuoyu-snailjob-server` | `tuoyu-visual/tuoyu-snailjob-server/src/main/resources/db/migration/job/<数据库>` |

- 命名：`V<版本>__<小写说明>.sql`，版本接着已有的最大版本连续编号，例如 `V2__add_notice_index.sql`；同一个改动在 `mysql` 与 `postgresql` 中使用相同的版本号与说明，内容按各自的方言编写；
- `V1__baseline.sql` 是基线，与 `deploy/sql` 下同一数据库的全新安装脚本逐字一致，**两者都不能修改**（已接入的数据库会校验失败）；
- 已发布的迁移脚本不能修改，需要更正时再写一个新版本；
- 服务启动时按 `TUOYU_DB_TYPE` 连接的数据库自动执行尚未执行的脚本；`TUOYU_FLYWAY_ENABLED=false` 时关闭，此时需手工按版本顺序执行；
- 建议脚本仍可重复执行（`if not exists`、`where not exists`），便于关闭 Flyway 时手工补执行。

**已有数据库首次启用 Flyway 前，必须先执行完本目录下对应数据库的全部升级脚本**（下表中的目录），使库达到 V1 基线状态；服务启动时按基线版本 1 接入，之后只执行 V2 起的脚本。接入前服务会核对 V1 中的表是否都已存在，缺少时拒绝启动并列出缺少的表；种子数据的差异无法这样核对，所以仍要先执行完升级脚本。

## Oracle：升级目录

Oracle 不在 1.0 的支持范围内，Flyway 连接 Oracle 时自动跳过迁移。需要自行维护 Oracle 的部署方按以下规则：

- 每次调整种子数据或表结构，都要同时：修改 Oracle 的全新安装脚本，并在本目录新增一个升级目录（MySQL、PostgreSQL 的改动写成上面的迁移脚本）。
- 目录命名：`<日期>-<说明>`，例如 `20261024-menu`。
- 文件命名与全新安装脚本一致，按库区分：`oracle_tuoyu_cloud.sql`、`oracle_tuoyu_workflow.sql` 等。
- 脚本应可重复执行（新增用 `where not exists`，删除表时忽略不存在的错误）。

| 目录 | 内容 |
|---|---|
| `20261024-menu` | 新增“安全中心”菜单；删除“项目官网”“测试菜单”与请假申请示例（菜单、角色授权、`test_leave` 表）；菜单图标改为 Iconify。含 MySQL、PostgreSQL 脚本（接入 Flyway 前的升级，已包含在 V1 基线中） |
| `20261024-gen` | 代码生成的前端模板默认改为 `vben`。含 MySQL、PostgreSQL 脚本（同上） |
| `20261028-menu` | 删除“批量强退”按钮（`monitor:online:batchLogout`，没有对应接口）及其角色授权。只有 Oracle 脚本，MySQL、PostgreSQL 由迁移脚本 `V2__remove_batch_logout_menu.sql` 完成 |
| `20261029-tenant` | 多租户：10 张表增加 `tenant_id`，新增租户、租户套餐、套餐菜单三张表与默认租户，新增“租户管理”“租户套餐”菜单。只有 Oracle 脚本，MySQL、PostgreSQL 由迁移脚本 `V3__tenant.sql` 完成 |
| `20261029-perms` | 菜单权限码与接口对齐：“流程设计”页面的权限码由已删除的请假示例 `workflow:leave:edit` 改为 `workflow:definition:edit`。只有 Oracle 脚本，MySQL、PostgreSQL 由迁移脚本 `V4__align_permissions.sql` 完成 |
| `20261030-menu` | 删除“流程表达式定义导出”按钮（`workflow:spel:export`，没有对应接口）及其角色授权与套餐菜单。只有 Oracle 脚本，MySQL、PostgreSQL 由迁移脚本 `V5__remove_spel_export_menu.sql` 完成 |
| `20261101-users` | 删除演示账号 `test`、`test1`（口令为公开的 666666）及其角色、岗位关联与第三方绑定，演示角色保留。只有 Oracle 脚本，MySQL、PostgreSQL 由迁移脚本 `V6__remove_demo_users.sql` 完成 |
| `20261107-gen` | 代码生成只提供 Vben 前端模板：已有业务表的前端模板类型（`gen_table.frontend_type`）统一为 `vben`。全新安装脚本中该列默认值已是 `vben`，无需修改。只有 Oracle 脚本，MySQL、PostgreSQL 由迁移脚本 `V7__gen_frontend_type_vben.sql` 完成 |
| `20261108-seed` | “流程设计”页面的显示状态与菜单状态改为 `'1'` / `'0'`（全新安装脚本中写成了 `'N'` / `'Y'`，菜单被当作停用，流程设计器无法打开）；流程表达式的按钮名称与页面名称一致；多租户新增的表与列补上注释。只有 Oracle 脚本，MySQL、PostgreSQL 由迁移脚本 `V8__align_seed_data.sql` 完成（同时清空授权类型字典中无用的样式类；PostgreSQL 同样补上多租户列的注释） |
| `20261111-gen` | 代码生成的前端模板分为 Element Plus（`ele`）与 Ant Design Vue（`antd`）两个版本：已有业务表的 `gen_table.frontend_type` 改为 `antd`（它们是按 Ant Design Vue 的模板生成的），该列默认值改为 `ele`（全新安装脚本同步修改）。只有 Oracle 脚本，MySQL、PostgreSQL 由迁移脚本 `V9__gen_frontend_type_ele.sql` 完成 |
