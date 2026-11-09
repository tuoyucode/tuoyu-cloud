package org.tuoyu.mes.common;

import org.tuoyu.common.core.exception.ServiceException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 状态流转的前置校验：每个业务动作只允许从指定的状态发起，否则抛出说明当前状态与动作的业务异常，
 * 例如“工单当前为已关闭，不能下达”。前端按状态控制按钮，但流转规则以这里为准。
 *
 * @author tuoyu
 */
public final class StateGuard {

    private StateGuard() {
    }

    /**
     * 校验当前状态允许执行动作
     *
     * @param object  对象名称，例如“工单”
     * @param type    状态枚举类型
     * @param current 当前状态编码
     * @param action  动作名称，例如“下达”
     * @param allowed 允许发起动作的状态
     * @return 当前状态
     */
    @SafeVarargs
    public static <E extends Enum<E> & CodeEnum> E require(String object, Class<E> type, String current, String action,
                                                           E... allowed) {
        E state = CodeEnum.find(type, current)
            .orElseThrow(() -> new ServiceException(object + "的状态不正确：" + current));
        for (E candidate : allowed) {
            if (candidate == state) {
                return state;
            }
        }
        String expected = Arrays.stream(allowed).map(CodeEnum::label).collect(Collectors.joining("、"));
        throw new ServiceException(object + "当前为“" + state.label() + "”，不能" + action + "（只有" + expected + "的" + object + "可以" + action + "）");
    }

}
