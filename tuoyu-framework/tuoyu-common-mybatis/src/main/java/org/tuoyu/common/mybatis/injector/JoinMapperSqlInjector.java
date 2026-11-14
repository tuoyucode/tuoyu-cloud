package org.tuoyu.common.mybatis.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.github.yulichang.base.JoinMapper;
import com.github.yulichang.injector.MPJSqlInjector;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

import java.util.List;

/**
 * SQL 注入器：只给继承了 {@link JoinMapper}（即 {@code MPJBaseMapper}）的 Mapper 注入 mybatis-plus-join 的方法。
 * <p>
 * mybatis-plus-join 自带的注入器对所有 Mapper 都把 MyBatis-Plus 的条件方法（更新、删除、各种查询）换成兼容联表
 * Wrapper 的实现，并登记实体与 Mapper 的对应关系。项目只在 JoinMapper 上使用联表，其他 Mapper 交给
 * MyBatis-Plus 默认的注入器，保持原生的语句。本类无状态，可以在多个线程中共用。
 *
 * @author tuoyu
 */
public class JoinMapperSqlInjector extends MPJSqlInjector {

    /**
     * 非 JoinMapper 使用的 MyBatis-Plus 默认注入器（无状态）
     */
    private final DefaultSqlInjector standard = new DefaultSqlInjector();

    public JoinMapperSqlInjector() {
        super();
    }

    @Override
    public List<AbstractMethod> getMethodList(Configuration configuration, Class<?> mapperClass, TableInfo tableInfo) {
        if (isJoinMapper(mapperClass)) {
            return super.getMethodList(configuration, mapperClass, tableInfo);
        }
        return standard.getMethodList(configuration, mapperClass, tableInfo);
    }

    @Override
    public void inspectInject(MapperBuilderAssistant builderAssistant, Class<?> mapperClass) {
        if (isJoinMapper(mapperClass)) {
            super.inspectInject(builderAssistant, mapperClass);
        } else {
            standard.inspectInject(builderAssistant, mapperClass);
        }
    }

    private static boolean isJoinMapper(Class<?> mapperClass) {
        return mapperClass != null && JoinMapper.class.isAssignableFrom(mapperClass);
    }

}
