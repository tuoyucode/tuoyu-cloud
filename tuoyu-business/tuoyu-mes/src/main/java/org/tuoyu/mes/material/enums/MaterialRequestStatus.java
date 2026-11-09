package org.tuoyu.mes.material.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 领退料单状态
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum MaterialRequestStatus implements CodeEnum {

    /**
     * 草稿
     */
    DRAFT("draft", "草稿"),
    /**
     * 已提交
     */
    SUBMITTED("submitted", "已提交"),
    /**
     * 已完成
     */
    COMPLETED("completed", "已完成"),
    /**
     * 已取消
     */
    CANCELLED("cancelled", "已取消");

    private final String code;

    private final String label;

    MaterialRequestStatus(String code, String label) {
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
