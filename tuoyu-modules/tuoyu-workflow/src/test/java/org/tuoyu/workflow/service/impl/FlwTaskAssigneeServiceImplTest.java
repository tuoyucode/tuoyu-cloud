package org.tuoyu.workflow.service.impl;

import org.dromara.warm.flow.ui.dto.HandlerQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.system.api.RemoteDeptService;
import org.tuoyu.system.api.RemotePostService;
import org.tuoyu.system.api.RemoteRoleService;
import org.tuoyu.system.api.RemoteTaskAssigneeService;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.bo.RemoteTaskAssigneeBo;
import org.tuoyu.system.api.domain.vo.RemoteTaskAssigneeVo;
import org.tuoyu.workflow.service.IFlwSpelService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("流程设计器选择办理人")
class FlwTaskAssigneeServiceImplTest {

    private final RemoteTaskAssigneeService remoteTaskAssigneeService = mock(RemoteTaskAssigneeService.class);

    private final FlwTaskAssigneeServiceImpl service = new FlwTaskAssigneeServiceImpl(remoteTaskAssigneeService,
        mock(RemoteUserService.class), mock(RemoteDeptService.class), mock(RemoteRoleService.class),
        mock(RemotePostService.class), mock(IFlwSpelService.class));

    @Test
    @DisplayName("设计器的 pageNum 转为远程查询的 page，查询条件原样传递")
    void handlerSelectPassesPaging() {
        RemoteTaskAssigneeVo empty = new RemoteTaskAssigneeVo();
        empty.setList(List.of());
        when(remoteTaskAssigneeService.selectRolesByTaskAssigneeList(any())).thenReturn(empty);
        HandlerQuery query = new HandlerQuery();
        query.setHandlerType("角色");
        query.setHandlerCode("common");
        query.setPageNum(3);
        query.setPageSize(20);

        service.getHandlerSelect(query);

        ArgumentCaptor<RemoteTaskAssigneeBo> captor = ArgumentCaptor.forClass(RemoteTaskAssigneeBo.class);
        verify(remoteTaskAssigneeService).selectRolesByTaskAssigneeList(captor.capture());
        assertThat(captor.getValue().getPage()).isEqualTo(3);
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getHandlerCode()).isEqualTo("common");
    }

}
