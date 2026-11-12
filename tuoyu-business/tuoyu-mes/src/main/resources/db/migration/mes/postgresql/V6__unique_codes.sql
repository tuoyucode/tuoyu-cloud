-- 用户填写的编码在租户内唯一、不区分大小写，由唯一索引保证（服务层的校验在并发时挡不住重复）
-- 只约束未删除（del_flag = '0'）的行：删除后编码可以重新使用。库位编码在同一仓库内唯一
--
-- 注意：已有库中存在重复的编码（同一租户内只有大小写不同的也算重复）时，本迁移会失败、服务拒绝启动。
-- 升级前先执行 tuoyu-business/tuoyu-mes/deploy/upgrade/postgresql/check-duplicate-codes.sql 找出重复的编码并处理，
-- 说明见 tuoyu-business/tuoyu-mes/deploy/upgrade/README.md
--
-- 部分索引 (tenant_id, lower(编码)) where del_flag = '0'：按 lower(编码) 且未删除的查询可以用上它

-- 工厂
create unique index if not exists uk_factory_code on mes_factory (tenant_id, lower(factory_code)) where del_flag = '0';
-- 车间
create unique index if not exists uk_workshop_code on mes_workshop (tenant_id, lower(workshop_code)) where del_flag = '0';
-- 产线
create unique index if not exists uk_line_code on mes_line (tenant_id, lower(line_code)) where del_flag = '0';
-- 工位
create unique index if not exists uk_station_code on mes_station (tenant_id, lower(station_code)) where del_flag = '0';
-- 班组
create unique index if not exists uk_team_code on mes_team (tenant_id, lower(team_code)) where del_flag = '0';
-- 物料
create unique index if not exists uk_material_code on mes_material (tenant_id, lower(material_code)) where del_flag = '0';
-- 物料清单
create unique index if not exists uk_bom_code on mes_bom (tenant_id, lower(bom_code)) where del_flag = '0';
-- 工艺路线
create unique index if not exists uk_route_code on mes_route (tenant_id, lower(route_code)) where del_flag = '0';
-- 设备
create unique index if not exists uk_device_code on mes_device (tenant_id, lower(device_code)) where del_flag = '0';
-- 仓库
create unique index if not exists uk_warehouse_code on mes_warehouse (tenant_id, lower(warehouse_code)) where del_flag = '0';
-- 库位（同一仓库内）
create unique index if not exists uk_location_code on mes_location (tenant_id, warehouse_id, lower(location_code)) where del_flag = '0';
-- 停机原因
create unique index if not exists uk_downtime_reason_code on mes_downtime_reason (tenant_id, lower(reason_code)) where del_flag = '0';
-- 检验方案
create unique index if not exists uk_inspection_scheme_code on mes_inspection_scheme (tenant_id, lower(scheme_code)) where del_flag = '0';
-- 编码规则
create unique index if not exists uk_code_rule_code on mes_code_rule (tenant_id, lower(rule_code)) where del_flag = '0';
