package org.tuoyu.mes.common.code;

import java.util.Map;

/**
 * 单据编号的规则编码（{@code mes_code_rule.rule_code}）与默认格式。
 * <p>
 * 默认租户的规则写在种子数据中，管理员可以在“编码规则”中调整前缀、日期与流水号位数；
 * 某个租户没有对应的规则（或规则已停用）时，按这里的默认格式生成。
 *
 * @author tuoyu
 */
public final class CodeRules {

    public static final String WORKORDER = "WORKORDER";
    public static final String TASK = "TASK";
    public static final String REPORT = "REPORT";
    public static final String MATERIAL_ISSUE = "MATERIAL_ISSUE";
    public static final String MATERIAL_SUPPLEMENT = "MATERIAL_SUPPLEMENT";
    public static final String MATERIAL_RETURN = "MATERIAL_RETURN";
    public static final String STOCK_IN = "STOCK_IN";
    public static final String INSPECTION = "INSPECTION";
    public static final String DEFECT = "DEFECT";
    public static final String ANDON = "ANDON";
    public static final String BATCH = "BATCH";
    public static final String SN = "SN";
    public static final String CHECK_RECORD = "CHECK_RECORD";
    public static final String MAINTENANCE_ORDER = "MAINTENANCE_ORDER";

    /**
     * 默认格式：前缀、日期格式、流水号位数、重置周期
     *
     * @param prefix       前缀
     * @param datePattern  日期格式，为空表示不带日期
     * @param serialLength 流水号位数
     * @param resetCycle   重置周期（none、day、month、year）
     */
    public record Format(String prefix, String datePattern, int serialLength, String resetCycle) {
    }

    private static final Map<String, Format> DEFAULTS = Map.ofEntries(
        Map.entry(WORKORDER, new Format("WO", "yyyyMMdd", 4, "day")),
        Map.entry(TASK, new Format("OT", "yyyyMMdd", 5, "day")),
        Map.entry(REPORT, new Format("RP", "yyyyMMdd", 5, "day")),
        Map.entry(MATERIAL_ISSUE, new Format("LL", "yyyyMMdd", 4, "day")),
        Map.entry(MATERIAL_SUPPLEMENT, new Format("BL", "yyyyMMdd", 4, "day")),
        Map.entry(MATERIAL_RETURN, new Format("TL", "yyyyMMdd", 4, "day")),
        Map.entry(STOCK_IN, new Format("RK", "yyyyMMdd", 4, "day")),
        Map.entry(INSPECTION, new Format("QC", "yyyyMMdd", 4, "day")),
        Map.entry(DEFECT, new Format("NG", "yyyyMMdd", 4, "day")),
        Map.entry(ANDON, new Format("AD", "yyyyMMdd", 4, "day")),
        Map.entry(BATCH, new Format("B", "yyMMdd", 3, "day")),
        Map.entry(SN, new Format("SN", "yyMMdd", 6, "day")),
        Map.entry(CHECK_RECORD, new Format("DJ", "yyyyMMdd", 4, "day")),
        Map.entry(MAINTENANCE_ORDER, new Format("BY", "yyyyMMdd", 4, "day")));

    private CodeRules() {
    }

    /**
     * 规则的默认格式；不是内置规则时为通用格式（规则编码作前缀、按天重置、4 位流水号）
     */
    public static Format defaultFormat(String ruleCode) {
        return DEFAULTS.getOrDefault(ruleCode, new Format(ruleCode, "yyyyMMdd", 4, "day"));
    }

}
