package org.tuoyu.system.dubbo;

import cn.hutool.core.lang.Dict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.system.api.RemoteConfigService;
import org.tuoyu.system.service.ISysConfigService;
import tools.jackson.core.JacksonException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 参数配置服务的跨模块实现。参数值经参数服务的缓存读取；按 JSON 解析时，空值与无法解析的值得到空结果，
 * 解析失败只记录参数键名（参数值可能含敏感内容）
 *
 * @author tuoyu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RemoteConfigServiceImpl implements RemoteConfigService {

    private final ISysConfigService configService;

    @Override
    public boolean selectRegisterEnabled() {
        return configService.selectRegisterEnabled();
    }

    @Override
    public String getConfigValue(String configKey) {
        return configService.selectConfigByKey(configKey);
    }

    @Override
    public Dict getConfigMap(String configKey) {
        return parse(configKey, JsonUtils::parseMap, Dict::new);
    }

    @Override
    public List<Dict> getConfigArrayMap(String configKey) {
        return parse(configKey, JsonUtils::parseArrayMap, ArrayList::new);
    }

    @Override
    public <T> T getConfigObject(String configKey, Class<T> clazz) {
        return parse(configKey, value -> JsonUtils.parseObject(value, clazz), () -> null);
    }

    @Override
    public <T> List<T> getConfigArray(String configKey, Class<T> clazz) {
        return parse(configKey, value -> JsonUtils.parseArray(value, clazz), ArrayList::new);
    }

    /**
     * 读取参数值并解析；值为空白、解析失败或解析结果为 null 时返回空结果
     *
     * @param configKey 参数键名
     * @param parser    解析方法
     * @param empty     空结果
     * @return 解析结果
     */
    private <R> R parse(String configKey, Function<String, R> parser, Supplier<R> empty) {
        String value = configService.selectConfigByKey(configKey);
        if (StringUtils.isBlank(value)) {
            return empty.get();
        }
        try {
            R result = parser.apply(value);
            return result == null ? empty.get() : result;
        } catch (JacksonException e) {
            log.warn("参数 {} 的值不能按 JSON 解析，按空结果处理", configKey);
            return empty.get();
        }
    }

}
