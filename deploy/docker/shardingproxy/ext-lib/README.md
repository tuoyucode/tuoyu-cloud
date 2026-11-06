# ext-lib

ShardingSphere-Proxy 的扩展依赖目录。部署时，此目录会被挂载到容器内的 `/opt/shardingsphere-proxy/ext-lib`（见 `deploy/docker/compose-extras.yml` 的 sharding profile）。

## MySQL 驱动需自行下载

仓库内**不附带** MySQL JDBC 驱动。原因是它采用 GPL-2.0 协议，与本项目“仓库内不分发 GPL 文件”的约定冲突。

使用 ShardingSphere-Proxy 连接 MySQL 前，请自行下载驱动 jar 并放入本目录：

- 下载地址：[MySQL Connector/J（Maven Central）](https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/)
- 推荐版本：`8.0.33`，与 ShardingSphere-Proxy 5.4.0 搭配使用
- 放置后的路径：`ext-lib/mysql-connector-j-8.0.33.jar`

ShardingSphere-Proxy 是可选组件，不使用分库分表时可以忽略本目录。
