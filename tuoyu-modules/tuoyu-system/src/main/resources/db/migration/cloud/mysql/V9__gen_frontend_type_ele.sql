-- V9：代码生成的前端模板分为 Element Plus（ele）与 Ant Design Vue（antd）两个版本。可重复执行
-- 已有业务表是按 Ant Design Vue 的页面模板生成的，改为 antd，重新生成时仍得到同样的页面；新导入的表默认 ele
update gen_table set frontend_type = 'antd' where frontend_type is null or frontend_type not in ('ele', 'antd');
alter table gen_table alter column frontend_type set default 'ele';
