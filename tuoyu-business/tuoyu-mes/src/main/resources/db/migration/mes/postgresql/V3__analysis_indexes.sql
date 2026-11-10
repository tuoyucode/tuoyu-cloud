-- 分析报表的查询索引：各报表按租户与时间范围读取报工、安灯事件、不良记录与工单

create index if not exists idx_production_report_tenant_report_time on mes_production_report (tenant_id, report_time);
create index if not exists idx_andon_event_tenant_report_time on mes_andon_event (tenant_id, report_time);
create index if not exists idx_defect_record_tenant_create_time on mes_defect_record (tenant_id, create_time);
create index if not exists idx_workorder_tenant_plan_start_time on mes_workorder (tenant_id, plan_start_time);
