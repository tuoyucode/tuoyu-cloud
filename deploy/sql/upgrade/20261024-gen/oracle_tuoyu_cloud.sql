-- 2026-10-24 代码生成默认使用 Vben 模板（tuoyu-cloud 库）。可重复执行。
alter table gen_table modify frontend_type default 'vben';
update gen_table set frontend_type = 'vben' where frontend_type = 'vue';
commit;
