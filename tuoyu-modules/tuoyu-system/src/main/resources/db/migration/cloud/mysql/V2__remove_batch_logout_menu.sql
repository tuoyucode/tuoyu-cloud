-- 删除“批量强退”按钮（权限 monitor:online:batchLogout）：没有对应的接口，在线用户只支持逐个强退。可重复执行
delete from sys_role_menu where menu_id = 1761400000000001047;
delete from sys_menu where menu_id = 1761400000000001047;
