package org.tuoyu.common.selfcheck;

import java.util.List;

/**
 * 启动自检发现的一项问题
 *
 * @param id      检查编号，如 C1、P2，可用于 tuoyu.selfcheck.skip
 * @param title   问题概述
 * @param details 具体条目（变量名、配置项名等），不包含任何敏感值
 * @param action  修复建议
 * @author tuoyu
 */
public record SelfCheckProblem(String id, String title, List<String> details, String action) {

    public SelfCheckProblem {
        details = List.copyOf(details);
    }

}
