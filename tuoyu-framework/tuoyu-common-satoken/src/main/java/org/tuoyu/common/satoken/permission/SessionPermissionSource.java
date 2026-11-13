package org.tuoyu.common.satoken.permission;

import cn.dev33.satoken.stp.StpInterface;
import org.springframework.beans.factory.ObjectProvider;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.service.PermissionService;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.model.LoginUser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Sa-Token 的权限来源。
 * <p>
 * 当前请求的登录对象直接取登录时保存在会话中的权限快照，不查询数据库；查询其他登录对象（或当前没有登录用户）时，
 * 从登录 ID（{@code <用户类型>-<用户 ID>}）中取出用户 ID，经 {@link PermissionService} 查询。
 * <p>
 * 每次返回新的可修改列表，调用方修改它不影响会话中的数据。
 *
 * @author tuoyu
 */
public class SessionPermissionSource implements StpInterface {

    private final ObjectProvider<PermissionService> permissionService;

    public SessionPermissionSource(ObjectProvider<PermissionService> permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return resolve(loginId, LoginUser::getMenuPermission, PermissionService::getMenuPermission);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return resolve(loginId, LoginUser::getRolePermission, PermissionService::getRolePermission);
    }

    private List<String> resolve(Object loginId, Function<LoginUser, Collection<String>> fromSession,
                                 PermissionLookup lookup) {
        String id = String.valueOf(loginId);
        LoginUser current = currentLoginUser();
        if (current != null && id.equals(loginIdOf(current))) {
            return copy(fromSession.apply(current));
        }
        long userId = parseUserId(id);
        PermissionService service = permissionService.getIfAvailable();
        if (service == null) {
            throw new ServiceException("PermissionService 实现类不存在");
        }
        return copy(lookup.find(service, userId));
    }

    private static LoginUser currentLoginUser() {
        try {
            return LoginHelper.getLoginUser();
        } catch (RuntimeException e) {
            // 不在请求上下文中（例如后台线程）：没有当前用户
            return null;
        }
    }

    private static String loginIdOf(LoginUser user) {
        try {
            return user.getLoginId();
        } catch (IllegalArgumentException e) {
            // 会话中的登录用户缺少类型或 ID，不能与任何登录 ID 对应
            return null;
        }
    }

    /**
     * 登录 ID 的格式为 {@code <用户类型>-<用户 ID>}，取最后一个 {@code -} 之后的部分
     */
    static long parseUserId(String loginId) {
        int separator = loginId.lastIndexOf(LoginUser.LOGIN_ID_SEPARATOR);
        if (separator < 0 || separator == loginId.length() - 1) {
            throw new ServiceException("登录 ID 格式不正确，应为“用户类型-用户ID”");
        }
        try {
            return Long.parseLong(loginId.substring(separator + 1));
        } catch (NumberFormatException e) {
            throw new ServiceException("登录 ID 格式不正确，用户 ID 不是数字");
        }
    }

    private static List<String> copy(Collection<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    @FunctionalInterface
    private interface PermissionLookup {

        Collection<String> find(PermissionService service, Long userId);

    }

}
