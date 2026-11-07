package org.tuoyu.common.core.web;

/**
 * 当前请求的 traceId。由链路追踪模块（tuoyu-common-observability）注册为 Bean；
 * 不依赖该模块的组件（例如请求大小限制过滤器、服务的登录校验过滤器）通过它把 traceId 写进错误响应。
 *
 * @author tuoyu
 */
@FunctionalInterface
public interface TraceIdSource {

    /**
     * 没有链路追踪时使用：traceId 始终为 null
     */
    TraceIdSource NONE = () -> null;

    /**
     * @return 当前线程的 traceId；不在任何链路中时为 null
     */
    String currentTraceId();

}
