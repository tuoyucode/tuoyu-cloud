-- V8：菜单与字典的种子数据在 MySQL、PostgreSQL 中保持一致。可重复执行
-- “流程设计”页面（隐藏菜单）：显示状态与菜单状态按 '0' / '1' 存储（visible '1' 为隐藏，status '0' 为正常）。
-- PostgreSQL 基线中写成了 'N' / 'Y'，菜单被当作停用，路由中没有该页面，流程设计器无法打开
update sys_menu set visible = '1', status = '0' where menu_id = 1761400000000011700 and (visible <> '1' or status <> '0');

-- 流程表达式的按钮名称与页面名称一致
update sys_menu set menu_name = '流程表达式查询' where menu_id = 1761400000000011802 and menu_name in ('流程达式定义查询', '流程spel表达式定义查询');
update sys_menu set menu_name = '流程表达式新增' where menu_id = 1761400000000011803 and menu_name in ('流程达式定义新增', '流程spel表达式定义新增');
update sys_menu set menu_name = '流程表达式修改' where menu_id = 1761400000000011804 and menu_name in ('流程达式定义修改', '流程spel表达式定义修改');
update sys_menu set menu_name = '流程表达式删除' where menu_id = 1761400000000011805 and menu_name in ('流程达式定义删除', '流程spel表达式定义删除');

-- 授权类型字典的样式类 el-check-tag 是 Element Plus 的组件类名，前端（Ant Design Vue）不使用，清空
update sys_dict_data set css_class = '' where dict_code between 1761600000000000030 and 1761600000000000034 and css_class = 'el-check-tag';
