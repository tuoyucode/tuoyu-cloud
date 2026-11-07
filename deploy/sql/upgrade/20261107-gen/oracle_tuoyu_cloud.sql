-- 2026-11-07 代码生成（tuoyu-cloud 库）。可重复执行
-- 代码生成只提供 Vben 前端模板：已有业务表的前端模板类型统一为 vben（列的默认值已是 vben）
-- MySQL、PostgreSQL 由迁移脚本 V7__gen_frontend_type_vben.sql 完成

update gen_table set frontend_type = 'vben' where frontend_type is null or frontend_type <> 'vben';

commit;
