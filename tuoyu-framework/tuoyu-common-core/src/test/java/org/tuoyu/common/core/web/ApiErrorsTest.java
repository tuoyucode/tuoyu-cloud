package org.tuoyu.common.core.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("错误响应 ApiErrors：HTTP 状态与 code 的映射、过滤器写出的 JSON")
class ApiErrorsTest {

    @Test
    @DisplayName("按状态码创建：HTTP 状态与 code 相同")
    void byStatus() {
        ResponseEntity<ApiResult<Void>> response = ApiErrors.response(404, "请求地址不存在");
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getCode()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("请求地址不存在");
    }

    @Test
    @DisplayName("业务异常：没有错误码时为 400；错误码在 400 ~ 599 时作为 HTTP 状态；自定义错误码对应 HTTP 400")
    void serviceException() {
        ResponseEntity<ApiResult<Void>> plain = ApiErrors.response(new ServiceException("名称已存在"));
        assertThat(plain.getStatusCode().value()).isEqualTo(400);
        assertThat(plain.getBody().getCode()).isEqualTo(400);
        assertThat(plain.getBody().getMessage()).isEqualTo("名称已存在");

        ResponseEntity<ApiResult<Void>> forbidden = ApiErrors.response(new ServiceException("租户不允许", 403));
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(forbidden.getBody().getCode()).isEqualTo(403);

        ResponseEntity<ApiResult<Void>> custom = ApiErrors.response(new ServiceException("库存不足", 10001));
        assertThat(custom.getStatusCode().value()).isEqualTo(400);
        assertThat(custom.getBody().getCode()).isEqualTo(10001);

        // 错误码为 0（与成功相同）时按 400 处理
        assertThat(ApiErrors.of(new ServiceException("x", 0)).getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("过滤器直接写出的 JSON：字段与 ApiResult 相同，转义引号、反斜杠与控制字符")
    void toJson() {
        assertThat(ApiErrors.toJson(413, "请求体超过 10MB 上限", "abc"))
            .isEqualTo("{\"code\":413,\"message\":\"请求体超过 10MB 上限\",\"data\":null,\"traceId\":\"abc\"}");
        assertThat(ApiErrors.toJson(401, null, null))
            .isEqualTo("{\"code\":401,\"message\":null,\"data\":null,\"traceId\":null}");
        assertThat(ApiErrors.toJson(400, "a\"b\\c\nd", null))
            .contains("\"message\":\"a\\\"b\\\\c\\u000ad\"");
    }

}
