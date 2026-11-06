package org.tuoyu.common.core.context;

import java.util.Map;

/**
 * 上下文透传：把当前线程中的某类上下文（租户、数据权限等）带到另一个执行位置，并在那里恢复。
 * <p>
 * 同一套实现用于两种场景：
 * <ul>
 *     <li>跨服务：微服务方式下的 RPC 调用（由 tuoyu-common-dubbo 的过滤器调用 {@link #capture()}、{@link #restore(Map)}）；</li>
 *     <li>跨线程：异步任务、线程池（由 {@link ContextPropagators} 包装任务，调用 {@link #captureLocal()}、{@link #restoreLocal(Map)}）。</li>
 * </ul>
 * 透传的值只能是字符串，便于签名、记录与审计；不能透传对象，避免反序列化不可信的数据。
 * 实现注册为 Spring Bean 即可生效。
 *
 * @author tuoyu
 */
public interface ContextPropagator {

    /**
     * @return 透传的上下文名称，用于日志与排查
     */
    String name();

    /**
     * 在调用方取出要透传的值。键在所有实现中必须唯一，建议使用 {@code 名称.字段} 的形式。
     *
     * @return 要透传的值；没有时返回空 Map
     */
    Map<String, String> capture();

    /**
     * 在执行位置恢复上下文。
     *
     * @param values 调用方透传的全部值（包含其他实现的键，只取自己的键）
     * @return 执行结束后关闭，恢复执行前的状态
     */
    Scope restore(Map<String, String> values);

    /**
     * 跨线程时取出要透传的值。值只在同一进程的内存中传递、不会被外部篡改，
     * 所以实现可以省去跨服务时的签名与有效期（例如租户：{@code @Async} 任务可能排队很久才执行）。
     * 默认与 {@link #capture()} 相同
     *
     * @return 要透传的值；没有时返回空 Map
     */
    default Map<String, String> captureLocal() {
        return capture();
    }

    /**
     * 跨线程时恢复上下文。只能传入同一进程中 {@link #captureLocal()} 取出的值，
     * 不能用于来自其他服务的值。默认与 {@link #restore(Map)} 相同
     *
     * @param values {@link #captureLocal()} 取出的全部值
     * @return 执行结束后关闭，恢复执行前的状态
     */
    default Scope restoreLocal(Map<String, String> values) {
        return restore(values);
    }

    /**
     * 恢复的作用范围
     */
    @FunctionalInterface
    interface Scope extends AutoCloseable {

        Scope NOOP = () -> {
        };

        @Override
        void close();
    }

}
