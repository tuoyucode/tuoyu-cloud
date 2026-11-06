package org.tuoyu.common.redis.codec;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.DatabindContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 反序列化的类型白名单。
 * <p>
 * Redis 中的 JSON 会携带类名（Jackson default typing），反序列化时按类名创建对象。若不加限制，
 * 能写入 Redis 的人就能让服务实例化任意类，构成远程代码执行风险。本校验器只放行白名单中的类型：
 * <ul>
 *     <li>按<b>类名</b>判断，在类被加载之前完成，不在白名单中的类不会被加载；</li>
 *     <li>JDK 类型逐个列出，不使用 {@code java.util.} 这类宽泛前缀；</li>
 *     <li>基本类型数组放行，对象数组要求元素类型也在白名单中；</li>
 *     <li>业务代码自己的缓存类型可通过 {@code tuoyu.redis.allowed-type-prefixes} 扩展，过于宽泛的前缀启动即报错；</li>
 *     <li>拒绝时按类名记录一次 WARN 日志，便于发现遗漏的类型。</li>
 * </ul>
 *
 * @author tuoyu
 */
@Slf4j
public final class RedisTypeAllowlist extends PolymorphicTypeValidator.Base {

    /**
     * 默认放行的包前缀
     */
    static final List<String> DEFAULT_PREFIXES = List.of(
        // 本项目自身的类型：登录用户、在线用户、推送消息、总线消息、缓存的 VO 等
        "org.tuoyu.",
        // Sa-Token 会话
        "cn.dev33.satoken.session.",
        // java.time 下的类型均为不可变的值对象
        "java.time.",
        // Collections.emptyList()、unmodifiableList() 等
        "java.util.Collections$",
        // List.of()、Set.of()、Map.of()
        "java.util.ImmutableCollections$");

    /**
     * 默认放行的具体类型
     */
    static final Set<String> DEFAULT_CLASSES = Set.of(
        // 集合
        "java.util.ArrayList", "java.util.LinkedList", "java.util.Vector", "java.util.Arrays$ArrayList",
        "java.util.HashMap", "java.util.LinkedHashMap", "java.util.TreeMap",
        "java.util.HashSet", "java.util.LinkedHashSet", "java.util.TreeSet",
        "java.util.concurrent.ConcurrentHashMap", "java.util.concurrent.CopyOnWriteArrayList",
        // 基本值类型（出现在 Object 类型的字段中时会携带类名）
        "java.lang.Long", "java.lang.Integer", "java.lang.Short", "java.lang.Byte",
        "java.lang.Double", "java.lang.Float", "java.lang.Boolean",
        "java.math.BigDecimal", "java.math.BigInteger", "java.util.UUID",
        // 日期：声明为 Date 的字段，从数据库读出的实际值可能是 java.sql.Timestamp
        "java.util.Date", "java.sql.Timestamp", "java.sql.Date", "java.sql.Time",
        // Spring Cache 缓存 null 值时的占位对象
        "org.redisson.spring.cache.NullValue",
        // Dubbo 使用 fastjson2 序列化时，Object 类型字段可能被还原为这两种类型
        "com.alibaba.fastjson2.JSONObject", "com.alibaba.fastjson2.JSONArray");

    /**
     * 扩展前缀不允许指向的根包：JDK 自身，以及历史上存在反序列化利用链的常见框架
     */
    private static final List<String> FORBIDDEN_EXTENSION_ROOTS = List.of(
        "java.", "javax.", "jakarta.", "jdk.", "sun.", "com.sun.",
        "org.springframework.", "org.apache.", "org.hibernate.", "org.jboss.", "org.codehaus.", "org.mybatis.",
        "com.fasterxml.", "tools.jackson.", "ch.qos.", "com.mchange.", "com.zaxxer.", "net.sf.");

    private static final Set<String> PRIMITIVE_ARRAY_CODES = Set.of("Z", "B", "C", "S", "I", "J", "F", "D");

    /**
     * 本进程启动以来被拒绝的反序列化次数（同一类型重复被拒也计数），供安全中心展示
     */
    private static final AtomicLong REJECTED = new AtomicLong();

    private final List<String> prefixes;

    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    /**
     * @param extraPrefixes 业务扩展的包前缀或类名，可为空
     * @throws IllegalArgumentException 扩展前缀过于宽泛或指向受限的根包
     */
    public RedisTypeAllowlist(List<String> extraPrefixes) {
        List<String> all = new ArrayList<>(DEFAULT_PREFIXES);
        if (extraPrefixes != null) {
            extraPrefixes.stream().map(String::trim).filter(p -> !p.isEmpty()).forEach(prefix -> {
                validateExtension(prefix);
                all.add(prefix);
            });
        }
        this.prefixes = List.copyOf(all);
    }

    @Override
    public Validity validateBaseType(DatabindContext ctxt, JavaType baseType) {
        return Validity.INDETERMINATE;
    }

    @Override
    public Validity validateSubClassName(DatabindContext ctxt, JavaType baseType, String subClassName) {
        return decide(subClassName);
    }

    @Override
    public Validity validateSubType(DatabindContext ctxt, JavaType baseType, JavaType subType) {
        return decide(subType.getRawClass().getName());
    }

    /**
     * 判断类名是否在白名单中，支持 JVM 数组名（如 [B、[Ljava.lang.Long;）与 Java 写法（如 byte[]）
     */
    public boolean isAllowed(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        if (className.startsWith("[")) {
            String component = className.replaceFirst("^\\[+", "");
            if (PRIMITIVE_ARRAY_CODES.contains(component)) {
                return true;
            }
            return component.startsWith("L") && component.endsWith(";")
                && isAllowedArrayComponent(component.substring(1, component.length() - 1));
        }
        if (className.endsWith("[]")) {
            String component = className.substring(0, className.indexOf('['));
            return isPrimitiveName(component) || isAllowedArrayComponent(component);
        }
        return DEFAULT_CLASSES.contains(className) || prefixes.stream().anyMatch(className::startsWith);
    }

    private Validity decide(String className) {
        if (isAllowed(className)) {
            return Validity.ALLOWED;
        }
        REJECTED.incrementAndGet();
        if (reported.add(className)) {
            log.warn("Redis 反序列化拒绝了不在白名单中的类型：{}。如果这是业务代码需要缓存的类型，"
                + "请将其包名加入配置 tuoyu.redis.allowed-type-prefixes", className);
        }
        return Validity.DENIED;
    }

    /**
     * 本进程启动以来被拒绝的反序列化次数
     */
    public static long rejectedCount() {
        return REJECTED.get();
    }

    static void validateExtension(String prefix) {
        long segments = Arrays.stream(prefix.split("\\.")).filter(s -> !s.isEmpty()).count();
        if (segments < 2) {
            throw new IllegalArgumentException("tuoyu.redis.allowed-type-prefixes 中的前缀过于宽泛：" + prefix
                + "，至少需要两级包名，例如 com.acme.");
        }
        for (String root : FORBIDDEN_EXTENSION_ROOTS) {
            if (prefix.startsWith(root) || root.startsWith(prefix)) {
                throw new IllegalArgumentException("tuoyu.redis.allowed-type-prefixes 不允许放行 " + root
                    + " 下的类型：" + prefix + "。JDK 与常见框架的类型由框架统一管理");
            }
        }
    }

    /**
     * Object[] 的元素各自带有类名并逐个校验，因此数组本身可以放行
     */
    private boolean isAllowedArrayComponent(String component) {
        return "java.lang.Object".equals(component) || isAllowed(component);
    }

    private static boolean isPrimitiveName(String name) {
        return Set.of("boolean", "byte", "char", "short", "int", "long", "float", "double").contains(name);
    }

}
