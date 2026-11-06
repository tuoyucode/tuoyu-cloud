package org.tuoyu.common.doc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.utils.ServletUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.doc.config.properties.SpringDocProperties;
import org.tuoyu.common.doc.core.customizer.ClassTagOperationCustomizer;
import org.tuoyu.common.doc.core.customizer.JavadocOperationCustomizer;
import org.tuoyu.common.doc.core.resolver.JavadocResolver;
import org.tuoyu.common.doc.core.resolver.SaTokenAnnotationMetadataJavadocResolver;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.providers.JavadocProvider;
import org.springdoc.core.utils.PropertyResolverUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 接口文档配置
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@AutoConfiguration(before = SpringDocConfiguration.class)
@EnableConfigurationProperties(SpringDocProperties.class)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class SpringDocAutoConfiguration {

    private final ServerProperties serverProperties;

    /**
     * 构建基础 OpenAPI 文档对象。
     *
     * @param properties SpringDoc 配置
     * @return OpenAPI 对象
     */
    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI openApi(SpringDocProperties properties) {
        OpenAPI openApi = new OpenAPI();
        // 文档基本信息
        SpringDocProperties.InfoProperties infoProperties = properties.getInfo();
        Info info = convertInfo(infoProperties);
        openApi.info(info);
        // 扩展文档信息
        openApi.externalDocs(properties.getExternalDocs());
        openApi.tags(properties.getTags());
        openApi.paths(properties.getPaths());
        if (properties.getComponents() != null) {
            openApi.components(properties.getComponents());
            Set<String> keySet = properties.getComponents().getSecuritySchemes().keySet();
            List<SecurityRequirement> list = new ArrayList<>();
            SecurityRequirement securityRequirement = new SecurityRequirement();
            keySet.forEach(securityRequirement::addList);
            list.add(securityRequirement);
            openApi.security(list);
        }
        return openApi;
    }

    /**
     * 将自定义文档信息配置转换为 OpenAPI Info。
     *
     * @param infoProperties 文档信息配置
     * @return Info 对象
     */
    private Info convertInfo(SpringDocProperties.InfoProperties infoProperties) {
        Info info = new Info();
        info.setTitle(infoProperties.getTitle());
        info.setDescription(infoProperties.getDescription());
        info.setContact(infoProperties.getContact());
        info.setLicense(infoProperties.getLicense());
        info.setVersion(infoProperties.getVersion());
        return info;
    }

    /**
     * Controller 类级标签增强
     */
    @Bean
    public ClassTagOperationCustomizer classTagOperationCustomizer(Optional<JavadocProvider> javadocProvider,
                                                                   PropertyResolverUtils propertyResolverUtils) {
        return new ClassTagOperationCustomizer(javadocProvider, propertyResolverUtils);
    }

    /**
     * 方法 JavaDoc 与权限描述增强
     */
    @Bean
    public JavadocOperationCustomizer javadocOperationCustomizer(Optional<JavadocProvider> javadocProvider,
                                                                 List<JavadocResolver> javadocResolvers) {
        return new JavadocOperationCustomizer(javadocProvider, javadocResolvers);
    }

    /**
     * 对已经生成好的 OpenApi 进行自定义操作
     */
    @Bean
    public GlobalOpenApiCustomizer openApiCustomizer() {
        String contextPath = serverProperties.getServlet().getContextPath();
        String finalContextPath = StringUtils.isBlank(contextPath) || "/".equals(contextPath) ? "" : contextPath;
        // 对所有路径增加前置上下文路径
        return openApi -> {
            HttpServletRequest request = ServletUtils.getRequest();
            // 从请求头获取gateway转发的服务前缀
            String prefix = request == null ? "" : StringUtils.blankToDefault(request.getHeader("X-Forwarded-Prefix"), "");
            Paths oldPaths = openApi.getPaths();
            if (oldPaths instanceof PrefixedPaths) {
                return;
            }
            PrefixedPaths newPaths = new PrefixedPaths();
            String pathPrefix = StringUtils.isNotBlank(prefix) ? prefix : finalContextPath;
            oldPaths.forEach((k, v) -> newPaths.addPathItem(pathPrefix + k, v));
            openApi.setPaths(newPaths);
        };
    }

    /**
     * 注册SaToken JavaDoc权限注解解析器
     */
    @Bean
    public JavadocResolver saTokenAnnotationJavadocResolver() {
        return new SaTokenAnnotationMetadataJavadocResolver();
    }

}
