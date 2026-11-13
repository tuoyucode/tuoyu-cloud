package org.tuoyu.common.satoken.dao;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.satoken.listener.OnlineTokenCleanupListener;
import org.tuoyu.common.satoken.support.SaTokenTestEnv;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.common.satoken.utils.LoginSessions;
import org.tuoyu.system.api.model.LoginUser;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Sa-Token 存储：写入 Redis 的有效期、本地缓存在写入后失效、在线记录随令牌失效清理")
class RedisSaTokenDaoTest {

    private SaTokenTestEnv.FakeRedis redis;

    private RedisSaTokenDao dao;

    @BeforeEach
    void setUp() {
        redis = SaTokenTestEnv.enter();
        dao = new RedisSaTokenDao();
    }

    @AfterEach
    void tearDown() {
        redis.close();
    }

    @Test
    @DisplayName("按有效期写入：永不过期不设有效期；0 或“无值”时不写入；剩余时间按秒向上补 1 秒，负值原样返回")
    void writeWithTimeout() {
        dao.set("k1", "v1", 60);
        assertThat(redis.values()).containsEntry("k1", "v1");
        assertThat(redis.ttlMillis("k1")).isEqualTo(60_000L);
        assertThat(dao.getTimeout("k1")).isEqualTo(61L);

        dao.set("k2", "v2", SaTokenDao.NEVER_EXPIRE);
        assertThat(redis.ttlMillis("k2")).isEqualTo(-1L);
        assertThat(dao.getTimeout("k2")).isEqualTo(SaTokenDao.NEVER_EXPIRE);

        dao.set("k3", "v3", 0);
        dao.setObject("k4", "v4", SaTokenDao.NOT_VALUE_EXPIRE);
        dao.set("k5", "v5", -3);
        assertThat(redis.values()).doesNotContainKeys("k3", "k4", "k5");
        assertThat(dao.getTimeout("missing")).isEqualTo(SaTokenDao.NOT_VALUE_EXPIRE);
        assertThat(dao.getObjectTimeout("missing")).isEqualTo(SaTokenDao.NOT_VALUE_EXPIRE);
    }

    @Test
    @DisplayName("读取经过本地缓存；写入、修改、删除后立即失效，不会读到旧值")
    void localCacheInvalidation() {
        dao.set("k", "v1", 60);
        assertThat(dao.get("k")).isEqualTo("v1");

        dao.set("k", "v2", 60);
        assertThat(dao.get("k")).isEqualTo("v2");

        dao.update("k", "v3");
        assertThat(dao.get("k")).isEqualTo("v3");
        assertThat(redis.ttlMillis("k")).isEqualTo(60_000L);
        // 不存在的键不会被 update 创建
        dao.update("absent", "x");
        dao.updateObject("absent", "x");
        assertThat(redis.values()).doesNotContainKey("absent");

        dao.delete("k");
        assertThat(dao.get("k")).isNull();
    }

    @Test
    @DisplayName("本地缓存属于实例：一个实例缓存的值不影响另一个实例")
    void cachePerInstance() {
        dao.set("k", "v1", 60);
        assertThat(dao.get("k")).isEqualTo("v1");

        RedisSaTokenDao other = new RedisSaTokenDao();
        redis.values().put("k", "changed-elsewhere");
        assertThat(other.get("k")).isEqualTo("changed-elsewhere");
    }

    @Test
    @DisplayName("对象读写与有效期修改；有效期改为永不过期时去掉有效期并保留值")
    void objects() {
        dao.setObject("o", 42, 30);
        assertThat(dao.getObject("o")).isEqualTo(42);
        assertThat(dao.getObject("o", Integer.class)).isEqualTo(42);

        dao.updateObject("o", 43);
        assertThat(dao.getObject("o")).isEqualTo(43);
        assertThat(redis.ttlMillis("o")).isEqualTo(30_000L);
        dao.updateObjectTimeout("o", 120);
        assertThat(dao.getObjectTimeout("o")).isEqualTo(121L);
        dao.updateTimeout("o", 10);
        assertThat(redis.ttlMillis("o")).isEqualTo(10_000L);

        dao.updateObjectTimeout("o", SaTokenDao.NEVER_EXPIRE);
        assertThat(redis.ttlMillis("o")).isEqualTo(-1L);
        assertThat(dao.getObject("o")).isEqualTo(43);

        dao.set("s", "v", 10);
        dao.updateTimeout("s", SaTokenDao.NEVER_EXPIRE);
        assertThat(redis.ttlMillis("s")).isEqualTo(-1L);
        assertThat(dao.get("s")).isEqualTo("v");
        // 不存在的键不会因修改有效期而被创建
        dao.updateTimeout("missing", SaTokenDao.NEVER_EXPIRE);
        assertThat(redis.values()).doesNotContainKey("missing");

        dao.deleteObject("o");
        assertThat(dao.getObject("o")).isNull();
    }

    @Test
    @DisplayName("按前缀与关键字搜索，支持分页")
    void search() {
        dao.set("satoken:token:aaa-1", "x", 60);
        dao.set("satoken:token:bbb-2", "x", 60);
        dao.set("satoken:token:aaa-3", "x", 60);
        dao.set("other:aaa", "x", 60);

        assertThat(dao.searchData("satoken:token:", "aaa", 0, -1, true))
            .containsExactlyInAnyOrder("satoken:token:aaa-1", "satoken:token:aaa-3");
        assertThat(dao.searchData("satoken:token:", "", 0, 2, true)).hasSize(2);
    }

    @Test
    @DisplayName("完整登录流程使用该存储；注销、踢下线、被顶下线时清理在线用户记录")
    void loginAndCleanup() {
        LoginUser user = new LoginUser();
        user.setUserId(5L);
        user.setUserType("sys_user");
        LoginHelper.login(user, null);
        String token = StpUtil.getTokenValue();
        assertThat(LoginSessions.findLoginUser(token)).get().extracting(LoginUser::getUserId).isEqualTo(5L);

        redis.values().put(CacheNames.ONLINE_TOKEN_KEY + token, "online");
        OnlineTokenCleanupListener listener = new OnlineTokenCleanupListener();
        listener.doLogout("login", "sys_user-5", token);
        assertThat(redis.values()).doesNotContainKey(CacheNames.ONLINE_TOKEN_KEY + token);

        redis.values().put(CacheNames.ONLINE_TOKEN_KEY + "t2", "online");
        listener.doKickout("login", "sys_user-5", "t2");
        redis.values().put(CacheNames.ONLINE_TOKEN_KEY + "t3", "online");
        listener.doReplaced("login", "sys_user-5", "t3");
        assertThat(redis.values()).doesNotContainKeys(CacheNames.ONLINE_TOKEN_KEY + "t2", CacheNames.ONLINE_TOKEN_KEY + "t3");
    }

}
