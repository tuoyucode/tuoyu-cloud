package org.tuoyu.common.selfcheck;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * 将启动自检的失败结果整理为 Spring Boot 标准的 “APPLICATION FAILED TO START” 报告，一次列出全部问题
 *
 * @author tuoyu
 */
public class SelfCheckFailureAnalyzer extends AbstractFailureAnalyzer<SelfCheckFailedException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, SelfCheckFailedException cause) {
        StringBuilder description = new StringBuilder()
            .append("启动自检未通过（").append(cause.isProduction() ? "生产模式" : "开发模式")
            .append("），共 ").append(cause.getProblems().size()).append(" 项问题：\n");
        StringBuilder action = new StringBuilder("按以下方式修复后重新启动：\n");
        for (SelfCheckProblem problem : cause.getProblems()) {
            description.append("\n[").append(problem.id()).append("] ").append(problem.title());
            problem.details().forEach(detail -> description.append("\n    - ").append(detail));
            action.append("\n[").append(problem.id()).append("] ").append(problem.action());
        }
        action.append("\n\n确需临时跳过某项检查时，可设置 ").append(SelfCheckEnvironmentPostProcessor.SKIP_KEY)
            .append("=<检查编号>，例如 ").append(SelfCheckEnvironmentPostProcessor.SKIP_KEY).append('=')
            .append(cause.getProblems().getFirst().id()).append("（跳过的检查会在日志中提示）");
        return new FailureAnalysis(description.toString(), action.toString(), cause);
    }

}
