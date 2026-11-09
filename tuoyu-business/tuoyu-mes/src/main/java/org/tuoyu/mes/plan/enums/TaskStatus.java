package org.tuoyu.mes.plan.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 工序任务状态
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum TaskStatus implements CodeEnum {

    /**
     * 待派工
     */
    PENDING("pending", "待派工"),
    /**
     * 已派工
     */
    DISPATCHED("dispatched", "已派工"),
    /**
     * 生产中
     */
    IN_PROGRESS("in_progress", "生产中"),
    /**
     * 已完成
     */
    COMPLETED("completed", "已完成"),
    /**
     * 已关闭
     */
    CLOSED("closed", "已关闭");

    private final String code;

    private final String label;

    TaskStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String label() {
        return label;
    }

}
