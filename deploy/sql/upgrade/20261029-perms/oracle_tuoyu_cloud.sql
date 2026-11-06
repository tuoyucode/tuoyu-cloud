-- 2026-10-29 菜单权限码与接口对齐（tuoyu-cloud 库）。可重复执行
-- “流程设计”页面（隐藏菜单）的权限码沿用了已删除的请假示例 workflow:leave:edit，没有任何接口使用；
-- 设计流程即修改流程定义，改为 workflow:definition:edit
-- MySQL、PostgreSQL 由迁移脚本 V4__align_permissions.sql 完成

update sys_menu set perms = 'workflow:definition:edit' where menu_id = 1761400000000011700 and perms = 'workflow:leave:edit';

commit;
