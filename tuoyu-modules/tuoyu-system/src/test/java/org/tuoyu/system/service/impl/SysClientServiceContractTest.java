package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.utils.CacheUtils;
import org.tuoyu.system.domain.SysClient;
import org.tuoyu.system.domain.bo.SysClientBo;
import org.tuoyu.system.domain.vo.SysClientVo;
import org.tuoyu.system.mapper.SysClientMapper;
import org.tuoyu.system.support.SystemTestSupport;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客户端服务的行为契约（规格 S2 第 7 节）
 */
@DisplayName("客户端服务契约")
class SysClientServiceContractTest {

    static {
        SystemTestSupport.init();
    }

    private final SysClientMapper mapper = SystemTestSupport.mapper(SysClientMapper.class);
    private final SysClientServiceImpl service = new SysClientServiceImpl(mapper);
    private MockedStatic<CacheUtils> cache;

    @BeforeEach
    void setUp() {
        cache = mockStatic(CacheUtils.class);
    }

    @AfterEach
    void tearDown() {
        cache.close();
    }

    private static String md5(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8));
        return String.format("%032x", new BigInteger(1, digest));
    }

    private static SysClient client(Long id, String clientId) {
        SysClient client = new SysClient();
        client.setId(id);
        client.setClientId(clientId);
        client.setClientKey("pc");
        client.setClientSecret("secret");
        client.setGrantType("password,sms");
        client.setAccessPath("/system/**;/monitor/**");
        client.setIpWhitelist("10.0.0.0/8\n192.168.1.1");
        client.setTimeout(3600L);
        return client;
    }

    @Test
    @DisplayName("新增：客户端 ID 为 md5(key + secret)；授权类型逗号连接；访问路径规范化，IP 白名单原样拆分")
    void insert() throws Exception {
        when(mapper.insert(any(SysClient.class))).thenAnswer(inv -> {
            inv.<SysClient>getArgument(0).setId(9L);
            return 1;
        });
        SysClientBo bo = new SysClientBo();
        bo.setClientKey("app");
        bo.setClientSecret("s3cret");
        bo.setGrantTypeList(List.of("password", "sms"));
        bo.setAccessPath(" system/** ; * ,\n/monitor/** ");
        bo.setIpWhitelistList(List.of("10.0.0.1", " ", "10.0.0.2"));
        service.insertByBo(bo);
        assertEquals(9L, bo.getId());
        ArgumentCaptor<SysClient> saved = ArgumentCaptor.forClass(SysClient.class);
        verify(mapper).insert(saved.capture());
        assertEquals(md5("apps3cret"), saved.getValue().getClientId());
        assertEquals("password,sms", saved.getValue().getGrantType());
        assertEquals("/system/**,/**,/monitor/**", saved.getValue().getAccessPath());
        assertEquals("10.0.0.1,10.0.0.2", saved.getValue().getIpWhitelist());
    }

    @Test
    @DisplayName("访问路径、IP 白名单：提交空串或空列表时保存空串（清空），都不提交时不改")
    void emptyRules() {
        when(mapper.updateById(any(SysClient.class))).thenReturn(1);
        when(mapper.selectById(1L)).thenReturn(client(1L, "cid"));
        SysClientBo clear = new SysClientBo();
        clear.setId(1L);
        clear.setClientKey("pc");
        clear.setAccessPath("");
        clear.setIpWhitelistList(List.of());
        service.updateByBo(clear);
        SysClientBo keep = new SysClientBo();
        keep.setId(1L);
        keep.setClientKey("pc");
        service.updateByBo(keep);
        ArgumentCaptor<SysClient> saved = ArgumentCaptor.forClass(SysClient.class);
        verify(mapper, org.mockito.Mockito.times(2)).updateById(saved.capture());
        assertEquals("", saved.getAllValues().get(0).getAccessPath());
        assertEquals("", saved.getAllValues().get(0).getIpWhitelist());
        assertNull(saved.getAllValues().get(1).getAccessPath());
        assertNull(saved.getAllValues().get(1).getIpWhitelist());
    }

    @Test
    @DisplayName("修改：密钥为空表示不修改；客户端不存在时报错")
    void update() {
        when(mapper.selectById(1L)).thenReturn(client(1L, "cid"));
        when(mapper.updateById(any(SysClient.class))).thenReturn(1);
        SysClientBo bo = new SysClientBo();
        bo.setId(1L);
        bo.setClientKey("pc");
        bo.setClientSecret(" ");
        bo.setGrantTypeList(List.of("password"));
        service.updateByBo(bo);
        ArgumentCaptor<SysClient> saved = ArgumentCaptor.forClass(SysClient.class);
        verify(mapper).updateById(saved.capture());
        assertNull(saved.getValue().getClientSecret());
        assertEquals("password", saved.getValue().getGrantType());

        when(mapper.updateById(any(SysClient.class))).thenReturn(0);
        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessage("修改客户端'pc'失败，客户端不存在");
    }

    @Test
    @DisplayName("修改：客户端 ID 不能改；按数据库中的客户端 ID 清除缓存")
    void updateKeepsClientIdAndEvictsStored() {
        // 现有实现不满足：请求中的 clientId 原样写入，并且只按请求中的 clientId 清缓存（请求没带时缓存 30 天不更新）
        when(mapper.selectById(1L)).thenReturn(client(1L, "stored-cid"));
        when(mapper.updateById(any(SysClient.class))).thenReturn(1);
        SysClientBo bo = new SysClientBo();
        bo.setId(1L);
        bo.setClientId("forged");
        bo.setClientKey("pc");
        service.updateByBo(bo);
        ArgumentCaptor<SysClient> saved = ArgumentCaptor.forClass(SysClient.class);
        verify(mapper).updateById(saved.capture());
        assertNull(saved.getValue().getClientId());
        cache.verify(() -> CacheUtils.evict(CacheNames.SYS_CLIENT, "stored-cid"));
    }

    @Test
    @DisplayName("按客户端 ID 查询（认证用）：带密钥并拆分规则，结果缓存；详情与列表不带密钥")
    void queries() throws Exception {
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(client(1L, "cid"));
        SysClientVo forAuth = service.queryByClientId("cid");
        assertEquals("secret", forAuth.getClientSecret());
        assertThat(forAuth.getGrantTypeList()).containsExactly("password", "sms");
        assertThat(forAuth.getAccessPathList()).containsExactly("/system/**", "/monitor/**");
        assertThat(forAuth.getIpWhitelistList()).containsExactly("10.0.0.0/8", "192.168.1.1");
        Cacheable cacheable = SysClientServiceImpl.class.getMethod("queryByClientId", String.class).getAnnotation(Cacheable.class);
        assertThat(cacheable.cacheNames()).containsExactly(CacheNames.SYS_CLIENT);
        assertEquals("#clientId", cacheable.key());

        when(mapper.selectById(1L)).thenReturn(client(1L, "cid"));
        SysClientVo detail = service.queryById(1L);
        assertNull(detail.getClientSecret());
        assertThat(detail.getGrantTypeList()).containsExactly("password", "sms");
        assertNull(service.queryById(2L));

        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(client(1L, "cid")));
        assertThat(service.queryList(new SysClientBo())).allSatisfy(vo -> assertNull(vo.getClientSecret()));
        when(mapper.selectList(any(com.baomidou.mybatisplus.core.metadata.IPage.class), any())).thenReturn(List.of(client(1L, "cid")));
        PageResult<SysClientVo> page = service.queryPageList(new SysClientBo(), new PageParam());
        assertThat(page.getItems()).allSatisfy(vo -> assertNull(vo.getClientSecret()));
        service.countList(new SysClientBo());
    }

    @Test
    @DisplayName("列表条件不含客户端密钥")
    void noSecretFilter() {
        // 现有实现不满足：列表可以按 clientSecret 精确过滤，成了猜测密钥的判定器
        SysClientBo query = new SysClientBo();
        query.setClientId("cid");
        query.setClientKey("pc");
        query.setClientSecret("guess");
        query.setStatus("0");
        service.queryList(query);
        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment()).contains("client_id =").contains("client_key =").contains("status =")
            .doesNotContain("client_secret");
    }

    @Test
    @DisplayName("修改状态、删除：清除缓存；不存在时报错；key 唯一性")
    void statusDeleteUnique() throws Exception {
        when(mapper.update(any(Wrapper.class))).thenReturn(0);
        when(mapper.update(any(), any(Wrapper.class))).thenReturn(0);
        assertThatThrownBy(() -> service.updateClientStatus("cid", "1")).hasMessage("修改客户端状态失败，客户端不存在");
        CacheEvict statusEvict = SysClientServiceImpl.class.getMethod("updateClientStatus", String.class, String.class).getAnnotation(CacheEvict.class);
        assertThat(statusEvict.cacheNames()).containsExactly(CacheNames.SYS_CLIENT);
        assertEquals("#clientId", statusEvict.key());

        when(mapper.deleteByIds(anyCollection())).thenReturn(0);
        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(1L), true)).hasMessage("删除客户端失败，客户端不存在");
        CacheEvict deleteEvict = SysClientServiceImpl.class.getMethod("deleteWithValidByIds", java.util.Collection.class, Boolean.class)
            .getAnnotation(CacheEvict.class);
        assertTrue(deleteEvict.allEntries());

        when(mapper.exists(any())).thenReturn(true);
        when(mapper.selectCount(any())).thenReturn(1L);
        SysClientBo bo = new SysClientBo();
        bo.setId(1L);
        bo.setClientKey("pc");
        assertFalse(service.checkClickKeyUnique(bo));
    }
}
