package org.tuoyu.common.core.context;

import java.util.Optional;

/**
 * 当前用户的来源。由认证模块实现（例如 tuoyu-common-satoken 基于登录会话的实现），框架通过它得到当前用户，不直接依赖认证框架。
 * <p>
 * 业务代码不直接使用本接口，用 {@link UserContext} 的静态方法。框架模块（数据权限、审计字段填充、多租户等）通过容器注入它。
 *
 * @author tuoyu
 */
@FunctionalInterface
public interface UserContextProvider {

    /**
     * @return 当前用户；没有用户（未登录、后台任务、系统内部调用）时为空。实现不能抛出“未登录”之类的异常
     */
    Optional<CurrentUser> currentUser();

}
