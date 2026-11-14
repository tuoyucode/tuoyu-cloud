package org.tuoyu.common.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.exception.base.BaseException;
import org.tuoyu.common.core.exception.user.CaptchaException;
import org.tuoyu.common.core.exception.user.CaptchaExpireException;
import org.tuoyu.common.core.exception.user.UserException;

import java.io.IOException;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 只测不需要解析国际化消息的部分；按消息键解析的 {@code UserException.getMessage()}
 * 在 {@code utils.CoreFrameworkUtilityContractTest} 中测试（需要 Spring 容器中的 MessageSource）。
 */
@DisplayName("框架异常的构造与取值")
class ExceptionContractTest {

    @Test
    @DisplayName("ServiceException：只有消息时错误码为 null")
    void messageOnly() {
        ServiceException e = new ServiceException("库存不足");
        assertThat(e.getMessage()).isEqualTo("库存不足");
        assertThat(e.getCode()).isNull();
        assertThat(e.getCause()).isNull();
        assertThat(e).isInstanceOf(RuntimeException.class).isInstanceOf(Serializable.class);
        assertThat(ServiceException.class).isFinal();
    }

    @Test
    @DisplayName("ServiceException：int 实参选中 (String, Integer) 构造器，作为错误码")
    void withCode() {
        assertThat(new ServiceException("数据冲突", 409).getCode()).isEqualTo(409);
        int status = HttpStatus.NOT_FOUND;
        ServiceException e = new ServiceException("单据不存在", status);
        assertThat(e.getCode()).isEqualTo(404);
        assertThat(e.getMessage()).isEqualTo("单据不存在");
        Integer boxed = 503;
        assertThat(new ServiceException("服务暂不可用", boxed).getCode()).isEqualTo(503);
    }

    @Test
    @DisplayName("ServiceException：其他实参按 {} 占位符格式化，错误码为 null")
    void formatted() {
        ServiceException e = new ServiceException("用户 {} 的第 {} 条记录不存在", "bob", 5L);
        assertThat(e.getMessage()).isEqualTo("用户 bob 的第 5 条记录不存在");
        assertThat(e.getCode()).isNull();
        assertThat(new ServiceException("id={}", 5L).getMessage()).isEqualTo("id=5");
        assertThat(new ServiceException("参数名称 {}", "name").getMessage()).isEqualTo("参数名称 name");
        assertThat(new ServiceException("a{}b{}", "1").getMessage()).isEqualTo("a1b{}");
        assertThat(new ServiceException("没有占位符", "x").getMessage()).isEqualTo("没有占位符");
    }

    @Test
    @DisplayName("ServiceException：带原因时消息取参数而不是原因的消息")
    void withCause() {
        IOException cause = new IOException("disk /data/secret");
        ServiceException e = new ServiceException("读取上传文件失败", cause);
        assertThat(e.getMessage()).isEqualTo("读取上传文件失败");
        assertThat(e.getCause()).isSameAs(cause);
        assertThat(e.getCode()).isNull();
    }

    @Test
    @DisplayName("ServiceException：无参构造器（反序列化使用）")
    void noArgs() {
        ServiceException e = new ServiceException();
        assertThat(e.getMessage()).isNull();
        assertThat(e.getCode()).isNull();
    }

    @Test
    @DisplayName("SseException：消息原样保存")
    void sse() {
        SseException e = new SseException("expired");
        assertThat(e.getMessage()).isEqualTo("expired");
        assertThat(e).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("BaseException：没有消息键时返回默认消息")
    void baseDefaultMessage() {
        BaseException e = new BaseException("user", "账号或密码错误");
        assertThat(e.getMessage()).isEqualTo("账号或密码错误");
        assertThat(new BaseException().getMessage()).isNull();
        assertThat(e).isInstanceOf(RuntimeException.class).isInstanceOf(Serializable.class);
    }

    @Test
    @DisplayName("账号、验证码类异常的继承关系（全局异常处理按 BaseException 返回 400，Dubbo 过滤器按它判断业务异常）")
    void hierarchy() {
        assertThat(BaseException.class).isAssignableFrom(UserException.class);
        assertThat(UserException.class).isAssignableFrom(CaptchaException.class);
        assertThat(UserException.class).isAssignableFrom(CaptchaExpireException.class);
        assertThat(RuntimeException.class).isAssignableFrom(BaseException.class);
        // 构造时不解析消息，不需要 Spring 容器
        Object captcha = new CaptchaException();
        Object expired = new CaptchaExpireException();
        Object user = new UserException("user.not.exists", "bob");
        assertThat(captcha).isInstanceOf(BaseException.class);
        assertThat(expired).isInstanceOf(BaseException.class);
        assertThat(user).isInstanceOf(BaseException.class);
    }
}
