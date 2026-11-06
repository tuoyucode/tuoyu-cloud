-- 删除“流程表达式定义导出”按钮（权限 workflow:spel:export）：流程表达式没有导出接口。可重复执行
delete from sys_tenant_package_menu where menu_id = 1761400000000011806;
delete from sys_role_menu where menu_id = 1761400000000011806;
delete from sys_menu where menu_id = 1761400000000011806;
