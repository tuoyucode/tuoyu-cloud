package org.tuoyu.system.listener;

import cn.hutool.core.lang.tree.Tree;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.fesod.sheet.metadata.data.ReadCellData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.TreeBuildUtils;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.vo.SysDeptVo;
import org.tuoyu.system.domain.vo.SysUserImportVo;
import org.tuoyu.system.domain.vo.SysUserVo;
import org.tuoyu.system.event.OnlineUserCleanEvent;
import org.tuoyu.system.service.ISysConfigService;
import org.tuoyu.system.service.ISysDeptService;
import org.tuoyu.system.service.ISysRoleService;
import org.tuoyu.system.service.ISysUserService;
import org.tuoyu.system.support.CurrentUserStub;
import org.tuoyu.system.support.SystemTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户导入、部门名称转换与在线会话清理
 */
@DisplayName("用户导入与 Excel 部门转换契约")
class IdentityListenersContractTest {

    static {
        SystemTestSupport.init();
    }

    private final ISysUserService userService = mock(ISysUserService.class);
    private final ISysConfigService configService = mock(ISysConfigService.class);
    private final ISysDeptService deptService = mock(ISysDeptService.class);
    private final PasswordHasher hasher = new PasswordHasher(4);
    private final CurrentUserStub login = CurrentUserStub.install().userId(7L);

    /**
     * 容器中某个类型只保留给定的一个 Bean（其他测试可能登记过同类型的对象）
     */
    private static <T> void only(Class<T> type, T bean) {
        DefaultListableBeanFactory factory = ((GenericApplicationContext) SpringUtils.context()).getDefaultListableBeanFactory();
        for (String name : factory.getBeanNamesForType(type)) {
            factory.destroySingleton(name);
        }
        SystemTestSupport.bean(bean);
    }

    @BeforeEach
    void setUp() {
        when(configService.selectConfigByKey("sys.user.initPassword")).thenReturn("Init@123");
        only(ISysUserService.class, userService);
        only(ISysConfigService.class, configService);
        only(ISysDeptService.class, deptService);
        only(PasswordHasher.class, hasher);
        only(Validator.class, Validation.buildDefaultValidatorFactory().getValidator());
    }

    @AfterEach
    void tearDown() {
        login.close();
    }

    private static SysUserImportVo row(String userName) {
        SysUserImportVo vo = new SysUserImportVo();
        vo.setUserName(userName);
        vo.setNickName("昵称");
        return vo;
    }

    @Test
    @DisplayName("新账号：校验后用初始密码的哈希新增；全部成功时返回成功明细")
    void insertNew() {
        SysUserImportListener listener = new SysUserImportListener(false);
        listener.invoke(row("bob"), null);
        ArgumentCaptor<SysUserBo> saved = ArgumentCaptor.forClass(SysUserBo.class);
        verify(userService).insertUser(saved.capture());
        assertEquals("bob", saved.getValue().getUserName());
        assertTrue(hasher.matches("Init@123", saved.getValue().getPassword()));
        assertEquals("恭喜您，数据已全部导入成功！共 1 条，数据如下：\n1、账号 bob 导入成功", listener.getExcelResult().getAnalysis());
    }

    @Test
    @DisplayName("已存在的账号：不允许更新时记为失败；允许更新时先检查再按 ID 修改")
    void existing() {
        SysUserVo existing = new SysUserVo();
        existing.setUserId(100L);
        existing.setUserName("alice");
        when(userService.selectUserByUserName("alice")).thenReturn(existing);

        SysUserImportListener noUpdate = new SysUserImportListener(false);
        noUpdate.invoke(row("alice"), null);
        assertThatThrownBy(() -> noUpdate.getExcelResult().getAnalysis()).isInstanceOf(ServiceException.class)
            .hasMessage("很抱歉，导入失败！共 1 条数据格式不正确，错误如下：\n1、账号 alice 已存在");

        SysUserImportListener update = new SysUserImportListener(true);
        update.invoke(row("alice"), null);
        verify(userService).checkUserAllowed(100L);
        verify(userService).checkUserDataScope(100L);
        ArgumentCaptor<SysUserBo> saved = ArgumentCaptor.forClass(SysUserBo.class);
        verify(userService).updateUser(saved.capture());
        assertEquals(100L, saved.getValue().getUserId());
        assertNull(saved.getValue().getPassword());
    }

    @Test
    @DisplayName("校验失败与业务异常：失败明细中带校验提示与业务提示")
    void businessFailures() {
        SysUserImportListener listener = new SysUserImportListener(false);
        listener.invoke(row("x"), null);
        doThrow(new ServiceException("没有权限访问岗位的数据")).when(userService).insertUser(any());
        listener.invoke(row("carol"), null);
        assertThatThrownBy(() -> listener.getExcelResult().getAnalysis())
            .hasMessageContaining("共 2 条")
            .hasMessageContaining("1、账号 x 导入失败：用户账号长度必须在2到30个字符之间")
            .hasMessageContaining("2、账号 carol 导入失败：没有权限访问岗位的数据");
    }

    @Test
    @DisplayName("意外异常不把原始消息（可能含 SQL）写进失败明细")
    void unexpectedFailureHidden() {
        doThrow(new RuntimeException("### SQL: insert into sys_user (user_id) values (?)")).when(userService).insertUser(any());
        SysUserImportListener listener = new SysUserImportListener(false);
        listener.invoke(row("dave"), null);
        assertThatThrownBy(() -> listener.getExcelResult().getAnalysis())
            .hasMessageContaining("1、账号 dave 导入失败")
            .satisfies(e -> assertThat(e.getMessage()).doesNotContain("insert into").doesNotContain("SQL"));
    }

    /**
     * 部门树：每棵都含“总部”（10），另加一个指定的顶级部门
     */
    private List<Tree<Long>> trees(Long id, String name) {
        SysDeptVo head = new SysDeptVo();
        head.setDeptId(10L);
        head.setParentId(0L);
        head.setDeptName("总部");
        head.setOrderNum(1);
        SysDeptVo dept = new SysDeptVo();
        dept.setDeptId(id);
        dept.setParentId(0L);
        dept.setDeptName(name);
        dept.setOrderNum(2);
        List<SysDeptVo> list = id.equals(10L) ? List.of(head) : List.of(head, dept);
        return TreeBuildUtils.buildMultiRoot(list, SysDeptVo::getDeptId, SysDeptVo::getParentId,
            (node, tree) -> tree.setId(node.getDeptId()).setParentId(node.getParentId()).setName(node.getDeptName()).setWeight(node.getOrderNum()));
    }

    @Test
    @DisplayName("Excel 部门：完整路径与部门 ID 互转；不存在的部门报错，空白为 null")
    void deptConverter() {
        login.userId(301L);
        when(deptService.selectDeptTreeList(any())).thenReturn(trees(10L, "总部"));
        DeptExcelConverter converter = new DeptExcelConverter();
        assertEquals(Long.class, converter.supportJavaTypeKey());
        assertEquals(10L, converter.convertToJavaData(new ReadCellData<>("总部"), null, null));
        assertEquals("总部", converter.convertToExcelData(10L, null, null).getStringValue());
        assertEquals("", converter.convertToExcelData(null, null, null).getStringValue());
        assertNull(converter.convertToJavaData(new ReadCellData<>(" "), null, null));
        assertThatThrownBy(() -> converter.convertToJavaData(new ReadCellData<>("无此部门"), null, null))
            .isInstanceOf(ServiceException.class).hasMessage("部门不存在：无此部门");
        assertThat(new DeptExcelOptions().getOptions()).containsExactly("总部");
    }

    @Test
    @DisplayName("Excel 部门映射按租户与用户分别缓存（各自的部门树受数据权限与租户限制）")
    void deptConverterCachePerUser() {
        DeptExcelConverter converter = new DeptExcelConverter();
        login.userId(401L);
        when(deptService.selectDeptTreeList(any())).thenReturn(trees(11L, "甲公司"));
        assertEquals(11L, converter.convertToJavaData(new ReadCellData<>("甲公司"), null, null));

        login.userId(402L);
        when(deptService.selectDeptTreeList(any())).thenReturn(trees(21L, "乙公司"));
        assertEquals(21L, converter.convertToJavaData(new ReadCellData<>("乙公司"), null, null));
        assertEquals("", converter.convertToExcelData(11L, null, null).getStringValue());
    }

    @Test
    @DisplayName("在线会话清理：事务提交后异步执行，按事件中的角色、用户、租户分别清理")
    void onlineClean() throws Exception {
        ISysRoleService roleService = mock(ISysRoleService.class);
        OnlineUserCleanListener listener = new OnlineUserCleanListener(roleService);
        listener.cleanOnlineUser(OnlineUserCleanEvent.byRole(5L));
        listener.cleanOnlineUser(OnlineUserCleanEvent.byUsers(List.of(1L, 2L)));
        listener.cleanOnlineUser(OnlineUserCleanEvent.byTenant("t1"));
        listener.cleanOnlineUser(OnlineUserCleanEvent.byUsers(List.of()));
        verify(roleService).cleanOnlineUserByRole(5L);
        verify(roleService).cleanOnlineUser(List.of(1L, 2L));
        verify(roleService).cleanOnlineUserByTenant("t1");
        verify(roleService, never()).cleanOnlineUser(List.of());

        var method = OnlineUserCleanListener.class.getMethod("cleanOnlineUser", OnlineUserCleanEvent.class);
        assertThat(method.getAnnotation(Async.class)).isNotNull();
        TransactionalEventListener tx = method.getAnnotation(TransactionalEventListener.class);
        assertEquals(TransactionPhase.AFTER_COMMIT, tx.phase());
        assertTrue(tx.fallbackExecution());
    }
}
