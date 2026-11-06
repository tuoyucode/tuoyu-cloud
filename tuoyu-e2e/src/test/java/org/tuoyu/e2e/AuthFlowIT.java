package org.tuoyu.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.tuoyu.e2e.support.ApiCrypto;
import org.tuoyu.e2e.support.ApiResponse;
import org.tuoyu.e2e.support.E2eClient;
import org.tuoyu.e2e.support.E2eConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证流程：公钥、加密登录、防重放、用户信息与路由、未登录拒绝、登出。
 * 微服务（经网关）与单体两种部署方式共用，访问的 URL 完全相同。
 *
 * @author tuoyu
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowIT {

    private E2eConfig config;

    private E2eClient anonymous;

    private E2eClient admin;

    /**
     * 测试中登录得到的令牌，结束时统一登出
     */
    private final List<E2eClient> sessions = new ArrayList<>();

    @BeforeAll
    void setUp() {
        config = E2eConfig.load();
        System.out.println("端到端测试：" + config);
        anonymous = E2eClient.anonymous(config);
    }

    @AfterAll
    void tearDown() {
        sessions.forEach(E2eClient::logoutQuietly);
    }

    @Test
    @Order(1)
    void publicKeyIsAvailableWithoutLogin() {
        JsonNode data = anonymous.get("/auth/crypto/public-key").assertOk("获取接口加密公钥").data();

        assertThat(data.path("enabled").asBoolean()).as("接口加密已开启").isTrue();
        assertThat(data.path("version").asInt()).isEqualTo(ApiCrypto.VERSION);
        assertThat(data.path("algorithm").asString()).isEqualTo(ApiCrypto.ALGORITHM);
        // kid 由公钥推导（ADR 0001 第 3.1 节），客户端自己计算的结果必须与服务端一致
        ApiCrypto.ServerKey key = ApiCrypto.parsePublicKey(data.path("publicKey").asString());
        assertThat(data.path("kid").asString()).isEqualTo(key.kid());
        assertThat(key.publicKey().getModulus().bitLength()).as("RSA 密钥长度").isGreaterThanOrEqualTo(2048);
    }

    @Test
    @Order(2)
    void captchaIsDisabledForThisEnvironment() {
        // 前置条件：端到端环境关闭登录图形验证码（security.captcha.enabled=false），否则无法自动登录
        JsonNode data = anonymous.get("/auth/code").assertOk("查询图形验证码开关").data();
        assertThat(data.path("captchaEnabled").asBoolean(true))
            .as("被测环境需关闭登录图形验证码（tuoyu-auth.yml 中 security.captcha.enabled=false）")
            .isFalse();
    }

    @Test
    @Order(3)
    void plainLoginIsRejected() {
        String body = E2eClient.toJson(anonymous.passwordLoginBody(config.username(), config.password()));
        ApiResponse response = anonymous.sendRaw("POST", "/auth/login", body, Map.of());

        response.assertCode(400, "未加密的登录请求应被拒绝");
        assertThat(response.msg()).contains("加密");
        assertThat(response.data().path("access_token").isMissingNode() || response.data().isNull()).isTrue();
    }

    @Test
    @Order(4)
    void encryptedLoginSucceeds() {
        ApiResponse response = anonymous.postEncrypted("/auth/login",
            anonymous.passwordLoginBody(config.username(), config.password())).assertOk("加密登录");

        assertThat(response.data().path("client_id").asString()).isEqualTo(config.clientId());
        assertThat(response.data().path("expire_in").asLong()).isPositive();
        admin = anonymous.withToken(E2eClient.accessToken(response));
        sessions.add(admin);
    }

    @Test
    @Order(5)
    void replayedRequestIsRejected() {
        ApiCrypto.Sealed sealed = anonymous.seal(anonymous.passwordLoginBody(config.username(), config.password()));

        ApiResponse first = anonymous.sendSealed("POST", "/auth/login", sealed).assertOk("第一次发送加密登录请求");
        sessions.add(anonymous.withToken(E2eClient.accessToken(first)));

        // 原样重发同一个信封：nonce 已使用，按协议返回 400“请求已过期”，不能再次登录
        ApiResponse replay = anonymous.sendSealed("POST", "/auth/login", sealed);
        replay.assertCode(400, "重放的加密请求应被拒绝");
        assertThat(replay.msg()).contains("过期");
        assertThat(replay.data().path("access_token").isMissingNode() || replay.data().isNull()).isTrue();
    }

    @Test
    @Order(6)
    void tamperedRequestIsRejected() {
        ApiCrypto.Sealed sealed = anonymous.seal(anonymous.passwordLoginBody(config.username(), config.password()));
        // 翻转密文的一个比特，GCM 认证失败
        ObjectNode envelope = (ObjectNode) JsonMapper.builder().build().readTree(sealed.body());
        byte[] data = Base64.getDecoder().decode(envelope.path("data").asString());
        data[data.length - 1] ^= 1;
        envelope.put("data", Base64.getEncoder().encodeToString(data));
        String tampered = E2eClient.toJson(envelope);

        ApiResponse response = anonymous.sendRaw("POST", "/auth/login", tampered, Map.of(ApiCrypto.HEADER, "1"));
        response.assertCode(400, "篡改的加密请求应被拒绝");
        assertThat(response.msg()).contains("解密失败");
    }

    @Test
    @Order(7)
    void userInfoAndRoutersAreAvailableAfterLogin() {
        JsonNode info = admin().get("/system/user/getInfo").assertOk("获取用户信息").data();
        assertThat(info.path("user").path("userName").asString()).isEqualTo(config.username());
        assertThat(info.path("roles").size()).as("角色").isPositive();
        assertThat(info.path("permissions").size()).as("权限").isPositive();

        ApiResponse routers = admin().get("/system/menu/getRouters").assertOk("获取路由");
        List<JsonNode> menus = routers.list();
        assertThat(menus).as("管理员的路由").isNotEmpty();
        assertThat(menus).allSatisfy(menu -> assertThat(menu.path("path").asString()).isNotBlank());
    }

    @Test
    @Order(8)
    void protectedEndpointsRequireLogin() {
        anonymous.get("/system/user/getInfo").assertCode(401, "未登录访问用户信息");
        anonymous.get("/system/user/list", Map.of("pageNum", 1, "pageSize", 10)).assertCode(401, "未登录访问用户列表");
        anonymous.get("/system/menu/getRouters").assertCode(401, "未登录访问路由");
        anonymous.withToken("not-a-valid-token").get("/system/user/getInfo").assertCode(401, "无效令牌访问用户信息");
    }

    @Test
    @Order(9)
    void logoutInvalidatesToken() {
        E2eClient session = anonymous.login(config.username(), config.password());
        session.get("/system/user/getInfo").assertOk("登出前访问用户信息");

        session.post("/auth/logout", Map.of()).assertOk("登出");
        session.get("/system/user/getInfo").assertCode(401, "登出后使用原令牌");
    }

    private E2eClient admin() {
        assertThat(admin).as("加密登录（第 4 个测试）未成功，无法继续").isNotNull();
        return admin;
    }
}
