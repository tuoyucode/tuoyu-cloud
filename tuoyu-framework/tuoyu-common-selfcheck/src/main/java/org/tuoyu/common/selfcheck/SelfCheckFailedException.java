package org.tuoyu.common.selfcheck;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 启动自检未通过
 *
 * @author tuoyu
 */
public class SelfCheckFailedException extends RuntimeException {

    private final boolean production;

    private final List<SelfCheckProblem> problems;

    public SelfCheckFailedException(boolean production, List<SelfCheckProblem> problems) {
        super("启动自检未通过：" + problems.stream().map(SelfCheckProblem::id).collect(Collectors.joining(", ")));
        this.production = production;
        this.problems = List.copyOf(problems);
    }

    public boolean isProduction() {
        return production;
    }

    public List<SelfCheckProblem> getProblems() {
        return problems;
    }

}
