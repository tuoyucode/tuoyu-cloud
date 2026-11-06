-- 2026-10-30 菜单调整（tuoyu-cloud 库）。可重复执行
-- 删除“流程表达式定义导出”按钮（权限 workflow:spel:export）及其角色授权与套餐菜单：流程表达式没有导出接口
-- MySQL、PostgreSQL 由迁移脚本 V5__remove_spel_export_menu.sql 完成

delete from sys_tenant_package_menu where menu_id = 1761400000000011806;
delete from sys_role_menu where menu_id = 1761400000000011806;
delete from sys_menu where menu_id = 1761400000000011806;

commit;
