package org.tuoyu.common.core.context;

import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;

import java.util.Optional;

/**
 * 当前用户。业务代码获取当前用户的唯一入口。
 * <p>
 * 用户来自容器中的 {@link UserContextProvider}（应用启动时由 {@code UserContextConfiguration} 设置）；没有提供者时按“没有用户”处理。
 * 没有用户（未登录、后台任务、系统内部调用）时 {@link #current()} 为空，调用方按“系统身份”处理，例如审计字段填系统用户；
 * 必须登录的地方用 {@link #require()}。
 * <p>
 * 测试中用 {@link #setProvider(UserContextProvider)} 替换提供者，结束时用它的返回值恢复。
 *
 * @author tuoyu
 */
public final class UserContext {

    /**
     * 未登录时 {@link #require()} 抛出的异常提示
     */
    public static final String NOT_LOGIN_MESSAGE = "请先登录";

    private static volatile UserContextProvider provider;

    private UserContext() {
    }

    /**
     * @return 当前用户；没有用户时为空
     */
    public static Optional<CurrentUser> current() {
        UserContextProvider current = provider;
        if (current == null) {
            return Optional.empty();
        }
        Optional<CurrentUser> user = current.currentUser();
        return user == null ? Optional.empty() : user;
    }

    /**
     * @return 当前用户
     * @throws ServiceException 没有用户时抛出，错误码 401（未登录）
     */
    public static CurrentUser require() {
        return current().orElseThrow(() -> new ServiceException(NOT_LOGIN_MESSAGE, HttpStatus.UNAUTHORIZED));
    }

    /**
     * @return 当前用户 ID；没有用户时为 null
     */
    public static Long userId() {
        return current().map(CurrentUser::getUserId).orElse(null);
    }

    /**
     * 当前用户 ID 的字符串形式（工作流等以字符串保存办理人的地方使用）
     *
     * @return 当前用户 ID；没有用户时为 null
     */
    public static String userIdString() {
        Long userId = userId();
        return userId == null ? null : userId.toString();
    }

    /**
     * @return 是否有当前用户（已登录）
     */
    public static boolean isLoggedIn() {
        return current().isPresent();
    }

    /**
     * @return 当前用户是否超级管理员；没有用户时为 false
     */
    public static boolean isSuperAdmin() {
        return current().map(CurrentUser::isSuperAdmin).orElse(false);
    }

    /**
     * @param userId 用户 ID
     * @return 该用户是否超级管理员（按用户 ID 判断，与是否登录无关）
     */
    public static boolean isSuperAdmin(Long userId) {
        return SystemConstants.SUPER_ADMIN_USER_ID.equals(userId);
    }

    /**
     * 设置当前用户的提供者
     *
     * @param newProvider 新的提供者，为 null 表示没有提供者
     * @return 替换前的提供者，用于恢复
     */
    public static UserContextProvider setProvider(UserContextProvider newProvider) {
        synchronized (UserContext.class) {
            UserContextProvider previous = provider;
            provider = newProvider;
            return previous;
        }
    }

    /**
     * 只有当前提供者仍是 {@code expected} 时才替换（容器关闭时使用，不覆盖其他容器设置的提供者）
     *
     * @param expected    预期的当前提供者
     * @param newProvider 新的提供者
     * @return 是否已替换
     */
    public static boolean replaceProvider(UserContextProvider expected, UserContextProvider newProvider) {
        synchronized (UserContext.class) {
            if (provider != expected) {
                return false;
            }
            provider = newProvider;
            return true;
        }
    }

    /**
     * @return 当前的提供者；没有时为 null
     */
    public static UserContextProvider getProvider() {
        return provider;
    }

}
