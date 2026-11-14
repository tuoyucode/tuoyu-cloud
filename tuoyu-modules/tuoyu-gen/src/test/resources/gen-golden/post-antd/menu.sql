-- 菜单 SQL
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1001, '岗位', 1761400000000000003, 1, 'postDemo', 'system/postDemo/index', 'N', 'Y', 'C', '0', '0', 'system:postDemo:list', 'lucide:file-text', 1761000000000000103, 1761100000000000001, now(), null, null, '岗位菜单');

-- 按钮 SQL
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1002, '岗位查询', 1001, 1,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:postDemo:query',        '#', 1761000000000000103, 1761100000000000001, now(), null, null, '');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1003, '岗位新增', 1001, 2,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:postDemo:add',          '#', 1761000000000000103, 1761100000000000001, now(), null, null, '');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1004, '岗位修改', 1001, 3,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:postDemo:edit',         '#', 1761000000000000103, 1761100000000000001, now(), null, null, '');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1005, '岗位删除', 1001, 4,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:postDemo:remove',       '#', 1761000000000000103, 1761100000000000001, now(), null, null, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1006, '岗位导出', 1001, 5,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:postDemo:export',       '#', 1761000000000000103, 1761100000000000001, now(), null, null, '');
