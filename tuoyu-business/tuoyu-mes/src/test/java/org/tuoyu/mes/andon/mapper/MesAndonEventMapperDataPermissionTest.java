package org.tuoyu.mes.andon.mapper;

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
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.aspect.DataPermissionPointcutAdvisor;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.handler.DataPermissionSqlHandler;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.common.mybatis.interceptor.DataPermissionSqlInterceptor;
import org.tuoyu.mes.andon.domain.bo.MesAndonEventBo;
import org.tuoyu.mes.andon.service.impl.MesAndonEventServiceImpl;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.resource.api.RemoteMessageService;
import org.tuoyu.system.api.RemoteUserService;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 安灯事件的数据权限。按运行时的结构组装：MyBatis 的 Mapper 代理（默认方法在代理内部转调）外面再包一层带数据权限切面的
 * Spring 代理，检查执行 SQL 时数据权限是否生效、改写后的 SELECT 语句是否带上创建部门的条件。
 */
@DisplayName("安灯事件的数据权限：列表、导出、导出前统计与按 ID 查看都限定在数据范围内")
class MesAndonEventMapperDataPermissionTest {

    static {
        MesTestSupport.init();
    }

    private static final String SELECT_SQL = "SELECT event_id FROM mes_andon_event WHERE event_status = ?";

    /** 执行 SQL 时的数据权限注解（没有时为 null） */
    private final List<DataPermission> seen = new ArrayList<>();

    /** 执行 SQL 时由数据权限拦截器改写后的 SQL */
    private final List<String> rewritten = new ArrayList<>();

    private DataPermissionSqlInterceptor interceptor;

    private UserContextProvider previous;

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
        previous = UserContext.setProvider(() -> Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        DataPermissionHelper.removePermission();
        DataPermissionHelper.getContext().clear();
        UserContext.setProvider(previous);
    }

    @Test
    @DisplayName("带数据权限的方法执行 SQL 时都带上创建部门条件，BaseMapper 自带的方法不带")
    void scopedMethodsCarryDataPermission() {
        MesAndonEventMapper mapper = springProxy(mybatisProxy());

        mapper.selectPageInScope(new PageParam(1, 10).toPage(), null);
        mapper.selectListInScope(null);
        mapper.countInScope(null);
        mapper.selectByIdInScope(1L);
        assertThat(seen).hasSize(4).doesNotContainNull();
        assertThat(seen).allSatisfy(permission -> assertThat(permission.value())
            .extracting(column -> column.value()[0]).containsExactly("create_dept", "create_by"));
        assertThat(rewritten).allSatisfy(sql -> assertThat(sql)
            .isEqualTo("SELECT event_id FROM mes_andon_event WHERE event_status = ? AND (create_dept = 103)"));
        // 调用结束后不残留
        assertThat(DataPermissionHelper.getPermission()).isNull();

        mapper.selectById(1L);
        assertThat(seen.get(4)).isNull();
    }

    @Test
    @DisplayName("服务的列表、导出、统计与详情经过数据权限；只看派给我的不按数据权限过滤")
    void serviceQueriesUseScope() {
        MesAndonEventMapper mapper = springProxy(mybatisProxy());
        MesAndonEventServiceImpl service = new MesAndonEventServiceImpl(mapper,
            MesTestSupport.mapper(MesDowntimeReasonMapper.class), MesTestSupport.mapper(MesWorkshopMapper.class),
            MesTestSupport.mapper(MesLineMapper.class), MesTestSupport.mapper(MesStationMapper.class),
            MesTestSupport.mapper(MesDeviceMapper.class), MesTestSupport.mapper(MesWorkorderMapper.class),
            mock(IMesCodeService.class), mock(IMesStatusLogService.class), mock(RemoteMessageService.class),
            mock(RemoteUserService.class));

        MesAndonEventBo all = new MesAndonEventBo();
        service.queryPageList(all, new PageParam(1, 10));
        service.queryList(all);
        service.countList(all);
        assertThat(seen).hasSize(3).doesNotContainNull();

        seen.clear();
        // 范围内查不到时再按 ID 查一次，只有处理人是当前用户时才返回
        assertThatThrownBy(() -> service.queryById(1L)).hasMessageContaining("不存在");
        assertThat(seen).hasSize(2);
        assertThat(seen.get(0)).isNotNull();
        assertThat(seen.get(1)).isNull();

        seen.clear();
        MesAndonEventBo mine = new MesAndonEventBo();
        mine.setMine(true);
        service.queryPageList(mine, new PageParam(1, 10));
        service.queryList(mine);
        assertThat(seen).hasSize(2).containsOnlyNulls();
    }

    /**
     * 按 MyBatis Mapper 代理的方式处理调用：默认方法在代理自身上执行（转调不经过外层 Spring 代理），
     * 抽象方法视为执行 SQL，记录此时的数据权限并让拦截器改写一条 SELECT 语句
     */
    private MesAndonEventMapper mybatisProxy() {
        Class<MesAndonEventMapper> type = MesAndonEventMapper.class;
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, args);
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            seen.add(DataPermissionHelper.getPermission());
            rewritten.add(DataPermissionHelper.getPermission() == null ? SELECT_SQL
                : interceptor.parserSingle(SELECT_SQL, type.getName() + "." + method.getName())
                .replaceAll("\\s+", " ").trim());
            return emptyResult(method.getReturnType());
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object emptyResult(Class<?> returnType) {
        if (List.class.isAssignableFrom(returnType)) {
            return List.of();
        }
        if (returnType == long.class || returnType == Long.class) {
            return 0L;
        }
        if (returnType == int.class) {
            return 0;
        }
        return null;
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
    private static MesAndonEventMapper springProxy(MesAndonEventMapper target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setInterfaces(MesAndonEventMapper.class);
        factory.addAdvisor(new DataPermissionPointcutAdvisor());
        return MesAndonEventMapper.class.cast(factory.getProxy(MesAndonEventMapper.class.getClassLoader()));
    }

}
