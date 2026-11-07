package org.tuoyu.common.tenant;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.tuoyu.common.core.context.TenantContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;
import org.tuoyu.common.mybatis.handler.InjectionMetaObjectHandler;
import org.tuoyu.common.tenant.core.DefaultTenantContext;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.common.tenant.core.TenantProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 不能借插入、更新把数据写进其他租户（ADR 0006）：插入时改写为当前租户，按实体更新时不改动所属租户
 */
@DisplayName("多租户：插入与更新时的租户字段")
class TenantFillTest {

    private InjectionMetaObjectHandler handler;

    @BeforeEach
    void setUp() {
        TenantProperties properties = new TenantProperties();
        properties.setEnabled(true);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("tenantContext", new DefaultTenantContext(properties,
            new StaticListableBeanFactory().getBeanProvider(UserContextProvider.class), id -> null));
        handler = new InjectionMetaObjectHandler(new StaticListableBeanFactory().getBeanProvider(UserContextProvider.class),
            beans.getBeanProvider(TenantContext.class));
    }

    @Test
    @DisplayName("插入时一律改写为当前租户")
    void insertUsesCurrentTenant() {
        TenantEntity entity = new TenantEntity();
        entity.setTenantId("tenant-b");
        MetaObject meta = SystemMetaObject.forObject(entity);

        TenantHelper.dynamic("tenant-a", () -> handler.insertFill(meta));

        assertEquals("tenant-a", entity.getTenantId());
    }

    @Test
    @DisplayName("按实体更新时不写入租户字段（更新策略为 NEVER），更新填充也不改动它")
    void updateDoesNotChangeTenant() throws NoSuchFieldException {
        // 不能靠填充把租户置空：MyBatis-Plus 先按字段是否为空生成 SET 子句再填充，置空只会写入 null（真实库上的验证见越权测试）
        TableField field = TenantEntity.class.getDeclaredField("tenantId").getAnnotation(TableField.class);
        assertEquals(FieldStrategy.NEVER, field.updateStrategy());

        TenantEntity entity = new TenantEntity();
        entity.setTenantId("tenant-b");
        MetaObject meta = SystemMetaObject.forObject(entity);

        TenantHelper.dynamic("tenant-a", () -> handler.updateFill(meta));

        assertEquals("tenant-b", entity.getTenantId());
    }

    @Test
    @DisplayName("忽略租户的代码块中保留实体中的值（平台工具需要写入指定租户）")
    void ignoreKeepsExplicitTenant() {
        TenantEntity entity = new TenantEntity();
        entity.setTenantId("tenant-b");

        TenantHelper.ignore(() -> handler.insertFill(SystemMetaObject.forObject(entity)));

        assertEquals("tenant-b", entity.getTenantId());
    }

}
