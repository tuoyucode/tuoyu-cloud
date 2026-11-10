package org.tuoyu.mes.analysis.enums;

import org.tuoyu.mes.common.CodeEnum;

/**
 * 分析报表的统计维度（按什么分组）。各报表支持的维度不同，由服务层校验。
 * <p>
 * 编码在接口中传递（查询参数 {@code dimension}），并作为聚合 SQL 中选择分组列的依据，只能取这里列出的值。
 *
 * @author tuoyu
 */
public enum AnalysisDimension implements CodeEnum {

    /**
     * 日期（再按日、周、月归并）
     */
    DATE("date", "日期"),
    /**
     * 产品
     */
    PRODUCT("product", "产品"),
    /**
     * 产线
     */
    LINE("line", "产线"),
    /**
     * 工序
     */
    OPERATION("operation", "工序"),
    /**
     * 人员（报工人）
     */
    USER("user", "人员"),
    /**
     * 班组
     */
    TEAM("team", "班组"),
    /**
     * 停机原因
     */
    REASON("reason", "停机原因"),
    /**
     * 不良等级
     */
    LEVEL("level", "不良等级"),
    /**
     * 不良来源
     */
    SOURCE("source", "不良来源"),
    /**
     * 不良处置方式
     */
    HANDLE_METHOD("handleMethod", "处置方式");

    private final String code;

    private final String label;

    AnalysisDimension(String code, String label) {
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
