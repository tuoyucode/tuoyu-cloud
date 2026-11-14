package org.tuoyu.gen.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.gen.config.properties.GenProperties;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.domain.GenTableColumn;

import java.lang.reflect.ParameterizedType;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mapper 的声明与代码生成配置的默认值
 */
@DisplayName("代码生成：Mapper 与配置")
class GenMapperContractTest {

    private static Class<?> entityOf(Class<?> mapper) {
        ParameterizedType type = (ParameterizedType) mapper.getGenericInterfaces()[0];
        assertEquals(BaseMapper.class, type.getRawType());
        return (Class<?>) type.getActualTypeArguments()[0];
    }

    @Test
    @DisplayName("两个 Mapper 继承 BaseMapper，不受数据权限与租户条件约束（代码生成的表平台共享）")
    void mappers() {
        assertEquals(GenTable.class, entityOf(GenTableMapper.class));
        assertEquals(GenTableColumn.class, entityOf(GenTableColumnMapper.class));
        for (Class<?> mapper : List.of(GenTableMapper.class, GenTableColumnMapper.class)) {
            InterceptorIgnore ignore = mapper.getAnnotation(InterceptorIgnore.class);
            assertNotNull(ignore, mapper.getSimpleName());
            assertEquals("true", ignore.dataPermission());
            assertEquals("true", ignore.tenantLine());
        }
    }

    @Test
    @DisplayName("已导入的表名：在主数据源查询（@DS(\"\")），即使调用方切换到了其他数据源")
    void tableNames() throws Exception {
        DS ds = GenTableMapper.class.getMethod("selectTableNameList", String.class).getAnnotation(DS.class);
        assertNotNull(ds);
        assertEquals("", ds.value());
        assertEquals(List.class, GenTableMapper.class.getMethod("selectTableNameList", String.class).getReturnType());
    }

    @Test
    @DisplayName("配置默认值：不去除表前缀，新导入的表生成 Element Plus 版页面")
    void properties() {
        GenProperties properties = new GenProperties();
        assertFalse(properties.isAutoRemovePre());
        assertEquals("ele", properties.getFrontendType());
        assertNull(properties.getAuthor());
        assertNull(properties.getPackageName());
        assertNull(properties.getTablePrefix());
    }
}
