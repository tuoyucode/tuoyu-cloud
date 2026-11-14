package org.tuoyu.common.mybatis.enums;

/**
 * 角色的数据范围。编码是角色表 {@code data_scope} 字段的存储值。
 * <p>
 * 各范围生成的 SQL 条件见 {@code DataPermissionSqlHandler}。
 *
 * @author tuoyu
 */
public enum DataScopeType {

    /**
     * 全部数据
     */
    ALL("1"),

    /**
     * 角色自定义的部门
     */
    CUSTOM("2"),

    /**
     * 本部门
     */
    DEPT("3"),

    /**
     * 本部门及以下
     */
    DEPT_AND_CHILD("4"),

    /**
     * 仅本人
     */
    SELF("5"),

    /**
     * 本部门及以下或本人
     */
    DEPT_AND_CHILD_OR_SELF("6");

    private final String code;

    DataScopeType(String code) {
        this.code = code;
    }

    /**
     * @return 角色表中保存的编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 按编码精确查找（不去空白、不忽略大小写）。
     *
     * @param code 编码
     * @return 对应的数据范围；编码为 null、空白或不认识时为 null
     */
    public static DataScopeType findCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (DataScopeType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

}
