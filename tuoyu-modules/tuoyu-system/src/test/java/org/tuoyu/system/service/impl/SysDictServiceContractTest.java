package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.utils.CacheUtils;
import org.tuoyu.system.domain.SysDictData;
import org.tuoyu.system.domain.SysDictType;
import org.tuoyu.system.domain.bo.SysDictDataBo;
import org.tuoyu.system.domain.bo.SysDictTypeBo;
import org.tuoyu.system.domain.vo.SysDictDataVo;
import org.tuoyu.system.mapper.SysDictDataMapper;
import org.tuoyu.system.mapper.SysDictTypeMapper;
import org.tuoyu.system.support.SystemTestSupport;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 字典类型与字典数据服务的行为契约（规格 S2 第 5、6 节）
 */
@DisplayName("字典服务契约")
class SysDictServiceContractTest {

    static {
        SystemTestSupport.init();
    }

    private final SysDictTypeMapper typeMapper = SystemTestSupport.mapper(SysDictTypeMapper.class);
    private final SysDictDataMapper dataMapper = SystemTestSupport.mapper(SysDictDataMapper.class);
    private MockedStatic<CacheUtils> cache;

    @BeforeEach
    void setUp() {
        cache = mockStatic(CacheUtils.class);
    }

    @AfterEach
    void tearDown() {
        cache.close();
    }

    private static SysDictType type(Long id, String type) {
        SysDictType t = new SysDictType();
        t.setDictId(id);
        t.setDictType(type);
        t.setDictName("字典" + id);
        return t;
    }

    private static SysDictData data(Long code, String type, String value) {
        SysDictData d = new SysDictData();
        d.setDictCode(code);
        d.setDictType(type);
        d.setDictValue(value);
        d.setDictLabel("标签" + value);
        d.setDictSort(code.intValue());
        return d;
    }

    @Nested
    @DisplayName("字典类型")
    class Types {

        private final SysDictTypeServiceImpl service = new SysDictTypeServiceImpl(typeMapper, dataMapper);

        @Test
        @DisplayName("按类型取字典数据与字典类型：分别缓存在 SYS_DICT、SYS_DICT_TYPE，键为类型")
        void byType() throws Exception {
            doReturn(List.of(data(1L, "sys_yes_no", "Y"))).when(dataMapper).selectDictDataByType("sys_yes_no");
            List<SysDictDataVo> list = service.selectDictDataByType("sys_yes_no");
            assertThat(list).extracting(SysDictDataVo::getDictValue).containsExactly("Y");
            assertThat(service.selectDictDataByType("none")).isEmpty();
            when(typeMapper.selectOne(any(), anyBoolean())).thenReturn(type(1L, "sys_yes_no"));
            assertEquals(1L, service.selectDictTypeByType("sys_yes_no").getDictId());

            Cacheable data = SysDictTypeServiceImpl.class.getMethod("selectDictDataByType", String.class).getAnnotation(Cacheable.class);
            assertThat(data.cacheNames()).containsExactly(CacheNames.SYS_DICT);
            assertEquals("#dictType", data.key());
            Cacheable typeCache = SysDictTypeServiceImpl.class.getMethod("selectDictTypeByType", String.class).getAnnotation(Cacheable.class);
            assertThat(typeCache.cacheNames()).containsExactly(CacheNames.SYS_DICT_TYPE);
        }

        @Test
        @DisplayName("列表条件、全部、详情")
        void queries() {
            SysDictTypeBo query = new SysDictTypeBo();
            query.setDictName("是否");
            query.setDictType("sys");
            when(typeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(type(1L, "sys_yes_no")));
            assertThat(service.selectDictTypeList(query)).hasSize(1);
            ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
            verify(typeMapper).selectList(wrapper.capture());
            assertThat(wrapper.getValue().getSqlSegment()).contains("dict_name LIKE").contains("dict_type LIKE")
                .contains("ORDER BY dict_id ASC");
            assertThat(service.selectDictTypeAll()).hasSize(1);
            service.selectPageDictTypeList(new SysDictTypeBo(), new PageParam());
            service.countDictTypeList(new SysDictTypeBo());
            assertNull(service.selectDictTypeById(9L));
        }

        @Test
        @DisplayName("删除：有字典数据的类型不能删除；删除后清除两种缓存；重置时清空两种缓存")
        void delete() {
            when(typeMapper.selectByIds(anyList())).thenReturn(List.of(type(1L, "sys_yes_no")));
            when(dataMapper.exists(any())).thenReturn(true);
            when(dataMapper.selectCount(any())).thenReturn(1L);
            assertThatThrownBy(() -> service.deleteDictTypeByIds(List.of(1L))).hasMessage("字典1已分配,不能删除");
            verify(typeMapper, never()).deleteByIds(anyList());

            when(dataMapper.exists(any())).thenReturn(false);
            when(dataMapper.selectCount(any())).thenReturn(0L);
            service.deleteDictTypeByIds(List.of(1L));
            verify(typeMapper).deleteByIds(anyList());
            cache.verify(() -> CacheUtils.evict(CacheNames.SYS_DICT, "sys_yes_no"));
            cache.verify(() -> CacheUtils.evict(CacheNames.SYS_DICT_TYPE, "sys_yes_no"));

            service.resetDictCache();
            cache.verify(() -> CacheUtils.clear(CacheNames.SYS_DICT));
            cache.verify(() -> CacheUtils.clear(CacheNames.SYS_DICT_TYPE));
        }

        @Test
        @DisplayName("新增：缓存该类型的空字典数据；写入失败时报错")
        void insert() throws Exception {
            when(typeMapper.insert(any(SysDictType.class))).thenReturn(1);
            SysDictTypeBo bo = new SysDictTypeBo();
            bo.setDictName("新字典");
            bo.setDictType("biz_new");
            assertThat(service.insertDictType(bo)).isEmpty();
            CachePut put = SysDictTypeServiceImpl.class.getMethod("insertDictType", SysDictTypeBo.class).getAnnotation(CachePut.class);
            assertThat(put.cacheNames()).containsExactly(CacheNames.SYS_DICT);
            assertEquals("#bo.dictType", put.key());
            when(typeMapper.insert(any(SysDictType.class))).thenReturn(0);
            assertThatThrownBy(() -> service.insertDictType(bo)).hasMessage("新增字典'新字典'失败");
        }

        @Test
        @DisplayName("修改：改了类型编码时把字典数据改到新类型，清除旧类型的缓存，返回新类型的数据")
        void update() {
            when(typeMapper.selectById(1L)).thenReturn(type(1L, "sys_old"));
            when(typeMapper.updateById(any(SysDictType.class))).thenReturn(1);
            doReturn(List.of(data(1L, "sys_new", "1"))).when(dataMapper).selectDictDataByType("sys_new");
            SysDictTypeBo bo = new SysDictTypeBo();
            bo.setDictId(1L);
            bo.setDictName("字典");
            bo.setDictType("sys_new");
            assertThat(service.updateDictType(bo)).extracting(SysDictDataVo::getDictType).containsExactly("sys_new");
            ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
            verify(dataMapper).update(wrapper.capture());
            assertThat(wrapper.getValue().getSqlSet()).contains("dict_type");
            assertThat(wrapper.getValue().getSqlSegment()).contains("dict_type =");
            cache.verify(() -> CacheUtils.evict(CacheNames.SYS_DICT, "sys_old"));

            when(typeMapper.updateById(any(SysDictType.class))).thenReturn(0);
            assertThatThrownBy(() -> service.updateDictType(bo)).hasMessage("修改字典'字典'失败，字典不存在");
        }

        @Test
        @DisplayName("修改：同时清除新旧两个类型的字典类型缓存")
        void updateEvictsTypeCache() {
            // 现有实现不满足：只清除旧类型的字典数据缓存，字典类型缓存（按类型查询）新旧两个键都不清
            when(typeMapper.selectById(1L)).thenReturn(type(1L, "sys_old"));
            when(typeMapper.updateById(any(SysDictType.class))).thenReturn(1);
            SysDictTypeBo bo = new SysDictTypeBo();
            bo.setDictId(1L);
            bo.setDictName("字典");
            bo.setDictType("sys_new");
            service.updateDictType(bo);
            cache.verify(() -> CacheUtils.evict(CacheNames.SYS_DICT_TYPE, "sys_old"));
            cache.verify(() -> CacheUtils.evict(CacheNames.SYS_DICT_TYPE, "sys_new"));
        }

        @Test
        @DisplayName("修改不存在的字典类型：提示不存在，不改动字典数据")
        void updateMissing() {
            // 现有实现不满足：读取原类型为 null 时空指针（500）
            SysDictTypeBo bo = new SysDictTypeBo();
            bo.setDictId(9L);
            bo.setDictName("字典");
            bo.setDictType("sys_x");
            assertThatThrownBy(() -> service.updateDictType(bo)).isInstanceOf(ServiceException.class)
                .hasMessage("修改字典'字典'失败，字典不存在");
            verify(dataMapper, never()).update(any(Wrapper.class));
        }

        @Test
        @DisplayName("类型唯一：存在其他同类型编码时不唯一")
        void unique() {
            when(typeMapper.exists(any())).thenReturn(true);
            when(typeMapper.selectCount(any())).thenReturn(1L);
            SysDictTypeBo bo = new SysDictTypeBo();
            bo.setDictId(1L);
            bo.setDictType("sys_yes_no");
            assertFalse(service.checkDictTypeUnique(bo));
        }
    }

    @Nested
    @DisplayName("字典数据")
    class Data {

        private final SysDictDataServiceImpl service = new SysDictDataServiceImpl(dataMapper);

        private SysDictDataBo bo(Long code, String type, String value) {
            SysDictDataBo bo = new SysDictDataBo();
            bo.setDictCode(code);
            bo.setDictType(type);
            bo.setDictValue(value);
            bo.setDictLabel("标签" + value);
            return bo;
        }

        @Test
        @DisplayName("列表条件：排序号、标签、类型；按排序号与编码升序")
        void queries() {
            SysDictDataBo query = new SysDictDataBo();
            query.setDictSort(1);
            query.setDictLabel("是");
            query.setDictType("sys_yes_no");
            service.selectDictDataList(query);
            ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
            verify(dataMapper).selectList(wrapper.capture());
            assertThat(wrapper.getValue().getSqlSegment()).contains("dict_sort =").contains("dict_label LIKE")
                .contains("dict_type =").contains("ORDER BY dict_sort ASC,dict_code ASC");
            service.selectPageDictDataList(new SysDictDataBo(), new PageParam());
            service.countDictDataList(new SysDictDataBo());
            when(dataMapper.selectById(1L)).thenReturn(data(1L, "t", "v"));
            assertEquals("v", service.selectDictDataById(1L).getDictValue());
        }

        @Test
        @DisplayName("新增、修改：返回并缓存该类型的全部数据；失败时报错")
        void writes() throws Exception {
            when(dataMapper.insert(any(SysDictData.class))).thenReturn(1);
            doReturn(new ArrayList<>(List.of(data(1L, "t", "v")))).when(dataMapper).selectDictDataByType("t");
            assertThat(service.insertDictData(bo(null, "t", "v"))).hasSize(1);
            CachePut put = SysDictDataServiceImpl.class.getMethod("insertDictData", SysDictDataBo.class).getAnnotation(CachePut.class);
            assertThat(put.cacheNames()).containsExactly(CacheNames.SYS_DICT);
            assertEquals("#bo.dictType", put.key());
            when(dataMapper.insert(any(SysDictData.class))).thenReturn(0);
            assertThatThrownBy(() -> service.insertDictData(bo(null, "t", "v"))).hasMessage("新增字典数据'标签v'失败");

            when(dataMapper.selectById(1L)).thenReturn(data(1L, "t", "v"));
            when(dataMapper.updateById(any(SysDictData.class))).thenReturn(1);
            assertThat(service.updateDictData(bo(1L, "t", "v"))).hasSize(1);
            when(dataMapper.updateById(any(SysDictData.class))).thenReturn(0);
            assertThatThrownBy(() -> service.updateDictData(bo(1L, "t", "v"))).hasMessage("修改字典数据'标签v'失败，字典数据不存在");
        }

        @Test
        @DisplayName("修改时改了类型：清除原类型的缓存")
        void updateMovesType() {
            // 现有实现不满足：只更新新类型的缓存，原类型的缓存中仍有这条数据
            when(dataMapper.selectById(1L)).thenReturn(data(1L, "t_old", "v"));
            when(dataMapper.updateById(any(SysDictData.class))).thenReturn(1);
            service.updateDictData(bo(1L, "t_new", "v"));
            cache.verify(() -> CacheUtils.evict(CacheNames.SYS_DICT, "t_old"));
        }

        @Test
        @DisplayName("请求中的创建部门不写入（审计字段由框架填充）")
        void createDeptIgnored() {
            // 现有实现不满足：Bo 中的 createDept 原样写入实体
            when(dataMapper.insert(any(SysDictData.class))).thenReturn(1);
            SysDictDataBo bo = bo(null, "t", "v");
            bo.setCreateDept(5L);
            service.insertDictData(bo);
            ArgumentCaptor<SysDictData> saved = ArgumentCaptor.forClass(SysDictData.class);
            verify(dataMapper).insert(saved.capture());
            assertNull(saved.getValue().getCreateDept());
        }

        @Test
        @DisplayName("删除：清除被删数据所属类型的缓存；唯一性按类型与键值")
        void deleteAndUnique() {
            when(dataMapper.selectByIds(anyList())).thenReturn(List.of(data(1L, "t", "v")));
            service.deleteDictDataByIds(List.of(1L));
            verify(dataMapper).deleteByIds(anyList());
            cache.verify(() -> CacheUtils.evict(CacheNames.SYS_DICT, "t"));

            when(dataMapper.exists(any())).thenReturn(true);
            when(dataMapper.selectCount(any())).thenReturn(1L);
            assertFalse(service.checkDictDataUnique(bo(1L, "t", "v")));
        }
    }
}
