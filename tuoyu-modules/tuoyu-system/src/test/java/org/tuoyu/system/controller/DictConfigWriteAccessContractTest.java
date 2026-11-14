package org.tuoyu.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.system.controller.system.SysClientController;
import org.tuoyu.system.controller.system.SysConfigController;
import org.tuoyu.system.controller.system.SysDictDataController;
import org.tuoyu.system.controller.system.SysDictTypeController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 字典、参数、客户端是平台共享的数据：写接口只允许超级管理员。
 * 读取接口（列表、详情、导出、按键或类型读取、下拉）不限制角色
 */
@DisplayName("字典、参数、客户端的写接口只允许超级管理员")
class DictConfigWriteAccessContractTest {

    /**
     * 写接口：新增（POST，导出除外）、修改（PUT）、删除与刷新缓存（DELETE）
     */
    private static List<String> writeMethods(Class<?> controller) {
        List<String> names = new ArrayList<>();
        for (Method method : controller.getDeclaredMethods()) {
            PostMapping post = method.getAnnotation(PostMapping.class);
            boolean export = post != null && List.of(post.value()).contains("/export");
            if ((post != null && !export) || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)) {
                names.add(method.getName());
            }
        }
        return names;
    }

    private static List<String> readMethods(Class<?> controller) {
        List<String> names = new ArrayList<>();
        for (Method method : controller.getDeclaredMethods()) {
            PostMapping post = method.getAnnotation(PostMapping.class);
            boolean export = post != null && List.of(post.value()).contains("/export");
            if (method.isAnnotationPresent(GetMapping.class) || export) {
                names.add(method.getName());
            }
        }
        return names;
    }

    private static boolean superAdminOnly(Class<?> controller, String name) {
        for (Method method : controller.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                SaCheckRole role = method.getAnnotation(SaCheckRole.class);
                return role != null && List.of(role.value()).equals(List.of(SystemConstants.SUPER_ADMIN_ROLE_KEY));
            }
        }
        return false;
    }

    private static void check(Class<?> controller, Set<String> expectedWrites) {
        assertThat(writeMethods(controller)).containsExactlyInAnyOrderElementsOf(expectedWrites);
        assertThat(expectedWrites).allSatisfy(name -> assertThat(superAdminOnly(controller, name))
            .as(controller.getSimpleName() + "." + name + " 应标注 @SaCheckRole(\"superadmin\")").isTrue());
        assertThat(readMethods(controller)).allSatisfy(name -> assertThat(controller.getDeclaredMethods())
            .filteredOn(m -> m.getName().equals(name))
            .allSatisfy(m -> assertThat(m.getAnnotation(SaCheckRole.class)).as(name + " 是读取接口，不限制角色").isNull()));
    }

    @Test
    @DisplayName("参数：新增、修改、按键修改、删除、刷新缓存")
    void config() {
        check(SysConfigController.class, Set.of("add", "edit", "updateByKey", "remove", "refreshCache"));
    }

    @Test
    @DisplayName("字典类型：新增、修改、删除、刷新缓存")
    void dictType() {
        check(SysDictTypeController.class, Set.of("add", "edit", "remove", "refreshCache"));
    }

    @Test
    @DisplayName("字典数据：新增、修改、删除")
    void dictData() {
        check(SysDictDataController.class, Set.of("add", "edit", "remove"));
    }

    @Test
    @DisplayName("客户端：新增、修改、启用与停用、删除")
    void client() {
        check(SysClientController.class, Set.of("add", "edit", "changeStatus", "remove"));
    }
}
