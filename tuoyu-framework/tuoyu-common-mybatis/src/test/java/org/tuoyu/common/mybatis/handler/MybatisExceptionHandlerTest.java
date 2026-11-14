package org.tuoyu.common.mybatis.handler;

import com.baomidou.dynamic.datasource.exception.CannotFindDataSourceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.web.ExceptionHandlerOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据访问异常的响应：不把 SQL、表名、数据库报错返回客户端
 */
@DisplayName("MybatisExceptionHandler：数据访问异常的响应")
class MybatisExceptionHandlerTest {

    private final MybatisExceptionHandler handler = new MybatisExceptionHandler();

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/user/list");

    @Test
    @DisplayName("是模块级的异常处理，排在全局异常处理之前")
    void moduleAdvice() {
        assertThat(AnnotationUtils.findAnnotation(MybatisExceptionHandler.class, RestControllerAdvice.class)).isNotNull();
        Order order = AnnotationUtils.findAnnotation(MybatisExceptionHandler.class, Order.class);
        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(ExceptionHandlerOrder.MODULE);
    }

    @Test
    @DisplayName("主键或唯一索引冲突：409，提示中不带数据库报错")
    void duplicateKey() {
        ResponseEntity<ApiResult<Void>> response = handler.handleDuplicateKeyException(
            new DuplicateKeyException("Duplicate entry 'admin' for key 'sys_user.uk_user_name'"), request);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getCode()).isEqualTo(409);
        assertThat(response.getBody().getMessage()).isEqualTo("数据库中已存在该记录，请联系管理员确认").doesNotContain("admin");
    }

    @Test
    @DisplayName("根因是业务异常时按业务异常返回（状态与提示）")
    void serviceExceptionRootCause() {
        MyBatisSystemException e = new MyBatisSystemException("wrapped",
            new IllegalStateException("x", new ServiceException("缺少租户上下文", 400)));
        ResponseEntity<ApiResult<Void>> response = handler.handleMyBatisSystemException(e, request);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("缺少租户上下文");

        ResponseEntity<ApiResult<Void>> unauthorized = handler.handleMyBatisSystemException(
            new MyBatisSystemException("wrapped", new ServiceException("认证失败，无法访问系统资源", 401)), request);
        assertThat(unauthorized.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("找不到数据源：500，固定提示")
    void cannotFindDataSource() {
        ResponseEntity<ApiResult<Void>> response = handler.handleMyBatisSystemException(
            new MyBatisSystemException("wrapped", new CannotFindDataSourceException("dynamic-datasource can not find primary datasource")),
            request);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).isEqualTo("未找到数据源，请联系管理员确认");
    }

    @Test
    @DisplayName("其他数据访问异常：500，只返回错误编号，不带 SQL 与数据库报错")
    void otherFailures() {
        ResponseEntity<ApiResult<Void>> response = handler.handleMyBatisSystemException(
            new MyBatisSystemException("### SQL: select password from sys_user", new IllegalStateException("Table 'sys_user' doesn't exist")),
            request);
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getMessage())
            .matches("数据访问异常，请联系管理员 \\[错误编号: \\d{8}]")
            .doesNotContain("sys_user").doesNotContain("password");
    }

}
