-- 菜单 SQL
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1001, '部门', 1761400000000000001, 1, 'deptDemo', 'system/deptDemo/index', 'N', 'Y', 'C', '0', '0', 'system:deptDemo:list', 'lucide:file-text', 1761000000000000103, 1761100000000000001, sysdate, null, null, '部门菜单');

-- 按钮 SQL
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1002, '部门查询', 1001, 1,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:deptDemo:query',        '#', 1761000000000000103, 1761100000000000001, sysdate, null, null, '');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1003, '部门新增', 1001, 2,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:deptDemo:add',          '#', 1761000000000000103, 1761100000000000001, sysdate, null, null, '');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1004, '部门修改', 1001, 3,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:deptDemo:edit',         '#', 1761000000000000103, 1761100000000000001, sysdate, null, null, '');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1005, '部门删除', 1001, 4,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:deptDemo:remove',       '#', 1761000000000000103, 1761100000000000001, sysdate, null, null, '');
