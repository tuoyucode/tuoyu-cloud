package org.tuoyu.mes.andon.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 安灯异常类型
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum AndonType implements CodeEnum {

    /**
     * 设备
     */
    EQUIPMENT("equipment", "设备"),
    /**
     * 质量
     */
    QUALITY("quality", "质量"),
    /**
     * 物料
     */
    MATERIAL("material", "物料"),
    /**
     * 工艺
     */
    PROCESS("process", "工艺"),
    /**
     * 其他
     */
    OTHER("other", "其他");

    private final String code;

    private final String label;

    AndonType(String code, String label) {
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
