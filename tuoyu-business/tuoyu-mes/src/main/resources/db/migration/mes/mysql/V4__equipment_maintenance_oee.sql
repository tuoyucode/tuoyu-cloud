-- 设备点检、保养与 OEE：MySQL

-- ----------------------------
-- 点检项目
-- ----------------------------
create table mes_check_item (
  check_item_id        bigint           not null                             comment '主键',
  item_name            varchar(128)     not null                             comment '项目名称',
  device_id            bigint           default null                         comment '适用设备（与适用设备类型二选一）',
  device_type          varchar(32)      default null                         comment '适用设备类型（适用设备为空时按设备类型匹配）',
  check_method         varchar(255)     default null                         comment '点检方法',
  check_standard       varchar(255)     default null                         comment '点检标准',
  result_type          varchar(16)      not null default 'judge'             comment '结果类型（judge 判定合格或不合格、numeric 数值与上下限）',
  lower_limit          decimal(18,6)    default null                         comment '下限（数值型）',
  upper_limit          decimal(18,6)    default null                         comment '上限（数值型）',
  unit                 varchar(32)      default null                         comment '单位（数值型）',
  mandatory            char(1)          not null default 'Y'                 comment '是否必检（Y 是 N 否）',
  sort_no              int              not null default 0                   comment '排序号',
  status               char(1)          not null default '0'                 comment '状态（0正常 1停用）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (check_item_id),
  key idx_check_item_tenant (tenant_id),
  key idx_check_item_device_id (device_id),
  key idx_check_item_device_type (device_type)
) engine=innodb default charset=utf8mb4 comment = '点检项目';

-- ----------------------------
-- 点检记录
-- ----------------------------
create table mes_check_record (
  check_record_id      bigint           not null                             comment '主键',
  record_code          varchar(64)      not null                             comment '点检单号',
  device_id            bigint           not null                             comment '设备',
  check_time           datetime         not null                             comment '点检时间',
  checker_user_id      bigint           not null                             comment '点检人（用户）',
  check_result         varchar(16)      not null                             comment '点检结论（normal 正常、abnormal 异常）',
  abnormal_count       int              not null default 0                   comment '不合格项数',
  record_status        varchar(32)      not null default 'completed'         comment '记录状态（completed 已提交、voided 已作废）',
  andon_raised         char(1)          not null default 'N'                 comment '是否已发起安灯（Y 是 N 否）',
  andon_time           datetime         default null                         comment '发起安灯的时间',
  void_reason          varchar(500)     default null                         comment '作废原因',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (check_record_id),
  key idx_check_record_tenant (tenant_id),
  key idx_check_record_record_code (record_code),
  key idx_check_record_device_id (device_id),
  key idx_check_record_check_time (check_time)
) engine=innodb default charset=utf8mb4 comment = '点检记录';

-- ----------------------------
-- 点检记录明细（逐项结果，保存提交时的项目快照）
-- ----------------------------
create table mes_check_record_detail (
  detail_id            bigint           not null                             comment '主键',
  check_record_id      bigint           not null                             comment '点检记录',
  check_item_id        bigint           default null                         comment '点检项目',
  item_name            varchar(128)     not null                             comment '项目名称',
  check_method         varchar(255)     default null                         comment '点检方法',
  check_standard       varchar(255)     default null                         comment '点检标准',
  result_type          varchar(16)      not null                             comment '结果类型（judge、numeric）',
  lower_limit          decimal(18,6)    default null                         comment '下限',
  upper_limit          decimal(18,6)    default null                         comment '上限',
  unit                 varchar(32)      default null                         comment '单位',
  mandatory            char(1)          not null default 'Y'                 comment '是否必检（Y 是 N 否）',
  measured_value       decimal(18,6)    default null                         comment '测量值（数值型）',
  item_result          varchar(16)      default null                         comment '单项结果（ok 合格、ng 不合格，为空表示未检）',
  sort_no              int              not null default 0                   comment '排序号',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  remark               varchar(255)     default null                         comment '说明',
  primary key (detail_id),
  key idx_check_record_detail_tenant (tenant_id),
  key idx_check_record_detail_record_id (check_record_id)
) engine=innodb default charset=utf8mb4 comment = '点检记录明细';

-- ----------------------------
-- 保养计划
-- ----------------------------
create table mes_maintenance_plan (
  plan_id              bigint           not null                             comment '主键',
  plan_name            varchar(128)     not null                             comment '计划名称',
  device_id            bigint           not null                             comment '设备',
  maintenance_content  varchar(1000)    not null                             comment '保养内容',
  cycle_days           int              not null                             comment '周期（天）',
  lead_days            int              not null default 0                   comment '提前生成天数',
  last_maintenance_date date            default null                         comment '上次保养日期',
  next_due_date        date             not null                             comment '下次到期日期',
  generate_from_date   date             default null                         comment '从这一天起生成本周期的工单（下次到期日期减提前天数；本周期已生成时为空）',
  generated_due_date   date             default null                         comment '已生成工单的到期日期（同一到期日期只生成一张工单）',
  status               char(1)          not null default '0'                 comment '状态（0正常 1停用）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (plan_id),
  key idx_maintenance_plan_tenant (tenant_id),
  key idx_maintenance_plan_device_id (device_id),
  key idx_maintenance_plan_generate_from_date (generate_from_date)
) engine=innodb default charset=utf8mb4 comment = '保养计划';

-- ----------------------------
-- 保养工单
-- ----------------------------
create table mes_maintenance_order (
  order_id             bigint           not null                             comment '主键',
  order_code           varchar(64)      not null                             comment '保养工单号',
  plan_id              bigint           not null                             comment '保养计划',
  device_id            bigint           not null                             comment '设备',
  due_date             date             not null                             comment '到期日期',
  maintenance_content  varchar(1000)    not null                             comment '计划的保养内容（生成时的快照）',
  order_status         varchar(32)      not null default 'pending'           comment '工单状态（pending 待保养、in_progress 保养中、completed 已完成、cancelled 已取消）',
  executor_user_id     bigint           default null                         comment '保养人（用户）',
  start_time           datetime         default null                         comment '开始时间',
  finish_time          datetime         default null                         comment '完成时间',
  actual_content       varchar(1000)    default null                         comment '实际保养内容',
  work_minutes         int              default null                         comment '用时（分钟）',
  spare_parts          varchar(500)     default null                         comment '更换的备件',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (order_id),
  key idx_maintenance_order_tenant (tenant_id),
  key idx_maintenance_order_order_code (order_code),
  key idx_maintenance_order_plan_id (plan_id),
  key idx_maintenance_order_device_id (device_id),
  key idx_maintenance_order_order_status (order_status)
) engine=innodb default charset=utf8mb4 comment = '保养工单';

-- ----------------------------
-- OEE 配置（按设备或产线：每日计划生产时间与标准节拍）
-- ----------------------------
create table mes_oee_config (
  config_id            bigint           not null                             comment '主键',
  target_type          varchar(16)      not null                             comment '对象类型（device 设备、line 产线）',
  target_id            bigint           not null                             comment '设备或产线',
  planned_minutes      int              default null                         comment '每日计划生产时间（分钟，为空表示未配置）',
  cycle_minutes        decimal(18,4)    default null                         comment '标准节拍（分钟/件，为空时取工序的单件标准工时）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  remark               varchar(500)     default null                         comment '备注',
  primary key (config_id),
  unique key uk_oee_config_target (tenant_id, target_type, target_id),
  key idx_oee_config_tenant (tenant_id)
) engine=innodb default charset=utf8mb4 comment = 'OEE 配置';

-- ----------------------------
-- 默认租户的编码规则（其他租户没有规则时按同样的格式生成）
-- ----------------------------
insert into mes_code_rule (rule_id, rule_code, rule_name, prefix, date_pattern, serial_length, reset_cycle, status, tenant_id, create_time, del_flag) values (1762000000000001091, 'CHECK_RECORD', '点检记录', 'DJ', 'yyyyMMdd', 4, 'day', '0', 'default', now(), '0');
insert into mes_code_rule (rule_id, rule_code, rule_name, prefix, date_pattern, serial_length, reset_cycle, status, tenant_id, create_time, del_flag) values (1762000000000001092, 'MAINTENANCE_ORDER', '保养工单', 'BY', 'yyyyMMdd', 4, 'day', '0', 'default', now(), '0');
