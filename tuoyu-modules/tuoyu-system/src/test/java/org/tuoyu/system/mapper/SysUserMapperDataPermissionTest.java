package org.tuoyu.system.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.tuoyu.common.core.context.AccessRequirement;
import org.tuoyu.common.core.context.CurrentRole;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.PermissionContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.aspect.DataPermissionPointcutAdvisor;
import org.tuoyu.common.mybatis.handler.DataPermissionSqlHandler;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.common.mybatis.interceptor.DataPermissionSqlInterceptor;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.service.impl.SysUserServiceImpl;
import org.tuoyu.system.service.impl.TenantAccountQuota;
import org.tuoyu.system.support.SystemTestSupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 用户的修改状态、修改资料、重置密码按条件更新（{@code update(Wrapper)}）。数据权限由 Spring 代理在调用 Mapper
 * 方法时设置，这里按运行时的结构组装：MyBatis 的 Mapper 代理（默认方法在代理内部转调）外面再包一层带数据权限切面的
 * Spring 代理，检查执行 SQL 时数据权限是否生效、改写后的 UPDATE 语句是否带上数据范围条件。
 */
@DisplayName("用户更新的数据权限：按条件更新也带数据范围条件")
class SysUserMapperDataPermissionTest {

    static {
        SystemTestSupport.init();
    }

    /**
     * 对照：只在两个参数的 update 上标注数据权限，按条件更新使用 BaseMapper 自带的默认方法
     */
    interface PlainUserMapper extends BaseMapper<SysUser> {

        @Override
        @DataPermission({@DataColumn(key = "deptName", value = "dept_id")})
        int update(SysUser entity, Wrapper<SysUser> updateWrapper);

    }

    private static final String UPDATE_SQL = "UPDATE sys_user SET status = ? WHERE user_id = ?";

    /** 执行 UPDATE 时的数据权限注解（没有时为 null） */
    private final List<DataPermission> seen = new ArrayList<>();

    /** 执行 UPDATE 时由数据权限拦截器改写后的 SQL */
    private final List<String> rewritten = new ArrayList<>();

    private DataPermissionSqlInterceptor interceptor;

    @BeforeEach
    void setUp() {
        CurrentUser user = new CurrentUser();
        user.setUserId(7L);
        user.setDeptId(103L);
        // 数据范围“本部门”
        user.setRoles(new ArrayList<>(List.of(new CurrentRole(2L, "dept", "3"))));
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("userContext", (UserContextProvider) () -> Optional.of(user));
        beans.addBean("permissionContext", (PermissionContext) () -> AccessRequirement.NONE);
        interceptor = new DataPermissionSqlInterceptor(new DataPermissionSqlHandler(
            beans.getBeanProvider(UserContextProvider.class), beans.getBeanProvider(PermissionContext.class)));
    }

    @AfterEach
    void tearDown() {
        DataPermissionHelper.removePermission();
        DataPermissionHelper.getContext().clear();
    }

    @Test
    @DisplayName("对照：BaseMapper 自带的 update(Wrapper) 在 MyBatis 代理内部转调，数据权限不生效")
    void baseMapperDefaultUpdateBypassesDataPermission() {
        PlainUserMapper mapper = springProxy(mybatisProxy(PlainUserMapper.class), PlainUserMapper.class);

        mapper.update(Wrappers.<SysUser>lambdaUpdate().eq(SysUser::getUserId, 100L));

        assertThat(seen).hasSize(1).containsOnlyNulls();
        assertThat(rewritten).containsExactly(UPDATE_SQL);
    }

    @Test
    @DisplayName("SysUserMapper 的 update(Wrapper)、update(实体, Wrapper)、updateById 执行时都带数据权限")
    void userMapperUpdatesCarryDataPermission() {
        SysUserMapper mapper = springProxy(mybatisProxy(SysUserMapper.class), SysUserMapper.class);
        SysUser entity = new SysUser();
        entity.setUserId(100L);

        mapper.update(Wrappers.<SysUser>lambdaUpdate().eq(SysUser::getUserId, 100L));
        mapper.update(entity, Wrappers.<SysUser>lambdaUpdate().eq(SysUser::getUserId, 100L));
        mapper.updateById(entity);

        assertThat(seen).hasSize(3).doesNotContainNull();
        assertThat(seen).allSatisfy(permission -> assertThat(permission.value())
            .extracting(column -> column.key()[0]).containsExactly("deptName", "userName"));
        // 调用结束后不残留
        assertThat(DataPermissionHelper.getPermission()).isNull();
    }

    @Test
    @DisplayName("数据范围为本部门时，修改状态、重置密码、修改资料的 UPDATE 都限定在本部门（其他部门的用户不会被改到）")
    void serviceUpdatesAreLimitedToOwnDept() {
        SysUserMapper mapper = springProxy(mybatisProxy(SysUserMapper.class), SysUserMapper.class);
        SysUserServiceImpl service = new SysUserServiceImpl(mapper, mock(SysDeptMapper.class), mock(SysRoleMapper.class),
            mock(SysPostMapper.class), mock(SysUserRoleMapper.class), mock(SysUserPostMapper.class),
            mock(TenantAccountQuota.class));

        service.updateUserStatus(100L, "1");
        service.resetUserPwd(100L, "hash");
        SysUserBo profile = new SysUserBo();
        profile.setUserId(100L);
        profile.setNickName("新昵称");
        service.updateUserProfile(profile);

        assertThat(rewritten).hasSize(3)
            .allSatisfy(sql -> assertThat(sql).isEqualTo("UPDATE sys_user SET status = ? WHERE user_id = ? AND (dept_id = 103)"));
    }

    /**
     * 按 MyBatis Mapper 代理的方式处理调用：默认方法在代理自身上执行（转调不经过外层 Spring 代理），
     * 抽象方法视为执行 SQL，记录此时的数据权限并让拦截器改写一条 UPDATE 语句
     */
    private <M> M mybatisProxy(Class<M> type) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, args);
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            seen.add(DataPermissionHelper.getPermission());
            rewritten.add(DataPermissionHelper.getPermission() == null ? UPDATE_SQL
                : interceptor.parserMulti(UPDATE_SQL, type.getName() + "." + method.getName())
                .replaceAll("\\s+", " ").trim());
            return 1;
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> "MapperProxy";
        };
    }

    /**
     * 与容器中相同：Mapper 外面包一层带数据权限切面的 Spring 代理
     */
    private static <M> M springProxy(M target, Class<M> type) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setInterfaces(type);
        factory.addAdvisor(new DataPermissionPointcutAdvisor());
        return type.cast(factory.getProxy(type.getClassLoader()));
    }

}
