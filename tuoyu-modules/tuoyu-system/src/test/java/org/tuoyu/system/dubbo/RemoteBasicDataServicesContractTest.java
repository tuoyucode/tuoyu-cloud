package org.tuoyu.system.dubbo;

import cn.hutool.core.lang.Dict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;
import org.tuoyu.system.api.domain.vo.RemoteDictDataVo;
import org.tuoyu.system.api.domain.vo.RemoteDictTypeVo;
import org.tuoyu.system.domain.vo.SysClientVo;
import org.tuoyu.system.domain.vo.SysDictDataVo;
import org.tuoyu.system.domain.vo.SysDictTypeVo;
import org.tuoyu.system.service.ISysClientService;
import org.tuoyu.system.service.ISysConfigService;
import org.tuoyu.system.service.ISysDictTypeService;
import org.tuoyu.system.support.SystemTestSupport;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 参数、字典、客户端的跨模块接口实现（规格 S2 第 8 节）
 */
@DisplayName("跨模块接口契约：参数、字典、客户端")
class RemoteBasicDataServicesContractTest {

    static {
        SystemTestSupport.init();
    }

    private final ISysConfigService configService = mock(ISysConfigService.class);
    private final RemoteConfigServiceImpl remoteConfig = new RemoteConfigServiceImpl(configService);

    public static class Point {
        private int x;
        private String label;

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

    @Test
    @DisplayName("参数：按键取值、注册开关转交参数服务；默认方法按值转换")
    void configValues() {
        when(configService.selectConfigByKey("k")).thenReturn("42");
        when(configService.selectRegisterEnabled()).thenReturn(true);
        assertEquals("42", remoteConfig.getConfigValue("k"));
        assertTrue(remoteConfig.selectRegisterEnabled());
        assertEquals(42, remoteConfig.getConfigInt("k"));
        assertEquals(42L, remoteConfig.getConfigLong("k"));
    }

    @Test
    @DisplayName("参数：JSON 解析为 Map、Map 列表、对象、对象列表")
    void configJson() {
        when(configService.selectConfigByKey("map")).thenReturn("{\"a\":1,\"b\":\"x\"}");
        when(configService.selectConfigByKey("maps")).thenReturn("[{\"a\":1},{\"a\":2}]");
        when(configService.selectConfigByKey("obj")).thenReturn("{\"x\":3,\"label\":\"p\"}");
        when(configService.selectConfigByKey("objs")).thenReturn("[{\"x\":1},{\"x\":2}]");
        Dict map = remoteConfig.getConfigMap("map");
        assertEquals(1, ((Number) map.get("a")).intValue());
        assertEquals("x", map.get("b"));
        assertThat(remoteConfig.getConfigArrayMap("maps")).hasSize(2);
        Point point = remoteConfig.getConfigObject("obj", Point.class);
        assertEquals(3, point.getX());
        assertEquals("p", point.getLabel());
        assertThat(remoteConfig.getConfigArray("objs", Point.class)).extracting(Point::getX).containsExactly(1, 2);
        assertNull(remoteConfig.getConfigObject("none", Point.class));
    }

    @Test
    @DisplayName("参数：值为空或不能解析时，Map 为空 Dict、列表为空列表、对象为 null，不抛异常")
    void configJsonEmptyOrInvalid() {
        // 现有实现不满足：值为空时 Map 与 Map 列表返回 null，值不是合法 JSON 时抛出解析异常（接口说明要求返回空结果）
        when(configService.selectConfigByKey("empty")).thenReturn("");
        when(configService.selectConfigByKey("bad")).thenReturn("not json");
        assertThat(remoteConfig.getConfigMap("empty")).isNotNull().isEmpty();
        assertThat(remoteConfig.getConfigArrayMap("empty")).isNotNull().isEmpty();
        assertThat(remoteConfig.getConfigArray("empty", Point.class)).isNotNull().isEmpty();
        assertThat(remoteConfig.getConfigMap("bad")).isNotNull().isEmpty();
        assertThat(remoteConfig.getConfigArrayMap("bad")).isNotNull().isEmpty();
        assertThat(remoteConfig.getConfigArray("bad", Point.class)).isNotNull().isEmpty();
        assertNull(remoteConfig.getConfigObject("bad", Point.class));
    }

    @Test
    @DisplayName("字典：按类型取字典类型与字典数据，转换为跨模块对象；不存在时为 null 与空列表")
    void dict() {
        ISysDictTypeService dictService = mock(ISysDictTypeService.class);
        RemoteDictServiceImpl remoteDict = new RemoteDictServiceImpl(dictService);
        SysDictTypeVo type = new SysDictTypeVo();
        type.setDictId(1L);
        type.setDictName("是否");
        type.setDictType("sys_yes_no");
        type.setRemark("r");
        type.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(dictService.selectDictTypeByType("sys_yes_no")).thenReturn(type);
        RemoteDictTypeVo remoteType = remoteDict.selectDictTypeByType("sys_yes_no");
        assertEquals(1L, remoteType.getDictId());
        assertEquals("是否", remoteType.getDictName());
        assertEquals("sys_yes_no", remoteType.getDictType());
        assertEquals("r", remoteType.getRemark());
        assertEquals(type.getCreateTime(), remoteType.getCreateTime());
        assertNull(remoteDict.selectDictTypeByType("none"));

        SysDictDataVo data = new SysDictDataVo();
        data.setDictCode(2L);
        data.setDictSort(1);
        data.setDictLabel("是");
        data.setDictValue("Y");
        data.setDictType("sys_yes_no");
        data.setCssClass("c");
        data.setListClass("primary");
        data.setIsDefault("Y");
        data.setRemark("rm");
        when(dictService.selectDictDataByType("sys_yes_no")).thenReturn(List.of(data));
        RemoteDictDataVo remoteData = remoteDict.selectDictDataByType("sys_yes_no").get(0);
        assertEquals(2L, remoteData.getDictCode());
        assertEquals(1, remoteData.getDictSort());
        assertEquals("是", remoteData.getDictLabel());
        assertEquals("Y", remoteData.getDictValue());
        assertEquals("sys_yes_no", remoteData.getDictType());
        assertEquals("c", remoteData.getCssClass());
        assertEquals("primary", remoteData.getListClass());
        assertEquals("Y", remoteData.getIsDefault());
        assertEquals("rm", remoteData.getRemark());
        when(dictService.selectDictDataByType("none")).thenReturn(List.of());
        assertThat(remoteDict.selectDictDataByType("none")).isEmpty();
    }

    @Test
    @DisplayName("客户端：按客户端 ID 取（含密钥，供认证服务）；不存在时为 null")
    void client() {
        ISysClientService clientService = mock(ISysClientService.class);
        RemoteClientServiceImpl remoteClient = new RemoteClientServiceImpl(clientService);
        SysClientVo vo = new SysClientVo();
        vo.setId(1L);
        vo.setClientId("cid");
        vo.setClientKey("pc");
        vo.setClientSecret("secret");
        vo.setGrantType("password,sms");
        vo.setGrantTypeList(List.of("password", "sms"));
        vo.setDeviceType("pc");
        vo.setAccessPath("/system/**");
        vo.setIpWhitelist("10.0.0.0/8");
        vo.setActiveTimeout(1800L);
        vo.setTimeout(3600L);
        vo.setStatus("0");
        when(clientService.queryByClientId("cid")).thenReturn(vo);
        RemoteClientVo remote = remoteClient.queryByClientId("cid");
        assertEquals(1L, remote.getId());
        assertEquals("cid", remote.getClientId());
        assertEquals("pc", remote.getClientKey());
        assertEquals("secret", remote.getClientSecret());
        assertEquals("password,sms", remote.getGrantType());
        assertThat(remote.getGrantTypeList()).containsExactly("password", "sms");
        assertEquals("pc", remote.getDeviceType());
        assertEquals("/system/**", remote.getAccessPath());
        assertEquals("10.0.0.0/8", remote.getIpWhitelist());
        assertEquals(1800L, remote.getActiveTimeout());
        assertEquals(3600L, remote.getTimeout());
        assertEquals("0", remote.getStatus());
        assertNull(remoteClient.queryByClientId("none"));
    }
}
