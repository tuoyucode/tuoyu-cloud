-- 安灯超时升级：升级规则；安灯事件记录按规则算出的截止时间、各时限的超时升级时间与升级次数
-- 标签打印：序列号与批次标签的模板

-- ----------------------------
-- 安灯升级规则
-- ----------------------------
create table mes_andon_rule (
  andon_rule_id        bigint           not null                             comment '主键',
  rule_name            varchar(64)      not null                             comment '规则名称',
  event_type           varchar(32)      default null                         comment '适用的异常类型（为空表示全部类型）',
  event_level          varchar(32)      default null                         comment '适用的紧急程度（为空表示全部级别）',
  response_minutes     int              default null                         comment '响应时限（分钟，从上报到开始处理；为空表示不检查）',
  resolve_minutes      int              default null                         comment '解决时限（分钟，从上报到解决；为空表示不检查）',
  escalate_level       varchar(32)      default null                         comment '超时后升级到的紧急程度（为空表示只提醒）',
  notify_users         varchar(500)     default null                         comment '超时提醒的用户（用户 ID，逗号分隔）',
  status               char(1)          not null default '0'                 comment '状态（0 正常 1 停用）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (andon_rule_id),
  key idx_andon_rule_tenant (tenant_id)
) engine=innodb default charset=utf8mb4 comment = '安灯升级规则';

-- ----------------------------
-- 安灯事件：超时升级
-- ----------------------------
alter table mes_andon_event
  add column andon_rule_id         bigint       default null         comment '计算截止时间所用的升级规则' after event_status,
  add column deadline_checked      char(1)      not null default '0' comment '截止时间是否已按升级规则计算（0 待计算 1 已计算）' after andon_rule_id,
  add column response_due_time     datetime     default null         comment '响应截止时间（须在此前开始处理）' after deadline_checked,
  add column resolve_due_time      datetime     default null         comment '解决截止时间（须在此前解决）' after response_due_time,
  add column response_overdue_time datetime     default null         comment '响应超时的升级时间（为空表示未超时）' after resolve_due_time,
  add column resolve_overdue_time  datetime     default null         comment '解决超时的升级时间（为空表示未超时）' after response_overdue_time,
  add column escalate_count        int          not null default 0   comment '超时升级次数' after resolve_overdue_time;

-- ----------------------------
-- 标签模板
-- ----------------------------
create table mes_label_template (
  label_template_id    bigint           not null                             comment '主键',
  template_name        varchar(64)      not null                             comment '模板名称',
  target_type          varchar(32)      not null                             comment '适用对象（sn 序列号、batch 批次）',
  paper_width          int              not null                             comment '标签宽度（毫米）',
  paper_height         int              not null                             comment '标签高度（毫米）',
  column_count         int              not null default 1                   comment '每行几列',
  column_gap           int              not null default 0                   comment '列间距（毫米）',
  barcode_type         varchar(32)      not null default 'code128'           comment '条码类型（code128 一维码、qrcode 二维码）',
  field_keys           varchar(255)     not null                             comment '显示的字段（按顺序，逗号分隔）',
  default_flag         char(1)          not null default '0'                 comment '是否为该对象的默认模板（1 是 0 否）',
  status               char(1)          not null default '0'                 comment '状态（0 正常 1 停用）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (label_template_id),
  key idx_label_template_tenant (tenant_id),
  key idx_label_template_target_type (target_type)
) engine=innodb default charset=utf8mb4 comment = '标签模板';
