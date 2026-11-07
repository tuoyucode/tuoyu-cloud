-- 代码生成只提供 Vben 前端模板：已有业务表的前端模板类型统一为 vben（列的默认值已是 vben）。可重复执行
update gen_table set frontend_type = 'vben' where frontend_type is null or frontend_type <> 'vben';
