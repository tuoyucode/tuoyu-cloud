package org.tuoyu.boot.module;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 读取并校验业务模块的单体接入声明（{@code tuoyu.monolith.modules}）
 *
 * @author tuoyu
 */
public final class MonolithModules {

    /**
     * 声明所在的配置项
     */
    public static final String PROPERTY = "tuoyu.monolith.modules";

    /**
     * 声明文件：每个业务模块的 jar 中一份，单体启动时全部读取
     */
    public static final String LOCATION = "classpath*:META-INF/tuoyu/monolith-module.yml";

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]{1,30}");

    private static final Pattern PREFIX = Pattern.compile("/[a-z0-9-]+");

    private MonolithModules() {
    }

    /**
     * 读取全部声明；声明不完整时拒绝启动
     *
     * @param environment 环境
     * @return 模块名 → 声明，按声明顺序
     */
    public static Map<String, MonolithModule> bind(Environment environment) {
        Map<String, MonolithModule> modules = Binder.get(environment)
            .bind(PROPERTY, Bindable.mapOf(String.class, MonolithModule.class))
            .orElse(Map.of());
        validate(modules);
        return new LinkedHashMap<>(modules);
    }

    static void validate(Map<String, MonolithModule> modules) {
        modules.forEach((name, module) -> {
            if (!NAME.matcher(name).matches()) {
                throw new IllegalStateException(PROPERTY + " 中的模块名只能是小写字母、数字与连字符：" + name);
            }
            if (module.getPrefix() == null || !PREFIX.matcher(module.getPrefix()).matches()) {
                throw new IllegalStateException(PROPERTY + "." + name + ".prefix 应为一级路径（例如 /mes），当前为 " + module.getPrefix());
            }
            List<String> packages = module.getPackages();
            if (packages == null || packages.isEmpty() || packages.stream().anyMatch(p -> p == null || p.isBlank())) {
                throw new IllegalStateException(PROPERTY + "." + name + ".packages 不能为空");
            }
            for (String pkg : packages) {
                if (pkg.startsWith("org.tuoyu.common") || pkg.equals("org.tuoyu") || pkg.startsWith("org.tuoyu.boot")) {
                    throw new IllegalStateException(PROPERTY + "." + name + ".packages 不能包含框架的包：" + pkg);
                }
            }
        });
    }

}
