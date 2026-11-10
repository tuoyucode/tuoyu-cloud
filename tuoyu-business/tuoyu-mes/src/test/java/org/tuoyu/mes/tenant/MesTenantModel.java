package org.tuoyu.mes.tenant;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * MES 的表、实体与 Mapper：多租户约定检查与越权测试共用。
 * <ul>
 *     <li>表结构从迁移脚本（{@code db/migration/mes/<数据库>}）按版本顺序读出，不连数据库；</li>
 *     <li>实体与 Mapper 从类路径扫描（{@code org.tuoyu.mes} 下标注 {@code @TableName} 的类、继承 {@code BaseMapper} 的接口，
 *     以及 {@code mapper} 包下只做查询、没有实体的接口），
 *     新增的表、实体与 Mapper 自动纳入检查，不需要修改测试。</li>
 * </ul>
 */
final class MesTenantModel {

    static final String BASE_PACKAGE = "org.tuoyu.mes";

    static final String TENANT_COLUMN = "tenant_id";

    /**
     * 迁移脚本所在的目录（相对模块目录），下级为 mysql、postgresql
     */
    static final String MIGRATION_DIR = "src/main/resources/db/migration/mes";

    static final List<String> VENDORS = List.of("mysql", "postgresql");

    private static final Pattern VERSION = Pattern.compile("V([1-9][0-9]*)__.+\\.sql");

    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\r\\n]*");

    /**
     * 建表语句：MySQL 以 {@code ) engine=...;} 结束，PostgreSQL 以 {@code );} 结束
     */
    private static final Pattern CREATE_TABLE = Pattern.compile(
        "create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?(?:[`\"]?\\w+[`\"]?\\.)?[`\"]?(\\w+)[`\"]?\\s*\\((.*?)\\)\\s*(?:engine[^;]*)?;",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern ALTER_TABLE = Pattern.compile(
        "alter\\s+table\\s+(?:if\\s+exists\\s+)?(?:[`\"]?\\w+[`\"]?\\.)?[`\"]?(\\w+)[`\"]?\\s+(.*?);",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern ADD_TENANT_COLUMN = Pattern.compile(
        "add\\s+(?:column\\s+)?(?:if\\s+not\\s+exists\\s+)?[`\"]?tenant_id[`\"]?\\s+([^,]*(?:\\([^)]*\\)[^,]*)*)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern DROP_TENANT_COLUMN = Pattern.compile(
        "drop\\s+(?:column\\s+)?(?:if\\s+exists\\s+)?[`\"]?tenant_id[`\"]?", Pattern.CASE_INSENSITIVE);

    private static final Pattern DROP_TABLE = Pattern.compile(
        "drop\\s+table\\s+(?:if\\s+exists\\s+)?(?:[`\"]?\\w+[`\"]?\\.)?[`\"]?(\\w+)[`\"]?\\s*;", Pattern.CASE_INSENSITIVE);

    private static final Pattern COLUMN_COMMENT = Pattern.compile("\\s+comment\\s+'(?:[^']|'')*'", Pattern.CASE_INSENSITIVE);

    private MesTenantModel() {
    }

    /**
     * 模块目录：Maven 中工作目录就是模块目录；在仓库根目录运行时（例如 IDE）取 tuoyu-business/tuoyu-mes
     */
    static Path moduleDir() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.isDirectory(cwd.resolve(MIGRATION_DIR))) {
            return cwd;
        }
        Path fromRoot = cwd.resolve("tuoyu-business/tuoyu-mes");
        if (Files.isDirectory(fromRoot.resolve(MIGRATION_DIR))) {
            return fromRoot;
        }
        throw new IllegalStateException("找不到 MES 的迁移脚本目录 " + MIGRATION_DIR + "，当前目录为 " + cwd);
    }

    /**
     * 某种数据库的迁移脚本，按版本号排序
     */
    static List<Path> migrationScripts(String vendor) {
        Path dir = moduleDir().resolve(MIGRATION_DIR).resolve(vendor);
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> scripts = files.filter(file -> VERSION.matcher(file.getFileName().toString()).matches())
                .sorted(Comparator.comparingInt(MesTenantModel::versionOf))
                .toList();
            if (scripts.isEmpty()) {
                throw new IllegalStateException(dir + " 下没有迁移脚本");
            }
            return scripts;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int versionOf(Path script) {
        Matcher matcher = VERSION.matcher(script.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalStateException("不是迁移脚本：" + script);
        }
        return Integer.parseInt(matcher.group(1));
    }

    /**
     * 依次执行全部迁移脚本后的表：表名（小写）→ tenant_id 字段的定义（去掉注释、合并空白、小写），没有该字段时为空。
     * <p>
     * 解析迁移脚本中用到的写法：建表、{@code alter table ... add/drop [column] tenant_id}、删表。
     */
    static Map<String, Optional<String>> tenantColumns(String vendor) {
        Map<String, Optional<String>> tables = new TreeMap<>();
        for (Path script : migrationScripts(vendor)) {
            String sql = LINE_COMMENT.matcher(read(script)).replaceAll("");
            Matcher create = CREATE_TABLE.matcher(sql);
            while (create.find()) {
                tables.put(create.group(1).toLowerCase(Locale.ROOT), tenantColumnIn(create.group(2)));
            }
            Matcher alter = ALTER_TABLE.matcher(sql);
            while (alter.find()) {
                String table = alter.group(1).toLowerCase(Locale.ROOT);
                Matcher add = ADD_TENANT_COLUMN.matcher(alter.group(2));
                if (add.find()) {
                    tables.put(table, Optional.of(normalize(add.group(1))));
                } else if (DROP_TENANT_COLUMN.matcher(alter.group(2)).find()) {
                    tables.put(table, Optional.empty());
                }
            }
            Matcher drop = DROP_TABLE.matcher(sql);
            while (drop.find()) {
                tables.remove(drop.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return tables;
    }

    private static Optional<String> tenantColumnIn(String body) {
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith(TENANT_COLUMN + " ")) {
                String definition = trimmed.substring(TENANT_COLUMN.length());
                return Optional.of(normalize(definition));
            }
        }
        return Optional.empty();
    }

    /**
     * 字段定义的规范形式：去掉 MySQL 的字段注释与行尾逗号，合并空白，小写
     */
    static String normalize(String definition) {
        String text = COLUMN_COMMENT.matcher(definition).replaceAll("").trim();
        if (text.endsWith(",")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        return text.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * MES 的实体：表名（小写）→ 实体类
     */
    static Map<String, Class<?>> entitiesByTable() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));
        Map<String, Class<?>> entities = new TreeMap<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> entity = load(definition.getBeanClassName());
            String table = entity.getAnnotation(TableName.class).value().toLowerCase(Locale.ROOT);
            Class<?> previous = entities.put(table, entity);
            if (previous != null) {
                throw new IllegalStateException("表 " + table + " 对应了多个实体：" + previous.getName() + "、" + entity.getName());
            }
        }
        return entities;
    }

    /**
     * MES 的 Mapper（继承 BaseMapper 的接口）→ 它的实体，按 Mapper 名称排序
     */
    static Map<Class<?>, Class<?>> mappers() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isInterface() && definition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(BaseMapper.class));
        List<Class<?>> mappers = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            mappers.add(load(definition.getBeanClassName()));
        }
        mappers.sort(Comparator.comparing(Class::getName));
        Map<Class<?>, Class<?>> result = new LinkedHashMap<>();
        for (Class<?> mapper : mappers) {
            result.put(mapper, entityOf(mapper));
        }
        return result;
    }

    /**
     * MES 中只做查询、没有实体的 Mapper：{@code mapper} 包下不继承 BaseMapper 的接口（例如分析报表的聚合查询），按名称排序。
     * 它们的方法都是手写 SQL，同样要在越权测试中登记
     */
    static List<Class<?>> queryMappers() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isInterface() && definition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter((reader, factory) -> reader.getClassMetadata().isInterface()
            && reader.getClassMetadata().getClassName().contains(".mapper."));
        scanner.addExcludeFilter(new AssignableTypeFilter(BaseMapper.class));
        List<Class<?>> mappers = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            mappers.add(load(definition.getBeanClassName()));
        }
        mappers.sort(Comparator.comparing(Class::getName));
        return mappers;
    }

    /**
     * Mapper 的实体：{@code BaseMapper<实体>} 的类型参数
     */
    static Class<?> entityOf(Class<?> mapper) {
        for (Type type : mapper.getGenericInterfaces()) {
            if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == BaseMapper.class
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> entity) {
                return entity;
            }
        }
        throw new IllegalStateException(mapper.getName() + " 应直接继承 BaseMapper<实体>");
    }

    /**
     * 主代码的 Java 源文件
     */
    static List<Path> mainSources() {
        Path root = moduleDir().resolve("src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(file -> file.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String source(Path file) {
        return read(file);
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

}
