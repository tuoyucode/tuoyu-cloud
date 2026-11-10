package org.tuoyu.mes.common.code;

import java.util.List;

/**
 * 单据编号与批次号、序列号的生成
 *
 * @author tuoyu
 */
public interface IMesCodeService {

    /**
     * 按规则生成下一个编号，例如 WO202611080001
     *
     * @param ruleCode 规则编码，见 {@link CodeRules}
     * @return 编号
     */
    String next(String ruleCode);

    /**
     * 按规则连续生成多个编号（序列号批量生成时使用）
     *
     * @param ruleCode 规则编码
     * @param count    数量
     * @return 编号，按流水号升序
     */
    List<String> next(String ruleCode, int count);

    /**
     * 按规则预览下一个编号的格式，不占用流水号
     *
     * @param ruleCode 规则编码
     * @return 示例编号（流水号为 1）
     */
    String preview(String ruleCode);

    /**
     * 规则的重置周期变更后调用：按新周期计算的当前周期从该规则用过的最大流水号之后继续。
     * 周期标识随重置周期变化（例如按天变为不重置），不接续时新周期从 1 开始，会与当天已发出的编号重复
     *
     * @param ruleCode   规则编码
     * @param resetCycle 新的重置周期
     */
    void carryOver(String ruleCode, String resetCycle);

}
