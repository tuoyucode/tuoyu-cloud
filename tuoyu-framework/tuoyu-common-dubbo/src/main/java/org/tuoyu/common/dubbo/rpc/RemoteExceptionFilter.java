package org.tuoyu.common.dubbo.rpc;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.BaseFilter;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.service.GenericService;
import org.tuoyu.common.core.exception.ServiceException;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 提供方异常处理（取代 Dubbo 默认的 exception 过滤器，配置中以 {@code -exception} 关闭默认的）。
 * <p>
 * 让异常尽量原样到达调用方，与单体中的本地调用一致。只有调用方可能没有的异常类才转换：
 * <ul>
 *     <li>原样传回：受检异常、方法签名中声明的异常、JDK 异常、{@code org.tuoyu.common.core} 中的异常（各服务都有）、
 *     与接口在同一个 jar 中的异常、Dubbo 的 RpcException；</li>
 *     <li>其余异常转换为 {@link ServiceException}：只带错误编号，不带原异常的类名与消息（可能含 SQL、表达式等内部信息，
 *     调用方会把 ServiceException 的消息返回给客户端）；详细信息按编号记在提供方的日志中。</li>
 * </ul>
 *
 * @author tuoyu
 */
@Slf4j
@Activate(group = CommonConstants.PROVIDER)
public class RemoteExceptionFilter implements Filter, BaseFilter.Listener {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        return invoker.invoke(invocation);
    }

    @Override
    public void onResponse(Result result, Invoker<?> invoker, Invocation invocation) {
        if (!result.hasException() || GenericService.class == invoker.getInterface()) {
            return;
        }
        Throwable exception = result.getException();
        if (passThrough(exception, invoker.getInterface(), invocation)) {
            return;
        }
        String errorId = RandomUtil.randomNumbers(8);
        log.error("跨服务调用 {}.{} 抛出调用方可能没有的异常，已转换为 ServiceException, 错误编号: {}",
            invoker.getInterface().getName(), invocation.getMethodName(), errorId, exception);
        result.setException(new ServiceException("服务调用失败，请联系管理员 [错误编号: " + errorId + "]", HttpStatus.HTTP_INTERNAL_ERROR));
    }

    @Override
    public void onError(Throwable t, Invoker<?> invoker, Invocation invocation) {
        // 框架异常由 Dubbo 处理
    }

    static boolean passThrough(Throwable exception, Class<?> api, Invocation invocation) {
        if (!(exception instanceof RuntimeException) && exception instanceof Exception) {
            return true;
        }
        if (exception instanceof RpcException || isShared(exception.getClass().getName())) {
            return true;
        }
        try {
            Method method = api.getMethod(invocation.getMethodName(), invocation.getParameterTypes());
            if (Arrays.stream(method.getExceptionTypes()).anyMatch(type -> type.isInstance(exception))) {
                return true;
            }
        } catch (NoSuchMethodException e) {
            return false;
        }
        return sameJar(exception.getClass(), api);
    }

    private static boolean isShared(String className) {
        return className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("jakarta.")
            || className.startsWith("org.tuoyu.common.core.");
    }

    private static boolean sameJar(Class<?> exception, Class<?> api) {
        var exceptionSource = exception.getProtectionDomain().getCodeSource();
        var apiSource = api.getProtectionDomain().getCodeSource();
        return exceptionSource != null && apiSource != null
            && exceptionSource.getLocation() != null && exceptionSource.getLocation().equals(apiSource.getLocation());
    }

}
