# 制造执行（MES）演示数据

一套可选的演示数据，让刚装好的 MES 打开就有内容可看：主数据、各种状态的工单、最近 30 天的报工与不良、检验、领退料与入库、序列号、安灯、设备点检与保养、OEE 配置，以及各单据的状态履历。

- 不属于 Flyway 迁移，不随服务启动执行，需要时手工执行；
- 只写 `tuoyu-mes` 库，只写默认租户（`tenant_id = 'default'`），不改动 `tuoyu-cloud` 库（用户、角色、菜单都不动），也不带可登录的账号；
- 用户相关的字段（创建者、作业人、报工人、处理人、班组长与班组成员等）都是超级管理员 admin（用户 `1761100000000000001`，创建部门 `1761000000000000103`），用 admin 登录即可看到全部数据；
- 不写编码规则与编码流水号，不影响以后新建单据的编号；演示单据用固定编号 `DEMO-WO-0001`、`DEMO-RP-0001` 等。

| 文件 | 数据库 |
|---|---|
| `mysql/mes-demo-data.sql` | MySQL 8 |
| `postgresql/mes-demo-data.sql` | PostgreSQL |

两份脚本的内容一一对应，只在方言上不同（时间运算；MySQL 先用会话变量做判断、再插入）。修改时两份同步修改。

## 执行

前提：`tuoyu-mes` 库已由 Flyway 迁移到最新版本（启动过一次 `tuoyu-mes` 服务或单体即可）。

```bash
# MySQL
mysql -u root -p tuoyu-mes < tuoyu-business/tuoyu-mes/deploy/demo/mysql/mes-demo-data.sql

# PostgreSQL
psql -U postgres -d tuoyu-mes -f tuoyu-business/tuoyu-mes/deploy/demo/postgresql/mes-demo-data.sql
```

- 脚本在一个事务中执行，中途出错时整体回滚；脚本开头设置了客户端编码（`set names utf8mb4` / `set client_encoding = 'UTF8'`）。
- 时间都相对执行时刻：过去的业务时间写成“今天减若干天的某个钟点”（不晚于 20:00）或“当前时间减若干分钟”（不超过 4 小时），计划时间可以是将来。分析报表默认的最近 30 天、生产看板的今天、OEE 页面都有数据。隔一段时间后重新执行一次，数据就回到“最近”。
- 开启多租户时，只有默认租户能看到这些数据。

## 包含的数据

**主数据**：1 个工厂；装配车间、机加车间；装配一线、机加一线；8 个工位；装配甲班、机加乙班（班组长与成员都是 admin）；9 种物料：成品智能控制器 A100（按序列号追溯）、成品电源模块 PM200（按批次）、半成品铝合金外壳 H10（按批次）、原材料与辅料（按批次或不追溯）；3 个物料清单（A100 含半成品 H10；H10 的基准数量为 10）；3 条工艺路线（3 ~ 4 道工序，带单件标准工时，首道或车削工序要求首检）；5 台设备；原材料仓、成品仓与 5 个库位；5 个停机原因。

**配置**：6 个检验方案（首检、巡检、完工检）；2 条安灯升级规则（紧急事件 15 分钟响应、120 分钟解决，超时升为停线；其他事件 30 分钟响应、480 分钟解决，超时升为紧急）；序列号、批次标签模板各一个；6 个点检项目（判定型、数值型，按设备或设备类型适用）；4 个保养计划；4 条 OEE 配置（两条产线每日 480 分钟、按工序标准工时计算性能；数控车床每日 450 分钟、节拍 2.2 分钟；锁螺丝机只配节拍、计划时间取产线）。

**工单**（12 个）：

| 编号 | 产品 | 状态 | 计划 / 完工 / 不良 / 已入库 | 说明 |
|---|---|---|---|---|
| DEMO-WO-0001 | A100 | 已关闭 | 120 / 115 / 5 / 115 | 有不良，手工完工后关闭；有退料 |
| DEMO-WO-0002 | PM200 | 已完工 | 200 / 200 / 0 / 150 | 来自外部系统；末道工序达到计划数自动完工；另有 50 待入库 |
| DEMO-WO-0003 | H10 | 已完工 | 300 / 286 / 14 / 286 | 手工完工；有退料 |
| DEMO-WO-0004 | A100 | 已完工 | 100 / 94 / 6 / 60 | 首检一次不合格、完工检不合格；一张入库单已取消；有序列号 |
| DEMO-WO-0005 | H10 | 已完工 | 150 / 150 / 0 / 150 | 自动完工；完工检待检验 |
| DEMO-WO-0006 | PM200 | 已暂停 | 150 / 39 / 2 / 0 | 变压器缺料暂停；领料实发不足，补料单为草稿 |
| DEMO-WO-0007 | A100 | 已取消 | 50 / 0 / 0 / 0 | 下达后取消，工序任务已关闭 |
| DEMO-WO-0008 | A100 | 生产中 | 80 / 39 / 2 / 20 | 含今天的报工、一条已撤销的报工；有序列号 |
| DEMO-WO-0009 | H10 | 生产中 | 250 / 115 / 8 / 0 | 含今天的报工；巡检不合格；手工登记的不良 |
| DEMO-WO-0010 | A100 | 已下达 | 60 / 0 / 0 / 0 | 首道工序已派工、首检待检验，领料单待仓库发料 |
| DEMO-WO-0011 | PM200 | 草稿 | 100 | 计划在 2 天后开始 |
| DEMO-WO-0012 | H10 | 草稿 | 200 | 计划在 3 天后开始 |

**生产与质量**：已下达以后的工单都有工序任务与批次号；110 条报工分布在最近 30 天、8 个工位、两个班组，带实际工时（3 天以前的已确认）；34 条不良记录（报工来源 30 条、检验来源 3 条、手工登记 1 条，5 天以前的已处置，处置方式覆盖返工、报废、让步接收）；17 张检验单（首检、巡检、完工检，有合格、不合格与待检验）。

**物料**：12 张领退料单（按物料清单生成的领料单 9 张，其中 8 张已完成、1 张已提交；退料 2 张；补料草稿 1 张），按批次追溯的物料明细带物料批次号，可在追溯页按批次号查询；9 张完工入库单（已入库、已提交、已取消）；16 个序列号（已完工、已生成、已报废）。

**安灯**：11 个事件，覆盖 5 种异常类型、3 种紧急程度与全部状态。6 个已解决的事件带停机原因与停机时长（其中一个为停线级别），用于停机分析与 OEE；3 个未解决的事件发生在最近 1 小时内，其中一个由点检异常发起。

**设备**：5 条点检记录（含一条异常并已发起安灯、一条已作废）；4 个保养计划与 6 张保养工单（待保养、保养中、已完成、已取消；数控车床 2 天后到期、工单已提前生成，测试台已过期未保养）。

**状态履历**：574 条，与上面各单据的状态变化一一对应（工单的创建、下达、开工、暂停、完工、关闭、取消，工序任务的派工与开工，报工的确认与撤销，检验、处置、入库、安灯、点检、保养等），详情页的履历都不是空的。

几点说明：

- 安灯升级规则按“异常类型 + 紧急程度”组合唯一。已有同样组合的规则时，脚本跳过对应的演示规则；标签模板在该对象已有默认模板时不设为默认。
- 已解决、已关闭的安灯事件按升级规则写好了截止时间，都在时限内处理完；未解决的事件截止时间待计算，与界面上报的事件一样由安灯超时检查任务按当前的规则计算，之后可能按规则超时升级，并给 admin 推送提醒。
- 保养计划的“下次到期日期”“生成日期”与保养工单相互对应：保养定时任务不会为已生成工单的周期重复生成。

## 号段与清除

全部演示数据的主键在 `1769000000000000000` ~ `1769000000009999999` 内，每张表一段（脚本开头列出了全部号段），例如：

| 表 | 号段 |
|---|---|
| mes_workorder | 1769000000002200000 ~ 1769000000002299999 |
| mes_operation_task | 1769000000002300000 ~ 1769000000002399999 |
| mes_production_report | 1769000000002400000 ~ 1769000000002499999 |
| mes_defect_record | 1769000000002600000 ~ 1769000000002699999 |
| mes_stock_in | 1769000000002900000 ~ 1769000000002999999 |
| mes_andon_event | 1769000000003100000 ~ 1769000000003199999 |
| mes_status_log | 1769000000003500000 ~ 1769000000003599999 |

脚本开头的“清除”一节按号段删除（先子表后主表），所以重复执行时先清掉上次的演示数据，不会删到号段以外的数据。

在演示数据上操作产生的业务数据也一并删除，否则重新写入后数量对不上：演示工单下的工序任务、报工、检验、不良、领退料、入库与序列号，演示设备的点检项目与点检记录，演示保养计划的保养工单，演示产线与设备的 OEE 配置，以及这些单据的状态履历。在演示主数据（物料、工艺路线等）上新建的工单不会删除。

只清除、不重新写入：执行脚本中“清除”一节的 `delete` 语句（到第一条 `insert` 之前），再提交。

## 执行后的检查

下面的查询都应当返回 0 行（MySQL 与 PostgreSQL 通用，最后一条分方言）。

```sql
-- 工单完工数 = 末道工序的合格数
select w.workorder_code, w.completed_qty, t.qualified_qty
  from mes_workorder w
  join mes_operation_task t on t.workorder_id = w.workorder_id
 where w.workorder_id between 1769000000002200000 and 1769000000002299999
   and t.seq_no = (select max(x.seq_no) from mes_operation_task x where x.workorder_id = w.workorder_id)
   and w.completed_qty <> t.qualified_qty;

-- 工序任务的合格数、不良数 = 未撤销报工的合计
select t.task_code, t.qualified_qty, t.defect_qty
  from mes_operation_task t
  left join mes_production_report r on r.task_id = t.task_id and r.report_status <> 'cancelled'
 where t.task_id between 1769000000002300000 and 1769000000002399999
 group by t.task_id, t.task_code, t.qualified_qty, t.defect_qty
having t.qualified_qty <> coalesce(sum(r.qualified_qty), 0) or t.defect_qty <> coalesce(sum(r.defect_qty), 0);

-- 工单不良数 = 各工序不良数之和
select w.workorder_code, w.defect_qty
  from mes_workorder w
  left join mes_operation_task t on t.workorder_id = w.workorder_id
 where w.workorder_id between 1769000000002200000 and 1769000000002299999
 group by w.workorder_id, w.workorder_code, w.defect_qty
having w.defect_qty <> coalesce(sum(t.defect_qty), 0);

-- 已入库数 = 已入库的入库单合计，已提交与已入库的合计不超过完工数
select w.workorder_code, w.stocked_qty, w.completed_qty
  from mes_workorder w
  left join mes_stock_in s on s.workorder_id = w.workorder_id and s.stock_in_status <> 'cancelled'
 where w.workorder_id between 1769000000002200000 and 1769000000002299999
 group by w.workorder_id, w.workorder_code, w.stocked_qty, w.completed_qty
having w.stocked_qty <> coalesce(sum(case when s.stock_in_status = 'completed' then s.qty else 0 end), 0)
    or coalesce(sum(s.qty), 0) > w.completed_qty;

-- 报工数 = 合格数 + 不良数；有不良的报工都有一条报工来源、数量相同的不良记录
select r.report_code
  from mes_production_report r
  left join mes_defect_record d on d.source_type = 'report' and d.source_id = r.report_id
 where r.report_id between 1769000000002400000 and 1769000000002499999
   and (r.report_qty <> r.qualified_qty + r.defect_qty
        or (r.defect_qty > 0 and r.report_status <> 'cancelled' and (d.defect_id is null or d.defect_qty <> r.defect_qty)));

-- 后道工序的报工数不超过前道工序的合格数
select t.task_code
  from mes_operation_task t
  join mes_operation_task p on p.workorder_id = t.workorder_id
   and p.seq_no = (select max(x.seq_no) from mes_operation_task x where x.workorder_id = t.workorder_id and x.seq_no < t.seq_no)
 where t.task_id between 1769000000002300000 and 1769000000002399999
   and t.qualified_qty + t.defect_qty > p.qualified_qty;

-- 停线或有停机原因的已解决事件：停机时长 = 解决时间 − 上报时间（分钟）
-- MySQL
select event_code from mes_andon_event
 where event_id between 1769000000003100000 and 1769000000003199999 and resolve_time is not null
   and (event_level = 'critical' or downtime_reason_id is not null)
   and downtime_minutes <> timestampdiff(minute, report_time, resolve_time);
-- PostgreSQL
select event_code from mes_andon_event
 where event_id between 1769000000003100000 and 1769000000003199999 and resolve_time is not null
   and (event_level = 'critical' or downtime_reason_id is not null)
   and downtime_minutes <> extract(epoch from resolve_time - report_time)::int / 60;
```

行数核对（应为 12、38、110、34、11、574）：

```sql
select 'workorder', count(*) from mes_workorder where workorder_id between 1769000000002200000 and 1769000000002299999
union all select 'task', count(*) from mes_operation_task where task_id between 1769000000002300000 and 1769000000002399999
union all select 'report', count(*) from mes_production_report where report_id between 1769000000002400000 and 1769000000002499999
union all select 'defect', count(*) from mes_defect_record where defect_id between 1769000000002600000 and 1769000000002699999
union all select 'andon', count(*) from mes_andon_event where event_id between 1769000000003100000 and 1769000000003199999
union all select 'status_log', count(*) from mes_status_log where log_id between 1769000000003500000 and 1769000000003599999;
```

页面上可以看：生产看板（今天的产量、生产中的工单、未解决的安灯）、分析报表（最近 30 天的产量、直通率、不良柏拉图、停机分析）、OEE（产线 DEMO-L01 / DEMO-L02、设备 DEMO-EQ03）、追溯（输入 `DEMO-WO-0004`、批次号 `DEMO-B-0003` 或物料批次 `PCB-L2610A`、序列号 `DEMO-SN-0001`）。
