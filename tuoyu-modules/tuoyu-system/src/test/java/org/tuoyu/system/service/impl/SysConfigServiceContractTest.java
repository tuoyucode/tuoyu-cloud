package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.utils.CacheUtils;
import org.tuoyu.system.domain.SysConfig;
import org.tuoyu.system.domain.bo.SysConfigBo;
import org.tuoyu.system.mapper.SysConfigMapper;
import org.tuoyu.system.support.SystemTestSupport;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 参数服务的行为契约
 */
@DisplayName("参数服务契约")
class SysConfigServiceContractTest {

    static {
        SystemTestSupport.init();
    }

    private final SysConfigMapper mapper = SystemTestSupport.mapper(SysConfigMapper.class);
    private final SysConfigServiceImpl service = new SysConfigServiceImpl(mapper);
    private MockedStatic<CacheUtils> cache;

    @BeforeEach
    void setUp() {
        cache = mockStatic(CacheUtils.class);
    }

    @AfterEach
    void tearDown() {
        cache.close();
    }

    private static SysConfig config(Long id, String key, String value, String type) {
        SysConfig config = new SysConfig();
        config.setConfigId(id);
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setConfigType(type);
        config.setConfigName("参数" + id);
        return config;
    }

    private static SysConfigBo bo(Long id, String key, String value) {
        SysConfigBo bo = new SysConfigBo();
        bo.setConfigId(id);
        bo.setConfigKey(key);
        bo.setConfigValue(value);
        bo.setConfigName("名称");
        return bo;
    }

    @Test
    @DisplayName("按键读取：不存在时为空串；结果按键缓存在 SYS_CONFIG")
    void byKey() throws Exception {
        assertEquals("", service.selectConfigByKey("none"));
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(config(1L, "k", "v", "N"));
        assertEquals("v", service.selectConfigByKey("k"));
        Cacheable cacheable = SysConfigServiceImpl.class.getMethod("selectConfigByKey", String.class).getAnnotation(Cacheable.class);
        assertThat(cacheable.cacheNames()).containsExactly(CacheNames.SYS_CONFIG);
        assertEquals("#configKey", cacheable.key());
    }

    @Test
    @DisplayName("注册开关：按布尔值解析")
    void registerEnabled() {
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(config(1L, "sys.account.registerUser", "true", "Y"));
        assertTrue(service.selectRegisterEnabled());
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(config(1L, "sys.account.registerUser", "false", "Y"));
        assertFalse(service.selectRegisterEnabled());
    }

    @Test
    @DisplayName("注册开关：参数不存在或不是布尔值时为关闭")
    void registerEnabledMissing() {
        assertFalse(service.selectRegisterEnabled());
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(config(1L, "sys.account.registerUser", "abc", "Y"));
        assertFalse(service.selectRegisterEnabled());
    }

    @Test
    @DisplayName("列表、分页、统计使用同一组条件；详情按 ID")
    void queries() {
        SysConfigBo query = new SysConfigBo();
        query.setConfigName("名");
        query.setConfigType("Y");
        query.setConfigKey("sys");
        query.setParams(Map.of("beginTime", "2026-01-01 00:00:00", "endTime", "2026-12-31 23:59:59"));
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(config(1L, "k", "v", "Y")));
        assertThat(service.selectConfigList(query)).hasSize(1);
        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment()).contains("config_name LIKE").contains("config_type =")
            .contains("config_key LIKE").contains("create_time BETWEEN").contains("ORDER BY config_id ASC");
        service.selectPageConfigList(new SysConfigBo(), new PageParam());
        service.countConfigList(new SysConfigBo());
        when(mapper.selectById(1L)).thenReturn(config(1L, "k", "v", "N"));
        assertEquals("k", service.selectConfigById(1L).getConfigKey());
        assertNull(service.selectConfigById(2L));
    }

    @Test
    @DisplayName("新增：写入并把参数值放入缓存；写入失败时报错")
    void insert() throws Exception {
        when(mapper.insert(any(SysConfig.class))).thenReturn(1);
        assertEquals("v", service.insertConfig(bo(null, "k", "v")));
        CachePut put = SysConfigServiceImpl.class.getMethod("insertConfig", SysConfigBo.class).getAnnotation(CachePut.class);
        assertThat(put.cacheNames()).containsExactly(CacheNames.SYS_CONFIG);
        assertEquals("#bo.configKey", put.key());
        when(mapper.insert(any(SysConfig.class))).thenReturn(0);
        assertThatThrownBy(() -> service.insertConfig(bo(null, "k", "v"))).hasMessage("新增参数'名称'失败");
    }

    @Test
    @DisplayName("修改：按 ID 修改时改了键名清除旧键；没有 ID 时按键名修改；不存在时报错")
    void update() {
        when(mapper.selectById(1L)).thenReturn(config(1L, "old.key", "v", "N"));
        when(mapper.updateById(any(SysConfig.class))).thenReturn(1);
        assertEquals("v2", service.updateConfig(bo(1L, "new.key", "v2")));
        cache.verify(() -> CacheUtils.evict(CacheNames.SYS_CONFIG, "old.key"));

        when(mapper.update(any(), any(Wrapper.class))).thenReturn(1);
        SysConfigBo byKey = new SysConfigBo();
        byKey.setConfigKey("sys.key");
        byKey.setConfigValue("x");
        service.updateConfig(byKey);
        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(any(), wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment()).contains("config_key =");

        when(mapper.update(any(), any(Wrapper.class))).thenReturn(0);
        SysConfigBo missing = new SysConfigBo();
        missing.setConfigKey("no.key");
        assertThatThrownBy(() -> service.updateConfig(missing)).hasMessage("修改参数'no.key'失败，参数不存在");
        when(mapper.updateById(any(SysConfig.class))).thenReturn(0);
        assertThatThrownBy(() -> service.updateConfig(bo(1L, "old.key", "v"))).hasMessage("修改参数'名称'失败，参数不存在");
    }

    @Test
    @DisplayName("修改后清除该键的缓存，而不是把请求中的值写入缓存（按键修改时可能只提交部分字段）")
    void updateEvictsInsteadOfPut() throws Exception {
        Method update = SysConfigServiceImpl.class.getMethod("updateConfig", SysConfigBo.class);
        assertNull(update.getAnnotation(CachePut.class));
        CacheEvict evict = update.getAnnotation(CacheEvict.class);
        assertThat(evict).isNotNull();
        assertThat(evict.cacheNames()).containsExactly(CacheNames.SYS_CONFIG);
        assertEquals("#bo.configKey", evict.key());
    }

    @Test
    @DisplayName("删除：有内置参数时整体拒绝；普通参数删除并清除缓存；重置缓存清空 SYS_CONFIG")
    void delete() {
        when(mapper.selectByIds(anyList())).thenReturn(List.of(config(1L, "a", "v", "N"), config(2L, "sys.b", "v", "Y")));
        assertThatThrownBy(() -> service.deleteConfigByIds(List.of(1L, 2L))).hasMessage("内置参数【sys.b】不能删除");
        verify(mapper, never()).deleteByIds(anyList());

        when(mapper.selectByIds(anyList())).thenReturn(List.of(config(1L, "a", "v", "N")));
        service.deleteConfigByIds(List.of(1L));
        verify(mapper).deleteByIds(anyList());
        cache.verify(() -> CacheUtils.evict(CacheNames.SYS_CONFIG, "a"), org.mockito.Mockito.atLeastOnce());

        service.resetConfigCache();
        cache.verify(() -> CacheUtils.clear(CacheNames.SYS_CONFIG));
    }

    @Test
    @DisplayName("键名唯一：存在其他同键名的参数时不唯一（修改时排除自己）")
    void unique() {
        when(mapper.exists(any())).thenReturn(true);
        when(mapper.selectCount(any())).thenReturn(1L);
        assertFalse(service.checkConfigKeyUnique(bo(1L, "k", "v")));
        when(mapper.exists(any())).thenReturn(false);
        when(mapper.selectCount(any())).thenReturn(0L);
        assertTrue(service.checkConfigKeyUnique(bo(1L, "k", "v")));
    }
}
