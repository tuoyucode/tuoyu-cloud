package org.tuoyu.system.runner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.mapper.SysUserMapper;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("管理员初始口令：种子数据中的公开口令在启动时改为配置的口令，未配置时生产环境拒绝启动")
class AdminPasswordGuardTest {

    private final PasswordHasher hasher = new PasswordHasher(PasswordHasher.MIN_COST);

    private SysUserMapper userMapper;

    private AdminPasswordGuard guard;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        guard = new AdminPasswordGuard(userMapper, hasher, null);
    }

    private void adminPassword(String hash) {
        SysUser admin = new SysUser();
        admin.setUserId(SystemConstants.SUPER_ADMIN_USER_ID);
        admin.setUserName("admin");
        admin.setPassword(hash);
        when(userMapper.selectById(SystemConstants.SUPER_ADMIN_USER_ID)).thenReturn(admin);
    }

    @Test
    @DisplayName("仍是种子口令且配置了初始口令：改为配置的口令（只改口令）")
    void replacesSeedPassword() {
        adminPassword(AdminPasswordGuard.SEED_HASH);

        guard.check("Init#2026pass", true);

        ArgumentCaptor<SysUser> update = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(update.capture());
        assertThat(update.getValue().getUserId()).isEqualTo(SystemConstants.SUPER_ADMIN_USER_ID);
        assertThat(update.getValue().getUserName()).isNull();
        assertThat(hasher.matches("Init#2026pass", update.getValue().getPassword())).isTrue();
    }

    @Test
    @DisplayName("修改口令时跳过数据权限：启动线程中没有登录用户，updateById 带有数据权限")
    void updatesWithoutDataPermission() {
        adminPassword(AdminPasswordGuard.SEED_HASH);
        AtomicBoolean ignoring = new AtomicBoolean();
        when(userMapper.updateById(any(SysUser.class))).thenAnswer(invocation -> {
            ignoring.set(DataPermissionHelper.isIgnoring());
            return 1;
        });

        guard.check("Init#2026pass", true);

        assertThat(ignoring).isTrue();
        assertThat(DataPermissionHelper.isIgnoring()).isFalse();
    }

    @Test
    @DisplayName("口令已经改过：不做任何改动，配置的口令不会覆盖")
    void keepsChangedPassword() {
        adminPassword(hasher.hash("changed-by-admin"));

        guard.check("Init#2026pass", true);

        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("没有 admin（已删除）时不处理")
    void noAdmin() {
        assertThatCode(() -> guard.check("", true)).doesNotThrowAnyException();
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("仍是种子口令且没有配置：生产模式拒绝启动，开发模式只警告")
    void missingConfiguration() {
        adminPassword(AdminPasswordGuard.SEED_HASH);

        assertThatThrownBy(() -> guard.check("", true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TUOYU_ADMIN_INITIAL_PASSWORD");
        assertThatCode(() -> guard.check(" ", false)).doesNotThrowAnyException();
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("配置的口令少于 8 位时拒绝启动（任何环境）")
    void rejectsShortPassword() {
        adminPassword(AdminPasswordGuard.SEED_HASH);

        assertThatThrownBy(() -> guard.check("short", false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("至少 8 位");
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

}
