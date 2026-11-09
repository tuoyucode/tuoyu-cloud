package org.tuoyu.common.menu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.system.api.RemoteMenuService;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("业务模块的菜单登记（调用方）")
class ModuleMenuRegistrarTest {

    private final RemoteMenuService menuService = mock(RemoteMenuService.class);

    private static ModuleMenuProperties properties(int attempts) {
        ModuleMenuProperties properties = new ModuleMenuProperties();
        properties.setMaxAttempts(attempts);
        properties.setRetryInterval(Duration.ZERO);
        return properties;
    }

    private static ModuleMenuDeclaration declaration() {
        return ModuleMenuLoader.load(new PathMatchingResourcePatternResolver()).get(0);
    }

    @Test
    @DisplayName("读取类路径中的菜单声明")
    void loadsDeclarations() {
        List<ModuleMenuDeclaration> declarations = ModuleMenuLoader.load(new PathMatchingResourcePatternResolver());

        assertThat(declarations).extracting(ModuleMenuDeclaration::getModule).containsExactly("demo");
        assertThat(declarations.get(0).getMenus()).hasSize(2);
        assertThat(declarations.get(0).getMenus().get(1).getPerms()).isEqualTo("demo:item:list");
    }

    @Test
    @DisplayName("文件名与模块标识不一致、没有菜单或无法解析时拒绝")
    void rejectsMalformedDeclarations() {
        assertThatThrownBy(() -> ModuleMenuLoader.parse(json("other.json", "{\"module\":\"demo\",\"menus\":[{\"menuId\":1}]}")))
            .hasMessageContaining("文件名");
        assertThatThrownBy(() -> ModuleMenuLoader.parse(json("demo.json", "{\"module\":\"demo\",\"menus\":[]}")))
            .hasMessageContaining("没有菜单");
        assertThatThrownBy(() -> ModuleMenuLoader.parse(json("demo.json", "{")))
            .hasMessageContaining("无法解析");
    }

    @Test
    @DisplayName("tuoyu-system 暂时不可用时重试，成功后停止")
    void retriesUntilAvailable() {
        when(menuService.registerModuleMenus(eq("demo"), anyList()))
            .thenThrow(new IllegalStateException("No provider available"))
            .thenReturn(2);
        ModuleMenuRegistrar registrar = new ModuleMenuRegistrar(List.of(declaration()), menuService, properties(3));

        assertThat(registrar.register(declaration())).isTrue();
        verify(menuService, times(2)).registerModuleMenus(eq("demo"), anyList());
    }

    @Test
    @DisplayName("声明不合法时不重试")
    void doesNotRetryInvalidDeclaration() {
        when(menuService.registerModuleMenus(any(), anyList())).thenThrow(new ServiceException("权限码不合法"));
        ModuleMenuRegistrar registrar = new ModuleMenuRegistrar(List.of(declaration()), menuService, properties(5));

        assertThat(registrar.register(declaration())).isFalse();
        verify(menuService, times(1)).registerModuleMenus(any(), anyList());
    }

    @Test
    @DisplayName("超过最大次数后放弃")
    void givesUpAfterMaxAttempts() {
        when(menuService.registerModuleMenus(any(), anyList())).thenThrow(new IllegalStateException("No provider available"));
        ModuleMenuRegistrar registrar = new ModuleMenuRegistrar(List.of(declaration()), menuService, properties(3));

        assertThat(registrar.register(declaration())).isFalse();
        verify(menuService, times(3)).registerModuleMenus(any(), anyList());
    }

    private static ByteArrayResource json(String name, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }

}
