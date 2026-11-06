package org.tuoyu.workflow.config;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.workflow.service.IFlwInstanceService;

/**
 * Warm-Flow 自带接口（/warm-flow/**，第三方 jar 中的 Controller）的权限。
 * <p>
 * 这些接口没有我们的权限注解，登录校验之外需要按用途补上权限：
 * <ul>
 *     <li>流程图 {@code query-flow-chart/{实例id}}：办理人、发起人都要看，与审批记录相同，按流程参与关系校验；</li>
 *     <li>保存设计 {@code save-json}、保存表单 {@code POST form-content}：修改流程定义，需要 workflow:definition:edit；</li>
 *     <li>其他（读取定义、办理人与监听器的候选列表等，只有设计器使用）：需要 workflow:definition:query 或 edit。
 *     办理人候选列表中有用户、角色、部门，不能对所有登录用户开放；</li>
 *     <li>内置表单办理 {@code execute/**}：不开放。办理任务走 /workflow/task 下的接口，那里校验办理人并去掉
 *     请求中的 ignore 等内部变量；这里放开等于绕过这些校验。</li>
 * </ul>
 *
 * @author tuoyu
 */
public class WarmFlowAccessInterceptor implements HandlerInterceptor {

    static final String PATTERN = "/warm-flow/**";

    static final String DEFINITION_EDIT = "workflow:definition:edit";

    static final String DEFINITION_QUERY = "workflow:definition:query";

    private static final String CHART = "/warm-flow/query-flow-chart/{id}";

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    /**
     * 访问规则
     */
    enum Rule {
        /** 流程图：按流程参与关系校验 */
        CHART,
        /** 修改流程定义 */
        EDIT,
        /** 设计器读取 */
        QUERY,
        /** 不开放 */
        BLOCKED
    }

    private final IFlwInstanceService instanceService;

    public WarmFlowAccessInterceptor(IFlwInstanceService instanceService) {
        this.instanceService = instanceService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = PATH_HELPER.getPathWithinApplication(request);
        switch (rule(request.getMethod(), path)) {
            case CHART -> instanceService.checkInstanceReadAccess(instanceId(path));
            case EDIT -> StpUtil.checkPermission(DEFINITION_EDIT);
            case QUERY -> StpUtil.checkPermissionOr(DEFINITION_QUERY, DEFINITION_EDIT);
            case BLOCKED -> throw new NotPermissionException(
                "未开放 Warm-Flow 内置表单办理，请使用 /workflow/task 下的办理接口");
        }
        return true;
    }

    /**
     * 按请求方法与路径确定访问规则
     */
    static Rule rule(String method, String path) {
        if (MATCHER.match(CHART, path)) {
            return Rule.CHART;
        }
        if (MATCHER.match("/warm-flow/execute/**", path)) {
            return Rule.BLOCKED;
        }
        boolean write = !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method);
        if (write) {
            return Rule.EDIT;
        }
        return Rule.QUERY;
    }

    private static Long instanceId(String path) {
        String id = MATCHER.extractUriTemplateVariables(CHART, path).get("id");
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            throw new ServiceException("流程实例id不正确：" + id);
        }
    }

}
