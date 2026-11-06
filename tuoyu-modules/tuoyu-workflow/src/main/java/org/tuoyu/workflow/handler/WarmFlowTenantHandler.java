package org.tuoyu.workflow.handler;

import org.dromara.warm.flow.core.handler.TenantHandler;
import org.springframework.stereotype.Component;
import org.tuoyu.common.tenant.core.TenantHelper;

/**
 * Warm-Flow 的租户（ADR 0006 第 6 节）：流程定义、实例、任务等按当前租户隔离。
 * 未开启多租户时返回 null，Warm-Flow 不按租户过滤
 *
 * @author tuoyu
 */
@Component
public class WarmFlowTenantHandler implements TenantHandler {

    @Override
    public String getTenantId() {
        return TenantHelper.currentTenantId();
    }

}
