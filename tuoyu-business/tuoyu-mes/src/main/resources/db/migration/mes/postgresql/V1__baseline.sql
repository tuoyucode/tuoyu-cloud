-- 制造执行（MES）库 tuoyu-mes 的基线：PostgreSQL
-- 全新安装与 Flyway 的 V1 基线使用同一份脚本，发布后不再修改，改动写成新的迁移脚本

-- ----------------------------
-- 工厂
-- ----------------------------
create table if not exists mes_factory
(
    factory_id           int8           not null,
    factory_code         varchar(64)    not null,
    factory_name         varchar(128)   not null,
    short_name           varchar(64)    null,
    address              varchar(255)   null,
    contact              varchar(64)    null,
    contact_phone        varchar(32)    null,
    status               char(1)        not null default '0',
    sort_no              int4           not null default 0,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_factory_pk primary key (factory_id)
);

create index if not exists idx_factory_tenant on mes_factory (tenant_id);
create index if not exists idx_factory_factory_code on mes_factory (factory_code);

comment on table mes_factory is '工厂';
comment on column mes_factory.factory_id is '主键';
comment on column mes_factory.factory_code is '工厂编码';
comment on column mes_factory.factory_name is '工厂名称';
comment on column mes_factory.short_name is '工厂简称';
comment on column mes_factory.address is '地址';
comment on column mes_factory.contact is '联系人';
comment on column mes_factory.contact_phone is '联系电话';
comment on column mes_factory.status is '状态（0正常 1停用）';
comment on column mes_factory.sort_no is '排序号';
comment on column mes_factory.tenant_id is '租户编码';
comment on column mes_factory.create_dept is '创建部门';
comment on column mes_factory.create_by is '创建者';
comment on column mes_factory.create_time is '创建时间';
comment on column mes_factory.update_by is '更新者';
comment on column mes_factory.update_time is '更新时间';
comment on column mes_factory.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_factory.remark is '备注';

-- ----------------------------
-- 车间
-- ----------------------------
create table if not exists mes_workshop
(
    workshop_id          int8           not null,
    factory_id           int8           not null,
    workshop_code        varchar(64)    not null,
    workshop_name        varchar(128)   not null,
    dept_id              int8           null,
    manager              varchar(64)    null,
    status               char(1)        not null default '0',
    sort_no              int4           not null default 0,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_workshop_pk primary key (workshop_id)
);

create index if not exists idx_workshop_tenant on mes_workshop (tenant_id);
create index if not exists idx_workshop_factory_id on mes_workshop (factory_id);
create index if not exists idx_workshop_workshop_code on mes_workshop (workshop_code);

comment on table mes_workshop is '车间';
comment on column mes_workshop.workshop_id is '主键';
comment on column mes_workshop.factory_id is '所属工厂';
comment on column mes_workshop.workshop_code is '车间编码';
comment on column mes_workshop.workshop_name is '车间名称';
comment on column mes_workshop.dept_id is '对应的组织（部门）';
comment on column mes_workshop.manager is '负责人';
comment on column mes_workshop.status is '状态（0正常 1停用）';
comment on column mes_workshop.sort_no is '排序号';
comment on column mes_workshop.tenant_id is '租户编码';
comment on column mes_workshop.create_dept is '创建部门';
comment on column mes_workshop.create_by is '创建者';
comment on column mes_workshop.create_time is '创建时间';
comment on column mes_workshop.update_by is '更新者';
comment on column mes_workshop.update_time is '更新时间';
comment on column mes_workshop.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_workshop.remark is '备注';

-- ----------------------------
-- 产线
-- ----------------------------
create table if not exists mes_line
(
    line_id              int8           not null,
    factory_id           int8           not null,
    workshop_id          int8           not null,
    line_code            varchar(64)    not null,
    line_name            varchar(128)   not null,
    status               char(1)        not null default '0',
    sort_no              int4           not null default 0,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_line_pk primary key (line_id)
);

create index if not exists idx_line_tenant on mes_line (tenant_id);
create index if not exists idx_line_workshop_id on mes_line (workshop_id);
create index if not exists idx_line_line_code on mes_line (line_code);

comment on table mes_line is '产线';
comment on column mes_line.line_id is '主键';
comment on column mes_line.factory_id is '所属工厂';
comment on column mes_line.workshop_id is '所属车间';
comment on column mes_line.line_code is '产线编码';
comment on column mes_line.line_name is '产线名称';
comment on column mes_line.status is '状态（0正常 1停用）';
comment on column mes_line.sort_no is '排序号';
comment on column mes_line.tenant_id is '租户编码';
comment on column mes_line.create_dept is '创建部门';
comment on column mes_line.create_by is '创建者';
comment on column mes_line.create_time is '创建时间';
comment on column mes_line.update_by is '更新者';
comment on column mes_line.update_time is '更新时间';
comment on column mes_line.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_line.remark is '备注';

-- ----------------------------
-- 工位
-- ----------------------------
create table if not exists mes_station
(
    station_id           int8           not null,
    factory_id           int8           not null,
    workshop_id          int8           not null,
    line_id              int8           not null,
    station_code         varchar(64)    not null,
    station_name         varchar(128)   not null,
    station_type         varchar(32)    null,
    status               char(1)        not null default '0',
    sort_no              int4           not null default 0,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_station_pk primary key (station_id)
);

create index if not exists idx_station_tenant on mes_station (tenant_id);
create index if not exists idx_station_line_id on mes_station (line_id);
create index if not exists idx_station_station_code on mes_station (station_code);

comment on table mes_station is '工位';
comment on column mes_station.station_id is '主键';
comment on column mes_station.factory_id is '所属工厂';
comment on column mes_station.workshop_id is '所属车间';
comment on column mes_station.line_id is '所属产线';
comment on column mes_station.station_code is '工位编码';
comment on column mes_station.station_name is '工位名称';
comment on column mes_station.station_type is '工位类型（与工序的建议工位类型对应）';
comment on column mes_station.status is '状态（0正常 1停用）';
comment on column mes_station.sort_no is '排序号';
comment on column mes_station.tenant_id is '租户编码';
comment on column mes_station.create_dept is '创建部门';
comment on column mes_station.create_by is '创建者';
comment on column mes_station.create_time is '创建时间';
comment on column mes_station.update_by is '更新者';
comment on column mes_station.update_time is '更新时间';
comment on column mes_station.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_station.remark is '备注';

-- ----------------------------
-- 班组
-- ----------------------------
create table if not exists mes_team
(
    team_id              int8           not null,
    factory_id           int8           not null,
    workshop_id          int8           not null,
    team_code            varchar(64)    not null,
    team_name            varchar(128)   not null,
    leader_user_id       int8           null,
    status               char(1)        not null default '0',
    sort_no              int4           not null default 0,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_team_pk primary key (team_id)
);

create index if not exists idx_team_tenant on mes_team (tenant_id);
create index if not exists idx_team_workshop_id on mes_team (workshop_id);
create index if not exists idx_team_team_code on mes_team (team_code);

comment on table mes_team is '班组';
comment on column mes_team.team_id is '主键';
comment on column mes_team.factory_id is '所属工厂';
comment on column mes_team.workshop_id is '所属车间';
comment on column mes_team.team_code is '班组编码';
comment on column mes_team.team_name is '班组名称';
comment on column mes_team.leader_user_id is '班组长（用户）';
comment on column mes_team.status is '状态（0正常 1停用）';
comment on column mes_team.sort_no is '排序号';
comment on column mes_team.tenant_id is '租户编码';
comment on column mes_team.create_dept is '创建部门';
comment on column mes_team.create_by is '创建者';
comment on column mes_team.create_time is '创建时间';
comment on column mes_team.update_by is '更新者';
comment on column mes_team.update_time is '更新时间';
comment on column mes_team.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_team.remark is '备注';

-- ----------------------------
-- 班组成员
-- ----------------------------
create table if not exists mes_team_member
(
    member_id            int8           not null,
    team_id              int8           not null,
    user_id              int8           not null,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    constraint mes_team_member_pk primary key (member_id)
);

create index if not exists idx_team_member_tenant on mes_team_member (tenant_id);
create index if not exists idx_team_member_team_id on mes_team_member (team_id);
create index if not exists idx_team_member_user_id on mes_team_member (user_id);

comment on table mes_team_member is '班组成员';
comment on column mes_team_member.member_id is '主键';
comment on column mes_team_member.team_id is '班组';
comment on column mes_team_member.user_id is '成员（用户）';
comment on column mes_team_member.tenant_id is '租户编码';
comment on column mes_team_member.create_dept is '创建部门';
comment on column mes_team_member.create_by is '创建者';
comment on column mes_team_member.create_time is '创建时间';
comment on column mes_team_member.update_by is '更新者';
comment on column mes_team_member.update_time is '更新时间';

-- ----------------------------
-- 物料
-- ----------------------------
create table if not exists mes_material
(
    material_id          int8           not null,
    material_code        varchar(64)    not null,
    material_name        varchar(128)   not null,
    specification        varchar(255)   null,
    material_type        varchar(32)    not null default 'raw',
    unit                 varchar(32)    not null,
    trace_mode           varchar(32)    not null default 'none',
    status               char(1)        not null default '0',
    sort_no              int4           not null default 0,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_material_pk primary key (material_id)
);

create index if not exists idx_material_tenant on mes_material (tenant_id);
create index if not exists idx_material_material_code on mes_material (material_code);
create index if not exists idx_material_material_type on mes_material (material_type);

comment on table mes_material is '物料';
comment on column mes_material.material_id is '主键';
comment on column mes_material.material_code is '物料编码';
comment on column mes_material.material_name is '物料名称';
comment on column mes_material.specification is '规格型号';
comment on column mes_material.material_type is '物料类型（product 成品、semi 半成品、raw 原材料、aux 辅料）';
comment on column mes_material.unit is '计量单位';
comment on column mes_material.trace_mode is '追溯方式（none 不追溯、batch 按批次、sn 按序列号）';
comment on column mes_material.status is '状态（0正常 1停用）';
comment on column mes_material.sort_no is '排序号';
comment on column mes_material.tenant_id is '租户编码';
comment on column mes_material.create_dept is '创建部门';
comment on column mes_material.create_by is '创建者';
comment on column mes_material.create_time is '创建时间';
comment on column mes_material.update_by is '更新者';
comment on column mes_material.update_time is '更新时间';
comment on column mes_material.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_material.remark is '备注';

-- ----------------------------
-- 物料清单
-- ----------------------------
create table if not exists mes_bom
(
    bom_id               int8           not null,
    material_id          int8           not null,
    bom_code             varchar(64)    not null,
    bom_version          varchar(32)    not null default 'V1',
    base_qty             numeric(18,6)  not null default 1,
    is_default           char(1)        not null default 'N',
    status               char(1)        not null default '0',
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_bom_pk primary key (bom_id)
);

create index if not exists idx_bom_tenant on mes_bom (tenant_id);
create index if not exists idx_bom_material_id on mes_bom (material_id);
create index if not exists idx_bom_bom_code on mes_bom (bom_code);

comment on table mes_bom is '物料清单';
comment on column mes_bom.bom_id is '主键';
comment on column mes_bom.material_id is '产品（成品或半成品）';
comment on column mes_bom.bom_code is 'BOM 编码';
comment on column mes_bom.bom_version is '版本';
comment on column mes_bom.base_qty is '基准数量（明细用量对应的产品数量）';
comment on column mes_bom.is_default is '是否默认版本（Y 是 N 否）';
comment on column mes_bom.status is '状态（0正常 1停用）';
comment on column mes_bom.tenant_id is '租户编码';
comment on column mes_bom.create_dept is '创建部门';
comment on column mes_bom.create_by is '创建者';
comment on column mes_bom.create_time is '创建时间';
comment on column mes_bom.update_by is '更新者';
comment on column mes_bom.update_time is '更新时间';
comment on column mes_bom.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_bom.remark is '备注';

-- ----------------------------
-- 物料清单明细
-- ----------------------------
create table if not exists mes_bom_item
(
    item_id              int8           not null,
    bom_id               int8           not null,
    material_id          int8           not null,
    qty                  numeric(18,6)  not null,
    loss_rate            numeric(18,4)  not null default 0,
    sort_no              int4           not null default 0,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    remark               varchar(500)   null,
    constraint mes_bom_item_pk primary key (item_id)
);

create index if not exists idx_bom_item_tenant on mes_bom_item (tenant_id);
create index if not exists idx_bom_item_bom_id on mes_bom_item (bom_id);
create index if not exists idx_bom_item_material_id on mes_bom_item (material_id);

comment on table mes_bom_item is '物料清单明细';
comment on column mes_bom_item.item_id is '主键';
comment on column mes_bom_item.bom_id is '物料清单';
comment on column mes_bom_item.material_id is '子件物料';
comment on column mes_bom_item.qty is '用量（对应基准数量）';
comment on column mes_bom_item.loss_rate is '损耗率（0.05 表示 5%）';
comment on column mes_bom_item.sort_no is '排序号';
comment on column mes_bom_item.tenant_id is '租户编码';
comment on column mes_bom_item.create_dept is '创建部门';
comment on column mes_bom_item.create_by is '创建者';
comment on column mes_bom_item.create_time is '创建时间';
comment on column mes_bom_item.update_by is '更新者';
comment on column mes_bom_item.update_time is '更新时间';
comment on column mes_bom_item.remark is '备注';

-- ----------------------------
-- 工艺路线
-- ----------------------------
create table if not exists mes_route
(
    route_id             int8           not null,
    material_id          int8           not null,
    route_code           varchar(64)    not null,
    route_name           varchar(128)   not null,
    route_version        varchar(32)    not null default 'V1',
    is_default           char(1)        not null default 'N',
    status               char(1)        not null default '0',
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_route_pk primary key (route_id)
);

create index if not exists idx_route_tenant on mes_route (tenant_id);
create index if not exists idx_route_material_id on mes_route (material_id);
create index if not exists idx_route_route_code on mes_route (route_code);

comment on table mes_route is '工艺路线';
comment on column mes_route.route_id is '主键';
comment on column mes_route.material_id is '产品';
comment on column mes_route.route_code is '工艺路线编码';
comment on column mes_route.route_name is '工艺路线名称';
comment on column mes_route.route_version is '版本';
comment on column mes_route.is_default is '是否默认版本（Y 是 N 否）';
comment on column mes_route.status is '状态（0正常 1停用）';
comment on column mes_route.tenant_id is '租户编码';
comment on column mes_route.create_dept is '创建部门';
comment on column mes_route.create_by is '创建者';
comment on column mes_route.create_time is '创建时间';
comment on column mes_route.update_by is '更新者';
comment on column mes_route.update_time is '更新时间';
comment on column mes_route.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_route.remark is '备注';

-- ----------------------------
-- 工艺路线工序
-- ----------------------------
create table if not exists mes_route_operation
(
    operation_id         int8           not null,
    route_id             int8           not null,
    seq_no               int4           not null,
    operation_code       varchar(64)    not null,
    operation_name       varchar(128)   not null,
    station_type         varchar(32)    null,
    standard_minutes     numeric(18,4)  not null default 0,
    first_inspection     char(1)        not null default 'N',
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    remark               varchar(500)   null,
    constraint mes_route_operation_pk primary key (operation_id)
);

create index if not exists idx_route_operation_tenant on mes_route_operation (tenant_id);
create index if not exists idx_route_operation_route_id on mes_route_operation (route_id);

comment on table mes_route_operation is '工艺路线工序';
comment on column mes_route_operation.operation_id is '主键';
comment on column mes_route_operation.route_id is '工艺路线';
comment on column mes_route_operation.seq_no is '工序顺序（按升序执行）';
comment on column mes_route_operation.operation_code is '工序编码';
comment on column mes_route_operation.operation_name is '工序名称';
comment on column mes_route_operation.station_type is '建议工位类型';
comment on column mes_route_operation.standard_minutes is '单件标准工时（分钟）';
comment on column mes_route_operation.first_inspection is '是否需要首检合格后才能批量报工（Y 是 N 否）';
comment on column mes_route_operation.tenant_id is '租户编码';
comment on column mes_route_operation.create_dept is '创建部门';
comment on column mes_route_operation.create_by is '创建者';
comment on column mes_route_operation.create_time is '创建时间';
comment on column mes_route_operation.update_by is '更新者';
comment on column mes_route_operation.update_time is '更新时间';
comment on column mes_route_operation.remark is '备注';

-- ----------------------------
-- 设备
-- ----------------------------
create table if not exists mes_device
(
    device_id            int8           not null,
    device_code          varchar(64)    not null,
    device_name          varchar(128)   not null,
    device_type          varchar(32)    null,
    model                varchar(128)   null,
    manufacturer         varchar(128)   null,
    factory_id           int8           not null,
    workshop_id          int8           null,
    line_id              int8           null,
    station_id           int8           null,
    status               char(1)        not null default '0',
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_device_pk primary key (device_id)
);

create index if not exists idx_device_tenant on mes_device (tenant_id);
create index if not exists idx_device_device_code on mes_device (device_code);
create index if not exists idx_device_workshop_id on mes_device (workshop_id);

comment on table mes_device is '设备';
comment on column mes_device.device_id is '主键';
comment on column mes_device.device_code is '设备编码';
comment on column mes_device.device_name is '设备名称';
comment on column mes_device.device_type is '设备类型';
comment on column mes_device.model is '型号';
comment on column mes_device.manufacturer is '制造商';
comment on column mes_device.factory_id is '所属工厂';
comment on column mes_device.workshop_id is '所属车间';
comment on column mes_device.line_id is '所属产线';
comment on column mes_device.station_id is '所属工位';
comment on column mes_device.status is '状态（0正常 1停用）';
comment on column mes_device.tenant_id is '租户编码';
comment on column mes_device.create_dept is '创建部门';
comment on column mes_device.create_by is '创建者';
comment on column mes_device.create_time is '创建时间';
comment on column mes_device.update_by is '更新者';
comment on column mes_device.update_time is '更新时间';
comment on column mes_device.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_device.remark is '备注';

-- ----------------------------
-- 仓库
-- ----------------------------
create table if not exists mes_warehouse
(
    warehouse_id         int8           not null,
    factory_id           int8           not null,
    warehouse_code       varchar(64)    not null,
    warehouse_name       varchar(128)   not null,
    warehouse_type       varchar(32)    not null default 'raw',
    status               char(1)        not null default '0',
    sort_no              int4           not null default 0,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_warehouse_pk primary key (warehouse_id)
);

create index if not exists idx_warehouse_tenant on mes_warehouse (tenant_id);
create index if not exists idx_warehouse_factory_id on mes_warehouse (factory_id);
create index if not exists idx_warehouse_warehouse_code on mes_warehouse (warehouse_code);

comment on table mes_warehouse is '仓库';
comment on column mes_warehouse.warehouse_id is '主键';
comment on column mes_warehouse.factory_id is '所属工厂';
comment on column mes_warehouse.warehouse_code is '仓库编码';
comment on column mes_warehouse.warehouse_name is '仓库名称';
comment on column mes_warehouse.warehouse_type is '仓库类型（raw 原材料、wip 在制品、finished 成品）';
comment on column mes_warehouse.status is '状态（0正常 1停用）';
comment on column mes_warehouse.sort_no is '排序号';
comment on column mes_warehouse.tenant_id is '租户编码';
comment on column mes_warehouse.create_dept is '创建部门';
comment on column mes_warehouse.create_by is '创建者';
comment on column mes_warehouse.create_time is '创建时间';
comment on column mes_warehouse.update_by is '更新者';
comment on column mes_warehouse.update_time is '更新时间';
comment on column mes_warehouse.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_warehouse.remark is '备注';

-- ----------------------------
-- 库位
-- ----------------------------
create table if not exists mes_location
(
    location_id          int8           not null,
    warehouse_id         int8           not null,
    location_code        varchar(64)    not null,
    location_name        varchar(128)   not null,
    status               char(1)        not null default '0',
    sort_no              int4           not null default 0,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_location_pk primary key (location_id)
);

create index if not exists idx_location_tenant on mes_location (tenant_id);
create index if not exists idx_location_warehouse_id on mes_location (warehouse_id);
create index if not exists idx_location_location_code on mes_location (location_code);

comment on table mes_location is '库位';
comment on column mes_location.location_id is '主键';
comment on column mes_location.warehouse_id is '所属仓库';
comment on column mes_location.location_code is '库位编码';
comment on column mes_location.location_name is '库位名称';
comment on column mes_location.status is '状态（0正常 1停用）';
comment on column mes_location.sort_no is '排序号';
comment on column mes_location.tenant_id is '租户编码';
comment on column mes_location.create_dept is '创建部门';
comment on column mes_location.create_by is '创建者';
comment on column mes_location.create_time is '创建时间';
comment on column mes_location.update_by is '更新者';
comment on column mes_location.update_time is '更新时间';
comment on column mes_location.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_location.remark is '备注';

-- ----------------------------
-- 生产工单
-- ----------------------------
create table if not exists mes_workorder
(
    workorder_id         int8           not null,
    workorder_code       varchar(64)    not null,
    source_type          varchar(32)    not null default 'manual',
    source_code          varchar(64)    null,
    material_id          int8           not null,
    bom_id               int8           null,
    route_id             int8           not null,
    factory_id           int8           not null,
    workshop_id          int8           not null,
    line_id              int8           null,
    plan_qty             numeric(18,6)  not null,
    completed_qty        numeric(18,6)  not null default 0,
    defect_qty           numeric(18,6)  not null default 0,
    stocked_qty          numeric(18,6)  not null default 0,
    priority             int4           not null default 0,
    batch_code           varchar(64)    null,
    plan_start_time      timestamp      null,
    plan_end_time        timestamp      null,
    actual_start_time    timestamp      null,
    actual_end_time      timestamp      null,
    workorder_status     varchar(32)    not null default 'draft',
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_workorder_pk primary key (workorder_id)
);

create index if not exists idx_workorder_tenant on mes_workorder (tenant_id);
create index if not exists idx_workorder_workorder_code on mes_workorder (workorder_code);
create index if not exists idx_workorder_workorder_status on mes_workorder (workorder_status);
create index if not exists idx_workorder_material_id on mes_workorder (material_id);
create index if not exists idx_workorder_workshop_id on mes_workorder (workshop_id);

comment on table mes_workorder is '生产工单';
comment on column mes_workorder.workorder_id is '主键';
comment on column mes_workorder.workorder_code is '工单编号';
comment on column mes_workorder.source_type is '来源（manual 手工、erp 外部系统）';
comment on column mes_workorder.source_code is '来源单号（例如销售订单号）';
comment on column mes_workorder.material_id is '产品';
comment on column mes_workorder.bom_id is '物料清单';
comment on column mes_workorder.route_id is '工艺路线';
comment on column mes_workorder.factory_id is '工厂';
comment on column mes_workorder.workshop_id is '车间';
comment on column mes_workorder.line_id is '产线';
comment on column mes_workorder.plan_qty is '计划数量';
comment on column mes_workorder.completed_qty is '完工数量（末道工序的合格数）';
comment on column mes_workorder.defect_qty is '不良数量（各工序累计）';
comment on column mes_workorder.stocked_qty is '已入库数量';
comment on column mes_workorder.priority is '优先级（数值越大越优先）';
comment on column mes_workorder.batch_code is '生产批次号（下达时生成）';
comment on column mes_workorder.plan_start_time is '计划开始时间';
comment on column mes_workorder.plan_end_time is '计划完成时间';
comment on column mes_workorder.actual_start_time is '实际开始时间';
comment on column mes_workorder.actual_end_time is '实际完成时间';
comment on column mes_workorder.workorder_status is '工单状态（draft 草稿、released 已下达、in_progress 生产中、paused 已暂停、completed 已完工、closed 已关闭、cancelled 已取消）';
comment on column mes_workorder.tenant_id is '租户编码';
comment on column mes_workorder.create_dept is '创建部门';
comment on column mes_workorder.create_by is '创建者';
comment on column mes_workorder.create_time is '创建时间';
comment on column mes_workorder.update_by is '更新者';
comment on column mes_workorder.update_time is '更新时间';
comment on column mes_workorder.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_workorder.remark is '备注';

-- ----------------------------
-- 工序任务
-- ----------------------------
create table if not exists mes_operation_task
(
    task_id              int8           not null,
    task_code            varchar(64)    not null,
    workorder_id         int8           not null,
    operation_id         int8           null,
    seq_no               int4           not null,
    operation_code       varchar(64)    not null,
    operation_name       varchar(128)   not null,
    material_id          int8           not null,
    workshop_id          int8           not null,
    line_id              int8           null,
    station_id           int8           null,
    team_id              int8           null,
    assignee_user_id     int8           null,
    plan_qty             numeric(18,6)  not null,
    qualified_qty        numeric(18,6)  not null default 0,
    defect_qty           numeric(18,6)  not null default 0,
    standard_minutes     numeric(18,4)  not null default 0,
    first_inspection     char(1)        not null default 'N',
    actual_start_time    timestamp      null,
    actual_end_time      timestamp      null,
    task_status          varchar(32)    not null default 'pending',
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_operation_task_pk primary key (task_id)
);

create index if not exists idx_operation_task_tenant on mes_operation_task (tenant_id);
create index if not exists idx_operation_task_workorder_id on mes_operation_task (workorder_id);
create index if not exists idx_operation_task_task_status on mes_operation_task (task_status);
create index if not exists idx_operation_task_assignee_user_id on mes_operation_task (assignee_user_id);
create index if not exists idx_operation_task_team_id on mes_operation_task (team_id);

comment on table mes_operation_task is '工序任务';
comment on column mes_operation_task.task_id is '主键';
comment on column mes_operation_task.task_code is '任务编号';
comment on column mes_operation_task.workorder_id is '工单';
comment on column mes_operation_task.operation_id is '工艺路线工序';
comment on column mes_operation_task.seq_no is '工序顺序';
comment on column mes_operation_task.operation_code is '工序编码';
comment on column mes_operation_task.operation_name is '工序名称';
comment on column mes_operation_task.material_id is '产品';
comment on column mes_operation_task.workshop_id is '车间';
comment on column mes_operation_task.line_id is '产线';
comment on column mes_operation_task.station_id is '派工工位';
comment on column mes_operation_task.team_id is '派工班组';
comment on column mes_operation_task.assignee_user_id is '作业人（用户）';
comment on column mes_operation_task.plan_qty is '计划数量';
comment on column mes_operation_task.qualified_qty is '合格数量（累计）';
comment on column mes_operation_task.defect_qty is '不良数量（累计）';
comment on column mes_operation_task.standard_minutes is '单件标准工时（分钟）';
comment on column mes_operation_task.first_inspection is '是否需要首检（Y 是 N 否）';
comment on column mes_operation_task.actual_start_time is '实际开始时间';
comment on column mes_operation_task.actual_end_time is '实际完成时间';
comment on column mes_operation_task.task_status is '任务状态（pending 待派工、dispatched 已派工、in_progress 生产中、completed 已完成、closed 已关闭）';
comment on column mes_operation_task.tenant_id is '租户编码';
comment on column mes_operation_task.create_dept is '创建部门';
comment on column mes_operation_task.create_by is '创建者';
comment on column mes_operation_task.create_time is '创建时间';
comment on column mes_operation_task.update_by is '更新者';
comment on column mes_operation_task.update_time is '更新时间';
comment on column mes_operation_task.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_operation_task.remark is '备注';

-- ----------------------------
-- 报工记录
-- ----------------------------
create table if not exists mes_production_report
(
    report_id            int8           not null,
    report_code          varchar(64)    not null,
    workorder_id         int8           not null,
    task_id              int8           not null,
    material_id          int8           not null,
    workshop_id          int8           not null,
    line_id              int8           null,
    station_id           int8           null,
    team_id              int8           null,
    report_user_id       int8           not null,
    report_qty           numeric(18,6)  not null,
    qualified_qty        numeric(18,6)  not null,
    defect_qty           numeric(18,6)  not null default 0,
    work_minutes         numeric(18,4)  null,
    start_time           timestamp      null,
    end_time             timestamp      null,
    report_time          timestamp      not null,
    batch_code           varchar(64)    null,
    report_status        varchar(32)    not null default 'reported',
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_production_report_pk primary key (report_id)
);

create index if not exists idx_production_report_tenant on mes_production_report (tenant_id);
create index if not exists idx_production_report_workorder_id on mes_production_report (workorder_id);
create index if not exists idx_production_report_task_id on mes_production_report (task_id);
create index if not exists idx_production_report_report_user_id on mes_production_report (report_user_id);
create index if not exists idx_production_report_report_time on mes_production_report (report_time);

comment on table mes_production_report is '报工记录';
comment on column mes_production_report.report_id is '主键';
comment on column mes_production_report.report_code is '报工编号';
comment on column mes_production_report.workorder_id is '工单';
comment on column mes_production_report.task_id is '工序任务';
comment on column mes_production_report.material_id is '产品';
comment on column mes_production_report.workshop_id is '车间';
comment on column mes_production_report.line_id is '产线';
comment on column mes_production_report.station_id is '工位';
comment on column mes_production_report.team_id is '班组';
comment on column mes_production_report.report_user_id is '报工人（用户）';
comment on column mes_production_report.report_qty is '报工数量（合格 + 不良）';
comment on column mes_production_report.qualified_qty is '合格数量';
comment on column mes_production_report.defect_qty is '不良数量';
comment on column mes_production_report.work_minutes is '实际工时（分钟）';
comment on column mes_production_report.start_time is '开始时间';
comment on column mes_production_report.end_time is '结束时间';
comment on column mes_production_report.report_time is '报工时间';
comment on column mes_production_report.batch_code is '批次号';
comment on column mes_production_report.report_status is '报工状态（reported 已提交、confirmed 已确认、cancelled 已撤销）';
comment on column mes_production_report.tenant_id is '租户编码';
comment on column mes_production_report.create_dept is '创建部门';
comment on column mes_production_report.create_by is '创建者';
comment on column mes_production_report.create_time is '创建时间';
comment on column mes_production_report.update_by is '更新者';
comment on column mes_production_report.update_time is '更新时间';
comment on column mes_production_report.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_production_report.remark is '备注';

-- ----------------------------
-- 领退料单
-- ----------------------------
create table if not exists mes_material_request
(
    request_id           int8           not null,
    request_code         varchar(64)    not null,
    request_type         varchar(32)    not null,
    workorder_id         int8           not null,
    warehouse_id         int8           not null,
    request_status       varchar(32)    not null default 'draft',
    submit_time          timestamp      null,
    complete_time        timestamp      null,
    operator_user_id     int8           null,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_material_request_pk primary key (request_id)
);

create index if not exists idx_material_request_tenant on mes_material_request (tenant_id);
create index if not exists idx_material_request_request_code on mes_material_request (request_code);
create index if not exists idx_material_request_workorder_id on mes_material_request (workorder_id);
create index if not exists idx_material_request_request_status on mes_material_request (request_status);

comment on table mes_material_request is '领退料单';
comment on column mes_material_request.request_id is '主键';
comment on column mes_material_request.request_code is '单号';
comment on column mes_material_request.request_type is '类型（issue 领料、supplement 补料、return 退料）';
comment on column mes_material_request.workorder_id is '工单';
comment on column mes_material_request.warehouse_id is '仓库';
comment on column mes_material_request.request_status is '单据状态（draft 草稿、submitted 已提交、completed 已完成、cancelled 已取消）';
comment on column mes_material_request.submit_time is '提交时间';
comment on column mes_material_request.complete_time is '完成时间';
comment on column mes_material_request.operator_user_id is '仓库执行人（用户）';
comment on column mes_material_request.tenant_id is '租户编码';
comment on column mes_material_request.create_dept is '创建部门';
comment on column mes_material_request.create_by is '创建者';
comment on column mes_material_request.create_time is '创建时间';
comment on column mes_material_request.update_by is '更新者';
comment on column mes_material_request.update_time is '更新时间';
comment on column mes_material_request.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_material_request.remark is '备注';

-- ----------------------------
-- 领退料明细
-- ----------------------------
create table if not exists mes_material_request_item
(
    item_id              int8           not null,
    request_id           int8           not null,
    material_id          int8           not null,
    plan_qty             numeric(18,6)  null,
    request_qty          numeric(18,6)  not null,
    actual_qty           numeric(18,6)  not null default 0,
    batch_code           varchar(64)    null,
    location_id          int8           null,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    remark               varchar(500)   null,
    constraint mes_material_request_item_pk primary key (item_id)
);

create index if not exists idx_material_request_item_tenant on mes_material_request_item (tenant_id);
create index if not exists idx_material_request_item_request_id on mes_material_request_item (request_id);
create index if not exists idx_material_request_item_material_id on mes_material_request_item (material_id);
create index if not exists idx_material_request_item_batch_code on mes_material_request_item (batch_code);

comment on table mes_material_request_item is '领退料明细';
comment on column mes_material_request_item.item_id is '主键';
comment on column mes_material_request_item.request_id is '领退料单';
comment on column mes_material_request_item.material_id is '物料';
comment on column mes_material_request_item.plan_qty is '按 BOM 计算的需求数量';
comment on column mes_material_request_item.request_qty is '申请数量';
comment on column mes_material_request_item.actual_qty is '实发（实退）数量';
comment on column mes_material_request_item.batch_code is '物料批次号';
comment on column mes_material_request_item.location_id is '库位';
comment on column mes_material_request_item.tenant_id is '租户编码';
comment on column mes_material_request_item.create_dept is '创建部门';
comment on column mes_material_request_item.create_by is '创建者';
comment on column mes_material_request_item.create_time is '创建时间';
comment on column mes_material_request_item.update_by is '更新者';
comment on column mes_material_request_item.update_time is '更新时间';
comment on column mes_material_request_item.remark is '备注';

-- ----------------------------
-- 完工入库单
-- ----------------------------
create table if not exists mes_stock_in
(
    stock_in_id          int8           not null,
    stock_in_code        varchar(64)    not null,
    workorder_id         int8           not null,
    material_id          int8           not null,
    warehouse_id         int8           not null,
    location_id          int8           null,
    qty                  numeric(18,6)  not null,
    batch_code           varchar(64)    null,
    stock_in_status      varchar(32)    not null default 'submitted',
    complete_time        timestamp      null,
    operator_user_id     int8           null,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_stock_in_pk primary key (stock_in_id)
);

create index if not exists idx_stock_in_tenant on mes_stock_in (tenant_id);
create index if not exists idx_stock_in_stock_in_code on mes_stock_in (stock_in_code);
create index if not exists idx_stock_in_workorder_id on mes_stock_in (workorder_id);

comment on table mes_stock_in is '完工入库单';
comment on column mes_stock_in.stock_in_id is '主键';
comment on column mes_stock_in.stock_in_code is '入库单号';
comment on column mes_stock_in.workorder_id is '工单';
comment on column mes_stock_in.material_id is '产品';
comment on column mes_stock_in.warehouse_id is '仓库';
comment on column mes_stock_in.location_id is '库位';
comment on column mes_stock_in.qty is '入库数量';
comment on column mes_stock_in.batch_code is '批次号';
comment on column mes_stock_in.stock_in_status is '单据状态（submitted 已提交、completed 已入库、cancelled 已取消）';
comment on column mes_stock_in.complete_time is '入库时间';
comment on column mes_stock_in.operator_user_id is '仓库执行人（用户）';
comment on column mes_stock_in.tenant_id is '租户编码';
comment on column mes_stock_in.create_dept is '创建部门';
comment on column mes_stock_in.create_by is '创建者';
comment on column mes_stock_in.create_time is '创建时间';
comment on column mes_stock_in.update_by is '更新者';
comment on column mes_stock_in.update_time is '更新时间';
comment on column mes_stock_in.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_stock_in.remark is '备注';

-- ----------------------------
-- 检验方案
-- ----------------------------
create table if not exists mes_inspection_scheme
(
    scheme_id            int8           not null,
    scheme_code          varchar(64)    not null,
    scheme_name          varchar(128)   not null,
    inspection_type      varchar(32)    not null,
    material_id          int8           null,
    operation_code       varchar(64)    null,
    sample_qty           numeric(18,6)  null,
    check_items          varchar(2000)  null,
    status               char(1)        not null default '0',
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_inspection_scheme_pk primary key (scheme_id)
);

create index if not exists idx_inspection_scheme_tenant on mes_inspection_scheme (tenant_id);
create index if not exists idx_inspection_scheme_inspection_type on mes_inspection_scheme (inspection_type);
create index if not exists idx_inspection_scheme_material_id on mes_inspection_scheme (material_id);

comment on table mes_inspection_scheme is '检验方案';
comment on column mes_inspection_scheme.scheme_id is '主键';
comment on column mes_inspection_scheme.scheme_code is '方案编码';
comment on column mes_inspection_scheme.scheme_name is '方案名称';
comment on column mes_inspection_scheme.inspection_type is '检验类型（first 首检、patrol 巡检、final 完工检）';
comment on column mes_inspection_scheme.material_id is '适用产品（为空表示通用）';
comment on column mes_inspection_scheme.operation_code is '适用工序编码（为空表示不限）';
comment on column mes_inspection_scheme.sample_qty is '抽样数量';
comment on column mes_inspection_scheme.check_items is '检验项目与标准（每行一项）';
comment on column mes_inspection_scheme.status is '状态（0正常 1停用）';
comment on column mes_inspection_scheme.tenant_id is '租户编码';
comment on column mes_inspection_scheme.create_dept is '创建部门';
comment on column mes_inspection_scheme.create_by is '创建者';
comment on column mes_inspection_scheme.create_time is '创建时间';
comment on column mes_inspection_scheme.update_by is '更新者';
comment on column mes_inspection_scheme.update_time is '更新时间';
comment on column mes_inspection_scheme.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_inspection_scheme.remark is '备注';

-- ----------------------------
-- 检验单
-- ----------------------------
create table if not exists mes_inspection
(
    inspection_id        int8           not null,
    inspection_code      varchar(64)    not null,
    inspection_type      varchar(32)    not null,
    workorder_id         int8           not null,
    task_id              int8           null,
    scheme_id            int8           null,
    material_id          int8           not null,
    inspect_qty          numeric(18,6)  null,
    qualified_qty        numeric(18,6)  null,
    defect_qty           numeric(18,6)  null,
    inspector_user_id    int8           null,
    inspection_time      timestamp      null,
    inspection_status    varchar(32)    not null default 'pending',
    inspection_result    varchar(32)    null,
    result_remark        varchar(500)   null,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_inspection_pk primary key (inspection_id)
);

create index if not exists idx_inspection_tenant on mes_inspection (tenant_id);
create index if not exists idx_inspection_inspection_code on mes_inspection (inspection_code);
create index if not exists idx_inspection_workorder_id on mes_inspection (workorder_id);
create index if not exists idx_inspection_task_id on mes_inspection (task_id);
create index if not exists idx_inspection_inspection_status on mes_inspection (inspection_status);

comment on table mes_inspection is '检验单';
comment on column mes_inspection.inspection_id is '主键';
comment on column mes_inspection.inspection_code is '检验单号';
comment on column mes_inspection.inspection_type is '检验类型（first 首检、patrol 巡检、final 完工检）';
comment on column mes_inspection.workorder_id is '工单';
comment on column mes_inspection.task_id is '工序任务（完工检可为空）';
comment on column mes_inspection.scheme_id is '检验方案';
comment on column mes_inspection.material_id is '产品';
comment on column mes_inspection.inspect_qty is '检验数量';
comment on column mes_inspection.qualified_qty is '合格数量';
comment on column mes_inspection.defect_qty is '不良数量';
comment on column mes_inspection.inspector_user_id is '检验员（用户）';
comment on column mes_inspection.inspection_time is '检验时间';
comment on column mes_inspection.inspection_status is '检验状态（pending 待检验、completed 已完成）';
comment on column mes_inspection.inspection_result is '检验结论（passed 合格、rejected 不合格）';
comment on column mes_inspection.result_remark is '检验说明';
comment on column mes_inspection.tenant_id is '租户编码';
comment on column mes_inspection.create_dept is '创建部门';
comment on column mes_inspection.create_by is '创建者';
comment on column mes_inspection.create_time is '创建时间';
comment on column mes_inspection.update_by is '更新者';
comment on column mes_inspection.update_time is '更新时间';
comment on column mes_inspection.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_inspection.remark is '备注';

-- ----------------------------
-- 不良记录
-- ----------------------------
create table if not exists mes_defect_record
(
    defect_id            int8           not null,
    defect_code          varchar(64)    not null,
    source_type          varchar(32)    not null,
    source_id            int8           null,
    workorder_id         int8           not null,
    task_id              int8           null,
    material_id          int8           not null,
    defect_qty           numeric(18,6)  not null,
    defect_level         varchar(32)    not null default 'minor',
    description          varchar(500)   null,
    handle_method        varchar(32)    null,
    handle_status        varchar(32)    not null default 'pending',
    handle_user_id       int8           null,
    handle_time          timestamp      null,
    handle_remark        varchar(500)   null,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_defect_record_pk primary key (defect_id)
);

create index if not exists idx_defect_record_tenant on mes_defect_record (tenant_id);
create index if not exists idx_defect_record_workorder_id on mes_defect_record (workorder_id);
create index if not exists idx_defect_record_handle_status on mes_defect_record (handle_status);
create index if not exists idx_defect_record_source_type_source_id on mes_defect_record (source_type, source_id);

comment on table mes_defect_record is '不良记录';
comment on column mes_defect_record.defect_id is '主键';
comment on column mes_defect_record.defect_code is '不良单号';
comment on column mes_defect_record.source_type is '来源（report 报工、inspection 检验、manual 手工登记）';
comment on column mes_defect_record.source_id is '来源单据';
comment on column mes_defect_record.workorder_id is '工单';
comment on column mes_defect_record.task_id is '工序任务';
comment on column mes_defect_record.material_id is '产品';
comment on column mes_defect_record.defect_qty is '不良数量';
comment on column mes_defect_record.defect_level is '不良等级（minor 轻微、major 严重、critical 致命）';
comment on column mes_defect_record.description is '不良描述';
comment on column mes_defect_record.handle_method is '处置方式（rework 返工、scrap 报废、concession 让步接收）';
comment on column mes_defect_record.handle_status is '处置状态（pending 待处置、handled 已处置）';
comment on column mes_defect_record.handle_user_id is '处置人（用户）';
comment on column mes_defect_record.handle_time is '处置时间';
comment on column mes_defect_record.handle_remark is '处置说明';
comment on column mes_defect_record.tenant_id is '租户编码';
comment on column mes_defect_record.create_dept is '创建部门';
comment on column mes_defect_record.create_by is '创建者';
comment on column mes_defect_record.create_time is '创建时间';
comment on column mes_defect_record.update_by is '更新者';
comment on column mes_defect_record.update_time is '更新时间';
comment on column mes_defect_record.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_defect_record.remark is '备注';

-- ----------------------------
-- 编码规则
-- ----------------------------
create table if not exists mes_code_rule
(
    rule_id              int8           not null,
    rule_code            varchar(64)    not null,
    rule_name            varchar(128)   not null,
    prefix               varchar(32)    null,
    date_pattern         varchar(16)    null,
    serial_length        int4           not null default 4,
    reset_cycle          varchar(16)    not null default 'day',
    status               char(1)        not null default '0',
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_code_rule_pk primary key (rule_id)
);

create index if not exists idx_code_rule_tenant on mes_code_rule (tenant_id);
create index if not exists idx_code_rule_rule_code on mes_code_rule (rule_code);

comment on table mes_code_rule is '编码规则';
comment on column mes_code_rule.rule_id is '主键';
comment on column mes_code_rule.rule_code is '规则编码（业务标识，例如 WORKORDER）';
comment on column mes_code_rule.rule_name is '规则名称';
comment on column mes_code_rule.prefix is '前缀';
comment on column mes_code_rule.date_pattern is '日期格式（yyyyMMdd、yyMMdd、yyyyMM，为空表示不带日期）';
comment on column mes_code_rule.serial_length is '流水号位数';
comment on column mes_code_rule.reset_cycle is '流水号重置周期（none 不重置、day 每天、month 每月、year 每年）';
comment on column mes_code_rule.status is '状态（0正常 1停用）';
comment on column mes_code_rule.tenant_id is '租户编码';
comment on column mes_code_rule.create_dept is '创建部门';
comment on column mes_code_rule.create_by is '创建者';
comment on column mes_code_rule.create_time is '创建时间';
comment on column mes_code_rule.update_by is '更新者';
comment on column mes_code_rule.update_time is '更新时间';
comment on column mes_code_rule.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_code_rule.remark is '备注';

-- ----------------------------
-- 编码流水号
-- ----------------------------
create table if not exists mes_code_sequence
(
    sequence_id          int8           not null,
    rule_code            varchar(64)    not null,
    period_key           varchar(16)    not null,
    current_value        int8           not null default 0,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    constraint mes_code_sequence_pk primary key (sequence_id),
    constraint uk_code_sequence_rule_code_period_key unique (tenant_id, rule_code, period_key)
);

create index if not exists idx_code_sequence_tenant on mes_code_sequence (tenant_id);

comment on table mes_code_sequence is '编码流水号';
comment on column mes_code_sequence.sequence_id is '主键';
comment on column mes_code_sequence.rule_code is '规则编码';
comment on column mes_code_sequence.period_key is '周期（按重置周期取日期，例如 20261108；不重置时为 all）';
comment on column mes_code_sequence.current_value is '当前流水号';
comment on column mes_code_sequence.tenant_id is '租户编码';
comment on column mes_code_sequence.create_dept is '创建部门';
comment on column mes_code_sequence.create_by is '创建者';
comment on column mes_code_sequence.create_time is '创建时间';
comment on column mes_code_sequence.update_by is '更新者';
comment on column mes_code_sequence.update_time is '更新时间';

-- ----------------------------
-- 产品序列号
-- ----------------------------
create table if not exists mes_sn
(
    sn_id                int8           not null,
    sn_code              varchar(128)   not null,
    workorder_id         int8           not null,
    material_id          int8           not null,
    batch_code           varchar(64)    null,
    sn_status            varchar(32)    not null default 'created',
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_sn_pk primary key (sn_id)
);

create index if not exists idx_sn_tenant on mes_sn (tenant_id);
create index if not exists idx_sn_sn_code on mes_sn (sn_code);
create index if not exists idx_sn_workorder_id on mes_sn (workorder_id);

comment on table mes_sn is '产品序列号';
comment on column mes_sn.sn_id is '主键';
comment on column mes_sn.sn_code is '序列号';
comment on column mes_sn.workorder_id is '工单';
comment on column mes_sn.material_id is '产品';
comment on column mes_sn.batch_code is '生产批次号';
comment on column mes_sn.sn_status is '状态（created 已生成、completed 已完工、scrapped 已报废）';
comment on column mes_sn.tenant_id is '租户编码';
comment on column mes_sn.create_dept is '创建部门';
comment on column mes_sn.create_by is '创建者';
comment on column mes_sn.create_time is '创建时间';
comment on column mes_sn.update_by is '更新者';
comment on column mes_sn.update_time is '更新时间';
comment on column mes_sn.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_sn.remark is '备注';

-- ----------------------------
-- 安灯事件
-- ----------------------------
create table if not exists mes_andon_event
(
    event_id             int8           not null,
    event_code           varchar(64)    not null,
    event_type           varchar(32)    not null,
    event_level          varchar(32)    not null default 'normal',
    title                varchar(128)   not null,
    content              varchar(500)   null,
    workorder_id         int8           null,
    workshop_id          int8           not null,
    line_id              int8           null,
    station_id           int8           null,
    device_id            int8           null,
    downtime_reason_id   int8           null,
    report_user_id       int8           not null,
    report_time          timestamp      not null,
    handler_user_id      int8           null,
    assign_time          timestamp      null,
    response_time        timestamp      null,
    resolve_time         timestamp      null,
    close_time           timestamp      null,
    downtime_minutes     int4           null,
    resolve_remark       varchar(500)   null,
    event_status         varchar(32)    not null default 'reported',
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_andon_event_pk primary key (event_id)
);

create index if not exists idx_andon_event_tenant on mes_andon_event (tenant_id);
create index if not exists idx_andon_event_event_code on mes_andon_event (event_code);
create index if not exists idx_andon_event_event_status on mes_andon_event (event_status);
create index if not exists idx_andon_event_workshop_id on mes_andon_event (workshop_id);
create index if not exists idx_andon_event_handler_user_id on mes_andon_event (handler_user_id);
create index if not exists idx_andon_event_report_time on mes_andon_event (report_time);

comment on table mes_andon_event is '安灯事件';
comment on column mes_andon_event.event_id is '主键';
comment on column mes_andon_event.event_code is '事件编号';
comment on column mes_andon_event.event_type is '异常类型（equipment 设备、quality 质量、material 物料、process 工艺、other 其他）';
comment on column mes_andon_event.event_level is '紧急程度（normal 一般、urgent 紧急、critical 停线）';
comment on column mes_andon_event.title is '标题';
comment on column mes_andon_event.content is '异常描述';
comment on column mes_andon_event.workorder_id is '关联工单';
comment on column mes_andon_event.workshop_id is '车间';
comment on column mes_andon_event.line_id is '产线';
comment on column mes_andon_event.station_id is '工位';
comment on column mes_andon_event.device_id is '设备';
comment on column mes_andon_event.downtime_reason_id is '停机原因';
comment on column mes_andon_event.report_user_id is '上报人（用户）';
comment on column mes_andon_event.report_time is '上报时间';
comment on column mes_andon_event.handler_user_id is '处理人（用户）';
comment on column mes_andon_event.assign_time is '派单时间';
comment on column mes_andon_event.response_time is '开始处理时间';
comment on column mes_andon_event.resolve_time is '解决时间';
comment on column mes_andon_event.close_time is '关闭时间';
comment on column mes_andon_event.downtime_minutes is '停机时长（分钟，从上报到解决）';
comment on column mes_andon_event.resolve_remark is '处理说明';
comment on column mes_andon_event.event_status is '事件状态（reported 已上报、assigned 已派单、processing 处理中、resolved 已解决、closed 已关闭）';
comment on column mes_andon_event.tenant_id is '租户编码';
comment on column mes_andon_event.create_dept is '创建部门';
comment on column mes_andon_event.create_by is '创建者';
comment on column mes_andon_event.create_time is '创建时间';
comment on column mes_andon_event.update_by is '更新者';
comment on column mes_andon_event.update_time is '更新时间';
comment on column mes_andon_event.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_andon_event.remark is '备注';

-- ----------------------------
-- 停机原因
-- ----------------------------
create table if not exists mes_downtime_reason
(
    reason_id            int8           not null,
    reason_code          varchar(64)    not null,
    reason_name          varchar(128)   not null,
    reason_type          varchar(32)    not null default 'other',
    status               char(1)        not null default '0',
    sort_no              int4           not null default 0,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    del_flag             char(1)        not null default '0',
    remark               varchar(500)   null,
    constraint mes_downtime_reason_pk primary key (reason_id)
);

create index if not exists idx_downtime_reason_tenant on mes_downtime_reason (tenant_id);
create index if not exists idx_downtime_reason_reason_code on mes_downtime_reason (reason_code);

comment on table mes_downtime_reason is '停机原因';
comment on column mes_downtime_reason.reason_id is '主键';
comment on column mes_downtime_reason.reason_code is '原因编码';
comment on column mes_downtime_reason.reason_name is '原因名称';
comment on column mes_downtime_reason.reason_type is '原因分类（equipment 设备、material 物料、quality 质量、personnel 人员、other 其他）';
comment on column mes_downtime_reason.status is '状态（0正常 1停用）';
comment on column mes_downtime_reason.sort_no is '排序号';
comment on column mes_downtime_reason.tenant_id is '租户编码';
comment on column mes_downtime_reason.create_dept is '创建部门';
comment on column mes_downtime_reason.create_by is '创建者';
comment on column mes_downtime_reason.create_time is '创建时间';
comment on column mes_downtime_reason.update_by is '更新者';
comment on column mes_downtime_reason.update_time is '更新时间';
comment on column mes_downtime_reason.del_flag is '删除标志（0 存在 1 删除）';
comment on column mes_downtime_reason.remark is '备注';

-- ----------------------------
-- 状态履历
-- ----------------------------
create table if not exists mes_status_log
(
    log_id               int8           not null,
    biz_type             varchar(32)    not null,
    biz_id               int8           not null,
    biz_code             varchar(64)    null,
    action               varchar(32)    not null,
    from_status          varchar(32)    null,
    to_status            varchar(32)    not null,
    reason               varchar(500)   null,
    operator_user_id     int8           null,
    operate_time         timestamp      not null,
    tenant_id            varchar(20)    not null default 'default',
    create_dept          int8           null,
    create_by            int8           null,
    create_time          timestamp      null,
    update_by            int8           null,
    update_time          timestamp      null,
    constraint mes_status_log_pk primary key (log_id)
);

create index if not exists idx_status_log_tenant on mes_status_log (tenant_id);
create index if not exists idx_status_log_biz_type_biz_id on mes_status_log (biz_type, biz_id);

comment on table mes_status_log is '状态履历';
comment on column mes_status_log.log_id is '主键';
comment on column mes_status_log.biz_type is '单据类型（workorder、operation_task、production_report 等）';
comment on column mes_status_log.biz_id is '单据';
comment on column mes_status_log.biz_code is '单据编号';
comment on column mes_status_log.action is '动作（release、pause、complete 等）';
comment on column mes_status_log.from_status is '变更前状态';
comment on column mes_status_log.to_status is '变更后状态';
comment on column mes_status_log.reason is '原因或说明';
comment on column mes_status_log.operator_user_id is '操作人（用户）';
comment on column mes_status_log.operate_time is '操作时间';
comment on column mes_status_log.tenant_id is '租户编码';
comment on column mes_status_log.create_dept is '创建部门';
comment on column mes_status_log.create_by is '创建者';
comment on column mes_status_log.create_time is '创建时间';
comment on column mes_status_log.update_by is '更新者';
comment on column mes_status_log.update_time is '更新时间';

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
