package org.tuoyu.common.mybatis.handler;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DuplicateKeyException;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.TenantContext;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.domain.R;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("自动填充：创建人与时间、租户在插入时改写为当前租户、更新时不改动租户；数据库异常的提示")
class InjectionMetaObjectHandlerTest {

    /**
     * 带租户字段的实体
     */
    public static class TenantRow extends BaseEntity {

        private String tenantId;

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

    }

    /**
     * 租户表：tenantId 是主键
     */
    @TableName("test_tenant")
    public static class TenantRecord extends BaseEntity {

        @TableId(value = "tenant_id", type = IdType.INPUT)
        private String tenantId;

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

    }

    private static InjectionMetaObjectHandler handler(CurrentUser user, String tenantId) {
        return handler(user, tenantId != null, tenantId, false);
    }

    private static InjectionMetaObjectHandler handler(CurrentUser user, boolean enabled, String tenantId, boolean ignoring) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("userContext", (UserContext) () -> Optional.ofNullable(user));
        beans.addBean("tenantContext", new TenantContext() {
            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public String currentTenantId() {
                return tenantId;
            }

            @Override
            public boolean ignoring() {
                return ignoring;
            }
        });
        return new InjectionMetaObjectHandler(beans.getBeanProvider(UserContext.class), beans.getBeanProvider(TenantContext.class));
    }

    private static CurrentUser user() {
        CurrentUser user = new CurrentUser();
        user.setUserId(7L);
        user.setDeptId(103L);
        return user;
    }

    @Test
    @DisplayName("插入：填充创建人、更新人、创建部门与时间；租户一律改写为当前租户（实体中填了其他租户也不行）")
    void insert() {
        TenantRow row = new TenantRow();
        row.setTenantId("other");
        MetaObject meta = SystemMetaObject.forObject(row);

        handler(user(), "acme").insertFill(meta);

        assertThat(row.getTenantId()).isEqualTo("acme");
        assertThat(row.getCreateBy()).isEqualTo(7L);
        assertThat(row.getUpdateBy()).isEqualTo(7L);
        assertThat(row.getCreateDept()).isEqualTo(103L);
        assertThat(row.getCreateTime()).isNotNull().isEqualTo(row.getUpdateTime());
    }

    @Test
    @DisplayName("插入：已有创建人与时间时保留；没有登录用户时记为 -1；未开启多租户时不改动租户")
    void insertKeepsExistingValues() {
        LocalDateTime createTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        TenantRow preset = new TenantRow();
        preset.setCreateBy(9L);
        preset.setCreateTime(createTime);
        preset.setTenantId("t1");
        handler(user(), null).insertFill(SystemMetaObject.forObject(preset));
        assertThat(preset.getCreateBy()).isEqualTo(9L);
        assertThat(preset.getCreateTime()).isEqualTo(createTime);
        assertThat(preset.getTenantId()).isEqualTo("t1");

        TenantRow anonymous = new TenantRow();
        handler(null, null).insertFill(SystemMetaObject.forObject(anonymous));
        assertThat(anonymous.getCreateBy()).isEqualTo(-1L);
        assertThat(anonymous.getCreateDept()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("插入：未开启多租户（或没有多租户模块）时，实体中没有租户的填默认租户，不插入 null（tenant_id 非空）")
    void insertFillsDefaultTenantWhenDisabled() {
        TenantRow disabled = new TenantRow();
        handler(user(), null).insertFill(SystemMetaObject.forObject(disabled));
        assertThat(disabled.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);

        TenantRow withoutModule = new TenantRow();
        new InjectionMetaObjectHandler(new StaticListableBeanFactory().getBeanProvider(UserContext.class),
            new StaticListableBeanFactory().getBeanProvider(TenantContext.class))
            .insertFill(SystemMetaObject.forObject(withoutModule));
        assertThat(withoutModule.getTenantId()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }

    @Test
    @DisplayName("插入：租户表本身（tenantId 是主键）不是按租户隔离的数据，平台新增租户时不能把主键改写为当前租户")
    void insertKeepsTenantPrimaryKey() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TenantRecord.class);
        TenantRecord tenant = new TenantRecord();
        tenant.setTenantId("newco");

        handler(user(), "default").insertFill(SystemMetaObject.forObject(tenant));

        assertThat(tenant.getTenantId()).isEqualTo("newco");
        assertThat(tenant.getCreateBy()).isEqualTo(7L);

        TenantRecord withoutContext = new TenantRecord();
        withoutContext.setTenantId("other");
        handler(user(), true, null, false).insertFill(SystemMetaObject.forObject(withoutContext));
        assertThat(withoutContext.getTenantId()).isEqualTo("other");
    }

    @Test
    @DisplayName("插入：开启多租户却取不到当前租户（例如后台线程没有指定租户）时拒绝，提示缺少租户上下文，而不是插入 null")
    void insertRejectsMissingTenant() {
        TenantRow row = new TenantRow();
        MetaObject meta = SystemMetaObject.forObject(row);

        assertThatThrownBy(() -> handler(user(), true, null, false).insertFill(meta))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("缺少租户上下文");
        assertThat(row.getTenantId()).isNull();
    }

    @Test
    @DisplayName("插入：忽略租户的代码块中保留实体中的租户（例如平台为新租户初始化数据）")
    void insertKeepsTenantWhenIgnoring() {
        TenantRow row = new TenantRow();
        row.setTenantId("t9");
        handler(user(), true, null, true).insertFill(SystemMetaObject.forObject(row));
        assertThat(row.getTenantId()).isEqualTo("t9");
    }

    @Test
    @DisplayName("更新：填充更新人与时间；不改动实体中的租户（按实体更新不写入租户由 TenantEntity 的更新策略保证）")
    void update() {
        TenantRow row = new TenantRow();
        row.setTenantId("other");
        handler(user(), "acme").updateFill(SystemMetaObject.forObject(row));
        assertThat(row.getTenantId()).isEqualTo("other");
        assertThat(row.getUpdateBy()).isEqualTo(7L);
        assertThat(row.getUpdateTime()).isNotNull();

        TenantRow anonymous = new TenantRow();
        anonymous.setTenantId("keep");
        handler(null, null).updateFill(SystemMetaObject.forObject(anonymous));
        assertThat(anonymous.getUpdateBy()).isEqualTo(-1L);
        assertThat(anonymous.getTenantId()).isEqualTo("keep");
    }

    @Test
    @DisplayName("数据库异常：重复记录返回 409；认证失败的根因返回 401；不泄露重复键的细节")
    void exceptionHandler() {
        MybatisExceptionHandler handler = new MybatisExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/system/user");

        R<Void> duplicate = handler.handleDuplicateKeyException(new DuplicateKeyException("Duplicate entry 'admin' for key 'uk_user'"), request);
        assertThat(duplicate.getCode()).isEqualTo(409);
        assertThat(duplicate.getMsg()).doesNotContain("admin");

        MyBatisSystemException unauthorized = new MyBatisSystemException("wrapped",
            new RuntimeException(new ServiceException("认证失败，无法访问系统资源", 401)));
        assertThat(handler.handleCannotFindDataSourceException(unauthorized, request).getCode()).isEqualTo(401);

        // 其他数据访问异常：不把 SQL 与数据库报错返回给客户端，只返回通用提示与错误编号
        MyBatisSystemException sqlError = new MyBatisSystemException("### Error querying database. SQL: select password from sys_user",
            new RuntimeException("Table 'tuoyu-cloud.sys_user' doesn't exist"));
        R<Void> generic = handler.handleCannotFindDataSourceException(sqlError, request);
        assertThat(generic.getCode()).isEqualTo(500);
        assertThat(generic.getMsg()).contains("错误编号").doesNotContain("select", "sys_user", "doesn't exist");

        // 业务异常：消息本来就是给用户看的，照常返回
        MyBatisSystemException business = new MyBatisSystemException("wrapped", new ServiceException("缺少租户，拒绝执行"));
        assertThat(handler.handleCannotFindDataSourceException(business, request).getMsg()).isEqualTo("缺少租户，拒绝执行");

        assertThat(MybatisExceptionHandler.findCause(unauthorized, ServiceException.class)).isInstanceOf(ServiceException.class);
        assertThat(MybatisExceptionHandler.findCause(unauthorized, IllegalStateException.class)).isNull();
        assertThat(MybatisExceptionHandler.getRootCause(new IllegalStateException("root"))).hasMessage("root");
    }

}
