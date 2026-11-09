package org.tuoyu.mes.andon.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 安灯紧急程度
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum AndonLevel implements CodeEnum {

    /**
     * 一般
     */
    NORMAL("normal", "一般"),
    /**
     * 紧急
     */
    URGENT("urgent", "紧急"),
    /**
     * 停线
     */
    CRITICAL("critical", "停线");

    private final String code;

    private final String label;

    AndonLevel(String code, String label) {
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
