<#-- 菜单与按钮的初始化语句。说明文字已去掉引号与反斜杠，上级菜单编号只含数字 -->
<#assign now = "now()">
<#assign columnList = "menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark">
<#assign menuId = table.menuIds[0]>
-- 菜单 SQL
insert into sys_menu (${columnList})
values(${menuId?c}, '${functionName}', ${gen.parentMenuId}, 1, '${businessName}', '${moduleName}/${businessName}/index', 'N', 'Y', 'C', '0', '0', '${permissionPrefix}:list', 'lucide:file-text', 1761000000000000103, 1761100000000000001, ${now}, null, null, '${functionName}菜单');

-- 按钮 SQL
insert into sys_menu (${columnList})
values(${table.menuIds[1]?c}, '${functionName}查询', ${menuId?c}, 1,  '#', '', 'N', 'Y', 'F', '0', '0', '${permissionPrefix}:query',        '#', 1761000000000000103, 1761100000000000001, ${now}, null, null, '');

insert into sys_menu (${columnList})
values(${table.menuIds[2]?c}, '${functionName}新增', ${menuId?c}, 2,  '#', '', 'N', 'Y', 'F', '0', '0', '${permissionPrefix}:add',          '#', 1761000000000000103, 1761100000000000001, ${now}, null, null, '');

insert into sys_menu (${columnList})
values(${table.menuIds[3]?c}, '${functionName}修改', ${menuId?c}, 3,  '#', '', 'N', 'Y', 'F', '0', '0', '${permissionPrefix}:edit',         '#', 1761000000000000103, 1761100000000000001, ${now}, null, null, '');

insert into sys_menu (${columnList})
values(${table.menuIds[4]?c}, '${functionName}删除', ${menuId?c}, 4,  '#', '', 'N', 'Y', 'F', '0', '0', '${permissionPrefix}:remove',       '#', 1761000000000000103, 1761100000000000001, ${now}, null, null, '');
<#if enableExport>

insert into sys_menu (${columnList})
values(${table.menuIds[5]?c}, '${functionName}导出', ${menuId?c}, 5,  '#', '', 'N', 'Y', 'F', '0', '0', '${permissionPrefix}:export',       '#', 1761000000000000103, 1761100000000000001, ${now}, null, null, '');
</#if>
