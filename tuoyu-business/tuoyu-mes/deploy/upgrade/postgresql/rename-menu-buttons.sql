set client_encoding = 'UTF8';

-- MES 按钮的名称与界面一致（菜单声明 META-INF/tuoyu/menus/mes.json 已改名）
-- 启动时只登记不存在的菜单、不覆盖已有的，已有库中的这几个按钮需要手工改名
-- 在 tuoyu-cloud 库上执行；只改仍是原名称的行（在菜单管理中改过名的不动），可以重复执行
update sys_menu set menu_name = '工序任务完成工序' where menu_id = 1762000000000101203 and menu_name = '工序任务完工';
update sys_menu set menu_name = '报工记录撤销报工' where menu_id = 1762000000000101404 and menu_name = '报工记录取消';
update sys_menu set menu_name = '领退料确认完成' where menu_id = 1762000000000101506 and menu_name = '领退料完工';
update sys_menu set menu_name = '完工入库确认入库' where menu_id = 1762000000000101603 and menu_name = '完工入库完工';
