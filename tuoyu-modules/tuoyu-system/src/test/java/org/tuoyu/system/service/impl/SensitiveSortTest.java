package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.domain.SysClient;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysPostMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.mapper.SysUserPostMapper;
import org.tuoyu.system.mapper.SysUserRoleMapper;
import org.tuoyu.system.support.SystemTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("分页排序：敏感字段不能用来排序")
class SensitiveSortTest {

    static {
        SystemTestSupport.init();
    }

    private final SysUserMapper userMapper = mock(SysUserMapper.class);

    private final SysUserServiceImpl service = new SysUserServiceImpl(userMapper, mock(SysDeptMapper.class),
        mock(SysRoleMapper.class), mock(SysPostMapper.class), mock(SysUserRoleMapper.class),
        mock(SysUserPostMapper.class), mock(TenantAccountQuota.class));

    private static PageParam sortBy(String sort) {
        PageParam param = new PageParam();
        param.setSort(List.of(sort));
        return param;
    }

    private static void assertRejected(PageParam param, Class<?> entity) {
        assertThatThrownBy(() -> param.toPage(entity))
            .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(400));
    }

    @Test
    @DisplayName("用户：口令与 Vo 中脱敏的手机号、邮箱不能排序，普通列可以")
    void userSensitiveColumns() {
        for (String property : List.of("password", "phoneNumber", "email")) {
            assertRejected(sortBy(property + ",desc"), SysUser.class);
        }
        Page<Object> page = sortBy("userName,desc").toPage(SysUser.class);
        assertThat(page.orders()).singleElement().satisfies(o -> assertThat(o.getColumn()).isEqualTo("user_name"));
    }

    @Test
    @DisplayName("客户端：客户端密钥不能排序")
    void clientSecret() {
        assertRejected(sortBy("clientSecret"), SysClient.class);
        assertThat(sortBy("clientKey").toPage(SysClient.class).orders()).hasSize(1);
    }

    @Test
    @DisplayName("选择用户（只要求登录）：只能按用户 ID、账号、昵称排序")
    void userOptionsUseExplicitWhitelist() {
        when(userMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.selectUserOptionPage(new SysUserBo(), sortBy("deptId,desc")))
            .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getCode()).isEqualTo(400));
        assertThatThrownBy(() -> service.selectUserOptionPage(new SysUserBo(), sortBy("phoneNumber")))
            .isInstanceOf(ServiceException.class);

        assertThat(service.selectUserOptionPage(new SysUserBo(), sortBy("nickName,desc")).getItems()).isEmpty();
        ArgumentCaptor<Page<SysUser>> captor = ArgumentCaptor.captor();
        verify(userMapper).selectPage(captor.capture(), any());
        assertThat(captor.getValue().orders()).singleElement()
            .satisfies(o -> assertThat(o.getColumn() + (o.isAsc() ? " asc" : " desc")).isEqualTo("nick_name desc"));
    }

    @Test
    @DisplayName("非法排序在查询前就拒绝")
    void rejectBeforeQuery() {
        SysUserMapper untouched = mock(SysUserMapper.class);
        SysUserServiceImpl other = new SysUserServiceImpl(untouched, mock(SysDeptMapper.class),
            mock(SysRoleMapper.class), mock(SysPostMapper.class), mock(SysUserRoleMapper.class),
            mock(SysUserPostMapper.class), mock(TenantAccountQuota.class));
        assertThatThrownBy(() -> other.selectUserOptionPage(new SysUserBo(), sortBy("email")))
            .isInstanceOf(ServiceException.class);
        verifyNoInteractions(untouched);
    }

}
