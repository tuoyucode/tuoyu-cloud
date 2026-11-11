-- 2026-11-11 代码生成（tuoyu-cloud 库）。可重复执行
-- 前端模板分为 Element Plus（ele）与 Ant Design Vue（antd）两个版本：已有业务表是按 Ant Design Vue 的页面模板生成的，
-- 改为 antd，重新生成时仍得到同样的页面；新导入的表默认 ele
-- MySQL、PostgreSQL 由迁移脚本 V9__gen_frontend_type_ele.sql 完成

update gen_table set frontend_type = 'antd' where frontend_type is null or frontend_type not in ('ele', 'antd');
alter table gen_table modify frontend_type default 'ele';

commit;
