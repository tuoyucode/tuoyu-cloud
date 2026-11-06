-- 测试用基线：SupportedDatabaseMigrationStrategyTest 按它核对已有数据库（真实基线在负责迁移的服务中）
create table sys_user (
    user_id bigint not null
);

CREATE TABLE `sys_dept` (
    `dept_id` bigint not null
);
