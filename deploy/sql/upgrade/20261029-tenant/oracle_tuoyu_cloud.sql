-- 2026-10-29 多租户（tuoyu-cloud 库）。可重复执行。
-- 1. 按租户隔离的表增加 tenant_id（默认属于默认租户 default）；2. 新增租户、租户套餐、套餐菜单三张表与默认租户；3. 新增“租户管理”“租户套餐”菜单

declare
  procedure add_tenant_column(p_table varchar2) is
  begin
    execute immediate 'alter table ' || p_table || ' add (tenant_id varchar2(20) default ''default'' not null)';
  exception when others then
    if sqlcode != -1430 then raise; end if; -- ORA-01430：列已存在
  end;
  procedure add_index(p_index varchar2, p_table varchar2) is
  begin
    execute immediate 'create index ' || p_index || ' on ' || p_table || ' (tenant_id)';
  exception when others then
    if sqlcode != -955 then raise; end if; -- ORA-00955：名称已被使用
  end;
  procedure create_table(p_ddl varchar2) is
  begin
    execute immediate p_ddl;
  exception when others then
    if sqlcode != -955 then raise; end if;
  end;
begin
  add_tenant_column('sys_user');
  add_tenant_column('sys_role');
  add_tenant_column('sys_dept');
  add_tenant_column('sys_post');
  add_tenant_column('sys_notice');
  add_tenant_column('sys_message');
  add_tenant_column('sys_oper_log');
  add_tenant_column('sys_login_info');
  add_tenant_column('sys_social');
  add_tenant_column('sys_oss');
  add_index('idx_sys_user_tenant', 'sys_user');
  add_index('idx_sys_role_tenant', 'sys_role');
  add_index('idx_sys_dept_tenant', 'sys_dept');
  add_index('idx_sys_post_tenant', 'sys_post');
  add_index('idx_sys_notice_tenant', 'sys_notice');
  add_index('idx_sys_message_tenant', 'sys_message');
  add_index('idx_sys_oper_log_tenant', 'sys_oper_log');
  add_index('idx_sys_login_info_tenant', 'sys_login_info');
  add_index('idx_sys_social_tenant', 'sys_social');
  add_index('idx_sys_oss_tenant', 'sys_oss');
  create_table('create table sys_tenant (
    tenant_id varchar2(20) not null, tenant_name varchar2(50) not null, contact_user varchar2(30) default null,
    contact_phone varchar2(20) default null, domain varchar2(200) default null, package_id number(20) default null,
    expire_time date, account_count number(11) default null, status char(1) default ''0'', del_flag char(1) default ''0'',
    remark varchar2(500) default null, create_dept number(20) default null, create_by number(20) default null,
    create_time date, update_by number(20) default null, update_time date,
    constraint pk_sys_tenant primary key (tenant_id))');
  create_table('create table sys_tenant_package (
    package_id number(20) not null, package_name varchar2(50) not null, status char(1) default ''0'',
    del_flag char(1) default ''0'', remark varchar2(500) default null, create_dept number(20) default null,
    create_by number(20) default null, create_time date, update_by number(20) default null, update_time date,
    constraint pk_sys_tenant_package primary key (package_id))');
  create_table('create table sys_tenant_package_menu (
    package_id number(20) not null, menu_id number(20) not null,
    constraint pk_sys_tenant_package_menu primary key (package_id, menu_id))');
end;
/

insert into sys_tenant (tenant_id, tenant_name, status, del_flag, remark, create_dept, create_by, create_time)
select 'default', '默认租户', '0', '0', '平台所在的租户：关闭多租户时所有数据都属于它，不受套餐限制', 1761000000000000103, 1761100000000000001, sysdate
from dual where not exists (select 1 from sys_tenant where tenant_id = 'default');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000000150, '租户管理', 1761400000000000001, 10, 'tenant', 'system/tenant/index', '', 'N', 'Y', 'C', '0', '0', 'system:tenant:list', 'lucide:building-2', '', '', 1761000000000000103, 1761100000000000001, sysdate, '租户管理菜单（开启多租户时显示）'
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000000150);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000000151, '租户套餐', 1761400000000000001, 11, 'tenantPackage', 'system/tenantPackage/index', '', 'N', 'Y', 'C', '0', '0', 'system:tenantPackage:list', 'lucide:package', '', '', 1761000000000000103, 1761100000000000001, sysdate, '租户套餐菜单（开启多租户时显示）'
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000000151);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001100, '租户查询', 1761400000000000150, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:tenant:query', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate, ''
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000001100);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001101, '租户新增', 1761400000000000150, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:tenant:add', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate, ''
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000001101);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001102, '租户修改', 1761400000000000150, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:tenant:edit', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate, ''
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000001102);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001103, '租户删除', 1761400000000000150, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:tenant:remove', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate, ''
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000001103);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001105, '套餐查询', 1761400000000000151, 1, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:tenantPackage:query', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate, ''
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000001105);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001106, '套餐新增', 1761400000000000151, 2, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:tenantPackage:add', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate, ''
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000001106);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001107, '套餐修改', 1761400000000000151, 3, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:tenantPackage:edit', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate, ''
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000001107);
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, active_menu, ext, create_dept, create_by, create_time, remark)
select 1761400000000001108, '套餐删除', 1761400000000000151, 4, '', '', '', 'N', 'Y', 'F', '0', '0', 'system:tenantPackage:remove', '#', '', '', 1761000000000000103, 1761100000000000001, sysdate, ''
from dual where not exists (select 1 from sys_menu where menu_id = 1761400000000001108);

commit;
