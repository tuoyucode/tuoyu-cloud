-- 2026-10-24 菜单调整（tuoyu-cloud 库）。可重复执行。
-- 1. 新增“安全中心”菜单（系统监控下）；2. 删除“项目官网”“测试菜单”与请假申请示例的菜单及角色授权；3. 菜单图标改为 Iconify

insert into sys_menu
select 1761400000000000140, '安全中心', 1761400000000000002, 0, 'security', 'monitor/security/index', '', 'N', 'N', 'C', '0', '0', 'monitor:security:list', 'lucide:shield-check', '', '', 1761000000000000103, 1761100000000000001, sysdate(), null, null, '安全中心菜单：启动自检结果与运行时防护配置（只读）' from dual
where not exists (select 1 from sys_menu where menu_id = 1761400000000000140);

delete from sys_role_menu where menu_id in (1761400000000000004, 1761400000000000005, 1761400000000011638, 1761400000000011639, 1761400000000011640, 1761400000000011641, 1761400000000011642, 1761400000000011643, 1761400000000011701);
delete from sys_menu where menu_id in (1761400000000000004, 1761400000000000005, 1761400000000011638, 1761400000000011639, 1761400000000011640, 1761400000000011641, 1761400000000011642, 1761400000000011643, 1761400000000011701);

update sys_menu set icon = 'lucide:settings' where menu_id = 1761400000000000001;
update sys_menu set icon = 'lucide:monitor' where menu_id = 1761400000000000002;
update sys_menu set icon = 'lucide:wrench' where menu_id = 1761400000000000003;
update sys_menu set icon = 'lucide:workflow' where menu_id = 1761400000000011616;
update sys_menu set icon = 'lucide:list-todo' where menu_id = 1761400000000011618;
update sys_menu set icon = 'lucide:user' where menu_id = 1761400000000000100;
update sys_menu set icon = 'lucide:users' where menu_id = 1761400000000000101;
update sys_menu set icon = 'lucide:list-tree' where menu_id = 1761400000000000102;
update sys_menu set icon = 'lucide:network' where menu_id = 1761400000000000103;
update sys_menu set icon = 'lucide:briefcase' where menu_id = 1761400000000000104;
update sys_menu set icon = 'lucide:book-a' where menu_id = 1761400000000000105;
update sys_menu set icon = 'lucide:sliders-horizontal' where menu_id = 1761400000000000106;
update sys_menu set icon = 'lucide:megaphone' where menu_id = 1761400000000000107;
update sys_menu set icon = 'lucide:file-text' where menu_id = 1761400000000000108;
update sys_menu set icon = 'lucide:user-check' where menu_id = 1761400000000000109;
update sys_menu set icon = 'lucide:database' where menu_id = 1761400000000000113;
update sys_menu set icon = 'lucide:calendar-clock' where menu_id = 1761400000000000110;
update sys_menu set icon = 'lucide:bot' where menu_id = 1761400000000000121;
update sys_menu set icon = 'lucide:server-cog' where menu_id = 1761400000000000112;
update sys_menu set icon = 'lucide:server' where menu_id = 1761400000000000114;
update sys_menu set icon = 'lucide:code' where menu_id = 1761400000000000115;
update sys_menu set icon = 'lucide:app-window' where menu_id = 1761400000000000123;
update sys_menu set icon = 'lucide:folder-up' where menu_id = 1761400000000000118;
update sys_menu set icon = 'lucide:clipboard-list' where menu_id = 1761400000000000500;
update sys_menu set icon = 'lucide:log-in' where menu_id = 1761400000000000501;
update sys_menu set icon = 'lucide:hourglass' where menu_id = 1761400000000011619;
update sys_menu set icon = 'lucide:circle-check' where menu_id = 1761400000000011632;
update sys_menu set icon = 'lucide:copy' where menu_id = 1761400000000011633;
update sys_menu set icon = 'lucide:file-cog' where menu_id = 1761400000000011620;
update sys_menu set icon = 'lucide:list-checks' where menu_id = 1761400000000011621;
update sys_menu set icon = 'lucide:folder-tree' where menu_id = 1761400000000011622;
update sys_menu set icon = 'lucide:braces' where menu_id = 1761400000000011801;
update sys_menu set icon = 'lucide:send' where menu_id = 1761400000000011629;
update sys_menu set icon = 'lucide:activity' where menu_id = 1761400000000011630;
update sys_menu set icon = 'lucide:clock' where menu_id = 1761400000000011631;
