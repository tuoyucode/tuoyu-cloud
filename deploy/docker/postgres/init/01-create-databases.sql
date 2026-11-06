-- docker-compose 首次启动 PostgreSQL（数据卷为空）时执行一次：创建三个库。
-- 表结构与种子数据由各负责服务启动时用 Flyway 迁移（见 docs/adr/0004-数据库迁移.md），这里不建表
CREATE DATABASE "tuoyu-cloud";
CREATE DATABASE "tuoyu-workflow";
CREATE DATABASE "tuoyu-job";
