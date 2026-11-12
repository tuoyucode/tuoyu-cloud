-- 检查 tuoyu-mes 库中重复的编码（迁移 V6 的唯一索引要求同一租户内不重复、不区分大小写；库位在同一仓库内）
-- 只读，不修改数据。在 tuoyu-mes 库上执行，有结果时先处理（改编码或删除多余的行），再启动新版本的服务
-- 每行是一组重复：表、租户、仓库（只对库位有值）、小写后的编码、重复的行数、原始编码与主键（按主键排列）

select 'mes_factory' as table_name, tenant_id, cast(null as int8) as warehouse_id, lower(factory_code) as code_key,
       count(*) as duplicate_count, string_agg(factory_code, ',' order by factory_id) as codes, string_agg(cast(factory_id as varchar), ',' order by factory_id) as ids
from mes_factory where del_flag = '0'
group by tenant_id, lower(factory_code)
having count(*) > 1
union all
select 'mes_workshop' as table_name, tenant_id, cast(null as int8) as warehouse_id, lower(workshop_code) as code_key,
       count(*) as duplicate_count, string_agg(workshop_code, ',' order by workshop_id) as codes, string_agg(cast(workshop_id as varchar), ',' order by workshop_id) as ids
from mes_workshop where del_flag = '0'
group by tenant_id, lower(workshop_code)
having count(*) > 1
union all
select 'mes_line' as table_name, tenant_id, cast(null as int8) as warehouse_id, lower(line_code) as code_key,
       count(*) as duplicate_count, string_agg(line_code, ',' order by line_id) as codes, string_agg(cast(line_id as varchar), ',' order by line_id) as ids
from mes_line where del_flag = '0'
group by tenant_id, lower(line_code)
having count(*) > 1
union all
select 'mes_station' as table_name, tenant_id, cast(null as int8) as warehouse_id, lower(station_code) as code_key,
       count(*) as duplicate_count, string_agg(station_code, ',' order by station_id) as codes, string_agg(cast(station_id as varchar), ',' order by station_id) as ids
from mes_station where del_flag = '0'
group by tenant_id, lower(station_code)
having count(*) > 1
union all
select 'mes_team' as table_name, tenant_id, cast(null as int8) as warehouse_id, lower(team_code) as code_key,
       count(*) as duplicate_count, string_agg(team_code, ',' order by team_id) as codes, string_agg(cast(team_id as varchar), ',' order by team_id) as ids
from mes_team where del_flag = '0'
group by tenant_id, lower(team_code)
having count(*) > 1
union all
select 'mes_material' as table_name, tenant_id, cast(null as int8) as warehouse_id, lower(material_code) as code_key,
       count(*) as duplicate_count, string_agg(material_code, ',' order by material_id) as codes, string_agg(cast(material_id as varchar), ',' order by material_id) as ids
from mes_material where del_flag = '0'
group by tenant_id, lower(material_code)
having count(*) > 1
union all
select 'mes_bom' as table_name, tenant_id, cast(null as int8) as warehouse_id, lower(bom_code) as code_key,
       count(*) as duplicate_count, string_agg(bom_code, ',' order by bom_id) as codes, string_agg(cast(bom_id as varchar), ',' order by bom_id) as ids
from mes_bom where del_flag = '0'
group by tenant_id, lower(bom_code)
having count(*) > 1
union all
select 'mes_route' as table_name, tenant_id, cast(null as int8) as warehouse_id, lower(route_code) as code_key,
       count(*) as duplicate_count, string_agg(route_code, ',' order by route_id) as codes, string_agg(cast(route_id as varchar), ',' order by route_id) as ids
from mes_route where del_flag = '0'
group by tenant_id, lower(route_code)
having count(*) > 1
union all
select 'mes_device' as table_name, tenant_id, cast(null as int8) as warehouse_id, lower(device_code) as code_key,
       count(*) as duplicate_count, string_agg(device_code, ',' order by device_id) as codes, string_agg(cast(device_id as varchar), ',' order by device_id) as ids
from mes_device where del_flag = '0'
group by tenant_id, lower(device_code)
having count(*) > 1
union all
select 'mes_warehouse' as table_name, tenant_id, cast(null as int8) as warehouse_id, lower(warehouse_code) as code_key,
       count(*) as duplicate_count, string_agg(warehouse_code, ',' order by warehouse_id) as codes, string_agg(cast(warehouse_id as varchar), ',' order by warehouse_id) as ids
from mes_warehouse where del_flag = '0'
group by tenant_id, lower(warehouse_code)
having count(*) > 1
union all
select 'mes_location' as table_name, tenant_id, warehouse_id as warehouse_id, lower(location_code) as code_key,
       count(*) as duplicate_count, string_agg(location_code, ',' order by location_id) as codes, string_agg(cast(location_id as varchar), ',' order by location_id) as ids
from mes_location where del_flag = '0'
group by tenant_id, warehouse_id, lower(location_code)
having count(*) > 1
union all
select 'mes_downtime_reason' as table_name, tenant_id, cast(null as int8) as warehouse_id, lower(reason_code) as code_key,
       count(*) as duplicate_count, string_agg(reason_code, ',' order by reason_id) as codes, string_agg(cast(reason_id as varchar), ',' order by reason_id) as ids
from mes_downtime_reason where del_flag = '0'
group by tenant_id, lower(reason_code)
having count(*) > 1
union all
select 'mes_inspection_scheme' as table_name, tenant_id, cast(null as int8) as warehouse_id, lower(scheme_code) as code_key,
       count(*) as duplicate_count, string_agg(scheme_code, ',' order by scheme_id) as codes, string_agg(cast(scheme_id as varchar), ',' order by scheme_id) as ids
from mes_inspection_scheme where del_flag = '0'
group by tenant_id, lower(scheme_code)
having count(*) > 1
union all
select 'mes_code_rule' as table_name, tenant_id, cast(null as int8) as warehouse_id, lower(rule_code) as code_key,
       count(*) as duplicate_count, string_agg(rule_code, ',' order by rule_id) as codes, string_agg(cast(rule_id as varchar), ',' order by rule_id) as ids
from mes_code_rule where del_flag = '0'
group by tenant_id, lower(rule_code)
having count(*) > 1
order by table_name, tenant_id, code_key;
