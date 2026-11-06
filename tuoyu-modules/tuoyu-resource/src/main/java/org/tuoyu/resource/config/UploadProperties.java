package org.tuoyu.resource.config;

import lombok.Getter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.tuoyu.common.core.upload.UploadFileValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件上传配置（见 docs/adr/0003-上传-XSS-脱敏与字段加密.md）
 *
 * @author tuoyu
 */
@ConfigurationProperties(prefix = "tuoyu.upload")
public class UploadProperties implements InitializingBean {

    /**
     * 允许上传的扩展名（忽略大小写）。只能取有文件头校验规则的类型，
     * HTML、SVG、脚本与可执行文件等不在其中，无法放行
     */
    @Getter
    private List<String> allowedExtensions = new ArrayList<>(UploadFileValidator.DEFAULT_ALLOWED_EXTENSIONS);

    private volatile UploadFileValidator validator;

    /**
     * 设置白名单。Nacos 刷新配置后会重新绑定，校验器随之重建
     */
    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
        this.validator = null;
    }

    /**
     * 启动时校验白名单，配置了不支持的类型时拒绝启动
     */
    @Override
    public void afterPropertiesSet() {
        this.validator = UploadFileValidator.of(allowedExtensions);
    }

    /**
     * 当前白名单对应的校验器
     */
    public UploadFileValidator validator() {
        UploadFileValidator current = validator;
        if (current == null) {
            current = UploadFileValidator.of(allowedExtensions);
            validator = current;
        }
        return current;
    }

}
