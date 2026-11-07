package org.tuoyu.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.tuoyu.e2e.support.ApiResponse;
import org.tuoyu.e2e.support.E2eClient;
import org.tuoyu.e2e.support.E2eConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工作流模块使用自己的库（tuoyu-workflow）：微服务中是独立的服务，单体中按模块切换数据源。
 * 数据源切错时这里的查询会因为表不存在而失败
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("工作流：读取自己的库")
class WorkflowIT {

    private E2eClient admin;

    @BeforeAll
    void login() {
        admin = E2eClient.anonymous(E2eConfig.load()).loginAsAdmin();
    }

    @AfterAll
    void logout() {
        if (admin != null) {
            admin.logoutQuietly();
        }
    }

    @Test
    @DisplayName("流程分类（flow_category，在 tuoyu-workflow 库）可以读取")
    void listCategories() {
        ApiResponse response = admin.get("/workflow/category/list").assertOk("查询流程分类");
        assertThat(response.list()).as("种子数据中有流程分类").isNotEmpty();
    }

    @Test
    @DisplayName("流程定义列表（Warm-Flow 的表，在 tuoyu-workflow 库）可以读取")
    void listDefinitions() {
        admin.get("/workflow/definition/list").assertOk("查询流程定义");
    }

    @Test
    @DisplayName("Warm-Flow 自带接口按用途控制权限：设计器接口需要流程定义权限（管理员可用），内置表单办理对所有人关闭")
    void warmFlowEndpointsShouldBeGuarded() {
        // Warm-Flow 自带接口返回它自己的结构（成功时 code 为 200），供它自带的设计器使用
        ApiResponse handlerType = admin.get("/warm-flow/handler-type");
        assertThat(handlerType.status()).as("设计器读取办理人类型：" + handlerType).isEqualTo(200);
        assertThat(handlerType.code()).as("设计器读取办理人类型：" + handlerType).isEqualTo(200);
        // 拒绝访问由本框架的权限校验返回，是统一的错误结构
        admin.get("/warm-flow/execute/load/1").assertError(403, "内置表单办理接口不开放");
    }

}
