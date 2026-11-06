package org.tuoyu.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.model.RoleDTO;
import org.tuoyu.system.domain.SysClient;
import org.tuoyu.system.domain.SysLoginInfo;
import org.tuoyu.system.domain.SysNotice;
import org.tuoyu.system.domain.SysOperLog;
import org.tuoyu.system.domain.SysSocial;
import org.tuoyu.system.domain.SysTenantPackage;
import org.tuoyu.system.domain.SysTenantPackageMenu;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.bo.SysClientBo;
import org.tuoyu.system.domain.bo.SysLoginInfoBo;
import org.tuoyu.system.domain.bo.SysNoticeBo;
import org.tuoyu.system.domain.bo.SysOperLogBo;
import org.tuoyu.system.domain.bo.SysSocialBo;
import org.tuoyu.system.domain.bo.SysTenantPackageBo;
import org.tuoyu.system.domain.vo.SysClientVo;
import org.tuoyu.system.domain.vo.SysTenantPackageVo;
import org.tuoyu.system.mapper.SysClientMapper;
import org.tuoyu.system.mapper.SysLoginInfoMapper;
import org.tuoyu.system.mapper.SysNoticeMapper;
import org.tuoyu.system.mapper.SysOperLogMapper;
import org.tuoyu.system.mapper.SysSocialMapper;
import org.tuoyu.system.mapper.SysTenantMapper;
import org.tuoyu.system.mapper.SysTenantPackageMapper;
import org.tuoyu.system.mapper.SysTenantPackageMenuMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.service.ISysMenuService;
import org.tuoyu.system.service.ISysRoleService;
import org.tuoyu.system.service.ISysTenantService;
import org.tuoyu.system.support.SystemTestSupport;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("客户端、套餐、日志、通知、社交账号、权限：业务规则")
class SysOtherServicesTest {

    static {
        SystemTestSupport.init();
    }

    @Nested
    @DisplayName("客户端")
    class Clients {

        private final SysClientMapper mapper = SystemTestSupport.mapper(SysClientMapper.class);
        private final SysClientServiceImpl service = new SysClientServiceImpl(mapper);

        @Test
        @DisplayName("新增：客户端 ID 由 key 与 secret 生成；访问路径补全斜杠，* 视为全部路径")
        void insert() {
            when(mapper.insert(any(SysClient.class))).thenAnswer(inv -> {
                inv.<SysClient>getArgument(0).setId(5L);
                return 1;
            });
            SysClientBo bo = new SysClientBo();
            bo.setClientKey("pc");
            bo.setClientSecret("secret");
            bo.setGrantTypeList(List.of("password", "sms"));
            bo.setAccessPath("system/**, *\n/monitor/**");
            bo.setIpWhitelistList(List.of("10.0.0.1", " "));

            assertTrue(service.insertByBo(bo));

            ArgumentCaptor<SysClient> saved = ArgumentCaptor.forClass(SysClient.class);
            verify(mapper).insert(saved.capture());
            assertEquals("password,sms", saved.getValue().getGrantType());
            assertEquals("/system/**,/**,/monitor/**", saved.getValue().getAccessPath());
            assertEquals("10.0.0.1", saved.getValue().getIpWhitelist());
            assertEquals(cn.hutool.crypto.SecureUtil.md5("pcsecret"), saved.getValue().getClientId());
            assertEquals(5L, bo.getId());
        }

        @Test
        @DisplayName("修改：密钥为空表示不修改；访问路径清空时保存为空串")
        void updateKeepsSecret() {
            when(mapper.updateById(any(SysClient.class))).thenReturn(1);
            SysClientBo bo = new SysClientBo();
            bo.setId(5L);
            bo.setClientSecret("  ");
            bo.setGrantTypeList(List.of("password"));
            bo.setAccessPathList(List.of());

            assertTrue(service.updateByBo(bo));

            ArgumentCaptor<SysClient> updated = ArgumentCaptor.forClass(SysClient.class);
            verify(mapper).updateById(updated.capture());
            assertNull(updated.getValue().getClientSecret());
            assertEquals("", updated.getValue().getAccessPath());
            assertNull(updated.getValue().getIpWhitelist());
        }

        @Test
        @DisplayName("按客户端 ID 查询（登录用）保留密钥并拆分规则；列表不下发密钥")
        void queries() {
            SysClient client = new SysClient();
            client.setClientId("cid");
            client.setClientSecret("secret");
            client.setGrantType("password");
            client.setAccessPath("/system/**;/monitor/**");
            client.setIpWhitelist("10.0.0.1");
            when(mapper.selectOne(any(), anyBoolean())).thenReturn(client);
            SysClientVo vo = service.queryByClientId("cid");
            assertEquals("secret", vo.getClientSecret());
            assertEquals(List.of("/system/**", "/monitor/**"), vo.getAccessPathList());
            assertEquals(List.of("10.0.0.1"), vo.getIpWhitelistList());

            when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(client));
            List<SysClientVo> list = service.queryList(new SysClientBo());
            assertNull(list.get(0).getClientSecret());
            service.countList(new SysClientBo());
        }

        @Test
        @DisplayName("修改状态、删除、key 唯一性")
        void others() {
            when(mapper.update(any(Wrapper.class))).thenReturn(1);
            assertEquals(1, service.updateClientStatus("cid", "1"));
            when(mapper.deleteByIds(any())).thenReturn(1);
            assertTrue(service.deleteWithValidByIds(List.of(5L), true));
            when(mapper.exists(any())).thenReturn(true);
            SysClientBo bo = new SysClientBo();
            bo.setClientKey("pc");
            assertFalse(service.checkClickKeyUnique(bo));
        }
    }

    @Nested
    @DisplayName("租户套餐")
    class Packages {

        private final SysTenantPackageMapper packageMapper = SystemTestSupport.mapper(SysTenantPackageMapper.class);
        private final SysTenantPackageMenuMapper packageMenuMapper = SystemTestSupport.mapper(SysTenantPackageMenuMapper.class);
        private final SysTenantMapper tenantMapper = SystemTestSupport.mapper(SysTenantMapper.class);
        private final ISysTenantService tenantService = mock(ISysTenantService.class);
        private final SysTenantPackageServiceImpl service = new SysTenantPackageServiceImpl(packageMapper,
            packageMenuMapper, tenantMapper, tenantService);

        @Test
        @DisplayName("新增：默认正常状态，菜单去重后写入")
        void insert() {
            when(packageMapper.insert(any(SysTenantPackage.class))).thenAnswer(inv -> {
                inv.<SysTenantPackage>getArgument(0).setPackageId(7L);
                return 1;
            });
            SysTenantPackageBo bo = new SysTenantPackageBo();
            bo.setPackageName("基础版");
            bo.setMenuIds(new Long[]{10L, 10L, 11L});

            service.insertPackage(bo);

            ArgumentCaptor<SysTenantPackage> saved = ArgumentCaptor.forClass(SysTenantPackage.class);
            verify(packageMapper).insert(saved.capture());
            assertEquals(SystemConstants.NORMAL, saved.getValue().getStatus());
            ArgumentCaptor<Collection<SysTenantPackageMenu>> menus = ArgumentCaptor.captor();
            verify(packageMenuMapper).insertBatch(menus.capture());
            assertThat(menus.getValue()).extracting(SysTenantPackageMenu::getMenuId).containsExactly(10L, 11L);
            assertThat(menus.getValue()).allMatch(m -> m.getPackageId() == 7L);
        }

        @Test
        @DisplayName("修改：套餐不存在时拒绝；修改后同步使用该套餐的租户")
        void update() {
            SysTenantPackageBo bo = new SysTenantPackageBo();
            bo.setPackageId(7L);
            bo.setMenuIds(new Long[0]);
            assertThrows(ServiceException.class, () -> service.updatePackage(bo));

            when(packageMapper.selectById(7L)).thenReturn(new SysTenantPackage());
            when(tenantService.selectTenantIdsByPackage(7L)).thenReturn(List.of("acme", "beta"));
            service.updatePackage(bo);
            verify(packageMenuMapper).delete(any());
            verify(packageMenuMapper, never()).insertBatch(any());
            verify(tenantService).syncPackage("acme");
            verify(tenantService).syncPackage("beta");
        }

        @Test
        @DisplayName("删除：仍有租户使用时拒绝")
        void delete() {
            when(tenantMapper.exists(any())).thenReturn(true);
            assertThrows(ServiceException.class, () -> service.deleteByIds(List.of(7L)));
            verify(packageMapper, never()).deleteByIds(any());

            when(tenantMapper.exists(any())).thenReturn(false);
            service.deleteByIds(List.of(7L));
            verify(packageMenuMapper).delete(any());
            verify(packageMapper).deleteByIds(List.of(7L));
        }

        @Test
        @DisplayName("查询：详情带出菜单，分页与下拉")
        void queries() {
            SysTenantPackage pkg = new SysTenantPackage();
            pkg.setPackageId(7L);
            pkg.setPackageName("基础版");
            when(packageMapper.selectById(7L)).thenReturn(pkg);
            when(packageMenuMapper.selectObjs(any())).thenReturn(List.of(10L));
            SysTenantPackageVo vo = service.selectById(7L);
            assertEquals(List.of(10L), vo.getMenuIds());
            assertNull(service.selectById(8L));

            when(packageMapper.selectList(any(IPage.class), any())).thenReturn(List.of(pkg));
            assertEquals(1, service.selectPageList(new SysTenantPackageBo(), new PageQuery()).getRows().size());
            when(packageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(pkg));
            assertEquals(1, service.selectOptions().size());
        }
    }

    @Nested
    @DisplayName("操作日志与登录日志")
    class Logs {

        private final SysOperLogMapper operLogMapper = SystemTestSupport.mapper(SysOperLogMapper.class);
        private final SysOperLogServiceImpl operLog = new SysOperLogServiceImpl(operLogMapper);
        private final SysLoginInfoMapper loginInfoMapper = SystemTestSupport.mapper(SysLoginInfoMapper.class);
        private final SysLoginInfoServiceImpl loginInfo = new SysLoginInfoServiceImpl(loginInfoMapper);

        @Test
        @DisplayName("操作日志：按多个业务类型查询；未指定排序时按 ID 倒序")
        void operLogQueries() {
            when(operLogMapper.selectList(any(IPage.class), any())).thenReturn(List.of(new SysOperLog()));
            SysOperLogBo bo = new SysOperLogBo();
            bo.setBusinessTypes(new Integer[]{1, 2});
            bo.setTitle("用户");
            bo.setBusinessType(3);
            assertEquals(1, operLog.selectPageOperLogList(bo, new PageQuery()).getRows().size());
            ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
            verify(operLogMapper).selectList(any(IPage.class), wrapper.capture());
            assertThat(wrapper.getValue().getSqlSegment()).contains("business_type IN").contains("ORDER BY oper_id DESC");

            when(operLogMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new SysOperLog()));
            assertEquals(1, operLog.selectOperLogList(bo).size());
            operLog.countOperLogList(bo);
            when(operLogMapper.selectById(1L)).thenReturn(new SysOperLog());
            assertTrue(operLog.selectOperLogById(1L) != null);
        }

        @Test
        @DisplayName("操作日志：写入时记录时间，删除与清空")
        void operLogWrites() {
            SysOperLogBo bo = new SysOperLogBo();
            bo.setTitle("用户管理");
            operLog.insertOperlog(bo);
            ArgumentCaptor<SysOperLog> saved = ArgumentCaptor.forClass(SysOperLog.class);
            verify(operLogMapper).insert(saved.capture());
            assertTrue(saved.getValue().getOperTime() != null);

            when(operLogMapper.deleteByIds(List.of(1L, 2L))).thenReturn(2);
            assertEquals(2, operLog.deleteOperLogByIds(new Long[]{1L, 2L}));
            operLog.cleanOperLog();
            verify(operLogMapper).delete(any());
        }

        @Test
        @DisplayName("登录日志：查询、写入、删除、清空")
        void loginInfo() {
            when(loginInfoMapper.selectList(any(IPage.class), any())).thenReturn(List.of(new SysLoginInfo()));
            SysLoginInfoBo bo = new SysLoginInfoBo();
            bo.setUserName("alice");
            assertEquals(1, loginInfo.selectPageLoginInfoList(bo, new PageQuery()).getRows().size());
            when(loginInfoMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new SysLoginInfo()));
            assertEquals(1, loginInfo.selectLoginInfoList(bo).size());
            loginInfo.countLoginInfoList(bo);

            loginInfo.insertLoginInfo(bo);
            ArgumentCaptor<SysLoginInfo> saved = ArgumentCaptor.forClass(SysLoginInfo.class);
            verify(loginInfoMapper).insert(saved.capture());
            assertEquals("alice", saved.getValue().getUserName());
            assertTrue(saved.getValue().getLoginTime() != null);

            when(loginInfoMapper.deleteByIds(List.of(1L))).thenReturn(1);
            assertEquals(1, loginInfo.deleteLoginInfoByIds(new Long[]{1L}));
            loginInfo.cleanLoginInfo();
            verify(loginInfoMapper).delete(any());
        }
    }

    @Nested
    @DisplayName("通知公告")
    class Notices {

        private final SysNoticeMapper noticeMapper = SystemTestSupport.mapper(SysNoticeMapper.class);
        private final SysUserMapper userMapper = SystemTestSupport.mapper(SysUserMapper.class);
        private final SysNoticeServiceImpl service = new SysNoticeServiceImpl(noticeMapper, userMapper);

        @Test
        @DisplayName("按创建人用户名查询时转换为用户 ID")
        void queryByCreator() {
            SysUser user = new SysUser();
            user.setUserId(100L);
            when(userMapper.selectOne(any(), anyBoolean())).thenReturn(user);
            when(noticeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new SysNotice()));
            SysNoticeBo bo = new SysNoticeBo();
            bo.setCreateByName("alice");
            bo.setNoticeTitle("维护");

            assertEquals(1, service.selectNoticeList(bo).size());

            ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
            verify(noticeMapper).selectList(wrapper.capture());
            assertThat(wrapper.getValue().getSqlSegment()).contains("create_by =").contains("notice_title LIKE");
            assertThat(((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) wrapper.getValue()).getParamNameValuePairs()).containsValue(100L);
        }

        @Test
        @DisplayName("分页、详情、增删改")
        void crud() {
            when(noticeMapper.selectList(any(IPage.class), any())).thenReturn(List.of(new SysNotice()));
            assertEquals(1, service.selectPageNoticeList(new SysNoticeBo(), new PageQuery()).getRows().size());
            SysNotice notice = new SysNotice();
            notice.setNoticeTitle("标题");
            when(noticeMapper.selectById(1L)).thenReturn(notice);
            assertEquals("标题", service.selectNoticeById(1L).getNoticeTitle());

            when(noticeMapper.insert(any(SysNotice.class))).thenAnswer(inv -> {
                inv.<SysNotice>getArgument(0).setNoticeId(9L);
                return 1;
            });
            SysNoticeBo bo = new SysNoticeBo();
            bo.setNoticeTitle("新通知");
            assertEquals(1, service.insertNotice(bo));
            assertEquals(9L, bo.getNoticeId());
            when(noticeMapper.updateById(any(SysNotice.class))).thenReturn(1);
            assertEquals(1, service.updateNotice(bo));
            when(noticeMapper.deleteById(9L)).thenReturn(1);
            assertEquals(1, service.deleteNoticeById(9L));
            when(noticeMapper.deleteByIds(List.of(9L))).thenReturn(1);
            assertEquals(1, service.deleteNoticeByIds(new Long[]{9L}));
        }
    }

    @Nested
    @DisplayName("社交账号")
    class Socials {

        private final SysSocialMapper mapper = SystemTestSupport.mapper(SysSocialMapper.class);
        private final SysSocialServiceImpl service = new SysSocialServiceImpl(mapper);

        @Test
        @DisplayName("查询、绑定、更新、解绑")
        void crud() {
            SysSocial social = new SysSocial();
            social.setId(1L);
            social.setAuthId("gitee1");
            social.setUserId(100L);
            when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(social));
            SysSocialBo query = new SysSocialBo();
            query.setUserId(100L);
            query.setSource("gitee");
            assertEquals(1, service.queryList(query).size());
            assertEquals(1, service.queryListByUserId(100L).size());
            assertEquals("gitee1", service.selectByAuthId("gitee1").get(0).getAuthId());
            when(mapper.selectById("1")).thenReturn(social);
            assertEquals(100L, service.queryById("1").getUserId());

            when(mapper.insert(any(SysSocial.class))).thenAnswer(inv -> {
                inv.<SysSocial>getArgument(0).setId(2L);
                return 1;
            });
            SysSocialBo bo = new SysSocialBo();
            bo.setAuthId("gitee2");
            assertTrue(service.insertByBo(bo));
            assertEquals(2L, bo.getId());
            when(mapper.insert(any(SysSocial.class))).thenReturn(0);
            assertFalse(service.insertByBo(new SysSocialBo()));
            when(mapper.updateById(any(SysSocial.class))).thenReturn(1);
            assertTrue(service.updateByBo(bo));
            when(mapper.deleteById(2L)).thenReturn(1);
            assertTrue(service.deleteWithValidById(2L));
        }
    }

    @Nested
    @DisplayName("权限与脱敏")
    class Permissions {

        private final ISysRoleService roleService = mock(ISysRoleService.class);
        private final ISysMenuService menuService = mock(ISysMenuService.class);
        private final SysPermissionServiceImpl service = new SysPermissionServiceImpl(roleService, menuService);

        @Test
        @DisplayName("超级管理员拥有全部权限，其他用户按角色与菜单")
        void permissions() {
            Long admin = SystemConstants.SUPER_ADMIN_USER_ID;
            assertEquals(Set.of(SystemConstants.SUPER_ADMIN_ROLE_KEY), service.getRolePermission(admin));
            assertEquals(Set.of("*:*:*"), service.getMenuPermission(admin));
            verify(roleService, never()).selectRolePermissionByUserId(any());

            when(roleService.selectRolePermissionByUserId(100L)).thenReturn(Set.of("common"));
            when(menuService.selectMenuPermsByUserId(100L)).thenReturn(Set.of("system:user:list"));
            assertEquals(Set.of("common"), service.getRolePermission(100L));
            assertEquals(Set.of("system:user:list"), service.getMenuPermission(100L));
        }

        @Test
        @DisplayName("数据权限的角色映射：按权限码汇总角色")
        void dataScopeRoleMap() {
            assertTrue(service.getDataScopeRoleMap(List.of()).isEmpty());
            RoleDTO r1 = new RoleDTO();
            r1.setRoleId(1L);
            RoleDTO r2 = new RoleDTO();
            r2.setRoleId(2L);
            when(menuService.selectMenuPermsByRoleIds(List.of(1L, 2L))).thenReturn(Map.of(
                1L, new LinkedHashSet<>(List.of("system:user:list")),
                2L, new LinkedHashSet<>(List.of("system:user:list", "system:role:list"))));

            Map<String, List<Long>> map = service.getDataScopeRoleMap(List.of(r1, r2));

            assertThat(map.get("system:user:list")).containsExactlyInAnyOrder(1L, 2L);
            assertEquals(List.of(2L), map.get("system:role:list"));
        }

        @Test
        @DisplayName("脱敏：未登录一律脱敏；具备指定角色或权限时不脱敏；超级管理员不脱敏")
        void sensitive() {
            SysSensitiveServiceImpl sensitive = new SysSensitiveServiceImpl();
            try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class, CALLS_REAL_METHODS);
                 MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                login.when(LoginHelper::isLogin).thenReturn(false);
                assertTrue(sensitive.isSensitive(new String[]{"admin"}, new String[0]));

                login.when(LoginHelper::isLogin).thenReturn(true);
                login.when(LoginHelper::getUserId).thenReturn(100L);
                String[] roles = {"admin"};
                String[] perms = {"system:user:query"};
                stp.when(() -> StpUtil.hasRoleOr(roles)).thenReturn(true);
                stp.when(() -> StpUtil.hasPermissionOr(perms)).thenReturn(true);
                assertFalse(sensitive.isSensitive(roles, perms));
                assertFalse(sensitive.isSensitive(roles, null));
                assertFalse(sensitive.isSensitive(null, perms));

                stp.when(() -> StpUtil.hasPermissionOr(perms)).thenReturn(false);
                assertTrue(sensitive.isSensitive(roles, perms));
                assertTrue(sensitive.isSensitive(null, null));

                login.when(LoginHelper::getUserId).thenReturn(SystemConstants.SUPER_ADMIN_USER_ID);
                assertFalse(sensitive.isSensitive(null, null));
            }
        }
    }
}
