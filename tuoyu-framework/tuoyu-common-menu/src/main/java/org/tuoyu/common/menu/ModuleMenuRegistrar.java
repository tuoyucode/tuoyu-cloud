package org.tuoyu.common.menu;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.system.api.RemoteMenuService;

import java.time.Duration;
import java.util.List;

/**
 * 服务启动完成后登记业务模块的菜单。
 * <p>
 * 在后台线程中执行，不阻塞启动：微服务方式下 tuoyu-system 可能还没有启动，失败时按间隔重试；
 * 声明本身不合法（tuoyu-system 返回业务异常）时不再重试，记录错误日志，修正声明后重启才能登记。
 * 登记是幂等的，每个实例每次启动都会执行一次。
 *
 * @author tuoyu
 */
@Slf4j
public class ModuleMenuRegistrar implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    private final List<ModuleMenuDeclaration> declarations;

    private final RemoteMenuService menuService;

    private final ModuleMenuProperties properties;

    private volatile Thread worker;

    public ModuleMenuRegistrar(List<ModuleMenuDeclaration> declarations, RemoteMenuService menuService,
                               ModuleMenuProperties properties) {
        this.declarations = List.copyOf(declarations);
        this.menuService = menuService;
        this.properties = properties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (declarations.isEmpty() || worker != null) {
            return;
        }
        worker = Thread.ofVirtual().name("module-menu-registrar").start(() -> declarations.forEach(this::register));
    }

    /**
     * 登记一个模块的菜单，必要时重试
     *
     * @param declaration 菜单声明
     * @return 是否登记成功
     */
    boolean register(ModuleMenuDeclaration declaration) {
        int attempts = Math.max(1, properties.getMaxAttempts());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                int inserted = menuService.registerModuleMenus(declaration.getModule(), declaration.getMenus());
                log.info("模块 {} 的菜单已登记：声明 {} 个，新写入 {} 个", declaration.getModule(),
                    declaration.getMenus().size(), inserted);
                return true;
            } catch (ServiceException e) {
                log.error("模块 {} 的菜单声明不合法，未登记：{}", declaration.getModule(), e.getMessage());
                return false;
            } catch (RuntimeException e) {
                if (attempt == attempts) {
                    log.error("模块 {} 的菜单登记失败，已尝试 {} 次：{}", declaration.getModule(), attempts, e.getMessage());
                    return false;
                }
                log.warn("模块 {} 的菜单登记失败（第 {} 次），{} 后重试：{}", declaration.getModule(), attempt,
                    properties.getRetryInterval(), e.getMessage());
                if (!sleep(properties.getRetryInterval())) {
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean sleep(Duration interval) {
        try {
            Thread.sleep(interval.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void destroy() {
        Thread current = worker;
        if (current != null) {
            current.interrupt();
        }
    }

}
