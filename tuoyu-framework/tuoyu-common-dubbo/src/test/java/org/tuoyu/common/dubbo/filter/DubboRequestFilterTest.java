package org.tuoyu.common.dubbo.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.exception.user.UserException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Dubbo 请求日志：业务异常与其他异常分开记录")
class DubboRequestFilterTest {

    @Test
    @DisplayName("业务异常（ServiceException、账号类异常）按 WARN 记录，其他异常按 ERROR 记录")
    void shouldClassifyBusinessExceptions() {
        assertTrue(DubboRequestFilter.isBusinessException(new ServiceException("数据不存在")));
        assertTrue(DubboRequestFilter.isBusinessException(new UserException("user.not.exists")));
        assertFalse(DubboRequestFilter.isBusinessException(new IllegalStateException("连接失败")));
        assertFalse(DubboRequestFilter.isBusinessException(new NullPointerException()));
        assertFalse(DubboRequestFilter.isBusinessException(null));
    }
}
