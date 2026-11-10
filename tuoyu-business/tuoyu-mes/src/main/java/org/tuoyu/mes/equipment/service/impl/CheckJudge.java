package org.tuoyu.mes.equipment.service.impl;

import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.equipment.domain.MesCheckItem;
import org.tuoyu.mes.equipment.domain.MesCheckRecordDetail;
import org.tuoyu.mes.equipment.domain.bo.MesCheckRecordDetailBo;
import org.tuoyu.mes.equipment.enums.CheckItemResult;
import org.tuoyu.mes.equipment.enums.CheckResultType;

import java.math.BigDecimal;

/**
 * 点检的单项判定：
 * <ul>
 *     <li>判定型：取填写的合格或不合格；</li>
 *     <li>数值型：按测量值判定，低于下限或高于上限为不合格（等于上下限算合格），不采用客户端传来的结论；</li>
 *     <li>没有填写时：必检项拒绝提交，非必检项记为未检（结果为空）。</li>
 * </ul>
 *
 * @author tuoyu
 */
final class CheckJudge {

    private CheckJudge() {
    }

    /**
     * 按项目与填写的值生成明细（保存项目快照与判定结果）
     *
     * @param item  点检项目
     * @param input 填写的值，没有填写时为 null
     * @return 明细（点检记录 ID 由调用方设置）
     */
    static MesCheckRecordDetail detail(MesCheckItem item, MesCheckRecordDetailBo input) {
        MesCheckRecordDetail detail = new MesCheckRecordDetail();
        detail.setCheckItemId(item.getCheckItemId());
        detail.setItemName(item.getItemName());
        detail.setCheckMethod(item.getCheckMethod());
        detail.setCheckStandard(item.getCheckStandard());
        detail.setResultType(item.getResultType());
        detail.setLowerLimit(item.getLowerLimit());
        detail.setUpperLimit(item.getUpperLimit());
        detail.setUnit(item.getUnit());
        detail.setMandatory(StringUtils.blankToDefault(item.getMandatory(), MesCheckItemServiceImpl.YES));
        detail.setSortNo(item.getSortNo() == null ? 0 : item.getSortNo());
        detail.setRemark(input == null ? null : StringUtils.trimToNull(input.getRemark()));
        CheckItemResult result;
        if (CheckResultType.NUMERIC.code().equals(item.getResultType())) {
            BigDecimal value = input == null ? null : input.getMeasuredValue();
            detail.setMeasuredValue(value);
            result = value == null ? null : judge(value, item.getLowerLimit(), item.getUpperLimit());
        } else {
            String text = input == null ? null : StringUtils.trimToNull(input.getItemResult());
            result = text == null ? null : CodeEnum.of(CheckItemResult.class, text, "“" + item.getItemName() + "”的结果");
        }
        if (result == null && MesCheckItemServiceImpl.YES.equals(detail.getMandatory())) {
            throw new ServiceException("必检项目“" + item.getItemName() + "”没有填写结果");
        }
        detail.setItemResult(result == null ? null : result.code());
        return detail;
    }

    /**
     * 数值判定：超出上下限（不含边界）为不合格；没有设置的限值不检查
     */
    static CheckItemResult judge(BigDecimal value, BigDecimal lower, BigDecimal upper) {
        boolean belowLower = lower != null && value.compareTo(lower) < 0;
        boolean aboveUpper = upper != null && value.compareTo(upper) > 0;
        return belowLower || aboveUpper ? CheckItemResult.NG : CheckItemResult.OK;
    }

}
