package org.tuoyu.common.satoken.context;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.tuoyu.common.core.context.AccessRequirement;
import org.tuoyu.common.core.context.PermissionContext;
import org.tuoyu.common.core.utils.ServletUtils;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 从当前请求命中的接口上读取 Sa-Token 的权限注解（{@code @SaCheckPermission}、{@code @SaCheckRole}），
 * 方法上的注解优先于类上的注解
 *
 * @author tuoyu
 */
public class SaTokenPermissionContext implements PermissionContext {

    @Override
    public AccessRequirement currentRequirement() {
        HttpServletRequest request = ServletUtils.getRequest();
        if (request == null) {
            return AccessRequirement.NONE;
        }
        if (!(request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod handler)) {
            return AccessRequirement.NONE;
        }
        Set<String> permissions = new LinkedHashSet<>();
        Set<String> roleKeys = new LinkedHashSet<>();
        SaCheckPermission checkPermission = find(handler, SaCheckPermission.class);
        if (checkPermission != null) {
            addAll(permissions, checkPermission.value());
            addAll(roleKeys, checkPermission.orRole());
        }
        SaCheckRole checkRole = find(handler, SaCheckRole.class);
        if (checkRole != null) {
            addAll(roleKeys, checkRole.value());
        }
        return new AccessRequirement(permissions, roleKeys);
    }

    private static <A extends Annotation> A find(HandlerMethod handler, Class<A> type) {
        A annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), type);
        return annotation != null ? annotation : AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), type);
    }

    private static void addAll(Set<String> target, String[] values) {
        if (values != null) {
            Arrays.stream(values).filter(v -> v != null && !v.isBlank()).forEach(target::add);
        }
    }

}
