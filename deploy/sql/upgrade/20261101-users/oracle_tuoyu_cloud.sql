-- 删除种子数据中的演示账号 test、test1（口令为公开的 666666，写在昵称中），及其角色、岗位关联与第三方绑定。
-- 两个演示角色（本部门及以下、仅本人）保留。可重复执行
delete from sys_user_role where user_id in (1761100000000000003, 1761100000000000004);
delete from sys_user_post where user_id in (1761100000000000003, 1761100000000000004);
delete from sys_social where user_id in (1761100000000000003, 1761100000000000004);
delete from sys_user where user_id in (1761100000000000003, 1761100000000000004);
commit;
