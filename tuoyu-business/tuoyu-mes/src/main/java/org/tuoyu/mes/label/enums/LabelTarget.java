package org.tuoyu.mes.label.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 标签的适用对象
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum LabelTarget implements CodeEnum {

    /**
     * 产品序列号：一件一个标签，条码内容为序列号
     */
    SN("sn", "序列号"),
    /**
     * 生产批次：一个工单一个批次，条码内容为批次号
     */
    BATCH("batch", "批次");

    private final String code;

    private final String label;

    LabelTarget(String code, String label) {
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
