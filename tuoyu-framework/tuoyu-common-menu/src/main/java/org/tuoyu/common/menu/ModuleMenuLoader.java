package org.tuoyu.common.menu;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 读取类路径中各业务模块的菜单声明
 *
 * @author tuoyu
 */
public final class ModuleMenuLoader {

    /**
     * 菜单声明的位置：每个模块一个文件，文件名为模块标识
     */
    public static final String LOCATION = "classpath*:META-INF/tuoyu/menus/*.json";

    /**
     * 读取声明在创建 Bean 时进行，不依赖容器中的 JSON 配置
     */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ModuleMenuLoader() {
    }

    /**
     * 读取全部菜单声明。文件名与其中的模块标识不一致、或没有菜单时视为声明错误，拒绝启动
     *
     * @param resolver 资源解析器
     * @return 菜单声明，按模块标识排序
     */
    public static List<ModuleMenuDeclaration> load(ResourcePatternResolver resolver) {
        Resource[] resources;
        try {
            resources = resolver.getResources(LOCATION);
        } catch (IOException e) {
            throw new IllegalStateException("读取业务模块的菜单声明失败：" + LOCATION, e);
        }
        List<ModuleMenuDeclaration> declarations = new ArrayList<>();
        for (Resource resource : resources) {
            declarations.add(parse(resource));
        }
        declarations.sort(Comparator.comparing(ModuleMenuDeclaration::getModule));
        return declarations;
    }

    static ModuleMenuDeclaration parse(Resource resource) {
        String name = resource.getFilename();
        ModuleMenuDeclaration declaration;
        try (InputStream in = resource.getInputStream()) {
            declaration = JSON.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), ModuleMenuDeclaration.class);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("菜单声明 " + name + " 无法解析：" + e.getMessage(), e);
        }
        if (declaration == null || declaration.getModule() == null || name == null
            || !name.equals(declaration.getModule() + ".json")) {
            throw new IllegalStateException("菜单声明 " + name + " 中的 module 应与文件名一致");
        }
        if (declaration.getMenus() == null || declaration.getMenus().isEmpty()) {
            throw new IllegalStateException("菜单声明 " + name + " 中没有菜单");
        }
        return declaration;
    }

}
