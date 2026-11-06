-- V3：多租户（ADR 0006）
-- 1. 按租户隔离的表增加 tenant_id（始终存在，默认属于默认租户 default；开关 tuoyu.tenant.enabled 只决定是否按它过滤）
-- 2. 新增租户、租户套餐、套餐菜单三张表，写入默认租户
-- 3. 新增“租户管理”“租户套餐”菜单（关闭多租户时菜单接口不返回它们）

alter table sys_user        add column tenant_id varchar(20) not null default 'default' comment '租户编码';
alter table sys_role        add column tenant_id varchar(20) not null default 'default' comment '租户编码';
alter table sys_dept        add column tenant_id varchar(20) not null default 'default' comment '租户编码';
alter table sys_post        add column tenant_id varchar(20) not null default 'default' comment '租户编码';
alter table sys_notice      add column tenant_id varchar(20) not null default 'default' comment '租户编码';
alter table sys_message     add column tenant_id varchar(20) not null default 'default' comment '租户编码';
alter table sys_oper_log    add column tenant_id varchar(20) not null default 'default' comment '租户编码';
alter table sys_login_info  add column tenant_id varchar(20) not null default 'default' comment '租户编码';
alter table sys_social      add column tenant_id varchar(20) not null default 'default' comment '租户编码';
alter table sys_oss         add column tenant_id varchar(20) not null default 'default' comment '租户编码';

create index idx_sys_user_tenant       on sys_user (tenant_id);
create index idx_sys_role_tenant       on sys_role (tenant_id);
create index idx_sys_dept_tenant       on sys_dept (tenant_id);
create index idx_sys_post_tenant       on sys_post (tenant_id);
create index idx_sys_notice_tenant     on sys_notice (tenant_id);
create index idx_sys_message_tenant    on sys_message (tenant_id);
create index idx_sys_oper_log_tenant   on sys_oper_log (tenant_id);
create index idx_sys_login_info_tenant on sys_login_info (tenant_id);
create index idx_sys_social_tenant     on sys_social (tenant_id);
create index idx_sys_oss_tenant        on sys_oss (tenant_id);

create table if not exists sys_tenant (
  tenant_id       varchar(20)     not null                   comment '租户编码',
  tenant_name     varchar(50)     not null                   comment '租户名称',
  contact_user    varchar(30)     default null               comment '联系人',
  contact_phone   varchar(20)     default null               comment '联系电话',
  domain          varchar(200)    default null               comment '绑定的访问域名',
  package_id      bigint(20)      default null               comment '租户套餐',
  expire_time     datetime        default null               comment '过期时间（为空表示不过期）',
  account_count   int(11)         default null               comment '用户数上限（为空表示不限）',
  status          char(1)         default '0'                comment '状态（0正常 1停用）',
  del_flag        char(1)         default '0'                comment '删除标志（0存在 1删除）',
  remark          varchar(500)    default null               comment '备注',
  create_dept     bigint(20)      default null               comment '创建部门',
  create_by       bigint(20)      default null               comment '创建者',
  create_time     datetime                                   comment '创建时间',
  update_by       bigint(20)      default null               comment '更新者',
  update_time     datetime                                   comment '更新时间',
  primary key (tenant_id)
) engine=innodb comment = '租户表';

create table if not exists sys_tenant_package (
  package_id      bigint(20)      not null                   comment '套餐编号',
  package_name    varchar(50)     not null                   comment '套餐名称',
  status          char(1)         default '0'                comment '状态（0正常 1停用）',
  del_flag        char(1)         default '0'                comment '删除标志（0存在 1删除）',
  remark          varchar(500)    default null               comment '备注',
  create_dept     bigint(20)      default null               comment '创建部门',
  create_by       bigint(20)      default null               comment '创建者',
  create_time     datetime                                   comment '创建时间',
  update_by       bigint(20)      default null               comment '更新者',
  update_time     datetime                                   comment '更新时间',
  primary key (package_id)
) engine=innodb comment = '租户套餐表';

create table if not exists sys_tenant_package_menu (
  package_id      bigint(20)      not null                   comment '套餐编号',
  menu_id         bigint(20)      not null                   comment '菜单编号',
  primary key (package_id, menu_id)
) engine=innodb comment = '租户套餐与菜单关联表';

insert into sys_tenant (tenant_id, tenant_name, status, del_flag, remark, create_dept, create_by, create_time)
select 'default', '默认租户', '0', '0', '平台所在的租户：关闭多租户时所有数据都属于它，不受套餐限制', 1761000000000000103, 1761100000000000001, sysdate()
from dual where not exists (select 1 from sys_tenant where tenant_id = 'default');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000000150, '租户管理', 1761400000000000001, 10, 'tenant', 'system/tenant/index', '', 'N', 'Y', 'C', '0', '0', 'system:tenant:list', 'lucide:building-2', '', '', 1761000000000000103, 1761100000000000001, sysdate(), '租户管理菜单（开启多租户时显示）'
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000000150);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000000151, '租户套餐', 1761400000000000001, 11, 'tenantPackage', 'system/tenantPackage/index', '', 'N', 'Y', 'C', '0', '0', 'system:tenantPackage:list', 'lucide:package', '', '', 1761000000000000103, 1761100000000000001, sysdate(), '租户套餐菜单（开启多租户时显示）'
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000000151);

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001100, '租户查询', 1761400000000000150, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:tenant:query', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), ''
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000001100);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001101, '租户新增', 1761400000000000150, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:tenant:add', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), ''
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000001101);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001102, '租户修改', 1761400000000000150, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:tenant:edit', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), ''
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000001102);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001103, '租户删除', 1761400000000000150, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:tenant:remove', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), ''
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000001103);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001105, '套餐查询', 1761400000000000151, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:tenantPackage:query', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), ''
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000001105);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001106, '套餐新增', 1761400000000000151, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:tenantPackage:add', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), ''
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000001106);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001107, '套餐修改', 1761400000000000151, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:tenantPackage:edit', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), ''
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000001107);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001108, '套餐删除', 1761400000000000151, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:tenantPackage:remove', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate(), ''
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000001108);
