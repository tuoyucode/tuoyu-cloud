package org.tuoyu.mes.material.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 领退料类型
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum MaterialRequestType implements CodeEnum {

    /**
     * 领料
     */
    ISSUE("issue", "领料"),
    /**
     * 补料
     */
    SUPPLEMENT("supplement", "补料"),
    /**
     * 退料
     */
    RETURN("return", "退料");

    private final String code;

    private final String label;

    MaterialRequestType(String code, String label) {
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
