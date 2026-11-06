package org.tuoyu.workflow.liteflow.operation;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.ValidatorUtils;
import org.tuoyu.common.core.validate.AddGroup;
import org.tuoyu.common.core.validate.EditGroup;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.dromara.warm.flow.core.dto.FlowParams;
import org.tuoyu.workflow.common.ConditionalOnEnable;
import org.tuoyu.workflow.common.enums.TaskOperationEnum;
import org.tuoyu.workflow.domain.bo.TaskOperationBo;
import org.tuoyu.workflow.domain.context.TaskOperationContext;

import java.util.Collections;

/**
 * 准备任务操作参数。
 *
 * @author may
 */
@ConditionalOnEnable
@Slf4j
@LiteflowComponent("taskOpPrepare")
public class TaskOpPrepareComponent extends NodeComponent {

    @Override
    public void process() {
        TaskOperationContext context = getContextBean(TaskOperationContext.class);
        TaskOperationEnum op = TaskOperationEnum.getByCode(context.getTaskOperation());
        if (op == null) {
            log.error("Invalid operation type:{} ", context.getTaskOperation());
            throw new ServiceException("Invalid operation type " + context.getTaskOperation());
        }
        context.setOperation(op);

        TaskOperationBo bo = context.getTaskOperationBo();
        switch (op) {
            case DELEGATE_TASK, TRANSFER_TASK -> ValidatorUtils.validate(bo, AddGroup.class);
            case ADD_SIGNATURE, REDUCTION_SIGNATURE -> ValidatorUtils.validate(bo, EditGroup.class);
        }

        FlowParams flowParams = FlowParams.build().message(bo.getMessage());
        if (LoginHelper.isSuperAdmin()) {
            flowParams.ignore(true);
        }
        switch (op) {
            case DELEGATE_TASK, TRANSFER_TASK -> flowParams.addHandlers(Collections.singletonList(bo.getUserId()));
            case ADD_SIGNATURE -> flowParams.addHandlers(bo.getUserIds());
            case REDUCTION_SIGNATURE -> flowParams.reductionHandlers(bo.getUserIds());
        }
        context.setFlowParams(flowParams);
    }

}
