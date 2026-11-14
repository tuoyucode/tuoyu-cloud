package org.tuoyu.common.mybatis.handler;

import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.core.handlers.PostInitTableInfoHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import org.apache.ibatis.session.Configuration;
import org.tuoyu.common.core.utils.reflect.ReflectUtils;
import org.tuoyu.common.core.utils.SpringUtils;

/**
 * 全局开关逻辑删除：配置 {@code mybatis-plus.enableLogicDelete=false} 时，所有表都不使用逻辑删除
 * （删除语句直接删除，查询不带逻辑删除条件）；为 true 或未配置时由 MyBatis-Plus 按实体上的注解判断。
 *
 * @author tuoyu
 */
public class LogicDeleteTableInfoHandler implements PostInitTableInfoHandler {

    private static final String ENABLE_KEY = "mybatis-plus.enableLogicDelete";

    /**
     * TableInfo 中“使用逻辑删除”的私有字段。MyBatis-Plus 没有公开的写方法，只能通过反射修改
     */
    private static final String WITH_LOGIC_DELETE_FIELD = "withLogicDelete";

    public LogicDeleteTableInfoHandler() {
    }

    @Override
    public TableInfo postTableInfo(TableInfo tableInfo, Configuration configuration) {
        // 每张表初始化时读取配置，不在类加载时访问容器
        Boolean enabled = Convert.toBool(SpringUtils.getProperty(ENABLE_KEY, "true"));
        if (Boolean.FALSE.equals(enabled)) {
            ReflectUtils.setFieldValue(tableInfo, WITH_LOGIC_DELETE_FIELD, false);
        }
        return tableInfo;
    }

}
