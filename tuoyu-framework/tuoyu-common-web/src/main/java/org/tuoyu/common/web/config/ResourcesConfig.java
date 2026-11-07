package org.tuoyu.common.web.config;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import org.tuoyu.common.core.utils.ObjectUtils;
import org.tuoyu.common.json.enhance.JsonValueEnhancer;
import org.tuoyu.common.web.advice.ApiResultAdvice;
import org.tuoyu.common.web.advice.ResponseEnhancementAdvice;
import org.tuoyu.common.web.handler.GlobalExceptionHandler;
import org.tuoyu.common.web.xss.RichTextJsonFieldProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 通用配置
 *
 * @author Lion Li
 */
@AutoConfiguration
public class ResourcesConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        // 全局日期格式转换配置
        registry.addConverter(String.class, Date.class, source -> {
            DateTime parse = DateUtil.parse(source);
            if (ObjectUtils.isNull(parse)) {
                return null;
            }
            return parse.toJdkDate();
        });
        registry.addConverter(String.class, LocalDateTime.class, source -> {
            DateTime parse = DateUtil.parse(source);
            if (ObjectUtils.isNull(parse)) {
                return null;
            }
            return parse.toLocalDateTime();
        });
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
    }

    /**
     * 全局异常处理器
     */
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * 写出 ApiResult 前填入 traceId、按失败的 code 设置 HTTP 状态
     */
    @Bean
    public ApiResultAdvice apiResultAdvice() {
        return new ApiResultAdvice();
    }

    /**
     * 注册响应增强处理器。
     *
     * @param jsonValueEnhancer JSON 字段增强器
     * @return 响应增强处理器
     */
    @Bean
    public ResponseEnhancementAdvice responseEnhancementAdvice(JsonValueEnhancer jsonValueEnhancer) {
        return new ResponseEnhancementAdvice(jsonValueEnhancer);
    }

    /**
     * 响应输出前清洗富文本字段（{@link org.tuoyu.common.web.xss.RichText}）
     */
    @Bean
    public RichTextJsonFieldProcessor richTextJsonFieldProcessor() {
        return new RichTextJsonFieldProcessor();
    }

}
