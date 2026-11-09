package org.tuoyu.mes.quality.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 不良来源
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum DefectSource implements CodeEnum {

    /**
     * 报工
     */
    REPORT("report", "报工"),
    /**
     * 检验
     */
    INSPECTION("inspection", "检验"),
    /**
     * 手工登记
     */
    MANUAL("manual", "手工登记");

    private final String code;

    private final String label;

    DefectSource(String code, String label) {
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
