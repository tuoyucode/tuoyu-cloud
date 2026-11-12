# 制造执行（MES）升级时手工执行的脚本

表结构的变化都由 Flyway 迁移，随服务启动执行。这里放的是 Flyway 不能代办、需要在升级时手工处理的事项，MySQL、PostgreSQL 各一份，内容一一对应。

## 编码唯一（迁移 V6）

V6 给用户填写编码的 14 张表（工厂、车间、产线、工位、班组、物料、物料清单、工艺路线、设备、仓库、库位、停机原因、检验方案、编码规则）加了唯一索引：同一租户内编码不重复，**不区分大小写**（`M001` 与 `m001` 算重复）；库位编码在同一仓库内不重复；已删除的行不参与。

已有库中存在重复的编码时，V6 执行失败、服务拒绝启动。升级前先在 `tuoyu-mes` 库上执行检查脚本：

```bash
# MySQL
mysql -u root -p tuoyu-mes < tuoyu-business/tuoyu-mes/deploy/upgrade/mysql/check-duplicate-codes.sql

# PostgreSQL
psql -U postgres -d tuoyu-mes -f tuoyu-business/tuoyu-mes/deploy/upgrade/postgresql/check-duplicate-codes.sql
```

- 脚本只读；每行结果是一组重复的编码，列出表、租户、仓库（只对库位有值）、重复的行数、原始编码与主键。
- 没有结果时可以直接升级。有结果时在界面中修改其中一行的编码，或删除多余的行（被单据引用、不能删除的改编码），处理完再执行一次确认没有结果，然后启动新版本。
- MySQL 需要 8.0.13 及以上（V6 使用函数索引）。

## 按钮改名

菜单声明（`META-INF/tuoyu/menus/mes.json`）中有 4 个按钮的名称改为与界面一致：

| 菜单 ID | 原名称 | 新名称 |
|---|---|---|
| 1762000000000101203 | 工序任务完工 | 工序任务完成工序 |
| 1762000000000101404 | 报工记录取消 | 报工记录撤销报工 |
| 1762000000000101506 | 领退料完工 | 领退料确认完成 |
| 1762000000000101603 | 完工入库完工 | 完工入库确认入库 |

服务启动时只登记不存在的菜单、不覆盖已有的，已有库中的这几个按钮要手工改名。在 `tuoyu-cloud` 库上执行：

```bash
# MySQL
mysql -u root -p tuoyu-cloud < tuoyu-business/tuoyu-mes/deploy/upgrade/mysql/rename-menu-buttons.sql

# PostgreSQL
psql -U postgres -d tuoyu-cloud -f tuoyu-business/tuoyu-mes/deploy/upgrade/postgresql/rename-menu-buttons.sql
```

只改仍是原名称的行（在菜单管理中改过名的不动），可以重复执行。不执行也不影响使用，只是菜单管理与角色授权中显示的是原名称。
