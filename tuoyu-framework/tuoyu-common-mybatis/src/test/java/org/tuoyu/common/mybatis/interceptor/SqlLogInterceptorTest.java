package org.tuoyu.common.mybatis.interceptor;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.tuoyu.common.mybatis.config.properties.SqlLogProperties;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 完整 SQL 日志：把参数代入占位符后输出，执行失败时附上异常并原样抛出
 */
@DisplayName("SqlLogInterceptor：输出代入参数的 SQL")
class SqlLogInterceptorTest {

    enum Status { ACTIVE }

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private PrintStream originalErr;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void capture() {
        originalErr = System.err;
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger("SQL_FULL")).addAppender(appender);
    }

    @AfterEach
    void restore() {
        System.setErr(originalErr);
        ((Logger) LoggerFactory.getLogger("SQL_FULL")).detachAppender(appender);
    }

    private static SqlLogInterceptor interceptor(String output) {
        SqlLogProperties properties = new SqlLogProperties();
        properties.setEnabled(true);
        properties.setOutput(output);
        return new SqlLogInterceptor(properties);
    }

    private static Invocation update(String sql, Map<String, Object> parameter, List<String> properties, PreparedStatement ps)
        throws Exception {
        Configuration configuration = new Configuration();
        MappedStatement ms = new MappedStatement.Builder(configuration, "org.example.UserMapper.updateName",
            new StaticSqlSource(configuration, sql), SqlCommandType.UPDATE).build();
        List<ParameterMapping> mappings = properties.stream()
            .map(name -> new ParameterMapping.Builder(configuration, name, Object.class).build()).toList();
        BoundSql boundSql = new BoundSql(configuration, sql, mappings, parameter);
        StatementHandler handler = new RoutingStatementHandler(mock(Executor.class), ms, parameter, RowBounds.DEFAULT, null, boundSql);
        return new Invocation(handler, StatementHandler.class.getMethod("update", Statement.class), new Object[]{ps});
    }

    @Test
    @DisplayName("拦截语句执行（查询、修改、批量、游标）")
    void intercepts() throws Exception {
        Intercepts intercepts = SqlLogInterceptor.class.getAnnotation(Intercepts.class);
        assertThat(intercepts).isNotNull();
        assertThat(Arrays.stream(intercepts.value()).map(Signature::type)).containsOnly(StatementHandler.class);
        assertThat(Arrays.stream(intercepts.value()).map(Signature::method))
            .containsExactlyInAnyOrder("query", "update", "batch", "queryCursor");
        assertThat(Arrays.stream(intercepts.value()).filter(s -> s.method().equals("query")).findFirst().orElseThrow().args())
            .containsExactly(Statement.class, ResultHandler.class);
        assertThat(Interceptor.class).isAssignableFrom(SqlLogInterceptor.class);
    }

    @Test
    @DisplayName("控制台输出：参数按类型代入（字符串加引号并转义单引号，日期时间按格式），引号中的问号不替换")
    void consoleOutput() throws Throwable {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.getUpdateCount()).thenReturn(1);
        Map<String, Object> parameter = new HashMap<>();
        parameter.put("name", "O'Brien");
        parameter.put("created", LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        parameter.put("day", LocalDate.of(2026, 1, 2));
        parameter.put("status", Status.ACTIVE);
        parameter.put("id", 5L);
        parameter.put("flag", true);
        parameter.put("remark", null);
        Invocation invocation = update("update t\n set name = ?, created = ?, day = ?, status = ?, flag = ?, remark = ?"
            + " where id = ? and note = 'a?b'", parameter, List.of("name", "created", "day", "status", "flag", "remark", "id"), ps);

        Object result = interceptor("console").intercept(invocation);

        assertThat(result).isEqualTo(1);
        String printed = err.toString(StandardCharsets.UTF_8);
        assertThat(printed)
            .contains("Mapper ID：org.example.UserMapper.updateName")
            .contains("Execute SQL：update t set name = 'O''Brien', created = '2026-01-02 03:04:05', day = '2026-01-02',"
                + " status = 'ACTIVE', flag = true, remark = null where id = 5 and note = 'a?b'")
            .contains("Consume Time：")
            .doesNotContain("Execute Error");
        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("执行失败：输出异常类名与消息，异常原样抛出")
    void failure() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ps.execute()).thenThrow(new SQLException("boom"));
        Invocation invocation = update("update t set a = 1", Map.of(), List.of(), ps);

        assertThatThrownBy(() -> interceptor("console").intercept(invocation)).isInstanceOf(Exception.class);
        assertThat(err.toString(StandardCharsets.UTF_8))
            .contains("Execute SQL：update t set a = 1")
            .contains("Execute Error：java.sql.SQLException: boom");
    }

    @Test
    @DisplayName("日志输出：写到名为 SQL_FULL 的日志，单行；超长时分段并带同一个编号")
    void logOutput() throws Throwable {
        PreparedStatement ps = mock(PreparedStatement.class);
        Invocation shortSql = update("update t set a = ?", Map.of("a", 1), List.of("a"), ps);
        interceptor("log").intercept(shortSql);
        assertThat(appender.list).hasSize(1);
        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).contains("Mapper ID：org.example.UserMapper.updateName").contains("Execute SQL：update t set a = 1")
            .doesNotContain("\n");
        assertThat(err.size()).isZero();

        appender.list.clear();
        String longValue = "x".repeat(9000);
        interceptor("LOG").intercept(update("update t set a = ?", Map.of("a", longValue), List.of("a"), ps));
        assertThat(appender.list).hasSize(2);
        String first = appender.list.get(0).getFormattedMessage();
        String second = appender.list.get(1).getFormattedMessage();
        String id = first.substring("sqlLogId=".length(), first.indexOf(' '));
        assertThat(first).startsWith("sqlLogId=" + id + " part=1/2 ");
        assertThat(second).startsWith("sqlLogId=" + id + " part=2/2 ");
    }

}
