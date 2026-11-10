# 业务模块

本目录放基于 TuoYu-Cloud 框架构建的具体业务系统，只在 `business` 分支上（框架与业务分支的规则见仓库根目录 `CONTRIBUTING.md` 的“分支”一节）。业务模块默认不参与构建，由 profile `business` 启用，默认镜像也不包含它们。

| 模块 | 内容 | 端口 | 网关前缀 | 库 |
|---|---|---|---|---|
| `tuoyu-mes` | 制造执行（MES） | 9301 | `/mes` | `tuoyu-mes` |

## 构建与运行

```bash
mvn clean verify -P business      # 构建并测试框架与业务模块
```

- **微服务**：`tuoyu-mes` 与其他服务一样单独启动（`target/tuoyu-mes-exec.jar`，IDEA 启动配置 `.run/tuoyu-mes.run.xml`）。Nacos 中导入 `deploy/config/nacos/tuoyu-mes.yml`，网关已有 `/mes/**` 路由（模块未部署时返回 503）。docker 部署见 `deploy/docker/README.md`（`--profile business`）。
- **单体**：用 `-P business` 构建 `tuoyu-boot-all` 即可带上 MES，不需要改单体的配置与启动类：模块在 `META-INF/tuoyu/monolith-module.yml` 中声明了 URL 前缀、包与数据源，并把 `tuoyu-mes` 库的迁移加入单体。
- **数据库**：`tuoyu-mes` 库由模块自己的 Flyway 迁移（`src/main/resources/db/migration/mes/{mysql,postgresql}`），支持 MySQL 与 PostgreSQL；`deploy/sql` 下是与 V1 基线相同的全新安装脚本。
- **菜单**：模块在 `META-INF/tuoyu/menus/mes.json` 中声明菜单与权限码，启动后登记到系统的菜单表，只写入不存在的菜单。新菜单只有超级管理员默认可见，其他角色在“角色管理”中授权，开启多租户时还要在租户套餐中勾选。
- **前端**：页面在前端仓库的 `business` 分支（`apps/web-antd/src/views/mes`，接口在 `packages/shared/src/api/mes`）。

## 制造执行（MES）

### 功能

| 菜单 | 内容 |
|---|---|
| 主数据 | 工厂、车间、产线、工位、班组、物料、物料清单、工艺路线、设备、仓库与库位；物料、物料清单、工艺路线、设备支持 Excel 导入 |
| 生产计划 | 生产工单（下达、暂停、恢复、完工、关闭、取消）、工序任务（派工） |
| 生产执行 | 报工工作台、报工记录（确认、撤销） |
| 物料协同 | 领料、补料、退料，完工入库 |
| 质量管理 | 检验方案、首检 / 巡检 / 完工检、不良记录与处置 |
| 条码与追溯 | 编码规则、产品序列号、按序列号 / 批次 / 工单追溯、标签模板与打印 |
| 安灯与异常 | 安灯事件（派单、处理、解决、关闭）、超时升级规则、停机原因 |
| 设备管理 | 点检项目与点检记录（异常可一键发起安灯）、保养计划与保养工单、OEE |
| 分析报表 | 生产进度、在制品、良率与直通率、不良柏拉图、停机分析、工时、班组绩效 |
| 生产看板 | 当天的产量、良率、在制工单、未解决的安灯 |

### 约定

- **状态**：核心单据的状态用枚举定义，每个动作先在服务端按当前状态校验（`StateGuard`），再用带原状态条件的更新执行，并发或重复操作只有一次成功；每次状态变化写入状态履历（`mes_status_log`）。
- **数量**：报工、入库、退料等数量的累加与扣减在数据库中按条件执行，不先读出再写回；同一工单上需要先汇总再判断上限的操作先锁工单行（`select ... for update`），加锁顺序固定为报工记录 → 工单 → 工序任务 → 入库单。
- **数据权限**：工单、工序任务、报工、领退料、完工入库、检验、不良、安灯、点检记录、保养工单按创建部门与创建者控制数据权限。Mapper 中带 `InScope` 后缀的方法带数据权限，供列表、导出、下拉、详情与状态动作使用；`BaseMapper` 自带的方法不带数据权限，供单据之间的内部读取与按条件的写入使用。主数据是租户内共享的基础数据，不加数据权限。
- **多租户**：全部表都按租户隔离（`MesTenantConventionTest` 检查表结构与实体，`MesTenantIsolationIntegrationTest` 在真实 MySQL 上做越权测试，需要 Docker，在 CI 中执行）。后台任务（安灯超时检查、保养工单到期生成）先在忽略租户的条件下只查出主键与租户，再逐个在所属租户中处理。

### 配置

都有默认值，一般不需要配置。微服务写在 Nacos 的 `tuoyu-mes.yml` 中，单体写在 `tuoyu-boot-all.yml` 中（单体不读取 `tuoyu-mes.yml`）。

| 配置 | 默认值 | 说明 |
|---|---|---|
| `tuoyu.mes.approval.defect-disposition.enabled` / `flow-code` | `false` / `mes_defect_disposition` | 不良处置评审：处置方式为让步接收、报废时，审批通过后才执行处置 |
| `tuoyu.mes.approval.supplement.enabled` / `flow-code` | `false` / `mes_material_supplement` | 补料申请：审批通过后补料单才变为已提交 |
| `tuoyu.mes.andon.escalation.enabled` / `interval` / `initial-delay` / `batch-size` | `true` / `1m` / `30s` / `200` | 安灯超时检查：按“安灯升级规则”升级并提醒，多个实例同时执行也不会重复升级 |
| `tuoyu.mes.maintenance.generate.enabled` / `interval` / `initial-delay` | `true` / `1h` / `2m` | 按保养计划生成到期的保养工单，同一计划的同一到期日期只生成一张 |

### 单据审批

不良处置评审与补料申请可以接入工作流审批，默认关闭。开启前先导入并发布 `tuoyu-mes/deploy/workflow` 下的流程定义，步骤见该目录的 `README.md`。审批结果经流程事件回到 MES：单体中是进程内事件，微服务中经消息总线（`tuoyu-common-bus`）投递。在流程页面中打开审批时，“业务详情”页签显示单据内容（`/mes/defect/approval/{businessId}`、`/mes/material-request/approval/{businessId}`），由工作流按流程参与关系校验，不要求单据的查询权限。

### 演示数据

`tuoyu-mes/deploy/demo` 下有一套可选的演示数据（MySQL、PostgreSQL 各一份），写入默认租户，覆盖主数据、各状态的工单与单据、最近 30 天的报工、安灯、点检与保养，打开分析报表、看板、OEE 就有数据。在 Flyway 迁移完成后的 `tuoyu-mes` 库上执行，可以重复执行；用途、号段与清除方法见该目录的 `README.md`。不要在生产库上执行。

### 测试

- 单元测试：服务层用模拟的 Mapper，不连数据库（`MesTestSupport`）；数据权限测试按 MyBatis 与 Spring 代理组装，确认 SQL 带上了数据权限条件。
- 越权测试：见上文“多租户”。新增的手写 SQL（注解 SQL 的 Mapper 方法、条件构造器中的 SQL 片段）要在 `MesTenantIsolationIntegrationTest` 中登记并写越权用例，`MesTenantConventionTest` 会检查是否遗漏。
- 端到端测试：`tuoyu-e2e` 的 `MesFlowIT`（`-Dtuoyu.e2e.mes=true`），走通主数据 → 工单 → 领料 → 报工 → 检验 → 入库 → 追溯 → 安灯。

变更记录见同目录的 `CHANGELOG.md`。
