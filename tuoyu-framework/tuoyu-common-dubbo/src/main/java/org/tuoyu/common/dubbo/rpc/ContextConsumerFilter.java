package org.tuoyu.common.dubbo.rpc;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.tuoyu.common.core.context.ContextPropagators;

import java.util.Map;

/**
 * 调用方：取出全部上下文（租户、数据权限等，见 {@link org.tuoyu.common.core.context.ContextPropagator}），放进附件
 *
 * @author tuoyu
 */
@Activate(group = CommonConstants.CONSUMER, order = -10000)
public class ContextConsumerFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        ContextPropagators propagators = ContextFilters.propagators(invoker);
        if (propagators != null) {
            Map<String, String> values = propagators.capture();
            if (!values.isEmpty()) {
                RpcContext.getClientAttachment().setAttachment(ContextAttachment.KEY, ContextAttachment.encode(values));
            }
        }
        return invoker.invoke(invocation);
    }

}
