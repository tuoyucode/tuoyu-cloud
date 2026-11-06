package org.tuoyu.common.oss.factory;

import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.common.oss.client.DefaultOssClientImpl;
import org.tuoyu.common.oss.client.OssClient;
import org.tuoyu.common.oss.config.OssClientConfig;
import org.tuoyu.common.oss.constant.OssConstant;
import org.tuoyu.common.oss.properties.OssProperties;
import org.tuoyu.common.redis.utils.CacheUtils;
import org.tuoyu.common.redis.utils.RedisUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * S3存储客户端工厂
 *
 * @author 秋辞未寒
 */
@Slf4j
public class OssFactory {

    private static final Map<String, OssClient> CLIENT_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ReentrantLock> CLIENT_LOCKS = new ConcurrentHashMap<>();

    /**
     * 获取默认实例
     *
     * @throws ServiceException 没有设置默认的存储配置（提示给用户，不是系统异常）
     */
    public static OssClient instance() {
        // 获取redis 默认类型
        String configKey = RedisUtils.getCacheObject(OssConstant.DEFAULT_CONFIG_KEY);
        if (StringUtils.isEmpty(configKey)) {
            throw new ServiceException("未设置默认的文件存储，请在“存储配置”中启用一项配置");
        }
        return instance(configKey);
    }

    /**
     * 根据类型获取实例
     *
     * @throws ServiceException 存储配置不存在（例如已被删除）
     */
    public static OssClient instance(String configKey) {
        if (StringUtils.isBlank(configKey)) {
            throw new ServiceException("未设置默认的文件存储，请在“存储配置”中启用一项配置");
        }
        String json = CacheUtils.get(CacheNames.SYS_OSS_CONFIG, configKey);
        if (json == null) {
            throw new ServiceException("文件存储配置“" + configKey + "”不存在，请在“存储配置”中检查");
        }
        OssProperties properties = JsonUtils.parseObject(json, OssProperties.class);
        OssClientConfig config = OssClientConfig.formProperties(properties);
        ReentrantLock lock = getClientLock(configKey);
        lock.lock();
        try {
            OssClient client = CLIENT_CACHE.get(configKey);
            if (client != null) {
                if (client.verifyConfig(config)) {
                    return client;
                }
                closeClient(configKey, client);
            }
            OssClient newClient = new DefaultOssClientImpl(configKey, config);
            CLIENT_CACHE.put(configKey, newClient);
            return newClient;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 移除实例
     */
    public static boolean remove(String configKey) {
        if (StringUtils.isBlank(configKey)) {
            return false;
        }
        ReentrantLock lock = getClientLock(configKey);
        lock.lock();
        try {
            OssClient client = CLIENT_CACHE.remove(configKey);
            if (client == null) {
                return false;
            }
            closeClient(configKey, client);
            return true;
        } finally {
            lock.unlock();
        }
    }

    private static ReentrantLock getClientLock(String configKey) {
        return CLIENT_LOCKS.computeIfAbsent(configKey, key -> new ReentrantLock());
    }

    private static void closeClient(String configKey, OssClient client) {
        try {
            client.close();
        } catch (Exception e) {
            log.warn("S3存储客户端 [{}] 关闭异常，错误信息: {}", configKey, e.getMessage(), e);
        }
    }

}
