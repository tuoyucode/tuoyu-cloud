package org.tuoyu.common.dubbo.rpc;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.tuoyu.common.core.context.ContextPropagator;
import org.tuoyu.common.core.context.ContextPropagators;

/**
 * 提供方：按附件恢复调用方的上下文，调用结束后还原（线程会被复用，不能留下上一次调用的上下文）
 *
 * @author tuoyu
 */
@Activate(group = CommonConstants.PROVIDER, order = -10000)
public class ContextProviderFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        ContextPropagators propagators = ContextFilters.propagators(invoker);
        if (propagators == null) {
            return invoker.invoke(invocation);
        }
        try (ContextPropagator.Scope ignored = propagators.restore(
            ContextAttachment.decode(invocation.getAttachment(ContextAttachment.KEY)))) {
            return invoker.invoke(invocation);
        }
    }

}
