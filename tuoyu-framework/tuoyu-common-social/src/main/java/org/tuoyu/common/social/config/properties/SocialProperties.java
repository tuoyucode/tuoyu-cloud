package org.tuoyu.common.social.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Social 配置属性
 *
 * @author thiszhc
 */
@Data
@Component
@ConfigurationProperties(prefix = "justauth")
public class SocialProperties {

    /**
     * 授权类型
     */
    private Map<String, SocialLoginConfigProperties> type;

    /**
     * @param source 平台编码
     * @return 该平台的配置；没有配置或应用 ID、密钥未填写时为 null
     */
    public SocialLoginConfigProperties configured(String source) {
        SocialLoginConfigProperties config = type == null ? null : type.get(source);
        return config != null && config.hasCredentials() ? config : null;
    }

    /**
     * @return 可以使用的平台编码（应用 ID 与密钥都已填写），按配置顺序
     */
    public List<String> configuredSources() {
        if (type == null) {
            return List.of();
        }
        return type.entrySet().stream()
            .filter(entry -> entry.getValue() != null && entry.getValue().hasCredentials())
            .map(Map.Entry::getKey)
            .toList();
    }

}
