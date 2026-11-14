package org.tuoyu.auth.login;

import org.springframework.stereotype.Component;
import org.tuoyu.common.core.exception.ServiceException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按授权类型查找登录策略。两个策略声明了同一个授权类型时启动失败，避免请求被分派到意料之外的实现
 *
 * @author tuoyu
 */
@Component
public class LoginStrategies {

    private final Map<String, LoginStrategy> strategies;

    public LoginStrategies(List<LoginStrategy> strategies) {
        Map<String, LoginStrategy> index = new HashMap<>();
        for (LoginStrategy strategy : strategies) {
            LoginStrategy previous = index.putIfAbsent(strategy.grantType(), strategy);
            if (previous != null) {
                throw new IllegalStateException("授权类型 " + strategy.grantType() + " 有多个登录策略："
                    + previous.getClass().getName() + "、" + strategy.getClass().getName());
            }
        }
        this.strategies = Map.copyOf(index);
    }

    /**
     * @param grantType 授权类型
     * @return 对应的登录策略
     * @throws ServiceException 没有对应的登录策略时
     */
    public LoginStrategy find(String grantType) {
        LoginStrategy strategy = grantType == null ? null : strategies.get(grantType);
        if (strategy == null) {
            throw new ServiceException("授权类型不正确!");
        }
        return strategy;
    }

}
