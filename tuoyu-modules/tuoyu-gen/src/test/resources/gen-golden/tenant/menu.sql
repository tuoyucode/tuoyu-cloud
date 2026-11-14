-- 菜单 SQL
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1001, '验证示例', 1761400000000000003, 1, 'verifyDemo', 'system/verifyDemo/index', 'N', 'Y', 'C', '0', '0', 'system:verifyDemo:list', 'lucide:file-text', 1761000000000000103, 1761100000000000001, now(), null, null, '验证示例菜单');

-- 按钮 SQL
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1002, '验证示例查询', 1001, 1,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:verifyDemo:query',        '#', 1761000000000000103, 1761100000000000001, now(), null, null, '');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1003, '验证示例新增', 1001, 2,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:verifyDemo:add',          '#', 1761000000000000103, 1761100000000000001, now(), null, null, '');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1004, '验证示例修改', 1001, 3,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:verifyDemo:edit',         '#', 1761000000000000103, 1761100000000000001, now(), null, null, '');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1005, '验证示例删除', 1001, 4,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:verifyDemo:remove',       '#', 1761000000000000103, 1761100000000000001, now(), null, null, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1006, '验证示例导出', 1001, 5,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:verifyDemo:export',       '#', 1761000000000000103, 1761100000000000001, now(), null, null, '');
