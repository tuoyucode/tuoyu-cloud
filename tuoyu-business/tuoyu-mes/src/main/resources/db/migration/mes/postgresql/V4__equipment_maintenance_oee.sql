-- 设备点检、保养与 OEE：PostgreSQL

-- ----------------------------
-- 点检项目
-- ----------------------------
create table if not exists mes_check_item
(
    check_item_id        int8           not null,
    item_name            varchar(128)   not null,
    device_id            int8           null,
    device_type          varchar(32)    null,
    check_method         varchar(255)   null,
    check_standard       varchar(255)   null,
    result_type          varchar(16)    not null default 'judge',
    lower_limit          numeric(18,6)  null,
    upper_limit          numeric(18,6)  null,
    unit                 varchar(32)    null,
    mandatory            char(1)        not null default 'Y',
    sort_no              int4           not null default 0,
    status               char(1)        not null default '0',
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_check_item_pk primary key (check_item_id)
);

create index if not exists idx_check_item_tenant on mes_check_item (tenant_id);
create index if not exists idx_check_item_device_id on mes_check_item (device_id);
create index if not exists idx_check_item_device_type on mes_check_item (device_type);

comment on table mes_check_item is '点检项目';
comment on column mes_check_item.check_item_id is '主键';
comment on column mes_check_item.item_name is '项目名称';
comment on column mes_check_item.device_id is '适用设备（与适用设备类型二选一）';
comment on column mes_check_item.device_type is '适用设备类型（适用设备为空时按设备类型匹配）';
comment on column mes_check_item.check_method is '点检方法';
comment on column mes_check_item.check_standard is '点检标准';
comment on column mes_check_item.result_type is '结果类型（judge 判定合格或不合格、numeric 数值与上下限）';
comment on column mes_check_item.lower_limit is '下限（数值型）';
comment on column mes_check_item.upper_limit is '上限（数值型）';
comment on column mes_check_item.unit is '单位（数值型）';
comment on column mes_check_item.mandatory is '是否必检（Y 是 N 否）';
comment on column mes_check_item.sort_no is '排序号';
comment on column mes_check_item.status is '状态（0正常 1停用）';
comment on column mes_check_item.tenant_id is '租户编码';
comment on column mes_check_item.create_dept is '创建部门';
comment on column mes_check_item.create_by is '创建者';
comment on column mes_check_item.create_time is '创建时间';
comment on column mes_check_item.update_by is '更新者';
comment on column mes_check_item.update_time is '更新时间';
comment on column mes_check_item.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_check_item.remark is '备注';

-- ----------------------------
-- 点检记录
-- ----------------------------
create table if not exists mes_check_record
(
    check_record_id      int8           not null,
    record_code          varchar(64)    not null,
    device_id            int8           not null,
    check_time           timestamp      not null,
    checker_user_id      int8           not null,
    check_result         varchar(16)    not null,
    abnormal_count       int4           not null default 0,
    record_status        varchar(32)    not null default 'completed',
    andon_raised         char(1)        not null default 'N',
    andon_time           timestamp      null,
    void_reason          varchar(500)   null,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_check_record_pk primary key (check_record_id)
);

create index if not exists idx_check_record_tenant on mes_check_record (tenant_id);
create index if not exists idx_check_record_record_code on mes_check_record (record_code);
create index if not exists idx_check_record_device_id on mes_check_record (device_id);
create index if not exists idx_check_record_check_time on mes_check_record (check_time);

comment on table mes_check_record is '点检记录';
comment on column mes_check_record.check_record_id is '主键';
comment on column mes_check_record.record_code is '点检单号';
comment on column mes_check_record.device_id is '设备';
comment on column mes_check_record.check_time is '点检时间';
comment on column mes_check_record.checker_user_id is '点检人（用户）';
comment on column mes_check_record.check_result is '点检结论（normal 正常、abnormal 异常）';
comment on column mes_check_record.abnormal_count is '不合格项数';
comment on column mes_check_record.record_status is '记录状态（completed 已提交、voided 已作废）';
comment on column mes_check_record.andon_raised is '是否已发起安灯（Y 是 N 否）';
comment on column mes_check_record.andon_time is '发起安灯的时间';
comment on column mes_check_record.void_reason is '作废原因';
comment on column mes_check_record.tenant_id is '租户编码';
comment on column mes_check_record.create_dept is '创建部门';
comment on column mes_check_record.create_by is '创建者';
comment on column mes_check_record.create_time is '创建时间';
comment on column mes_check_record.update_by is '更新者';
comment on column mes_check_record.update_time is '更新时间';
comment on column mes_check_record.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_check_record.remark is '备注';

-- ----------------------------
-- 点检记录明细（逐项结果，保存提交时的项目快照）
-- ----------------------------
create table if not exists mes_check_record_detail
(
    detail_id            int8           not null,
    check_record_id      int8           not null,
    check_item_id        int8           null,
    item_name            varchar(128)   not null,
    check_method         varchar(255)   null,
    check_standard       varchar(255)   null,
    result_type          varchar(16)    not null,
    lower_limit          numeric(18,6)  null,
    upper_limit          numeric(18,6)  null,
    unit                 varchar(32)    null,
    mandatory            char(1)        not null default 'Y',
    measured_value       numeric(18,6)  null,
    item_result          varchar(16)    null,
    sort_no              int4           not null default 0,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    remark               varchar(255)   null,
    constraint mes_check_record_detail_pk primary key (detail_id)
);

create index if not exists idx_check_record_detail_tenant on mes_check_record_detail (tenant_id);
create index if not exists idx_check_record_detail_record_id on mes_check_record_detail (check_record_id);

comment on table mes_check_record_detail is '点检记录明细';
comment on column mes_check_record_detail.detail_id is '主键';
comment on column mes_check_record_detail.check_record_id is '点检记录';
comment on column mes_check_record_detail.check_item_id is '点检项目';
comment on column mes_check_record_detail.item_name is '项目名称';
comment on column mes_check_record_detail.check_method is '点检方法';
comment on column mes_check_record_detail.check_standard is '点检标准';
comment on column mes_check_record_detail.result_type is '结果类型（judge、numeric）';
comment on column mes_check_record_detail.lower_limit is '下限';
comment on column mes_check_record_detail.upper_limit is '上限';
comment on column mes_check_record_detail.unit is '单位';
comment on column mes_check_record_detail.mandatory is '是否必检（Y 是 N 否）';
comment on column mes_check_record_detail.measured_value is '测量值（数值型）';
comment on column mes_check_record_detail.item_result is '单项结果（ok 合格、ng 不合格，为空表示未检）';
comment on column mes_check_record_detail.sort_no is '排序号';
comment on column mes_check_record_detail.tenant_id is '租户编码';
comment on column mes_check_record_detail.create_dept is '创建部门';
comment on column mes_check_record_detail.create_by is '创建者';
comment on column mes_check_record_detail.create_time is '创建时间';
comment on column mes_check_record_detail.update_by is '更新者';
comment on column mes_check_record_detail.update_time is '更新时间';
comment on column mes_check_record_detail.remark is '说明';

-- ----------------------------
-- 保养计划
-- ----------------------------
create table if not exists mes_maintenance_plan
(
    plan_id              int8           not null,
    plan_name            varchar(128)   not null,
    device_id            int8           not null,
    maintenance_content  varchar(1000)  not null,
    cycle_days           int4           not null,
    lead_days            int4           not null default 0,
    last_maintenance_date date          null,
    next_due_date        date           not null,
    generate_from_date   date           null,
    generated_due_date   date           null,
    status               char(1)        not null default '0',
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_maintenance_plan_pk primary key (plan_id)
);

create index if not exists idx_maintenance_plan_tenant on mes_maintenance_plan (tenant_id);
create index if not exists idx_maintenance_plan_device_id on mes_maintenance_plan (device_id);
create index if not exists idx_maintenance_plan_generate_from_date on mes_maintenance_plan (generate_from_date);

comment on table mes_maintenance_plan is '保养计划';
comment on column mes_maintenance_plan.plan_id is '主键';
comment on column mes_maintenance_plan.plan_name is '计划名称';
comment on column mes_maintenance_plan.device_id is '设备';
comment on column mes_maintenance_plan.maintenance_content is '保养内容';
comment on column mes_maintenance_plan.cycle_days is '周期（天）';
comment on column mes_maintenance_plan.lead_days is '提前生成天数';
comment on column mes_maintenance_plan.last_maintenance_date is '上次保养日期';
comment on column mes_maintenance_plan.next_due_date is '下次到期日期';
comment on column mes_maintenance_plan.generate_from_date is '从这一天起生成本周期的工单（下次到期日期减提前天数；本周期已生成时为空）';
comment on column mes_maintenance_plan.generated_due_date is '已生成工单的到期日期（同一到期日期只生成一张工单）';
comment on column mes_maintenance_plan.status is '状态（0正常 1停用）';
comment on column mes_maintenance_plan.tenant_id is '租户编码';
comment on column mes_maintenance_plan.create_dept is '创建部门';
comment on column mes_maintenance_plan.create_by is '创建者';
comment on column mes_maintenance_plan.create_time is '创建时间';
comment on column mes_maintenance_plan.update_by is '更新者';
comment on column mes_maintenance_plan.update_time is '更新时间';
comment on column mes_maintenance_plan.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_maintenance_plan.remark is '备注';

-- ----------------------------
-- 保养工单
-- ----------------------------
create table if not exists mes_maintenance_order
(
    order_id             int8           not null,
    order_code           varchar(64)    not null,
    plan_id              int8           not null,
    device_id            int8           not null,
    due_date             date           not null,
    maintenance_content  varchar(1000)  not null,
    order_status         varchar(32)    not null default 'pending',
    executor_user_id     int8           null,
    start_time           timestamp      null,
    finish_time          timestamp      null,
    actual_content       varchar(1000)  null,
    work_minutes         int4           null,
    spare_parts          varchar(500)   null,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_maintenance_order_pk primary key (order_id)
);

create index if not exists idx_maintenance_order_tenant on mes_maintenance_order (tenant_id);
create index if not exists idx_maintenance_order_order_code on mes_maintenance_order (order_code);
create index if not exists idx_maintenance_order_plan_id on mes_maintenance_order (plan_id);
create index if not exists idx_maintenance_order_device_id on mes_maintenance_order (device_id);
create index if not exists idx_maintenance_order_order_status on mes_maintenance_order (order_status);

comment on table mes_maintenance_order is '保养工单';
comment on column mes_maintenance_order.order_id is '主键';
comment on column mes_maintenance_order.order_code is '保养工单号';
comment on column mes_maintenance_order.plan_id is '保养计划';
comment on column mes_maintenance_order.device_id is '设备';
comment on column mes_maintenance_order.due_date is '到期日期';
comment on column mes_maintenance_order.maintenance_content is '计划的保养内容（生成时的快照）';
comment on column mes_maintenance_order.order_status is '工单状态（pending 待保养、in_progress 保养中、completed 已完成、cancelled 已取消）';
comment on column mes_maintenance_order.executor_user_id is '保养人（用户）';
comment on column mes_maintenance_order.start_time is '开始时间';
comment on column mes_maintenance_order.finish_time is '完成时间';
comment on column mes_maintenance_order.actual_content is '实际保养内容';
comment on column mes_maintenance_order.work_minutes is '用时（分钟）';
comment on column mes_maintenance_order.spare_parts is '更换的备件';
comment on column mes_maintenance_order.tenant_id is '租户编码';
comment on column mes_maintenance_order.create_dept is '创建部门';
comment on column mes_maintenance_order.create_by is '创建者';
comment on column mes_maintenance_order.create_time is '创建时间';
comment on column mes_maintenance_order.update_by is '更新者';
comment on column mes_maintenance_order.update_time is '更新时间';
comment on column mes_maintenance_order.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_maintenance_order.remark is '备注';

-- ----------------------------
-- OEE 配置（按设备或产线：每日计划生产时间与标准节拍）
-- ----------------------------
create table if not exists mes_oee_config
(
    config_id            int8           not null,
    target_type          varchar(16)    not null,
    target_id            int8           not null,
    planned_minutes      int4           null,
    cycle_minutes        numeric(18,4)  null,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    remark               varchar(500)   null,
    constraint mes_oee_config_pk primary key (config_id),
    constraint uk_oee_config_target unique (tenant_id, target_type, target_id)
);

create index if not exists idx_oee_config_tenant on mes_oee_config (tenant_id);

comment on table mes_oee_config is 'OEE 配置';
comment on column mes_oee_config.config_id is '主键';
comment on column mes_oee_config.target_type is '对象类型（device 设备、line 产线）';
comment on column mes_oee_config.target_id is '设备或产线';
comment on column mes_oee_config.planned_minutes is '每日计划生产时间（分钟，为空表示未配置）';
comment on column mes_oee_config.cycle_minutes is '标准节拍（分钟/件，为空时取工序的单件标准工时）';
comment on column mes_oee_config.tenant_id is '租户编码';
comment on column mes_oee_config.create_dept is '创建部门';
comment on column mes_oee_config.create_by is '创建者';
comment on column mes_oee_config.create_time is '创建时间';
comment on column mes_oee_config.update_by is '更新者';
comment on column mes_oee_config.update_time is '更新时间';
comment on column mes_oee_config.remark is '备注';

-- ----------------------------
-- 默认租户的编码规则（其他租户没有规则时按同样的格式生成）
-- ----------------------------
insert into mes_code_rule (rule_id, rule_code, rule_name, prefix, date_pattern, serial_length, reset_cycle, status, tenant_id, create_time, del_flag) values (1762000000000001091, 'CHECK_RECORD', '点检记录', 'DJ', 'yyyyMMdd', 4, 'day', '0', 'default', now(), '0');
insert into mes_code_rule (rule_id, rule_code, rule_name, prefix, date_pattern, serial_length, reset_cycle, status, tenant_id, create_time, del_flag) values (1762000000000001092, 'MAINTENANCE_ORDER', '保养工单', 'BY', 'yyyyMMdd', 4, 'day', '0', 'default', now(), '0');
