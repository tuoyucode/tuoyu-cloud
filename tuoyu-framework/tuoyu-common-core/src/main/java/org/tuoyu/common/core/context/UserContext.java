package org.tuoyu.common.core.context;

import java.util.Optional;

/**
 * 当前用户上下文。由认证模块实现，框架模块通过它获取当前用户，不直接依赖认证框架。
 * <p>
 * 没有用户（未登录、后台任务、系统内部调用）时返回空，调用方按“系统身份”处理，例如审计字段填系统用户。
 *
 * @author tuoyu
 */
public interface UserContext {

    /**
     * @return 当前用户；没有用户时为空。实现不能抛出“未登录”之类的异常
     */
    Optional<CurrentUser> currentUser();

}
