package org.tuoyu.mes.material.support;

import java.math.BigDecimal;

/**
 * 数量的空值处理与提示格式
 *
 * @author tuoyu
 */
public final class Quantities {

    private Quantities() {
    }

    /**
     * 为空时视为 0
     */
    public static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 用于提示信息：去掉多余的 0，不用科学计数法（例如 12.500000 → 12.5）
     */
    public static String format(BigDecimal value) {
        return orZero(value).stripTrailingZeros().toPlainString();
    }

}
