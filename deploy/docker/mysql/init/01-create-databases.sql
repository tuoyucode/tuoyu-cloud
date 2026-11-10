-- docker-compose 首次启动 MySQL（数据卷为空）时执行一次：创建四个库。
-- 表结构与种子数据由各负责服务启动时用 Flyway 迁移，这里不建表：
--   tuoyu-cloud     tuoyu-system（单体中由单体负责）
--   tuoyu-workflow  tuoyu-workflow（单体中由单体负责）
--   tuoyu-job       tuoyu-snailjob-server
--   tuoyu-mes       tuoyu-mes（可选业务模块，单体中由单体负责）
CREATE DATABASE IF NOT EXISTS `tuoyu-cloud` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `tuoyu-workflow` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `tuoyu-job` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS `tuoyu-mes` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
