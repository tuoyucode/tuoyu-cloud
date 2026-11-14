package org.tuoyu.common.mybatis.core.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实体基类：审计字段的填充方式、租户字段的插入与更新策略、请求参数不映射到列
 */
@DisplayName("BaseEntity / TenantEntity：表字段元数据")
class EntityMetadataTest {

    @TableName("em_plain")
    static class PlainEntity extends BaseEntity {
        @TableId
        private Long id;
    }

    @TableName("em_tenant")
    static class TenantScoped extends TenantEntity {
        @TableId
        private Long id;
    }

    private static Map<String, TableFieldInfo> fields(Class<?> type) {
        TableInfo info = TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), type.getName()), type);
        return info.getFieldList().stream().collect(Collectors.toMap(TableFieldInfo::getProperty, f -> f));
    }

    @Test
    @DisplayName("审计字段：创建部门、创建者、创建时间在插入时填充，更新者、更新时间在插入与更新时填充；params 不是列")
    void baseEntity() {
        Map<String, TableFieldInfo> fields = fields(PlainEntity.class);
        assertThat(fields).containsOnlyKeys("createDept", "createBy", "createTime", "updateBy", "updateTime");
        assertThat(fields.get("createDept").getFieldFill()).isEqualTo(FieldFill.INSERT);
        assertThat(fields.get("createBy").getFieldFill()).isEqualTo(FieldFill.INSERT);
        assertThat(fields.get("createTime").getFieldFill()).isEqualTo(FieldFill.INSERT);
        assertThat(fields.get("updateBy").getFieldFill()).isEqualTo(FieldFill.INSERT_UPDATE);
        assertThat(fields.get("updateTime").getFieldFill()).isEqualTo(FieldFill.INSERT_UPDATE);
        assertThat(fields.get("createDept").getColumn()).isEqualTo("create_dept");
        assertThat(fields.get("createTime").getPropertyType()).isEqualTo(LocalDateTime.class);
        assertThat(fields.get("createBy").getPropertyType()).isEqualTo(Long.class);
    }

    @Test
    @DisplayName("租户字段：插入时填充，按实体更新时不写入（更新策略 NEVER）")
    void tenantEntity() {
        Map<String, TableFieldInfo> fields = fields(TenantScoped.class);
        assertThat(fields).containsKey("tenantId");
        TableFieldInfo tenantId = fields.get("tenantId");
        assertThat(tenantId.getColumn()).isEqualTo("tenant_id");
        assertThat(tenantId.getFieldFill()).isEqualTo(FieldFill.INSERT);
        assertThat(tenantId.getUpdateStrategy()).isEqualTo(FieldStrategy.NEVER);
        assertThat(new TenantScoped()).isInstanceOf(BaseEntity.class);
    }

    @Test
    @DisplayName("访问器、请求参数默认为空的可修改 Map、可序列化、equals 包含父类字段")
    void accessors() throws Exception {
        TenantScoped entity = new TenantScoped();
        assertThat(entity.getParams()).isEmpty();
        entity.getParams().put("beginTime", "2026-01-01");
        entity.setCreateBy(1L);
        entity.setCreateDept(2L);
        entity.setUpdateBy(3L);
        entity.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        entity.setUpdateTime(LocalDateTime.of(2026, 1, 2, 0, 0));
        entity.setTenantId("t1");
        assertThat(entity.getTenantId()).isEqualTo("t1");
        assertThat(entity.getCreateDept()).isEqualTo(2L);
        assertThat(entity.getUpdateBy()).isEqualTo(3L);

        assertThat(entity).isInstanceOf(Serializable.class);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(entity);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            TenantScoped copy = (TenantScoped) in.readObject();
            assertThat(copy.getTenantId()).isEqualTo("t1");
            assertThat(copy.getParams()).containsEntry("beginTime", "2026-01-01");
        }

        TenantScoped other = new TenantScoped();
        other.setTenantId("t1");
        assertThat(other).isNotEqualTo(entity);
        other.setCreateBy(1L);
        other.setCreateDept(2L);
        other.setUpdateBy(3L);
        other.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        other.setUpdateTime(LocalDateTime.of(2026, 1, 2, 0, 0));
        other.getParams().put("beginTime", "2026-01-01");
        assertThat(other).isEqualTo(entity);
    }

}
