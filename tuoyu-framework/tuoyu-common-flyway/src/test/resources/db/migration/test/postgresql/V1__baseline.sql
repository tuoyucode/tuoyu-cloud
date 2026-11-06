-- 测试用基线：SupportedDatabaseMigrationStrategyTest 按它核对已有的 PostgreSQL 数据库（真实基线在负责迁移的服务中）
create table sys_user
(
    user_id int8 not null
);

CREATE TABLE IF NOT EXISTS "flow_node"
(
    id int8 not null
);
