-- 制造执行（MES）库 tuoyu-mes 的基线：MySQL
-- 全新安装与 Flyway 的 V1 基线使用同一份脚本，发布后不再修改，改动写成新的迁移脚本

-- ----------------------------
-- 工厂
-- ----------------------------
create table mes_factory (
  factory_id           bigint           not null                             comment '主键',
  factory_code         varchar(64)      not null                             comment '工厂编码',
  factory_name         varchar(128)     not null                             comment '工厂名称',
  short_name           varchar(64)      default null                         comment '工厂简称',
  address              varchar(255)     default null                         comment '地址',
  contact              varchar(64)      default null                         comment '联系人',
  contact_phone        varchar(32)      default null                         comment '联系电话',
  status               char(1)          not null default '0'                 comment '状态（0正常 1停用）',
  sort_no              int              not null default 0                   comment '排序号',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (factory_id),
  key idx_factory_tenant (tenant_id),
  key idx_factory_factory_code (factory_code)
) engine=innodb default charset=utf8mb4 comment = '工厂';

-- ----------------------------
-- 车间
-- ----------------------------
create table mes_workshop (
  workshop_id          bigint           not null                             comment '主键',
  factory_id           bigint           not null                             comment '所属工厂',
  workshop_code        varchar(64)      not null                             comment '车间编码',
  workshop_name        varchar(128)     not null                             comment '车间名称',
  dept_id              bigint           default null                         comment '对应的组织（部门）',
  manager              varchar(64)      default null                         comment '负责人',
  status               char(1)          not null default '0'                 comment '状态（0正常 1停用）',
  sort_no              int              not null default 0                   comment '排序号',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (workshop_id),
  key idx_workshop_tenant (tenant_id),
  key idx_workshop_factory_id (factory_id),
  key idx_workshop_workshop_code (workshop_code)
) engine=innodb default charset=utf8mb4 comment = '车间';

-- ----------------------------
-- 产线
-- ----------------------------
create table mes_line (
  line_id              bigint           not null                             comment '主键',
  factory_id           bigint           not null                             comment '所属工厂',
  workshop_id          bigint           not null                             comment '所属车间',
  line_code            varchar(64)      not null                             comment '产线编码',
  line_name            varchar(128)     not null                             comment '产线名称',
  status               char(1)          not null default '0'                 comment '状态（0正常 1停用）',
  sort_no              int              not null default 0                   comment '排序号',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (line_id),
  key idx_line_tenant (tenant_id),
  key idx_line_workshop_id (workshop_id),
  key idx_line_line_code (line_code)
) engine=innodb default charset=utf8mb4 comment = '产线';

-- ----------------------------
-- 工位
-- ----------------------------
create table mes_station (
  station_id           bigint           not null                             comment '主键',
  factory_id           bigint           not null                             comment '所属工厂',
  workshop_id          bigint           not null                             comment '所属车间',
  line_id              bigint           not null                             comment '所属产线',
  station_code         varchar(64)      not null                             comment '工位编码',
  station_name         varchar(128)     not null                             comment '工位名称',
  station_type         varchar(32)      default null                         comment '工位类型（与工序的建议工位类型对应）',
  status               char(1)          not null default '0'                 comment '状态（0正常 1停用）',
  sort_no              int              not null default 0                   comment '排序号',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (station_id),
  key idx_station_tenant (tenant_id),
  key idx_station_line_id (line_id),
  key idx_station_station_code (station_code)
) engine=innodb default charset=utf8mb4 comment = '工位';

-- ----------------------------
-- 班组
-- ----------------------------
create table mes_team (
  team_id              bigint           not null                             comment '主键',
  factory_id           bigint           not null                             comment '所属工厂',
  workshop_id          bigint           not null                             comment '所属车间',
  team_code            varchar(64)      not null                             comment '班组编码',
  team_name            varchar(128)     not null                             comment '班组名称',
  leader_user_id       bigint           default null                         comment '班组长（用户）',
  status               char(1)          not null default '0'                 comment '状态（0正常 1停用）',
  sort_no              int              not null default 0                   comment '排序号',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (team_id),
  key idx_team_tenant (tenant_id),
  key idx_team_workshop_id (workshop_id),
  key idx_team_team_code (team_code)
) engine=innodb default charset=utf8mb4 comment = '班组';

-- ----------------------------
-- 班组成员
-- ----------------------------
create table mes_team_member (
  member_id            bigint           not null                             comment '主键',
  team_id              bigint           not null                             comment '班组',
  user_id              bigint           not null                             comment '成员（用户）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  primary key (member_id),
  key idx_team_member_tenant (tenant_id),
  key idx_team_member_team_id (team_id),
  key idx_team_member_user_id (user_id)
) engine=innodb default charset=utf8mb4 comment = '班组成员';

-- ----------------------------
-- 物料
-- ----------------------------
create table mes_material (
  material_id          bigint           not null                             comment '主键',
  material_code        varchar(64)      not null                             comment '物料编码',
  material_name        varchar(128)     not null                             comment '物料名称',
  specification        varchar(255)     default null                         comment '规格型号',
  material_type        varchar(32)      not null default 'raw'               comment '物料类型（product 成品、semi 半成品、raw 原材料、aux 辅料）',
  unit                 varchar(32)      not null                             comment '计量单位',
  trace_mode           varchar(32)      not null default 'none'              comment '追溯方式（none 不追溯、batch 按批次、sn 按序列号）',
  status               char(1)          not null default '0'                 comment '状态（0正常 1停用）',
  sort_no              int              not null default 0                   comment '排序号',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (material_id),
  key idx_material_tenant (tenant_id),
  key idx_material_material_code (material_code),
  key idx_material_material_type (material_type)
) engine=innodb default charset=utf8mb4 comment = '物料';

-- ----------------------------
-- 物料清单
-- ----------------------------
create table mes_bom (
  bom_id               bigint           not null                             comment '主键',
  material_id          bigint           not null                             comment '产品（成品或半成品）',
  bom_code             varchar(64)      not null                             comment 'BOM 编码',
  bom_version          varchar(32)      not null default 'V1'                comment '版本',
  base_qty             decimal(18,6)    not null default 1                   comment '基准数量（明细用量对应的产品数量）',
  is_default           char(1)          not null default 'N'                 comment '是否默认版本（Y 是 N 否）',
  status               char(1)          not null default '0'                 comment '状态（0正常 1停用）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (bom_id),
  key idx_bom_tenant (tenant_id),
  key idx_bom_material_id (material_id),
  key idx_bom_bom_code (bom_code)
) engine=innodb default charset=utf8mb4 comment = '物料清单';

-- ----------------------------
-- 物料清单明细
-- ----------------------------
create table mes_bom_item (
  item_id              bigint           not null                             comment '主键',
  bom_id               bigint           not null                             comment '物料清单',
  material_id          bigint           not null                             comment '子件物料',
  qty                  decimal(18,6)    not null                             comment '用量（对应基准数量）',
  loss_rate            decimal(18,4)    not null default 0                   comment '损耗率（0.05 表示 5%）',
  sort_no              int              not null default 0                   comment '排序号',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  remark               varchar(500)     default null                         comment '备注',
  primary key (item_id),
  key idx_bom_item_tenant (tenant_id),
  key idx_bom_item_bom_id (bom_id),
  key idx_bom_item_material_id (material_id)
) engine=innodb default charset=utf8mb4 comment = '物料清单明细';

-- ----------------------------
-- 工艺路线
-- ----------------------------
create table mes_route (
  route_id             bigint           not null                             comment '主键',
  material_id          bigint           not null                             comment '产品',
  route_code           varchar(64)      not null                             comment '工艺路线编码',
  route_name           varchar(128)     not null                             comment '工艺路线名称',
  route_version        varchar(32)      not null default 'V1'                comment '版本',
  is_default           char(1)          not null default 'N'                 comment '是否默认版本（Y 是 N 否）',
  status               char(1)          not null default '0'                 comment '状态（0正常 1停用）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (route_id),
  key idx_route_tenant (tenant_id),
  key idx_route_material_id (material_id),
  key idx_route_route_code (route_code)
) engine=innodb default charset=utf8mb4 comment = '工艺路线';

-- ----------------------------
-- 工艺路线工序
-- ----------------------------
create table mes_route_operation (
  operation_id         bigint           not null                             comment '主键',
  route_id             bigint           not null                             comment '工艺路线',
  seq_no               int              not null                             comment '工序顺序（按升序执行）',
  operation_code       varchar(64)      not null                             comment '工序编码',
  operation_name       varchar(128)     not null                             comment '工序名称',
  station_type         varchar(32)      default null                         comment '建议工位类型',
  standard_minutes     decimal(18,4)    not null default 0                   comment '单件标准工时（分钟）',
  first_inspection     char(1)          not null default 'N'                 comment '是否需要首检合格后才能批量报工（Y 是 N 否）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  remark               varchar(500)     default null                         comment '备注',
  primary key (operation_id),
  key idx_route_operation_tenant (tenant_id),
  key idx_route_operation_route_id (route_id)
) engine=innodb default charset=utf8mb4 comment = '工艺路线工序';

-- ----------------------------
-- 设备
-- ----------------------------
create table mes_device (
  device_id            bigint           not null                             comment '主键',
  device_code          varchar(64)      not null                             comment '设备编码',
  device_name          varchar(128)     not null                             comment '设备名称',
  device_type          varchar(32)      default null                         comment '设备类型',
  model                varchar(128)     default null                         comment '型号',
  manufacturer         varchar(128)     default null                         comment '制造商',
  factory_id           bigint           not null                             comment '所属工厂',
  workshop_id          bigint           default null                         comment '所属车间',
  line_id              bigint           default null                         comment '所属产线',
  station_id           bigint           default null                         comment '所属工位',
  status               char(1)          not null default '0'                 comment '状态（0正常 1停用）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (device_id),
  key idx_device_tenant (tenant_id),
  key idx_device_device_code (device_code),
  key idx_device_workshop_id (workshop_id)
) engine=innodb default charset=utf8mb4 comment = '设备';

-- ----------------------------
-- 仓库
-- ----------------------------
create table mes_warehouse (
  warehouse_id         bigint           not null                             comment '主键',
  factory_id           bigint           not null                             comment '所属工厂',
  warehouse_code       varchar(64)      not null                             comment '仓库编码',
  warehouse_name       varchar(128)     not null                             comment '仓库名称',
  warehouse_type       varchar(32)      not null default 'raw'               comment '仓库类型（raw 原材料、wip 在制品、finished 成品）',
  status               char(1)          not null default '0'                 comment '状态（0正常 1停用）',
  sort_no              int              not null default 0                   comment '排序号',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (warehouse_id),
  key idx_warehouse_tenant (tenant_id),
  key idx_warehouse_factory_id (factory_id),
  key idx_warehouse_warehouse_code (warehouse_code)
) engine=innodb default charset=utf8mb4 comment = '仓库';

-- ----------------------------
-- 库位
-- ----------------------------
create table mes_location (
  location_id          bigint           not null                             comment '主键',
  warehouse_id         bigint           not null                             comment '所属仓库',
  location_code        varchar(64)      not null                             comment '库位编码',
  location_name        varchar(128)     not null                             comment '库位名称',
  status               char(1)          not null default '0'                 comment '状态（0正常 1停用）',
  sort_no              int              not null default 0                   comment '排序号',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (location_id),
  key idx_location_tenant (tenant_id),
  key idx_location_warehouse_id (warehouse_id),
  key idx_location_location_code (location_code)
) engine=innodb default charset=utf8mb4 comment = '库位';

-- ----------------------------
-- 生产工单
-- ----------------------------
create table mes_workorder (
  workorder_id         bigint           not null                             comment '主键',
  workorder_code       varchar(64)      not null                             comment '工单编号',
  source_type          varchar(32)      not null default 'manual'            comment '来源（manual 手工、erp 外部系统）',
  source_code          varchar(64)      default null                         comment '来源单号（例如销售订单号）',
  material_id          bigint           not null                             comment '产品',
  bom_id               bigint           default null                         comment '物料清单',
  route_id             bigint           not null                             comment '工艺路线',
  factory_id           bigint           not null                             comment '工厂',
  workshop_id          bigint           not null                             comment '车间',
  line_id              bigint           default null                         comment '产线',
  plan_qty             decimal(18,6)    not null                             comment '计划数量',
  completed_qty        decimal(18,6)    not null default 0                   comment '完工数量（末道工序的合格数）',
  defect_qty           decimal(18,6)    not null default 0                   comment '不良数量（各工序累计）',
  stocked_qty          decimal(18,6)    not null default 0                   comment '已入库数量',
  priority             int              not null default 0                   comment '优先级（数值越大越优先）',
  batch_code           varchar(64)      default null                         comment '生产批次号（下达时生成）',
  plan_start_time      datetime         default null                         comment '计划开始时间',
  plan_end_time        datetime         default null                         comment '计划完成时间',
  actual_start_time    datetime         default null                         comment '实际开始时间',
  actual_end_time      datetime         default null                         comment '实际完成时间',
  workorder_status     varchar(32)      not null default 'draft'             comment '工单状态（draft 草稿、released 已下达、in_progress 生产中、paused 已暂停、completed 已完工、closed 已关闭、cancelled 已取消）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (workorder_id),
  key idx_workorder_tenant (tenant_id),
  key idx_workorder_workorder_code (workorder_code),
  key idx_workorder_workorder_status (workorder_status),
  key idx_workorder_material_id (material_id),
  key idx_workorder_workshop_id (workshop_id)
) engine=innodb default charset=utf8mb4 comment = '生产工单';

-- ----------------------------
-- 工序任务
-- ----------------------------
create table mes_operation_task (
  task_id              bigint           not null                             comment '主键',
  task_code            varchar(64)      not null                             comment '任务编号',
  workorder_id         bigint           not null                             comment '工单',
  operation_id         bigint           default null                         comment '工艺路线工序',
  seq_no               int              not null                             comment '工序顺序',
  operation_code       varchar(64)      not null                             comment '工序编码',
  operation_name       varchar(128)     not null                             comment '工序名称',
  material_id          bigint           not null                             comment '产品',
  workshop_id          bigint           not null                             comment '车间',
  line_id              bigint           default null                         comment '产线',
  station_id           bigint           default null                         comment '派工工位',
  team_id              bigint           default null                         comment '派工班组',
  assignee_user_id     bigint           default null                         comment '作业人（用户）',
  plan_qty             decimal(18,6)    not null                             comment '计划数量',
  qualified_qty        decimal(18,6)    not null default 0                   comment '合格数量（累计）',
  defect_qty           decimal(18,6)    not null default 0                   comment '不良数量（累计）',
  standard_minutes     decimal(18,4)    not null default 0                   comment '单件标准工时（分钟）',
  first_inspection     char(1)          not null default 'N'                 comment '是否需要首检（Y 是 N 否）',
  actual_start_time    datetime         default null                         comment '实际开始时间',
  actual_end_time      datetime         default null                         comment '实际完成时间',
  task_status          varchar(32)      not null default 'pending'           comment '任务状态（pending 待派工、dispatched 已派工、in_progress 生产中、completed 已完成、closed 已关闭）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (task_id),
  key idx_operation_task_tenant (tenant_id),
  key idx_operation_task_workorder_id (workorder_id),
  key idx_operation_task_task_status (task_status),
  key idx_operation_task_assignee_user_id (assignee_user_id),
  key idx_operation_task_team_id (team_id)
) engine=innodb default charset=utf8mb4 comment = '工序任务';

-- ----------------------------
-- 报工记录
-- ----------------------------
create table mes_production_report (
  report_id            bigint           not null                             comment '主键',
  report_code          varchar(64)      not null                             comment '报工编号',
  workorder_id         bigint           not null                             comment '工单',
  task_id              bigint           not null                             comment '工序任务',
  material_id          bigint           not null                             comment '产品',
  workshop_id          bigint           not null                             comment '车间',
  line_id              bigint           default null                         comment '产线',
  station_id           bigint           default null                         comment '工位',
  team_id              bigint           default null                         comment '班组',
  report_user_id       bigint           not null                             comment '报工人（用户）',
  report_qty           decimal(18,6)    not null                             comment '报工数量（合格 + 不良）',
  qualified_qty        decimal(18,6)    not null                             comment '合格数量',
  defect_qty           decimal(18,6)    not null default 0                   comment '不良数量',
  work_minutes         decimal(18,4)    default null                         comment '实际工时（分钟）',
  start_time           datetime         default null                         comment '开始时间',
  end_time             datetime         default null                         comment '结束时间',
  report_time          datetime         not null                             comment '报工时间',
  batch_code           varchar(64)      default null                         comment '批次号',
  report_status        varchar(32)      not null default 'reported'          comment '报工状态（reported 已提交、confirmed 已确认、cancelled 已撤销）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (report_id),
  key idx_production_report_tenant (tenant_id),
  key idx_production_report_workorder_id (workorder_id),
  key idx_production_report_task_id (task_id),
  key idx_production_report_report_user_id (report_user_id),
  key idx_production_report_report_time (report_time)
) engine=innodb default charset=utf8mb4 comment = '报工记录';

-- ----------------------------
-- 领退料单
-- ----------------------------
create table mes_material_request (
  request_id           bigint           not null                             comment '主键',
  request_code         varchar(64)      not null                             comment '单号',
  request_type         varchar(32)      not null                             comment '类型（issue 领料、supplement 补料、return 退料）',
  workorder_id         bigint           not null                             comment '工单',
  warehouse_id         bigint           not null                             comment '仓库',
  request_status       varchar(32)      not null default 'draft'             comment '单据状态（draft 草稿、submitted 已提交、completed 已完成、cancelled 已取消）',
  submit_time          datetime         default null                         comment '提交时间',
  complete_time        datetime         default null                         comment '完成时间',
  operator_user_id     bigint           default null                         comment '仓库执行人（用户）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (request_id),
  key idx_material_request_tenant (tenant_id),
  key idx_material_request_request_code (request_code),
  key idx_material_request_workorder_id (workorder_id),
  key idx_material_request_request_status (request_status)
) engine=innodb default charset=utf8mb4 comment = '领退料单';

-- ----------------------------
-- 领退料明细
-- ----------------------------
create table mes_material_request_item (
  item_id              bigint           not null                             comment '主键',
  request_id           bigint           not null                             comment '领退料单',
  material_id          bigint           not null                             comment '物料',
  plan_qty             decimal(18,6)    default null                         comment '按 BOM 计算的需求数量',
  request_qty          decimal(18,6)    not null                             comment '申请数量',
  actual_qty           decimal(18,6)    not null default 0                   comment '实发（实退）数量',
  batch_code           varchar(64)      default null                         comment '物料批次号',
  location_id          bigint           default null                         comment '库位',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  remark               varchar(500)     default null                         comment '备注',
  primary key (item_id),
  key idx_material_request_item_tenant (tenant_id),
  key idx_material_request_item_request_id (request_id),
  key idx_material_request_item_material_id (material_id),
  key idx_material_request_item_batch_code (batch_code)
) engine=innodb default charset=utf8mb4 comment = '领退料明细';

-- ----------------------------
-- 完工入库单
-- ----------------------------
create table mes_stock_in (
  stock_in_id          bigint           not null                             comment '主键',
  stock_in_code        varchar(64)      not null                             comment '入库单号',
  workorder_id         bigint           not null                             comment '工单',
  material_id          bigint           not null                             comment '产品',
  warehouse_id         bigint           not null                             comment '仓库',
  location_id          bigint           default null                         comment '库位',
  qty                  decimal(18,6)    not null                             comment '入库数量',
  batch_code           varchar(64)      default null                         comment '批次号',
  stock_in_status      varchar(32)      not null default 'submitted'         comment '单据状态（submitted 已提交、completed 已入库、cancelled 已取消）',
  complete_time        datetime         default null                         comment '入库时间',
  operator_user_id     bigint           default null                         comment '仓库执行人（用户）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (stock_in_id),
  key idx_stock_in_tenant (tenant_id),
  key idx_stock_in_stock_in_code (stock_in_code),
  key idx_stock_in_workorder_id (workorder_id)
) engine=innodb default charset=utf8mb4 comment = '完工入库单';

-- ----------------------------
-- 检验方案
-- ----------------------------
create table mes_inspection_scheme (
  scheme_id            bigint           not null                             comment '主键',
  scheme_code          varchar(64)      not null                             comment '方案编码',
  scheme_name          varchar(128)     not null                             comment '方案名称',
  inspection_type      varchar(32)      not null                             comment '检验类型（first 首检、patrol 巡检、final 完工检）',
  material_id          bigint           default null                         comment '适用产品（为空表示通用）',
  operation_code       varchar(64)      default null                         comment '适用工序编码（为空表示不限）',
  sample_qty           decimal(18,6)    default null                         comment '抽样数量',
  check_items          varchar(2000)    default null                         comment '检验项目与标准（每行一项）',
  status               char(1)          not null default '0'                 comment '状态（0正常 1停用）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (scheme_id),
  key idx_inspection_scheme_tenant (tenant_id),
  key idx_inspection_scheme_inspection_type (inspection_type),
  key idx_inspection_scheme_material_id (material_id)
) engine=innodb default charset=utf8mb4 comment = '检验方案';

-- ----------------------------
-- 检验单
-- ----------------------------
create table mes_inspection (
  inspection_id        bigint           not null                             comment '主键',
  inspection_code      varchar(64)      not null                             comment '检验单号',
  inspection_type      varchar(32)      not null                             comment '检验类型（first 首检、patrol 巡检、final 完工检）',
  workorder_id         bigint           not null                             comment '工单',
  task_id              bigint           default null                         comment '工序任务（完工检可为空）',
  scheme_id            bigint           default null                         comment '检验方案',
  material_id          bigint           not null                             comment '产品',
  inspect_qty          decimal(18,6)    default null                         comment '检验数量',
  qualified_qty        decimal(18,6)    default null                         comment '合格数量',
  defect_qty           decimal(18,6)    default null                         comment '不良数量',
  inspector_user_id    bigint           default null                         comment '检验员（用户）',
  inspection_time      datetime         default null                         comment '检验时间',
  inspection_status    varchar(32)      not null default 'pending'           comment '检验状态（pending 待检验、completed 已完成）',
  inspection_result    varchar(32)      default null                         comment '检验结论（passed 合格、rejected 不合格）',
  result_remark        varchar(500)     default null                         comment '检验说明',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (inspection_id),
  key idx_inspection_tenant (tenant_id),
  key idx_inspection_inspection_code (inspection_code),
  key idx_inspection_workorder_id (workorder_id),
  key idx_inspection_task_id (task_id),
  key idx_inspection_inspection_status (inspection_status)
) engine=innodb default charset=utf8mb4 comment = '检验单';

-- ----------------------------
-- 不良记录
-- ----------------------------
create table mes_defect_record (
  defect_id            bigint           not null                             comment '主键',
  defect_code          varchar(64)      not null                             comment '不良单号',
  source_type          varchar(32)      not null                             comment '来源（report 报工、inspection 检验、manual 手工登记）',
  source_id            bigint           default null                         comment '来源单据',
  workorder_id         bigint           not null                             comment '工单',
  task_id              bigint           default null                         comment '工序任务',
  material_id          bigint           not null                             comment '产品',
  defect_qty           decimal(18,6)    not null                             comment '不良数量',
  defect_level         varchar(32)      not null default 'minor'             comment '不良等级（minor 轻微、major 严重、critical 致命）',
  description          varchar(500)     default null                         comment '不良描述',
  handle_method        varchar(32)      default null                         comment '处置方式（rework 返工、scrap 报废、concession 让步接收）',
  handle_status        varchar(32)      not null default 'pending'           comment '处置状态（pending 待处置、handled 已处置）',
  handle_user_id       bigint           default null                         comment '处置人（用户）',
  handle_time          datetime         default null                         comment '处置时间',
  handle_remark        varchar(500)     default null                         comment '处置说明',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (defect_id),
  key idx_defect_record_tenant (tenant_id),
  key idx_defect_record_workorder_id (workorder_id),
  key idx_defect_record_handle_status (handle_status),
  key idx_defect_record_source_type_source_id (source_type, source_id)
) engine=innodb default charset=utf8mb4 comment = '不良记录';

-- ----------------------------
-- 编码规则
-- ----------------------------
create table mes_code_rule (
  rule_id              bigint           not null                             comment '主键',
  rule_code            varchar(64)      not null                             comment '规则编码（业务标识，例如 WORKORDER）',
  rule_name            varchar(128)     not null                             comment '规则名称',
  prefix               varchar(32)      default null                         comment '前缀',
  date_pattern         varchar(16)      default null                         comment '日期格式（yyyyMMdd、yyMMdd、yyyyMM，为空表示不带日期）',
  serial_length        int              not null default 4                   comment '流水号位数',
  reset_cycle          varchar(16)      not null default 'day'               comment '流水号重置周期（none 不重置、day 每天、month 每月、year 每年）',
  status               char(1)          not null default '0'                 comment '状态（0正常 1停用）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (rule_id),
  key idx_code_rule_tenant (tenant_id),
  key idx_code_rule_rule_code (rule_code)
) engine=innodb default charset=utf8mb4 comment = '编码规则';

-- ----------------------------
-- 编码流水号
-- ----------------------------
create table mes_code_sequence (
  sequence_id          bigint           not null                             comment '主键',
  rule_code            varchar(64)      not null                             comment '规则编码',
  period_key           varchar(16)      not null                             comment '周期（按重置周期取日期，例如 20261108；不重置时为 all）',
  current_value        bigint           not null default 0                   comment '当前流水号',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  primary key (sequence_id),
  unique key uk_code_sequence_rule_code_period_key (tenant_id, rule_code, period_key),
  key idx_code_sequence_tenant (tenant_id)
) engine=innodb default charset=utf8mb4 comment = '编码流水号';

-- ----------------------------
-- 产品序列号
-- ----------------------------
create table mes_sn (
  sn_id                bigint           not null                             comment '主键',
  sn_code              varchar(128)     not null                             comment '序列号',
  workorder_id         bigint           not null                             comment '工单',
  material_id          bigint           not null                             comment '产品',
  batch_code           varchar(64)      default null                         comment '生产批次号',
  sn_status            varchar(32)      not null default 'created'           comment '状态（created 已生成、completed 已完工、scrapped 已报废）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (sn_id),
  key idx_sn_tenant (tenant_id),
  key idx_sn_sn_code (sn_code),
  key idx_sn_workorder_id (workorder_id)
) engine=innodb default charset=utf8mb4 comment = '产品序列号';

-- ----------------------------
-- 安灯事件
-- ----------------------------
create table mes_andon_event (
  event_id             bigint           not null                             comment '主键',
  event_code           varchar(64)      not null                             comment '事件编号',
  event_type           varchar(32)      not null                             comment '异常类型（equipment 设备、quality 质量、material 物料、process 工艺、other 其他）',
  event_level          varchar(32)      not null default 'normal'            comment '紧急程度（normal 一般、urgent 紧急、critical 停线）',
  title                varchar(128)     not null                             comment '标题',
  content              varchar(500)     default null                         comment '异常描述',
  workorder_id         bigint           default null                         comment '关联工单',
  workshop_id          bigint           not null                             comment '车间',
  line_id              bigint           default null                         comment '产线',
  station_id           bigint           default null                         comment '工位',
  device_id            bigint           default null                         comment '设备',
  downtime_reason_id   bigint           default null                         comment '停机原因',
  report_user_id       bigint           not null                             comment '上报人（用户）',
  report_time          datetime         not null                             comment '上报时间',
  handler_user_id      bigint           default null                         comment '处理人（用户）',
  assign_time          datetime         default null                         comment '派单时间',
  response_time        datetime         default null                         comment '开始处理时间',
  resolve_time         datetime         default null                         comment '解决时间',
  close_time           datetime         default null                         comment '关闭时间',
  downtime_minutes     int              default null                         comment '停机时长（分钟，从上报到解决）',
  resolve_remark       varchar(500)     default null                         comment '处理说明',
  event_status         varchar(32)      not null default 'reported'          comment '事件状态（reported 已上报、assigned 已派单、processing 处理中、resolved 已解决、closed 已关闭）',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (event_id),
  key idx_andon_event_tenant (tenant_id),
  key idx_andon_event_event_code (event_code),
  key idx_andon_event_event_status (event_status),
  key idx_andon_event_workshop_id (workshop_id),
  key idx_andon_event_handler_user_id (handler_user_id),
  key idx_andon_event_report_time (report_time)
) engine=innodb default charset=utf8mb4 comment = '安灯事件';

-- ----------------------------
-- 停机原因
-- ----------------------------
create table mes_downtime_reason (
  reason_id            bigint           not null                             comment '主键',
  reason_code          varchar(64)      not null                             comment '原因编码',
  reason_name          varchar(128)     not null                             comment '原因名称',
  reason_type          varchar(32)      not null default 'other'             comment '原因分类（equipment 设备、material 物料、quality 质量、personnel 人员、other 其他）',
  status               char(1)          not null default '0'                 comment '状态（0正常 1停用）',
  sort_no              int              not null default 0                   comment '排序号',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  del_flag             char(1)          not null default '0'                 comment '删除标志（0 存在 1 删除）',
  remark               varchar(500)     default null                         comment '备注',
  primary key (reason_id),
  key idx_downtime_reason_tenant (tenant_id),
  key idx_downtime_reason_reason_code (reason_code)
) engine=innodb default charset=utf8mb4 comment = '停机原因';

-- ----------------------------
-- 状态履历
-- ----------------------------
create table mes_status_log (
  log_id               bigint           not null                             comment '主键',
  biz_type             varchar(32)      not null                             comment '单据类型（workorder、operation_task、production_report 等）',
  biz_id               bigint           not null                             comment '单据',
  biz_code             varchar(64)      default null                         comment '单据编号',
  action               varchar(32)      not null                             comment '动作（release、pause、complete 等）',
  from_status          varchar(32)      default null                         comment '变更前状态',
  to_status            varchar(32)      not null                             comment '变更后状态',
  reason               varchar(500)     default null                         comment '原因或说明',
  operator_user_id     bigint           default null                         comment '操作人（用户）',
  operate_time         datetime         not null                             comment '操作时间',
  tenant_id            varchar(20)      not null default 'default'           comment '租户编码',
  create_dept          bigint           default null                         comment '创建部门',
  create_by            bigint           default null                         comment '创建者',
  create_time          datetime         default null                         comment '创建时间',
  update_by            bigint           default null                         comment '更新者',
  update_time          datetime         default null                         comment '更新时间',
  primary key (log_id),
  key idx_status_log_tenant (tenant_id),
  key idx_status_log_biz_type_biz_id (biz_type, biz_id)
) engine=innodb default charset=utf8mb4 comment = '状态履历';

-- ----------------------------
-- 默认租户的编码规则（其他租户没有规则时按同样的格式生成）
-- ----------------------------
insert into mes_code_rule (rule_id, rule_code, rule_name, prefix, date_pattern, serial_length, reset_cycle, status, tenant_id, create_time, del_flag) values (1762000000000001001, 'WORKORDER', '生产工单', 'WO', 'yyyyMMdd', 4, 'day', '0', 'default', now(), '0');
insert into mes_code_rule (rule_id, rule_code, rule_name, prefix, date_pattern, serial_length, reset_cycle, status, tenant_id, create_time, del_flag) values (1762000000000001002, 'TASK', '工序任务', 'OT', 'yyyyMMdd', 5, 'day', '0', 'default', now(), '0');
insert into mes_code_rule (rule_id, rule_code, rule_name, prefix, date_pattern, serial_length, reset_cycle, status, tenant_id, create_time, del_flag) values (1762000000000001003, 'REPORT', '报工记录', 'RP', 'yyyyMMdd', 5, 'day', '0', 'default', now(), '0');
insert into mes_code_rule (rule_id, rule_code, rule_name, prefix, date_pattern, serial_length, reset_cycle, status, tenant_id, create_time, del_flag) values (1762000000000001004, 'MATERIAL_ISSUE', '领料单', 'LL', 'yyyyMMdd', 4, 'day', '0', 'default', now(), '0');
insert into mes_code_rule (rule_id, rule_code, rule_name, prefix, date_pattern, serial_length, reset_cycle, status, tenant_id, create_time, del_flag) values (1762000000000001005, 'MATERIAL_SUPPLEMENT', '补料单', 'BL', 'yyyyMMdd', 4, 'day', '0', 'default', now(), '0');
insert into mes_code_rule (rule_id, rule_code, rule_name, prefix, date_pattern, serial_length, reset_cycle, status, tenant_id, create_time, del_flag) values (1762000000000001006, 'MATERIAL_RETURN', '退料单', 'TL', 'yyyyMMdd', 4, 'day', '0', 'default', now(), '0');
insert into mes_code_rule (rule_id, rule_code, rule_name, prefix, date_pattern, serial_length, reset_cycle, status, tenant_id, create_time, del_flag) values (1762000000000001007, 'STOCK_IN', '完工入库单', 'RK', 'yyyyMMdd', 4, 'day', '0', 'default', now(), '0');
insert into mes_code_rule (rule_id, rule_code, rule_name, prefix, date_pattern, serial_length, reset_cycle, status, tenant_id, create_time, del_flag) values (1762000000000001008, 'INSPECTION', '检验单', 'QC', 'yyyyMMdd', 4, 'day', '0', 'default', now(), '0');
insert into mes_code_rule (rule_id, rule_code, rule_name, prefix, date_pattern, serial_length, reset_cycle, status, tenant_id, create_time, del_flag) values (1762000000000001009, 'DEFECT', '不良记录', 'NG', 'yyyyMMdd', 4, 'day', '0', 'default', now(), '0');
insert into mes_code_rule (rule_id, rule_code, rule_name, prefix, date_pattern, serial_length, reset_cycle, status, tenant_id, create_time, del_flag) values (1762000000000001010, 'ANDON', '安灯事件', 'AD', 'yyyyMMdd', 4, 'day', '0', 'default', now(), '0');
insert into mes_code_rule (rule_id, rule_code, rule_name, prefix, date_pattern, serial_length, reset_cycle, status, tenant_id, create_time, del_flag) values (1762000000000001011, 'BATCH', '生产批次号', 'B', 'yyMMdd', 3, 'day', '0', 'default', now(), '0');
insert into mes_code_rule (rule_id, rule_code, rule_name, prefix, date_pattern, serial_length, reset_cycle, status, tenant_id, create_time, del_flag) values (1762000000000001012, 'SN', '产品序列号', 'SN', 'yyMMdd', 6, 'day', '0', 'default', now(), '0');
