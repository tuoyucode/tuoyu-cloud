-- 2026-10-28 菜单调整（tuoyu-cloud 库）。可重复执行。
-- 删除“批量强退”按钮（权限 monitor:online:batchLogout）及其角色授权：没有对应的接口，在线用户只支持逐个强退

delete from sys_role_menu where menu_id = 1761400000000001047;
delete from sys_menu where menu_id = 1761400000000001047;
