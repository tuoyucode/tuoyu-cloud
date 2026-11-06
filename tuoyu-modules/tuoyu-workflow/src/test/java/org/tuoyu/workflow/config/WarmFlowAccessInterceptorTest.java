package org.tuoyu.workflow.config;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.tuoyu.workflow.service.IFlwInstanceService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("Warm-Flow 自带接口的权限")
class WarmFlowAccessInterceptorTest {

    @Test
    @DisplayName("按接口分级：流程图按参与关系、保存需要修改权限、设计器读取需要查询权限、内置表单办理不开放")
    void shouldMapRules() {
        assertEquals(WarmFlowAccessInterceptor.Rule.CHART, WarmFlowAccessInterceptor.rule("GET", "/warm-flow/query-flow-chart/123"));
        assertEquals(WarmFlowAccessInterceptor.Rule.EDIT, WarmFlowAccessInterceptor.rule("POST", "/warm-flow/save-json"));
        assertEquals(WarmFlowAccessInterceptor.Rule.EDIT, WarmFlowAccessInterceptor.rule("POST", "/warm-flow/form-content"));
        for (String path : new String[]{"/warm-flow/query-def", "/warm-flow/query-def/1", "/warm-flow/handler-type",
            "/warm-flow/handler-result", "/warm-flow/handler-feedback", "/warm-flow/handler-dict", "/warm-flow/node-ext",
            "/warm-flow/listener-list", "/warm-flow/published-form", "/warm-flow/form-content/1"}) {
            assertEquals(WarmFlowAccessInterceptor.Rule.QUERY, WarmFlowAccessInterceptor.rule("GET", path), path);
        }
        for (String path : new String[]{"/warm-flow/execute/load/1", "/warm-flow/execute/hisLoad/1", "/warm-flow/execute/handle"}) {
            assertEquals(WarmFlowAccessInterceptor.Rule.BLOCKED, WarmFlowAccessInterceptor.rule("POST", path), path);
            assertEquals(WarmFlowAccessInterceptor.Rule.BLOCKED, WarmFlowAccessInterceptor.rule("GET", path), path);
        }
    }

    @Test
    @DisplayName("流程图：按流程实例校验当前用户能否查看")
    void chartShouldCheckInstanceAccess() {
        IFlwInstanceService service = mock(IFlwInstanceService.class);

        new WarmFlowAccessInterceptor(service).preHandle(request("GET", "/warm-flow/query-flow-chart/42"),
            new MockHttpServletResponse(), new Object());

        verify(service).checkInstanceReadAccess(42L);
    }

    @Test
    @DisplayName("内置表单办理：一律拒绝，不调用任何服务")
    void executeShouldBeBlocked() {
        IFlwInstanceService service = mock(IFlwInstanceService.class);

        assertThrows(NotPermissionException.class, () -> new WarmFlowAccessInterceptor(service)
            .preHandle(request("POST", "/warm-flow/execute/handle"), new MockHttpServletResponse(), new Object()));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("保存设计需要 workflow:definition:edit；设计器读取需要 query 或 edit")
    void designerShouldCheckPermissions() {
        IFlwInstanceService service = mock(IFlwInstanceService.class);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            WarmFlowAccessInterceptor interceptor = new WarmFlowAccessInterceptor(service);

            interceptor.preHandle(request("POST", "/warm-flow/save-json"), new MockHttpServletResponse(), new Object());
            interceptor.preHandle(request("GET", "/warm-flow/handler-result"), new MockHttpServletResponse(), new Object());

            stp.verify(() -> StpUtil.checkPermission("workflow:definition:edit"));
            stp.verify(() -> StpUtil.checkPermissionOr("workflow:definition:query", "workflow:definition:edit"));
        }
        verify(service, never()).checkInstanceReadAccess(org.mockito.ArgumentMatchers.anyLong());
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

}
