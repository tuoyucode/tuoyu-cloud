-- 菜单 SQL
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1001, '【请填写功能名称】', 1761400000000000003, 1, 'item', 'system/item/index', 'N', 'Y', 'C', '0', '0', 'system:item:list', 'lucide:file-text', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '【请填写功能名称】菜单');

-- 按钮 SQL
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1002, '【请填写功能名称】查询', 1001, 1,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:item:query',        '#', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1003, '【请填写功能名称】新增', 1001, 2,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:item:add',          '#', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1004, '【请填写功能名称】修改', 1001, 3,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:item:edit',         '#', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1005, '【请填写功能名称】删除', 1001, 4,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:item:remove',       '#', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values(1006, '【请填写功能名称】导出', 1001, 5,  '#', '', 'N', 'Y', 'F', '0', '0', 'system:item:export',       '#', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '');
