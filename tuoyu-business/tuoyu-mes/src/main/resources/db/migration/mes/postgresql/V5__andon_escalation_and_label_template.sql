-- 安灯超时升级：升级规则；安灯事件记录按规则算出的截止时间、各时限的超时升级时间与升级次数
-- 标签打印：序列号与批次标签的模板

-- ----------------------------
-- 安灯升级规则
-- ----------------------------
create table if not exists mes_andon_rule
(
    andon_rule_id        int8           not null,
    rule_name            varchar(64)    not null,
    event_type           varchar(32)    null,
    event_level          varchar(32)    null,
    response_minutes     int4           null,
    resolve_minutes      int4           null,
    escalate_level       varchar(32)    null,
    notify_users         varchar(500)   null,
    status               char(1)        not null default '0',
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_andon_rule_pk primary key (andon_rule_id)
);

create index if not exists idx_andon_rule_tenant on mes_andon_rule (tenant_id);

comment on table mes_andon_rule is '安灯升级规则';
comment on column mes_andon_rule.andon_rule_id is '主键';
comment on column mes_andon_rule.rule_name is '规则名称';
comment on column mes_andon_rule.event_type is '适用的异常类型（为空表示全部类型）';
comment on column mes_andon_rule.event_level is '适用的紧急程度（为空表示全部级别）';
comment on column mes_andon_rule.response_minutes is '响应时限（分钟，从上报到开始处理；为空表示不检查）';
comment on column mes_andon_rule.resolve_minutes is '解决时限（分钟，从上报到解决；为空表示不检查）';
comment on column mes_andon_rule.escalate_level is '超时后升级到的紧急程度（为空表示只提醒）';
comment on column mes_andon_rule.notify_users is '超时提醒的用户（用户 ID，逗号分隔）';
comment on column mes_andon_rule.status is '状态（0 正常 1 停用）';
comment on column mes_andon_rule.tenant_id is '租户编码';
comment on column mes_andon_rule.create_dept is '创建部门';
comment on column mes_andon_rule.create_by is '创建者';
comment on column mes_andon_rule.create_time is '创建时间';
comment on column mes_andon_rule.update_by is '更新者';
comment on column mes_andon_rule.update_time is '更新时间';
comment on column mes_andon_rule.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_andon_rule.remark is '备注';

-- ----------------------------
-- 安灯事件：超时升级
-- ----------------------------
alter table mes_andon_event
    add column if not exists andon_rule_id         int8       null,
    add column if not exists deadline_checked      char(1)    not null default '0',
    add column if not exists response_due_time     timestamp  null,
    add column if not exists resolve_due_time      timestamp  null,
    add column if not exists response_overdue_time timestamp  null,
    add column if not exists resolve_overdue_time  timestamp  null,
    add column if not exists escalate_count        int4       not null default 0;

comment on column mes_andon_event.andon_rule_id is '计算截止时间所用的升级规则';
comment on column mes_andon_event.deadline_checked is '截止时间是否已按升级规则计算（0 待计算 1 已计算）';
comment on column mes_andon_event.response_due_time is '响应截止时间（须在此前开始处理）';
comment on column mes_andon_event.resolve_due_time is '解决截止时间（须在此前解决）';
comment on column mes_andon_event.response_overdue_time is '响应超时的升级时间（为空表示未超时）';
comment on column mes_andon_event.resolve_overdue_time is '解决超时的升级时间（为空表示未超时）';
comment on column mes_andon_event.escalate_count is '超时升级次数';

-- ----------------------------
-- 标签模板
-- ----------------------------
create table if not exists mes_label_template
(
    label_template_id    int8           not null,
    template_name        varchar(64)    not null,
    target_type          varchar(32)    not null,
    paper_width          int4           not null,
    paper_height         int4           not null,
    column_count         int4           not null default 1,
    column_gap           int4           not null default 0,
    barcode_type         varchar(32)    not null default 'code128',
    field_keys           varchar(255)   not null,
    default_flag         char(1)        not null default '0',
    status               char(1)        not null default '0',
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_label_template_pk primary key (label_template_id)
);

create index if not exists idx_label_template_tenant on mes_label_template (tenant_id);
create index if not exists idx_label_template_target_type on mes_label_template (target_type);

comment on table mes_label_template is '标签模板';
comment on column mes_label_template.label_template_id is '主键';
comment on column mes_label_template.template_name is '模板名称';
comment on column mes_label_template.target_type is '适用对象（sn 序列号、batch 批次）';
comment on column mes_label_template.paper_width is '标签宽度（毫米）';
comment on column mes_label_template.paper_height is '标签高度（毫米）';
comment on column mes_label_template.column_count is '每行几列';
comment on column mes_label_template.column_gap is '列间距（毫米）';
comment on column mes_label_template.barcode_type is '条码类型（code128 一维码、qrcode 二维码）';
comment on column mes_label_template.field_keys is '显示的字段（按顺序，逗号分隔）';
comment on column mes_label_template.default_flag is '是否为该对象的默认模板（1 是 0 否）';
comment on column mes_label_template.status is '状态（0 正常 1 停用）';
comment on column mes_label_template.tenant_id is '租户编码';
comment on column mes_label_template.create_dept is '创建部门';
comment on column mes_label_template.create_by is '创建者';
comment on column mes_label_template.create_time is '创建时间';
comment on column mes_label_template.update_by is '更新者';
comment on column mes_label_template.update_time is '更新时间';
comment on column mes_label_template.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_label_template.remark is '备注';
