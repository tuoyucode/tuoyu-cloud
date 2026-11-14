package org.tuoyu.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 推送消息的类型。取值写进推送的消息体，前端按它决定展示方式。
 *
 * @author tuoyu
 */
@Getter
@AllArgsConstructor
public enum PushTypeEnum {

    /**
     * 普通消息
     */
    MESSAGE("message"),

    /**
     * 通知公告
     */
    NOTICE("notice"),

    /**
     * 大模型的回复
     */
    LLM("llm"),

    /**
     * 自定义
     */
    CUSTOM("custom");

    /**
     * 类型标识
     */
    private final String type;

}
