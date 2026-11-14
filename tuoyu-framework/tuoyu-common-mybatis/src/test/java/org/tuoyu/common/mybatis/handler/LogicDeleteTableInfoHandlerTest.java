package org.tuoyu.common.mybatis.handler;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.mybatis.TestSpringContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tuoyu.common.mybatis.TestSpringContext.PROPERTIES;

/**
 * 全局关闭逻辑删除：mybatis-plus.enableLogicDelete=false 时所有表都不使用逻辑删除
 */
@DisplayName("LogicDeleteTableInfoHandler：全局开关逻辑删除")
class LogicDeleteTableInfoHandlerTest {

    private static final String KEY = "mybatis-plus.enableLogicDelete";

    @BeforeEach
    void installContext() {
        TestSpringContext.install();
    }

    @AfterEach
    void clearProperty() {
        PROPERTIES.remove(KEY);
    }

    @TableName("ld_on")
    static class LogicOn {
        @TableId
        private Long id;
        @TableLogic
        private Integer deleted;
    }

    @TableName("ld_default")
    static class LogicDefault {
        @TableId
        private Long id;
        @TableLogic
        private Integer deleted;
    }

    @TableName("ld_off")
    static class LogicOff {
        @TableId
        private Long id;
        @TableLogic
        private Integer deleted;
    }

    @TableName("ld_none")
    static class NoLogic {
        @TableId
        private Long id;
    }

    private static TableInfo init(Class<?> type) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        return TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, type.getName()), type);
    }

    @Test
    @DisplayName("开关为 true 或未配置时不改动，由 MyBatis-Plus 按实体判断")
    void enabledOrAbsentKeepsTableInfo() {
        LogicDeleteTableInfoHandler handler = new LogicDeleteTableInfoHandler();
        TableInfo absent = init(LogicDefault.class);
        assertThat(handler.postTableInfo(absent, absent.getConfiguration())).isSameAs(absent);
        assertThat(absent.isWithLogicDelete()).isTrue();

        PROPERTIES.put(KEY, "true");
        TableInfo on = init(LogicOn.class);
        handler.postTableInfo(on, on.getConfiguration());
        assertThat(on.isWithLogicDelete()).isTrue();
        assertThat(on.getLogicDeleteSql(false, true)).isEqualTo("deleted=0");

        TableInfo none = init(NoLogic.class);
        handler.postTableInfo(none, none.getConfiguration());
        assertThat(none.isWithLogicDelete()).isFalse();
    }

    @Test
    @DisplayName("开关为 false 时关闭逻辑删除，逻辑删除条件为空")
    void disabled() {
        PROPERTIES.put(KEY, "false");
        TableInfo off = init(LogicOff.class);
        assertThat(new LogicDeleteTableInfoHandler().postTableInfo(off, off.getConfiguration())).isSameAs(off);
        assertThat(off.isWithLogicDelete()).isFalse();
        assertThat(off.getLogicDeleteSql(false, true)).isEmpty();
    }

}
