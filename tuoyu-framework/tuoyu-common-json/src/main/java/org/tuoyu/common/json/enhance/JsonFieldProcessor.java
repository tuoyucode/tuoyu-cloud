package org.tuoyu.common.json.enhance;

/**
 * 响应字段处理器。
 */
public interface JsonFieldProcessor {

    /**
     * 判断当前处理器是否需要处理该字段。
     * 默认返回 true 以兼容无注解驱动的自定义处理器。
     */
    default boolean supports(JsonFieldContext fieldContext) {
        return true;
    }

    default void collect(JsonFieldContext fieldContext, JsonEnhancementContext context) {
    }

    default void prepare(JsonEnhancementContext context) {
    }

    default Object process(JsonFieldContext fieldContext, Object value, JsonEnhancementContext context) {
        return value;
    }

}
