package org.tuoyu.mes.andon.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 安灯事件状态
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum AndonStatus implements CodeEnum {

    /**
     * 已上报
     */
    REPORTED("reported", "已上报"),
    /**
     * 已派单
     */
    ASSIGNED("assigned", "已派单"),
    /**
     * 处理中
     */
    PROCESSING("processing", "处理中"),
    /**
     * 已解决
     */
    RESOLVED("resolved", "已解决"),
    /**
     * 已关闭
     */
    CLOSED("closed", "已关闭");

    private final String code;

    private final String label;

    AndonStatus(String code, String label) {
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
