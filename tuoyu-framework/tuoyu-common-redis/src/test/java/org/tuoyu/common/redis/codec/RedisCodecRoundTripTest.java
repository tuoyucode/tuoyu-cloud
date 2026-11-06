package org.tuoyu.common.redis.codec;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.SaTerminalInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.client.handler.State;
import org.redisson.codec.TypedJsonJackson3Codec;
import org.redisson.spring.cache.NullValue;
import org.tuoyu.system.api.domain.SysUserOnline;
import org.tuoyu.system.api.model.LoginUser;
import org.tuoyu.system.api.model.PostDTO;
import org.tuoyu.system.api.model.RoleDTO;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用与生产完全相同的 Redis 编解码器，验证盘点出的全部存储类型都能正常往返，且恶意数据被拒绝。
 */
@DisplayName("Redis 编解码白名单往返测试")
class RedisCodecRoundTripTest {

    private final TypedJsonJackson3Codec codec =
        new TypedJsonJackson3Codec(Object.class, RedisJsonMapperFactory.create(new RedisTypeAllowlist(List.of())));

    private Object roundTrip(Object value) throws IOException {
        return decode(encode(value));
    }

    private String encode(Object value) throws IOException {
        ByteBuf buf = codec.getValueEncoder().encode(value);
        try {
            return buf.toString(StandardCharsets.UTF_8);
        } finally {
            buf.release();
        }
    }

    private Object decode(String json) throws IOException {
        ByteBuf buf = Unpooled.copiedBuffer(json, StandardCharsets.UTF_8);
        try {
            return codec.getValueDecoder().decode(buf, new State());
        } finally {
            buf.release();
        }
    }

    private static LoginUser loginUser() {
        LoginUser user = new LoginUser();
        user.setUserId(1L);
        // 实际登录时一定会设置用户类型，getLoginId() 依赖它
        user.setUserType("sys_user");
        user.setUsername("admin");
        user.setMenuPermission(new HashSet<>(Set.of("system:user:list", "system:role:list")));
        user.setRolePermission(new HashSet<>(Set.of("admin")));
        RoleDTO role = new RoleDTO();
        role.setRoleId(10L);
        role.setRoleKey("admin");
        user.setRoles(new ArrayList<>(List.of(role)));
        PostDTO post = new PostDTO();
        post.setPostId(20L);
        user.setPosts(new ArrayList<>(List.of(post)));
        user.setDataScopeRoleMap(new HashMap<>(Map.of("1", new ArrayList<>(List.of(10L, 11L)))));
        return user;
    }

    @Test
    @DisplayName("登录用户（含角色、岗位、权限集合、数据权限映射）")
    void shouldRoundTripLoginUser() throws IOException {
        LoginUser user = loginUser();

        assertEquals(user, roundTrip(user));
    }

    @Test
    @DisplayName("Sa-Token 会话（会话数据中保存登录用户，含终端信息）")
    void shouldRoundTripSaSession() throws IOException {
        SaSession session = new SaSession("Authorization:login:session:1");
        session.getDataMap().put("loginUser", loginUser());
        session.setTerminalList(new Vector<>(List.of(new SaTerminalInfo(1, "token-value", "pc", new HashMap<>(Map.of("ip", "127.0.0.1"))))));

        SaSession decoded = assertInstanceOf(SaSession.class, roundTrip(session));

        assertEquals(session.getId(), decoded.getId());
        assertEquals(loginUser(), decoded.getDataMap().get("loginUser"));
        assertEquals("token-value", decoded.getTerminalList().getFirst().getTokenValue());
    }

    @Test
    @DisplayName("在线用户")
    void shouldRoundTripOnlineUser() throws IOException {
        SysUserOnline online = new SysUserOnline();
        online.setTokenId("token");
        online.setUserName("admin");
        online.setLoginTime(System.currentTimeMillis());

        assertEquals(online, roundTrip(online));
    }

    /**
     * 与推送消息结构相同：List.of 生成的不可变列表，以及 Object 字段中带 Long 的 HashMap
     */
    static class PushLike {
        List<Long> userIds;
        Object data;
    }

    @Test
    @DisplayName("推送消息：不可变列表与 Object 字段中的 Map、Long")
    void shouldRoundTripPushMessageShape() throws IOException {
        PushLike push = new PushLike();
        push.userIds = List.of(1L, 2L);
        push.data = new HashMap<>(Map.of("noticeId", 100L, "title", "通知"));

        PushLike decoded = assertInstanceOf(PushLike.class, roundTrip(push));

        assertEquals(List.of(1L, 2L), decoded.userIds);
        Map<?, ?> data = assertInstanceOf(Map.class, decoded.data);
        assertEquals(100L, data.get("noticeId"));
    }

    /**
     * 与消息总线的消息结构相同：byte[] 载荷，消息头中有 UUID、Long、String
     */
    static class BusMessageLike {
        Object payload;
        Map<String, Object> headers;
    }

    @Test
    @DisplayName("消息总线：byte[] 载荷与 UUID、Long、String 消息头")
    void shouldRoundTripBusMessageShape() throws IOException {
        BusMessageLike message = new BusMessageLike();
        message.payload = "{\"event\":\"ProcessEvent\"}".getBytes(StandardCharsets.UTF_8);
        UUID id = UUID.randomUUID();
        message.headers = new HashMap<>(Map.of("id", id, "timestamp", 1700000000000L,
            "contentType", "application/json"));

        BusMessageLike decoded = assertInstanceOf(BusMessageLike.class, roundTrip(message));

        assertArrayEquals((byte[]) message.payload, (byte[]) decoded.payload);
        assertEquals(id, decoded.headers.get("id"));
        assertEquals(1700000000000L, decoded.headers.get("timestamp"));
        assertEquals("application/json", decoded.headers.get("contentType"));
    }

    /**
     * 与缓存的 VO 结构相同：Date 字段的实际值为 java.sql.Timestamp（从数据库读出时常见）
     */
    static class VoLike {
        Date createTime;
        LocalDateTime updateTime;
        BigDecimal amount;
    }

    @Test
    @DisplayName("缓存的 VO 列表、空列表与缓存的 null 值")
    void shouldRoundTripCachedValues() throws IOException {
        VoLike vo = new VoLike();
        vo.createTime = new Timestamp(1700000000000L);
        vo.updateTime = LocalDateTime.of(2026, 10, 23, 9, 30, 0);
        vo.amount = new BigDecimal("12.50");

        List<?> list = assertInstanceOf(List.class, roundTrip(new ArrayList<>(List.of(vo))));
        VoLike decoded = assertInstanceOf(VoLike.class, list.getFirst());
        assertEquals(vo.createTime.getTime(), decoded.createTime.getTime());
        assertEquals(vo.updateTime, decoded.updateTime);
        assertEquals(vo.amount, decoded.amount);

        assertEquals(Collections.emptyList(), roundTrip(Collections.emptyList()));
        assertSame(NullValue.class, roundTrip(NullValue.INSTANCE).getClass());
    }

    /**
     * 与 UntrustedGadget 结构相同但位于白名单内，用来生成“合法”的数据，再把类名替换成不受信任的类
     */
    static class TrustedTwin {
        String name;
    }

    private String withUntrustedClass(Object trustedValue) throws IOException {
        String json = encode(trustedValue);
        String twin = TrustedTwin.class.getName();
        assertTrue(json.contains(twin), "测试数据应包含类名");
        return json.replace(twin, "com.example.untrusted.UntrustedGadget");
    }

    @Test
    @DisplayName("恶意数据：根对象、嵌套在 Map 中、放在数组中，都被拒绝，且不受信任的类从未被加载")
    void shouldRejectUntrustedTypesWithoutLoadingThem() throws IOException {
        TrustedTwin twin = new TrustedTwin();
        twin.name = "x";

        String asRoot = withUntrustedClass(twin);
        String nestedInMap = withUntrustedClass(new HashMap<>(Map.of("k", twin)));
        String inArray = withUntrustedClass(new Object[]{twin});

        long rejectedBefore = RedisTypeAllowlist.rejectedCount();
        assertThrows(Exception.class, () -> decode(asRoot));
        assertThrows(Exception.class, () -> decode(nestedInMap));
        assertThrows(Exception.class, () -> decode(inArray));
        assertFalse(LoadTracker.UNTRUSTED_LOADED.get(), "被拒绝的类不应被加载");
        assertTrue(RedisTypeAllowlist.rejectedCount() >= rejectedBefore + 3, "每次拒绝都应计数，供安全中心展示");
    }

}
