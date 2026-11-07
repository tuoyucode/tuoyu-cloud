package org.tuoyu.system.support;

import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.context.UserContextProvider;

import java.util.Optional;

/**
 * 测试用的当前用户：安装后 {@link UserContext} 返回这里设置的用户，关闭时恢复安装前的提供者。
 */
public final class CurrentUserStub implements AutoCloseable {

    private final UserContextProvider previous;
    private volatile CurrentUser user;

    private CurrentUserStub() {
        this.previous = UserContext.setProvider(() -> Optional.ofNullable(user));
    }

    /**
     * 安装一个没有当前用户的提供者
     */
    public static CurrentUserStub install() {
        return new CurrentUserStub();
    }

    /**
     * 当前用户换成指定 ID 的用户（超级管理员按 ID 判断），其余字段保留
     */
    public CurrentUserStub userId(Long userId) {
        CurrentUser next = user == null ? new CurrentUser() : user;
        next.setUserId(userId);
        next.setSuperAdmin(SystemConstants.SUPER_ADMIN_USER_ID.equals(userId));
        user = next;
        return this;
    }

    /**
     * 当前用户换成给定对象；null 表示未登录
     */
    public CurrentUserStub user(CurrentUser currentUser) {
        user = currentUser;
        return this;
    }

    /**
     * @return 当前设置的用户，未设置时创建一个
     */
    public CurrentUser current() {
        if (user == null) {
            user = new CurrentUser();
        }
        return user;
    }

    @Override
    public void close() {
        UserContext.setProvider(previous);
    }

}
