package org.tuoyu.common.satoken.support;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.github.benmanes.caffeine.cache.Cache;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.tuoyu.common.redis.utils.RedisUtils;
import org.tuoyu.common.satoken.core.dao.RedisSaTokenDao;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sa-Token 测试环境：与生产相同的 jwt-simple 模式与 {@link RedisSaTokenDao}，Redis 由内存中的假实现代替
 * （{@link RedisUtils} 的静态方法被替换为读写一个 Map），请求为 Sa-Token 的模拟上下文。JWT 密钥每次运行随机生成。
 * <p>
 * 用法：{@code try (FakeRedis redis = SaTokenTestEnv.enter()) { ... }}，结束时恢复。
 */
public final class SaTokenTestEnv {

    private static boolean initialized;

    private SaTokenTestEnv() {
    }

    /**
     * RedisUtils 在类加载时取 RedissonClient，先放一个不会被调用的模拟对象
     */
    private static synchronized void init() {
        if (initialized) {
            return;
        }
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(RedissonClient.class, () -> Mockito.mock(RedissonClient.class));
        context.refresh();
        new SpringUtil().setApplicationContext(context);
        SaManager.getConfig().setJwtSecretKey(UUID.randomUUID().toString());
        initialized = true;
    }

    public static FakeRedis enter() {
        init();
        // RedisSaTokenDao 的本地缓存是静态的，每个测试开始时清空，避免读到上一个测试的值
        ((Cache<?, ?>) ReflectionTestUtils.getField(RedisSaTokenDao.class, "CAFFEINE")).invalidateAll();
        FakeRedis redis = new FakeRedis();
        SaManager.setSaTokenDao(new RedisSaTokenDao());
        StpUtil.setStpLogic(new StpLogicJwtForSimple());
        SaTokenContextMockUtil.setMockContext();
        return redis;
    }

    /**
     * 内存中的 Redis：记录值与剩余毫秒数
     */
    public static final class FakeRedis implements AutoCloseable {

        private final Map<String, Object> values = new HashMap<>();

        private final Map<String, Long> ttl = new HashMap<>();

        private final MockedStatic<RedisUtils> mock;

        private FakeRedis() {
            mock = Mockito.mockStatic(RedisUtils.class, invocation -> {
                Object[] args = invocation.getArguments();
                return switch (invocation.getMethod().getName()) {
                    case "getCacheObject" -> values.get((String) args[0]);
                    case "setCacheObject" -> {
                        String key = (String) args[0];
                        values.put(key, args[1]);
                        if (args.length == 3 && args[2] instanceof Duration duration) {
                            ttl.put(key, duration.toMillis());
                        } else if (args.length == 2 || Boolean.FALSE.equals(args[2])) {
                            ttl.put(key, -1L);
                        }
                        yield null;
                    }
                    case "hasKey" -> values.containsKey((String) args[0]);
                    case "deleteObject" -> {
                        if (args[0] instanceof Collection<?> keys) {
                            keys.forEach(k -> remove((String) k));
                            yield null;
                        }
                        yield remove((String) args[0]);
                    }
                    case "getTimeToLive" -> values.containsKey((String) args[0]) ? ttl.getOrDefault((String) args[0], -1L) : -2L;
                    case "expire" -> {
                        String key = (String) args[0];
                        long millis = args[1] instanceof Duration duration ? duration.toMillis() : (Long) args[1] * 1000;
                        ttl.put(key, millis);
                        yield values.containsKey(key);
                    }
                    case "keys" -> {
                        String regex = ((String) args[0]).replace("*", ".*");
                        yield values.keySet().stream().filter(k -> k.matches(regex)).sorted().toList();
                    }
                    default -> throw new UnsupportedOperationException("FakeRedis 未实现 " + invocation.getMethod());
                };
            });
        }

        private boolean remove(String key) {
            ttl.remove(key);
            return values.remove(key) != null;
        }

        public Map<String, Object> values() {
            return values;
        }

        public Long ttlMillis(String key) {
            return ttl.get(key);
        }

        public List<String> keys() {
            return values.keySet().stream().sorted().toList();
        }

        @Override
        public void close() {
            SaTokenContextMockUtil.clearContext();
            mock.close();
        }

    }

}
