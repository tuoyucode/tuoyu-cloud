package org.tuoyu.workflow.liteflow.instance;

import cn.hutool.core.util.ObjectUtil;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.core.context.UserContext;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.service.DefService;
import org.tuoyu.workflow.common.ConditionalOnEnable;
import org.tuoyu.workflow.domain.context.InstanceDeleteContext;
import org.tuoyu.workflow.handler.FlowProcessEventHandler;

import java.util.Map;
import java.util.function.Function;

/**
 * 校验实例删除权限并发布业务删除事件。
 *
 * @author may
 */
@ConditionalOnEnable
@RequiredArgsConstructor
@Slf4j
@LiteflowComponent("instanceDeleteEvent")
public class InstanceDeleteEventComponent extends NodeComponent {

    private final DefService defService;
    private final FlowProcessEventHandler flowProcessEventHandler;

    @Override
    public void process() {
        InstanceDeleteContext context = getContextBean(InstanceDeleteContext.class);
        String userId = UserContext.userIdString();
        context.getFlowInstances().forEach(flowInstance -> {
            if (!UserContext.isSuperAdmin() && !flowInstance.getCreateBy().equals(userId)) {
                throw new ServiceException("权限不足，无法删除流程实例信息!");
            }
        });

        Map<Long, Definition> definitionMap = StreamUtils.toMap(
            defService.getByIds(StreamUtils.toList(context.getFlowInstances(), Instance::getDefinitionId)),
            Definition::getId,
            Function.identity()
        );
        context.getFlowInstances().forEach(instance -> {
            Definition definition = definitionMap.get(instance.getDefinitionId());
            if (ObjectUtil.isNull(definition)) {
                log.warn("实例 ID: {} 对应的流程定义信息未找到，跳过删除事件触发。", instance.getId());
                return;
            }
            flowProcessEventHandler.processDeleteHandler(definition.getFlowCode(), instance.getBusinessId());
        });
    }

}
