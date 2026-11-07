package org.tuoyu.common.web.handler;

import jakarta.servlet.Filter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.web.ApiErrors;
import org.tuoyu.common.core.web.ExceptionHandlerOrder;
import org.tuoyu.common.core.web.RequestSizeLimitFilter;
import org.tuoyu.common.observability.trace.TraceContext;
import org.tuoyu.common.observability.trace.TraceParent;
import org.tuoyu.common.web.config.ErrorPageConfiguration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("错误响应：分块传输超限 413、异常处理类的顺序、错误页面")
class ErrorResponseIntegrationTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    @RestController
    static class DemoController {

        @PostMapping("/demo/body")
        public ApiResult<Integer> body(@RequestBody Map<String, Object> body) {
            return ApiResult.ok(body.size());
        }

        @GetMapping("/demo/module")
        public ApiResult<Void> module() {
            throw new ModuleException("模块异常");
        }

    }

    static class ModuleException extends RuntimeException {

        ModuleException(String message) {
            super(message);
        }

    }

    /**
     * 模块专用的异常处理类：能处理的异常由它返回，不被全局异常处理的兜底方法抢先
     */
    @Order(ExceptionHandlerOrder.MODULE)
    @RestControllerAdvice
    static class ModuleExceptionHandler {

        @ExceptionHandler(ModuleException.class)
        public ResponseEntity<ApiResult<Void>> handle(ModuleException e) {
            return ApiErrors.response(409, e.getMessage());
        }

    }

    /**
     * 把请求改成长度未知（分块传输）
     */
    private static final Filter CHUNKED = (request, response, chain) -> chain.doFilter(
        new HttpServletRequestWrapper((HttpServletRequest) request) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        }, response);

    private static MockMvc mvc() {
        // 全局异常处理放在前面登记：生效顺序由 @Order 决定，与登记顺序无关
        return MockMvcBuilders.standaloneSetup(new DemoController())
            .setControllerAdvice(new GlobalExceptionHandler(), new ModuleExceptionHandler())
            .addFilters(CHUNKED, new RequestSizeLimitFilter(64, 1024))
            .build();
    }

    @Test
    @DisplayName("长度未知的 JSON 请求体在读取中超过上限：413，不是 400")
    void chunkedJsonTooLarge() throws Exception {
        String large = "{\"text\":\"" + "x".repeat(200) + "\"}";
        mvc().perform(post("/demo/body").contentType(MediaType.APPLICATION_JSON).content(large))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value(413))
            .andExpect(jsonPath("$.message").value("请求体超过上限"));

        mvc().perform(post("/demo/body").contentType(MediaType.APPLICATION_JSON).content("{\"a\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(1));
        mvc().perform(post("/demo/body").contentType(MediaType.APPLICATION_JSON).content("{bad"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("模块专用的异常处理类优先于全局异常处理")
    void moduleHandlerFirst() throws Exception {
        mvc().perform(get("/demo/module"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("模块异常"));
        assertThat(GlobalExceptionHandler.class.getAnnotation(Order.class).value()).isEqualTo(ExceptionHandlerOrder.GLOBAL);
        assertThat(ExceptionHandlerOrder.MODULE).isLessThan(ExceptionHandlerOrder.GLOBAL);
    }

    @Test
    @DisplayName("错误页面：过滤器链中逃出的异常只返回错误编号与 traceId；4xx 按状态给出固定提示；直接访问为 404")
    void errorController() {
        ApiErrorController controller = new ApiErrorController();
        try (TraceContext.Scope ignored = TraceContext.enter(TraceParent.parse("00-" + TRACE_ID + "-00f067aa0ba902b7-01"))) {
            MockHttpServletRequest failed = new MockHttpServletRequest("GET", "/error");
            failed.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
            failed.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/system/user");
            failed.setAttribute(RequestDispatcher.ERROR_EXCEPTION,
                new IllegalStateException("Connection refused: redis://10.0.0.8:6379"));
            ResponseEntity<ApiResult<Void>> response = controller.error(failed);
            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(response.getBody().getCode()).isEqualTo(500);
            assertThat(response.getBody().getMessage()).contains("错误编号").doesNotContain("10.0.0.8", "redis");
            assertThat(response.getBody().getTraceId()).isEqualTo(TRACE_ID);

            MockHttpServletRequest forbidden = new MockHttpServletRequest("GET", "/error");
            forbidden.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 403);
            forbidden.setAttribute(RequestDispatcher.ERROR_MESSAGE, "internal reason");
            ResponseEntity<ApiResult<Void>> denied = controller.error(forbidden);
            assertThat(denied.getStatusCode().value()).isEqualTo(403);
            assertThat(denied.getBody().getCode()).isEqualTo(403);
            assertThat(denied.getBody().getMessage()).isEqualTo("没有访问权限");

            MockHttpServletRequest unknownStatus = new MockHttpServletRequest("GET", "/error");
            unknownStatus.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 302);
            assertThat(controller.error(unknownStatus).getStatusCode().value()).isEqualTo(500);

            ResponseEntity<ApiResult<Void>> direct = controller.error(new MockHttpServletRequest("GET", "/error"));
            assertThat(direct.getStatusCode().value()).isEqualTo(404);
            assertThat(direct.getBody().getTraceId()).isEqualTo(TRACE_ID);
        }
    }

    @Test
    @DisplayName("自动配置注册 ApiErrorController，Spring Boot 默认的错误控制器不会创建")
    void replacesBasicErrorController() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ErrorPageConfiguration.class, ErrorMvcAutoConfiguration.class,
                DispatcherServletAutoConfiguration.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ErrorController.class);
                assertThat(context).hasSingleBean(ApiErrorController.class);
                assertThat(context).doesNotHaveBean(BasicErrorController.class);
            });
    }

}
