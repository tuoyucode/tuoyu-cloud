package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.common.redis.utils.CacheUtils;
import org.tuoyu.system.domain.SysConfig;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.domain.SysDictData;
import org.tuoyu.system.domain.SysDictType;
import org.tuoyu.system.domain.SysPost;
import org.tuoyu.system.domain.bo.SysConfigBo;
import org.tuoyu.system.domain.bo.SysDictDataBo;
import org.tuoyu.system.domain.bo.SysDictTypeBo;
import org.tuoyu.system.domain.bo.SysPostBo;
import org.tuoyu.system.domain.vo.SysPostVo;
import org.tuoyu.system.mapper.SysConfigMapper;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysDictDataMapper;
import org.tuoyu.system.mapper.SysDictTypeMapper;
import org.tuoyu.system.mapper.SysPostMapper;
import org.tuoyu.system.mapper.SysUserPostMapper;
import org.tuoyu.system.support.SystemTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("岗位、字典、参数：业务规则")
class SysBasicDataServiceTest {

    static {
        SystemTestSupport.init();
    }

    private final MockedStatic<CacheUtils> cache = mockStatic(CacheUtils.class);

    @AfterEach
    void closeStatic() {
        cache.close();
    }

    @Nested
    @DisplayName("岗位")
    class Posts {

        private final SysPostMapper postMapper = SystemTestSupport.mapper(SysPostMapper.class);
        private final SysDeptMapper deptMapper = SystemTestSupport.mapper(SysDeptMapper.class);
        private final SysUserPostMapper userPostMapper = SystemTestSupport.mapper(SysUserPostMapper.class);
        private final SysPostServiceImpl service = new SysPostServiceImpl(postMapper, deptMapper, userPostMapper);

        private SysPost post(Long id) {
            SysPost post = new SysPost();
            post.setPostId(id);
            post.setPostName("岗位" + id);
            post.setPostCode("P" + id);
            post.setDeptId(10L);
            return post;
        }

        @Test
        @DisplayName("列表：指定部门时按部门过滤，指定所属部门时包含子部门")
        void list() {
            when(postMapper.selectList(any(Wrapper.class))).thenReturn(List.of(post(1L)));
            SysPostBo byDept = new SysPostBo();
            byDept.setDeptId(10L);
            assertEquals(1, service.selectPostList(byDept).size());

            SysDept child = new SysDept();
            child.setDeptId(11L);
            when(deptMapper.selectList(any(Wrapper.class))).thenReturn(List.of(child));
            SysPostBo belong = new SysPostBo();
            belong.setBelongDeptId(10L);
            belong.setPostName("经理");
            service.selectPostList(belong);

            ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
            verify(postMapper, org.mockito.Mockito.times(2)).selectList(wrapper.capture());
            assertThat(wrapper.getAllValues().get(0).getSqlSegment()).contains("dept_id =");
            assertThat(wrapper.getAllValues().get(1).getSqlSegment()).contains("dept_id IN").contains("post_name LIKE");
        }

        @Test
        @DisplayName("分页、统计、全部、详情、用户的岗位、下拉选项")
        void queries() {
            when(postMapper.selectList(any(IPage.class), any())).thenReturn(List.of(post(1L)));
            assertEquals(1, service.selectPagePostList(new SysPostBo(), new PageQuery()).getRows().size());
            service.countPostList(new SysPostBo());
            when(postMapper.selectList(any(Wrapper.class))).thenReturn(List.of(post(1L), post(2L)));
            assertEquals(2, service.selectPostAll().size());
            assertEquals(2, service.selectPostByIds(List.of(1L, 2L)).size());
            when(postMapper.selectById(1L)).thenReturn(post(1L));
            assertEquals("P1", service.selectPostById(1L).getPostCode());
            SysPostVo vo = new SysPostVo();
            vo.setPostId(3L);
            doReturn(List.of(vo)).when(postMapper).selectJoinList(eq(SysPostVo.class), any());
            assertEquals(List.of(3L), service.selectPostListByUserId(100L));
            assertEquals(1, service.selectPostsByUserId(100L).size());
        }

        @Test
        @DisplayName("名称（同部门内）与编码唯一性；按部门、用户统计")
        void checks() {
            SysPostBo bo = new SysPostBo();
            bo.setPostName("经理");
            bo.setPostCode("PM");
            bo.setDeptId(10L);
            when(postMapper.exists(any())).thenReturn(false);
            assertTrue(service.checkPostNameUnique(bo));
            assertTrue(service.checkPostCodeUnique(bo));
            when(postMapper.exists(any())).thenReturn(true);
            assertFalse(service.checkPostNameUnique(bo));
            when(postMapper.selectCount(any())).thenReturn(4L);
            assertEquals(4L, service.countPostByDeptId(10L));
        }

        @Test
        @DisplayName("删除：已分配给用户的岗位不能删除")
        void delete() {
            when(postMapper.selectByIds(List.of(1L))).thenReturn(List.of(post(1L)));
            when(userPostMapper.selectCount(any())).thenReturn(2L);
            ServiceException ex = assertThrows(ServiceException.class, () -> service.deletePostByIds(List.of(1L)));
            assertTrue(ex.getMessage().contains("岗位1"));
            verify(postMapper, never()).deleteByIds(any());

            when(userPostMapper.selectCount(any())).thenReturn(0L);
            when(postMapper.deleteByIds(List.of(1L))).thenReturn(1);
            assertEquals(1, service.deletePostByIds(List.of(1L)));
            when(postMapper.deleteById(1L)).thenReturn(1);
            assertEquals(1, service.deletePostById(1L));
        }

        @Test
        @DisplayName("新增、修改")
        void writes() {
            when(postMapper.insert(any(SysPost.class))).thenReturn(1);
            when(postMapper.updateById(any(SysPost.class))).thenReturn(1);
            SysPostBo bo = new SysPostBo();
            bo.setPostName("经理");
            assertEquals(1, service.insertPost(bo));
            assertEquals(1, service.updatePost(bo));
            ArgumentCaptor<SysPost> saved = ArgumentCaptor.forClass(SysPost.class);
            verify(postMapper).insert(saved.capture());
            assertEquals("经理", saved.getValue().getPostName());
        }
    }

    @Nested
    @DisplayName("字典类型")
    class DictTypes {

        private final SysDictTypeMapper typeMapper = SystemTestSupport.mapper(SysDictTypeMapper.class);
        private final SysDictDataMapper dataMapper = SystemTestSupport.mapper(SysDictDataMapper.class);
        private final SysDictTypeServiceImpl service = new SysDictTypeServiceImpl(typeMapper, dataMapper);

        private SysDictType type(Long id, String code) {
            SysDictType type = new SysDictType();
            type.setDictId(id);
            type.setDictType(code);
            type.setDictName("字典" + id);
            return type;
        }

        @Test
        @DisplayName("查询：分页、列表、统计、全部、按类型、按 ID；没有数据时返回空列表")
        void queries() {
            when(typeMapper.selectList(any(IPage.class), any())).thenReturn(List.of(type(1L, "sys_yes_no")));
            SysDictTypeBo bo = new SysDictTypeBo();
            bo.setDictName("是否");
            assertEquals(1, service.selectPageDictTypeList(bo, new PageQuery()).getRows().size());
            service.countDictTypeList(bo);
            when(typeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(type(1L, "sys_yes_no")));
            assertEquals(1, service.selectDictTypeList(bo).size());
            assertEquals(1, service.selectDictTypeAll().size());
            when(typeMapper.selectById(1L)).thenReturn(type(1L, "sys_yes_no"));
            assertEquals("sys_yes_no", service.selectDictTypeById(1L).getDictType());
            when(typeMapper.selectOne(any(), anyBoolean())).thenReturn(type(1L, "sys_yes_no"));
            assertEquals("字典1", service.selectDictTypeByType("sys_yes_no").getDictName());

            assertTrue(service.selectDictDataByType("none").isEmpty());
            SysDictData data = new SysDictData();
            data.setDictValue("Y");
            when(dataMapper.selectList(any(Wrapper.class))).thenReturn(List.of(data));
            assertEquals("Y", service.selectDictDataByType("sys_yes_no").get(0).getDictValue());
        }

        @Test
        @DisplayName("删除：已有字典数据的类型不能删除；删除后清理两种缓存")
        void delete() {
            when(typeMapper.selectByIds(List.of(1L))).thenReturn(List.of(type(1L, "sys_yes_no")));
            when(dataMapper.exists(any())).thenReturn(true);
            assertThrows(ServiceException.class, () -> service.deleteDictTypeByIds(List.of(1L)));
            verify(typeMapper, never()).deleteByIds(any());

            when(dataMapper.exists(any())).thenReturn(false);
            service.deleteDictTypeByIds(List.of(1L));
            verify(typeMapper).deleteByIds(List.of(1L));
            cache.verify(() -> CacheUtils.evict(CacheNames.SYS_DICT, "sys_yes_no"));
            cache.verify(() -> CacheUtils.evict(CacheNames.SYS_DICT_TYPE, "sys_yes_no"));

            service.resetDictCache();
            cache.verify(() -> CacheUtils.clear(CacheNames.SYS_DICT));
        }

        @Test
        @DisplayName("新增与修改：修改类型编码时同步改写字典数据的类型；写入失败时报错")
        void writes() {
            SysDictTypeBo bo = new SysDictTypeBo();
            bo.setDictId(1L);
            bo.setDictType("sys_new");
            when(typeMapper.insert(any(SysDictType.class))).thenReturn(1);
            assertTrue(service.insertDictType(bo).isEmpty());
            when(typeMapper.insert(any(SysDictType.class))).thenReturn(0);
            assertThrows(ServiceException.class, () -> service.insertDictType(bo));

            when(typeMapper.selectById(1L)).thenReturn(type(1L, "sys_old"));
            when(typeMapper.updateById(any(SysDictType.class))).thenReturn(1);
            service.updateDictType(bo);
            ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
            verify(dataMapper).update(wrapper.capture());
            assertThat(wrapper.getValue().getSqlSet()).contains("dict_type");
            cache.verify(() -> CacheUtils.evict(CacheNames.SYS_DICT, "sys_old"));

            when(typeMapper.updateById(any(SysDictType.class))).thenReturn(0);
            assertThrows(ServiceException.class, () -> service.updateDictType(bo));

            when(typeMapper.exists(any())).thenReturn(true);
            assertFalse(service.checkDictTypeUnique(bo));
        }
    }

    @Nested
    @DisplayName("字典数据")
    class DictData {

        private final SysDictDataMapper dataMapper = SystemTestSupport.mapper(SysDictDataMapper.class);
        private final SysDictDataServiceImpl service = new SysDictDataServiceImpl(dataMapper);

        private SysDictData data(Long code, String value) {
            SysDictData data = new SysDictData();
            data.setDictCode(code);
            data.setDictType("sys_yes_no");
            data.setDictValue(value);
            data.setDictLabel("标签" + value);
            return data;
        }

        @Test
        @DisplayName("查询：分页、列表、统计、标签、详情")
        void queries() {
            when(dataMapper.selectList(any(IPage.class), any())).thenReturn(List.of(data(1L, "Y")));
            SysDictDataBo bo = new SysDictDataBo();
            bo.setDictType("sys_yes_no");
            bo.setDictLabel("是");
            assertEquals(1, service.selectPageDictDataList(bo, new PageQuery()).getRows().size());
            service.countDictDataList(bo);
            when(dataMapper.selectList(any(Wrapper.class))).thenReturn(List.of(data(1L, "Y")));
            assertEquals(1, service.selectDictDataList(bo).size());
            when(dataMapper.selectOne(any(Wrapper.class))).thenReturn(data(1L, "Y"));
            assertEquals("标签Y", service.selectDictLabel("sys_yes_no", "Y"));
            when(dataMapper.selectById(1L)).thenReturn(data(1L, "Y"));
            assertEquals("Y", service.selectDictDataById(1L).getDictValue());
        }

        @Test
        @DisplayName("增删改后刷新该类型的缓存；写入失败时报错")
        void writes() {
            SysDictDataBo bo = new SysDictDataBo();
            bo.setDictType("sys_yes_no");
            bo.setDictValue("Y");
            when(dataMapper.insert(any(SysDictData.class))).thenReturn(1);
            when(dataMapper.updateById(any(SysDictData.class))).thenReturn(1);
            when(dataMapper.selectList(any(Wrapper.class))).thenReturn(List.of(data(1L, "Y")));
            assertEquals(1, service.insertDictData(bo).size());
            assertEquals(1, service.updateDictData(bo).size());
            when(dataMapper.insert(any(SysDictData.class))).thenReturn(0);
            when(dataMapper.updateById(any(SysDictData.class))).thenReturn(0);
            assertThrows(ServiceException.class, () -> service.insertDictData(bo));
            assertThrows(ServiceException.class, () -> service.updateDictData(bo));

            when(dataMapper.selectByIds(List.of(1L))).thenReturn(List.of(data(1L, "Y")));
            service.deleteDictDataByIds(List.of(1L));
            verify(dataMapper).deleteByIds(List.of(1L));
            cache.verify(() -> CacheUtils.evict(CacheNames.SYS_DICT, "sys_yes_no"));

            when(dataMapper.exists(any())).thenReturn(false);
            assertTrue(service.checkDictDataUnique(bo));
        }
    }

    @Nested
    @DisplayName("参数")
    class Configs {

        private final SysConfigMapper configMapper = SystemTestSupport.mapper(SysConfigMapper.class);
        private final SysConfigServiceImpl service = new SysConfigServiceImpl(configMapper);

        private SysConfig config(Long id, String key, String value, String type) {
            SysConfig config = new SysConfig();
            config.setConfigId(id);
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setConfigType(type);
            return config;
        }

        @Test
        @DisplayName("按键读取：不存在时为空串；注册开关按布尔值解析")
        void byKey() {
            assertEquals("", service.selectConfigByKey("none"));
            assertFalse(service.selectRegisterEnabled());
            when(configMapper.selectOne(any(Wrapper.class))).thenReturn(config(1L, "sys.account.registerUser", "true", "Y"));
            assertEquals("true", service.selectConfigByKey("sys.account.registerUser"));
            assertTrue(service.selectRegisterEnabled());
        }

        @Test
        @DisplayName("查询：分页、列表、统计、详情")
        void queries() {
            when(configMapper.selectList(any(IPage.class), any())).thenReturn(List.of(config(1L, "k", "v", "N")));
            SysConfigBo bo = new SysConfigBo();
            bo.setConfigName("名称");
            assertEquals(1, service.selectPageConfigList(bo, new PageQuery()).getRows().size());
            service.countConfigList(bo);
            when(configMapper.selectList(any(Wrapper.class))).thenReturn(List.of(config(1L, "k", "v", "N")));
            assertEquals(1, service.selectConfigList(bo).size());
            when(configMapper.selectById(1L)).thenReturn(config(1L, "k", "v", "N"));
            assertEquals("v", service.selectConfigById(1L).getConfigValue());
        }

        @Test
        @DisplayName("修改：改了键名时清理旧键的缓存；没有 ID 时按键名更新")
        void update() {
            SysConfigBo bo = new SysConfigBo();
            bo.setConfigId(1L);
            bo.setConfigKey("new.key");
            bo.setConfigValue("v2");
            when(configMapper.selectById(1L)).thenReturn(config(1L, "old.key", "v", "N"));
            when(configMapper.updateById(any(SysConfig.class))).thenReturn(1);
            assertEquals("v2", service.updateConfig(bo));
            cache.verify(() -> CacheUtils.evict(CacheNames.SYS_CONFIG, "old.key"));

            SysConfigBo byKey = new SysConfigBo();
            byKey.setConfigKey("sys.key");
            byKey.setConfigValue("v3");
            when(configMapper.update(any(SysConfig.class), any())).thenReturn(1);
            assertEquals("v3", service.updateConfig(byKey));
            cache.verify(() -> CacheUtils.evict(CacheNames.SYS_CONFIG, "sys.key"));

            when(configMapper.update(any(SysConfig.class), any())).thenReturn(0);
            assertThrows(ServiceException.class, () -> service.updateConfig(byKey));
        }

        @Test
        @DisplayName("新增：写入失败时报错；返回参数值供缓存")
        void insert() {
            SysConfigBo bo = new SysConfigBo();
            bo.setConfigKey("k");
            bo.setConfigValue("v");
            when(configMapper.insert(any(SysConfig.class))).thenReturn(1);
            assertEquals("v", service.insertConfig(bo));
            when(configMapper.insert(any(SysConfig.class))).thenReturn(0);
            assertThrows(ServiceException.class, () -> service.insertConfig(bo));
            when(configMapper.exists(any())).thenReturn(true);
            assertFalse(service.checkConfigKeyUnique(bo));
        }

        @Test
        @DisplayName("删除：内置参数不能删除；普通参数删除并清理缓存")
        void delete() {
            when(configMapper.selectByIds(List.of(1L))).thenReturn(List.of(config(1L, "sys.builtin", "v", SystemConstants.YES)));
            assertThrows(ServiceException.class, () -> service.deleteConfigByIds(List.of(1L)));
            verify(configMapper, never()).deleteByIds(any());

            when(configMapper.selectByIds(List.of(2L))).thenReturn(List.of(config(2L, "custom", "v", "N")));
            service.deleteConfigByIds(List.of(2L));
            verify(configMapper).deleteByIds(List.of(2L));
            cache.verify(() -> CacheUtils.evict(CacheNames.SYS_CONFIG, "custom"));
            service.resetConfigCache();
            cache.verify(() -> CacheUtils.clear(CacheNames.SYS_CONFIG));
        }
    }
}
