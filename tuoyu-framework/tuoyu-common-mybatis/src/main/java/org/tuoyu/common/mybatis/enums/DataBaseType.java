package org.tuoyu.common.mybatis.enums;

/**
 * 数据库类型。
 * <p>
 * 框架支持 MySQL 与 PostgreSQL；Oracle 的全新安装脚本仍按手工维护，所以保留它的方言分支。
 * 其他数据库（包括 SQL Server）不在支持范围内，一律按 MySQL 处理。
 *
 * @author tuoyu
 */
public enum DataBaseType {

    /**
     * MySQL，也是无法识别的数据库的默认类型
     */
    MY_SQL,

    /**
     * Oracle
     */
    ORACLE,

    /**
     * PostgreSQL
     */
    POSTGRE_SQL;

    /**
     * @return 是否为 Oracle
     */
    public boolean isOracle() {
        return this == ORACLE;
    }

    /**
     * @return 是否为 PostgreSQL
     */
    public boolean isPostgreSql() {
        return this == POSTGRE_SQL;
    }

}
