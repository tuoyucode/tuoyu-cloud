package org.tuoyu.common.translation.core.impl;

import cn.hutool.core.convert.Convert;
import lombok.AllArgsConstructor;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.translation.annotation.TranslationType;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.common.translation.core.TranslationInterface;
import org.tuoyu.common.rpc.fallback.RemoteFallback;
import org.tuoyu.resource.api.RemoteFileService;
import org.tuoyu.resource.api.domain.RemoteFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OSS翻译实现。文件服务不可用时翻译为空，不影响主流程（统一降级 RemoteFallback，单体与微服务行为一致，ADR 0007）
 *
 * @author Lion Li
 */
@AllArgsConstructor
@TranslationType(type = TransConstant.OSS_ID_TO_URL)
public class OssUrlTranslationImpl implements TranslationInterface<String> {

    /**
     * 降级调用的名称（日志与计数）
     */
    static final String FALLBACK_NAME = "resource.file-url";

    private final RemoteFileService remoteFileService;

    /**
     * 将 OSS ID 或 ID 集合翻译为访问地址。
     *
     * @param key   OSS ID 或逗号分隔的 ID 字符串
     * @param other 额外参数
     * @return 访问地址
     */
    @Override
    public String translation(Object key, String other) {
        return RemoteFallback.call(FALLBACK_NAME, () -> remoteFileService.selectUrlByIds(key.toString()), StringUtils.EMPTY);
    }

    /**
     * 批量将 OSS ID 翻译为访问地址。
     *
     * @param keys  OSS ID 集合
     * @param other 额外参数
     * @return OSS ID 与访问地址映射
     */
    @Override
    public Map<Object, String> translationBatch(Set<Object> keys, String other) {
        Set<Long> ossIds = collectLongIds(keys);
        if (ossIds.isEmpty()) {
            return Map.of();
        }
        String idText = ossIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<RemoteFile> files = RemoteFallback.call(FALLBACK_NAME, () -> remoteFileService.selectByIds(idText), List.of());
        Map<Long, String> ossUrls = new LinkedHashMap<>(StreamUtils.toMap(files, RemoteFile::getOssId, RemoteFile::getUrl));
        Map<Object, String> result = new LinkedHashMap<>(keys.size());
        for (Object key : keys) {
            result.put(key, buildValue(key, ossUrls));
        }
        return result;
    }

    /**
     * 根据原始键构建 OSS 地址翻译值。
     *
     * @param source  原始键
     * @param ossUrls OSS ID 与访问地址映射
     * @return OSS 访问地址
     */
    private String buildValue(Object source, Map<Long, String> ossUrls) {
        if (source instanceof String ids) {
            return joinMappedValues(ids, ossUrls::get);
        }
        return source == null ? null : ossUrls.get(Convert.toLong(source));
    }

}
