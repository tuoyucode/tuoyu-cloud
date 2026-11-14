package org.tuoyu.common.mybatis.interceptor;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.config.properties.SqlLogProperties;

import java.lang.reflect.InvocationTargetException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 完整 SQL 日志：把参数值代入占位符，连同耗时与 Mapper 输出每条执行的 SQL，执行失败时附上异常。
 * <p>
 * 日志会带出全部参数值（口令哈希、手机号等），所以只在 {@code mybatis-plus.sql-log.enabled=true} 时注册，
 * 生产环境开启时启动自检拒绝启动。输出位置由 {@link SqlLogProperties#getOutput()} 决定：
 * {@code log} 写到名为 {@code SQL_FULL} 的日志（单行，过长时分段），其他值写到标准错误输出。
 *
 * @author tuoyu
 */
@Slf4j(topic = "SQL_FULL")
@Intercepts({
    @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
    @Signature(type = StatementHandler.class, method = "update", args = {Statement.class}),
    @Signature(type = StatementHandler.class, method = "batch", args = {Statement.class}),
    @Signature(type = StatementHandler.class, method = "queryCursor", args = {Statement.class})
})
public class SqlLogInterceptor implements Interceptor {

    /**
     * 日志输出时每条日志的最大长度，超过时分段输出
     */
    private static final int MAX_LOG_LENGTH = 8000;

    private static final String OUTPUT_LOG = "log";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SqlLogProperties properties;

    public SqlLogInterceptor(SqlLogProperties properties) {
        this.properties = properties;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler handler = PluginUtils.realTarget(invocation.getTarget());
        BoundSql boundSql = handler.getBoundSql();
        MappedStatement mappedStatement = PluginUtils.mpStatementHandler(handler).mappedStatement();
        long start = System.currentTimeMillis();
        Throwable error = null;
        try {
            return invocation.proceed();
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            // 日志本身出错时不影响语句的结果
            try {
                output(System.currentTimeMillis() - start, mappedStatement, boundSql, error);
            } catch (RuntimeException e) {
                log.warn("输出完整 SQL 失败：{}", mappedStatement.getId(), e);
            }
        }
    }

    private void output(long elapsed, MappedStatement mappedStatement, BoundSql boundSql, Throwable error) {
        List<String> parts = new ArrayList<>(4);
        parts.add("Consume Time：" + elapsed + " ms " + LocalDateTime.now().format(DATE_TIME));
        parts.add("Mapper ID：" + mappedStatement.getId());
        parts.add("Execute SQL：" + fullSql(mappedStatement.getConfiguration(), boundSql));
        Throwable cause = error instanceof InvocationTargetException target && target.getTargetException() != null
            ? target.getTargetException() : error;
        if (cause != null && StringUtils.isNotBlank(cause.getMessage())) {
            parts.add("Execute Error：" + cause.getClass().getName() + ": " + cause.getMessage());
        }
        if (OUTPUT_LOG.equalsIgnoreCase(properties.getOutput())) {
            writeLog(String.join(" ", parts));
        } else {
            System.err.print(String.join("\n", parts) + System.lineSeparator() + System.lineSeparator());
        }
    }

    private static void writeLog(String text) {
        if (text.length() <= MAX_LOG_LENGTH) {
            log.info("{}", text);
            return;
        }
        String id = UUID.randomUUID().toString();
        int total = (text.length() + MAX_LOG_LENGTH - 1) / MAX_LOG_LENGTH;
        for (int i = 0; i < total; i++) {
            String part = text.substring(i * MAX_LOG_LENGTH, Math.min(text.length(), (i + 1) * MAX_LOG_LENGTH));
            log.info("sqlLogId={} part={}/{} {}", id, i + 1, total, part);
        }
    }

    /**
     * 把参数文本依次代入 SQL 中不在引号内的问号；参数用完后剩余的问号原样保留
     */
    private static String fullSql(Configuration configuration, BoundSql boundSql) {
        String sql = WHITESPACE.matcher(boundSql.getSql()).replaceAll(" ").trim();
        List<String> values = parameterTexts(configuration, boundSql);
        StringBuilder result = new StringBuilder(sql.length() + values.size() * 8);
        boolean inSingle = false;
        boolean inDouble = false;
        int next = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == '?' && !inSingle && !inDouble && next < values.size()) {
                result.append(values.get(next++));
                continue;
            }
            result.append(c);
        }
        return result.toString();
    }

    private static List<String> parameterTexts(Configuration configuration, BoundSql boundSql) {
        List<ParameterMapping> mappings = boundSql.getParameterMappings();
        List<String> values = new ArrayList<>();
        if (mappings == null || mappings.isEmpty()) {
            return values;
        }
        Object parameter = boundSql.getParameterObject();
        MetaObject metaObject = null;
        for (ParameterMapping mapping : mappings) {
            if (mapping.getMode() == ParameterMode.OUT) {
                continue;
            }
            String property = mapping.getProperty();
            Object value;
            if (boundSql.hasAdditionalParameter(property)) {
                value = boundSql.getAdditionalParameter(property);
            } else if (parameter == null) {
                value = null;
            } else if (configuration.getTypeHandlerRegistry().hasTypeHandler(parameter.getClass())) {
                value = parameter;
            } else {
                if (metaObject == null) {
                    metaObject = configuration.newMetaObject(parameter);
                }
                value = metaObject.hasGetter(property) ? metaObject.getValue(property) : null;
            }
            values.add(format(value));
        }
        return values;
    }

    private static String format(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Date date) {
            // SimpleDateFormat 不是线程安全的，每次新建
            return quote(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date));
        }
        if (value instanceof LocalDateTime dateTime) {
            return quote(dateTime.format(DATE_TIME));
        }
        if (value instanceof LocalDate date) {
            return quote(date.format(DATE));
        }
        if (value instanceof LocalTime time) {
            return quote(time.format(TIME));
        }
        if (value instanceof TemporalAccessor) {
            return quote(value.toString());
        }
        if (value instanceof Enum<?> constant) {
            return quote(constant.name());
        }
        return quote(value.toString());
    }

    private static String quote(String text) {
        return "'" + text.replace("'", "''") + "'";
    }

}
