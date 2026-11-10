package org.tuoyu.mes.label.enums;

import org.tuoyu.mes.common.CodeEnum;

import java.util.EnumSet;
import java.util.Set;

/**
 * 标签上可以显示的字段，编码与打印数据（MesLabelDataVo）的属性名一致
 * <p>
 * 数据库与接口中保存 {@link #code()}，界面按它显示文案；值一经发布不再修改。
 *
 * @author tuoyu
 */
public enum LabelField implements CodeEnum {

    /**
     * 序列号
     */
    SN_CODE("snCode", "序列号", EnumSet.of(LabelTarget.SN)),
    /**
     * 批次号
     */
    BATCH_CODE("batchCode", "批次号", EnumSet.allOf(LabelTarget.class)),
    /**
     * 工单号
     */
    WORKORDER_CODE("workorderCode", "工单号", EnumSet.allOf(LabelTarget.class)),
    /**
     * 产品编码
     */
    MATERIAL_CODE("materialCode", "产品编码", EnumSet.allOf(LabelTarget.class)),
    /**
     * 产品名称
     */
    MATERIAL_NAME("materialName", "产品名称", EnumSet.allOf(LabelTarget.class)),
    /**
     * 规格型号
     */
    SPECIFICATION("specification", "规格型号", EnumSet.allOf(LabelTarget.class)),
    /**
     * 数量（批次的计划数与单位）
     */
    QUANTITY("quantity", "数量", EnumSet.of(LabelTarget.BATCH)),
    /**
     * 生产日期
     */
    PRODUCTION_DATE("productionDate", "生产日期", EnumSet.allOf(LabelTarget.class));

    private final String code;

    private final String label;

    private final Set<LabelTarget> targets;

    LabelField(String code, String label, Set<LabelTarget> targets) {
        this.code = code;
        this.label = label;
        this.targets = targets;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String label() {
        return label;
    }

    /**
     * 是否适用于给定的对象（例如序列号只出现在序列号标签上）
     */
    public boolean appliesTo(LabelTarget target) {
        return targets.contains(target);
    }

}
