package org.tuoyu.common.log;

import cn.hutool.extra.spring.SpringUtil;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.aspect.LogAspect;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.log.enums.BusinessStatus;
import org.tuoyu.common.log.enums.OperatorType;
import org.tuoyu.common.log.event.OperLogEvent;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@DisplayName("common-log 功能单元测试")
class LogFunctionTest {

    private static final AtomicReference<OperLogEvent> LAST_EVENT = new AtomicReference<>();

    private UserContextProvider previousProvider;

    /**
     * 初始化日志切面依赖的 JSON 映射器和事件容器，避免启动完整 Spring 应用。
     */
    @BeforeAll
    static void initializeLogInfrastructure() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("jsonMapper", JsonMapper.builder().build());
        context.addApplicationListener(event -> {
            if (event instanceof PayloadApplicationEvent<?> payload
                && payload.getPayload() instanceof OperLogEvent operLog) {
                LAST_EVENT.set(operLog);
            }
        });
        context.refresh();
        new SpringUtil().setApplicationContext(context);
    }

    /**
     * 为每个日志切面测试绑定独立 HTTP 请求并清空上一次捕获的事件。
     */
    @BeforeEach
    void bindRequestContext() {
        previousProvider = UserContext.setProvider(Optional::empty);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/system/users");
        request.addHeader("clientid", "web-client");
        request.addHeader("X-Forwarded-For", "10.0.0.8");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        LAST_EVENT.set(null);
    }

    /**
     * 清理线程请求上下文，避免日志切面测试污染后续用例。
     */
    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        UserContext.setProvider(previousProvider);
    }

    /**
     * 验证上传文件、Servlet 请求及包含这些对象的集合会从日志参数中排除。
     */
    @Test
    @DisplayName("识别不可记录的请求参数")
    void shouldFilterServletAndUploadObjects() {
        LogAspect aspect = new LogAspect();
        MockMultipartFile file = new MockMultipartFile("file", "demo.txt", "text/plain", new byte[]{1});
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertTrue(aspect.isFilterObject(file));
        assertTrue(aspect.isFilterObject(request));
        assertTrue(aspect.isFilterObject(new Object[]{"value", file}));
        assertTrue(aspect.isFilterObject(List.of("value", file)));
        assertTrue(aspect.isFilterObject(Map.of("file", file)));
        assertFalse(aspect.isFilterObject(List.of("value", 1L)));
    }

    /**
     * 验证操作日志注解保存业务类型、操作人类型及请求响应开关。
     *
     * @throws Exception 读取测试方法注解失败
     */
    @Test
    @DisplayName("读取操作日志注解配置")
    void shouldExposeLogAnnotationConfiguration() throws Exception {
        Method method = TestController.class.getDeclaredMethod("update");
        OperationLog log = method.getAnnotation(OperationLog.class);

        assertEquals("用户管理", log.module());
        assertEquals(AuditAction.UPDATE, log.action());
        assertEquals(OperatorType.MOBILE, log.operatorType());
        assertFalse(log.saveRequestData());
        assertTrue(log.saveResponseData());
        assertArrayEquals(new String[]{"password"}, log.excludeParamNames());
    }

    /**
     * 验证日志切面执行目标方法后发布完整事件，并从序列化请求参数中排除密码等敏感字段。
     */
    @Test
    @DisplayName("记录成功操作日志并排除敏感参数")
    void shouldPublishSuccessfulOperationLogWithFilteredParameters() throws Throwable {
        OperationLog annotation = TestController.class.getDeclaredMethod("create", CreateRequest.class).getAnnotation(OperationLog.class);
        ProceedingJoinPoint joinPoint = joinPoint(new CreateRequest("alice", "secret"), Map.of("id", 1L));

        Object result = new LogAspect().doAround(joinPoint, annotation);

        OperLogEvent event = LAST_EVENT.get();
        assertEquals(Map.of("id", 1L), result);
        assertNotNull(event);
        assertEquals(BusinessStatus.SUCCESS.ordinal(), event.getStatus());
        assertEquals("新增用户", event.getTitle());
        assertEquals("POST", event.getRequestMethod());
        assertEquals("/system/users", event.getOperUrl());
        assertEquals("10.0.0.8", event.getOperIp());
        assertEquals("web-client", event.getClientKey());
        assertTrue(event.getOperParam().contains("alice"));
        assertFalse(event.getOperParam().contains("secret"));
        assertTrue(event.getJsonResult().contains("\"id\":1"));
    }

    /**
     * 验证 @OperationLog 记录操作日志，操作类型按 AuditAction 的存储值保存；失败时记录失败状态与原因。
     */
    @Test
    @DisplayName("@OperationLog 记录操作日志，失败时记录原因")
    void shouldPublishOperationLog() throws Throwable {
        OperationLog annotation = TestController.class.getDeclaredMethod("audit", CreateRequest.class).getAnnotation(OperationLog.class);
        ProceedingJoinPoint joinPoint = joinPoint(new CreateRequest("bob", "secret"), Map.of("id", 2L));

        new LogAspect().doAround(joinPoint, annotation);

        OperLogEvent event = LAST_EVENT.get();
        assertNotNull(event);
        assertEquals("用户管理", event.getTitle());
        assertEquals(1, event.getBusinessType());
        assertEquals(OperatorType.MANAGE.ordinal(), event.getOperatorType());
        assertEquals(BusinessStatus.SUCCESS.ordinal(), event.getStatus());
        assertFalse(event.getOperParam().contains("secret"));
        assertNull(event.getJsonResult(), "saveResponseData = false 时不记录响应");

        ProceedingJoinPoint failing = joinPoint(new CreateRequest("bob", "secret"), null);
        when(failing.proceed()).thenThrow(new IllegalStateException("保存失败"));
        assertThrows(IllegalStateException.class, () -> new LogAspect().doAround(failing, annotation));
        assertEquals(BusinessStatus.FAIL.ordinal(), LAST_EVENT.get().getStatus());
        assertEquals("保存失败", LAST_EVENT.get().getErrorMsg());
    }

    /**
     * 有当前用户时记录操作人、部门与终端信息；请求头没有客户端标识时取登录时的客户端。
     */
    @Test
    @DisplayName("记录当前用户为操作人")
    void shouldRecordCurrentUserAsOperator() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/system/users");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        CurrentUser user = new CurrentUser();
        user.setUserId(7L);
        user.setUserName("carol");
        user.setDeptId(103L);
        user.setDeptName("研发部");
        user.setClientKey("pc");
        user.setDeviceType("pc");
        user.setBrowser("Chrome");
        user.setOs("Windows");
        UserContext.setProvider(() -> Optional.of(user));
        OperationLog annotation = TestController.class.getDeclaredMethod("audit", CreateRequest.class).getAnnotation(OperationLog.class);

        new LogAspect().doAround(joinPoint(new CreateRequest("dave", "secret"), null), annotation);

        OperLogEvent event = LAST_EVENT.get();
        assertNotNull(event);
        assertEquals("carol", event.getOperName());
        assertEquals(7L, event.getUserId());
        assertEquals(103L, event.getDeptId());
        assertEquals("研发部", event.getDeptName());
        assertEquals("pc", event.getClientKey());
        assertEquals("pc", event.getDeviceType());
        assertEquals("Chrome", event.getBrowser());
        assertEquals("Windows", event.getOs());
    }

    /**
     * 请求头中的客户端标识优先于登录时的客户端；没有当前用户时不记录操作人。
     */
    @Test
    @DisplayName("请求头的客户端标识优先，未登录时操作人为空")
    void shouldPreferHeaderClientKeyAndSkipOperatorWithoutUser() throws Throwable {
        CurrentUser user = new CurrentUser();
        user.setUserId(7L);
        user.setUserName("carol");
        user.setClientKey("pc");
        UserContext.setProvider(() -> Optional.of(user));
        OperationLog annotation = TestController.class.getDeclaredMethod("audit", CreateRequest.class).getAnnotation(OperationLog.class);

        new LogAspect().doAround(joinPoint(new CreateRequest("dave", "secret"), null), annotation);
        assertEquals("web-client", LAST_EVENT.get().getClientKey());
        assertEquals("carol", LAST_EVENT.get().getOperName());

        UserContext.setProvider(Optional::empty);
        new LogAspect().doAround(joinPoint(new CreateRequest("dave", "secret"), null), annotation);
        assertNull(LAST_EVENT.get().getOperName());
        assertNull(LAST_EVENT.get().getUserId());
    }

    /**
     * 操作类型的存储值写进操作日志表，与前端字典 sys_oper_type 对应，取值固定。
     */
    @Test
    @DisplayName("AuditAction 的存储值固定")
    void auditActionCodesAreStable() {
        assertEquals(10, AuditAction.values().length);
        assertEquals(0, AuditAction.OTHER.code());
        assertEquals(1, AuditAction.CREATE.code());
        assertEquals(2, AuditAction.UPDATE.code());
        assertEquals(3, AuditAction.DELETE.code());
        assertEquals(4, AuditAction.GRANT.code());
        assertEquals(5, AuditAction.EXPORT.code());
        assertEquals(6, AuditAction.IMPORT.code());
        assertEquals(7, AuditAction.FORCE_LOGOUT.code());
        assertEquals(8, AuditAction.GENERATE_CODE.code());
        assertEquals(9, AuditAction.CLEAN.code());
    }

    /**
     * 创建可返回指定结果的切点，并提供日志方法名、目标类和请求参数。
     *
     * @param request 请求参数
     * @param result  目标方法返回值
     * @return 模拟切点
     */
    private static ProceedingJoinPoint joinPoint(CreateRequest request, Object result) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn("create");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new TestController());
        when(joinPoint.getArgs()).thenReturn(new Object[]{request});
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    private static class TestController {

        /**
         * 提供完整日志注解配置供反射测试读取。
         */
        @OperationLog(module = "用户管理", action = AuditAction.UPDATE, operatorType = OperatorType.MOBILE,
            saveRequestData = false, excludeParamNames = "password")
        private void update() {
        }

        /**
         * 提供保存请求和响应数据的日志配置供完整切面测试。
         *
         * @param request 新增请求
         */
        @OperationLog(module = "新增用户", action = AuditAction.CREATE, excludeParamNames = "password")
        private void create(CreateRequest request) {
        }

        /**
         * 提供 @OperationLog 配置供切面测试。
         *
         * @param request 新增请求
         */
        @OperationLog(module = "用户管理", action = AuditAction.CREATE, saveResponseData = false, excludeParamNames = "password")
        private void audit(CreateRequest request) {
        }
    }

    private record CreateRequest(String username, String password) {
    }
}
