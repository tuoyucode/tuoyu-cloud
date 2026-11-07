package org.tuoyu.common.sms;

import cn.hutool.http.HttpStatus;
import org.tuoyu.common.core.domain.ApiResult;
import org.springframework.http.ResponseEntity;
import org.tuoyu.common.sms.handler.SmsExceptionHandler;
import org.dromara.sms4j.comm.exception.SmsBlendException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@DisplayName("common-sms 功能单元测试")
class SmsExceptionHandlerTest {

    /**
     * 验证短信服务异常会被转换为稳定的 HTTP 500 业务响应，避免向调用方泄露供应商细节。
     */
    @Test
    @DisplayName("转换短信服务异常")
    void shouldConvertSmsExceptionToFailureResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sms/send");

        ResponseEntity<ApiResult<Void>> response = new SmsExceptionHandler().handleSmsBlendException(mock(SmsBlendException.class), request);

        assertEquals(HttpStatus.HTTP_INTERNAL_ERROR, response.getStatusCode().value());
        assertEquals(HttpStatus.HTTP_INTERNAL_ERROR, response.getBody().getCode());
        assertEquals("短信发送失败，请稍后再试...", response.getBody().getMessage());
    }
}
