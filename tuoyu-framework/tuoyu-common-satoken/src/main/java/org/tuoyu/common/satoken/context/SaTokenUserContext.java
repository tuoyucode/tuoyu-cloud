package org.tuoyu.common.satoken.context;

import org.tuoyu.common.core.context.CurrentRole;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.model.LoginUser;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 Sa-Token 登录会话的当前用户上下文
 *
 * @author tuoyu
 */
public class SaTokenUserContext implements UserContext {

    @Override
    public Optional<CurrentUser> currentUser() {
        LoginUser loginUser;
        try {
            loginUser = LoginHelper.getLoginUser();
        } catch (RuntimeException e) {
            // 不在请求或 RPC 调用中（例如后台线程），Sa-Token 没有上下文
            return Optional.empty();
        }
        return Optional.ofNullable(loginUser).map(SaTokenUserContext::toCurrentUser);
    }

    static CurrentUser toCurrentUser(LoginUser loginUser) {
        CurrentUser user = new CurrentUser();
        user.setUserId(loginUser.getUserId());
        user.setUserName(loginUser.getUsername());
        user.setDeptId(loginUser.getDeptId());
        user.setDeptName(loginUser.getDeptName());
        user.setTenantId(loginUser.getTenantId());
        user.setSuperAdmin(LoginHelper.isSuperAdmin(loginUser.getUserId()));
        user.setRoles(loginUser.getRoles() == null ? List.of() : loginUser.getRoles().stream()
            .map(role -> new CurrentRole(role.getRoleId(), role.getRoleKey(), role.getDataScope()))
            .toList());
        user.setDataScopeRoleMap(loginUser.getDataScopeRoleMap() == null ? Map.of() : loginUser.getDataScopeRoleMap());
        return user;
    }

}
