package org.tuoyu.workflow.api.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.core.utils.SpringUtils;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;

import java.io.Serial;

/**
 * 删除流程监听
 *
 * @author AprilWind
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessDeleteEvent extends RemoteApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 流程定义编码
     */
    private String flowCode;

    /**
     * 业务id
     */
    private String businessId;

    public ProcessDeleteEvent() {
        super(new Object(), SpringUtils.getApplicationName(), DEFAULT_DESTINATION_FACTORY.getDestination(null));
    }

}
