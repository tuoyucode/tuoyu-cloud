package org.tuoyu.common.log.enums;

/**
 * 操作日志的操作类型。{@link #code()} 是写进操作日志表（business_type）的值，与前端字典 sys_oper_type 对应，不能改动。
 *
 * @author tuoyu
 */
public enum AuditAction {

    /**
     * 其它
     */
    OTHER(0),

    /**
     * 新增
     */
    CREATE(1),

    /**
     * 修改
     */
    UPDATE(2),

    /**
     * 删除
     */
    DELETE(3),

    /**
     * 授权
     */
    GRANT(4),

    /**
     * 导出
     */
    EXPORT(5),

    /**
     * 导入
     */
    IMPORT(6),

    /**
     * 强制下线
     */
    FORCE_LOGOUT(7),

    /**
     * 生成代码
     */
    GENERATE_CODE(8),

    /**
     * 清空数据
     */
    CLEAN(9);

    private final int code;

    AuditAction(int code) {
        this.code = code;
    }

    /**
     * @return 写进操作日志表的值
     */
    public int code() {
        return code;
    }

}
