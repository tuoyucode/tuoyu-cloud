package org.tuoyu.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 推送消息的来源。取值写进推送的消息体，前端按它区分消息。
 *
 * @author tuoyu
 */
@Getter
@AllArgsConstructor
public enum PushSourceEnum {

    /**
     * 后端业务
     */
    BACKEND("backend"),

    /**
     * 通知公告
     */
    NOTICE("notice"),

    /**
     * 工作流
     */
    WORKFLOW("workflow"),

    /**
     * 大模型
     */
    LLM("llm"),

    /**
     * 客户端
     */
    CLIENT("client");

    /**
     * 来源标识
     */
    private final String source;

}
