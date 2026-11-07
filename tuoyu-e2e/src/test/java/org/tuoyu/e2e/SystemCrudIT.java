package org.tuoyu.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.tuoyu.e2e.support.ApiResponse;
import org.tuoyu.e2e.support.E2eClient;
import org.tuoyu.e2e.support.E2eConfig;
import tools.jackson.databind.JsonNode;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 部门、岗位、用户的增删改查。测试数据带随机后缀，自己创建、自己清理（测试中途失败时在 {@link #cleanUp()} 中补删）。
 * 新增用户的接口要求加密传输（请求中有明文初始口令），新建的用户随后用自己的口令登录一次。
 *
 * @author tuoyu
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SystemCrudIT {

    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String suffix = random(8);

    private final String deptName = "E2E部门-" + suffix;

    private final String postCode = "e2e_" + suffix;

    private final String userName = "e2e_" + suffix;

    private final String userPassword = "E2e-" + random(20);

    private E2eClient anonymous;

    private E2eClient admin;

    private long rootDeptId;

    private long roleId;

    private Long deptId;

    private Long postId;

    private Long userId;

    @BeforeAll
    void setUp() {
        E2eConfig config = E2eConfig.load();
        anonymous = E2eClient.anonymous(config);
        admin = anonymous.loginAsAdmin();

        rootDeptId = admin.get("/system/dept/list").assertOk("查询部门列表").list().stream()
            .filter(dept -> ApiResponse.id(dept.path("parentId")) == 0L)
            .map(dept -> ApiResponse.id(dept.path("deptId")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("没有找到根部门"));
        // 新用户至少要有一个角色：取一个正常状态的非超级管理员角色
        roleId = admin.get("/system/role/list", Map.of("page", 1, "pageSize", 100)).assertOk("查询角色列表").items().stream()
            .filter(role -> !"superadmin".equals(role.path("roleKey").asString()))
            .filter(role -> "0".equals(role.path("status").asString()))
            .map(role -> ApiResponse.id(role.path("roleId")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("没有可分配的非超级管理员角色"));
    }

    @AfterAll
    void cleanUp() {
        // 按依赖顺序补删：用户 → 岗位 → 部门。正常结束时这些 ID 已在测试中置空
        if (admin == null) {
            return;
        }
        if (userId != null) {
            quietly("删除用户", () -> admin.delete("/system/user/" + userId));
        }
        if (postId != null) {
            quietly("删除岗位", () -> admin.delete("/system/post/" + postId));
        }
        if (deptId != null) {
            quietly("删除部门", () -> admin.delete("/system/dept/" + deptId));
        }
        admin.logoutQuietly();
    }

    @Test
    @Order(1)
    void createDept() {
        admin.post("/system/dept", Map.of(
            "parentId", rootDeptId,
            "deptName", deptName,
            "orderNum", 99,
            "status", "0")).assertOk("新增部门");

        deptId = admin.get("/system/dept/list", Map.of("deptName", deptName)).assertOk("按名称查询部门").list().stream()
            .filter(dept -> deptName.equals(dept.path("deptName").asString()))
            .map(dept -> ApiResponse.id(dept.path("deptId")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("新增的部门查询不到：" + deptName));
    }

    @Test
    @Order(2)
    void updateDept() {
        String newName = deptName + "-改";
        admin.put("/system/dept", Map.of(
            "deptId", requireId(deptId, "部门"),
            "parentId", rootDeptId,
            "deptName", newName,
            "orderNum", 98,
            "status", "0")).assertOk("修改部门");

        JsonNode dept = admin.get("/system/dept/" + deptId).assertOk("查询部门详情").data();
        assertThat(dept.path("deptName").asString()).isEqualTo(newName);
        assertThat(dept.path("orderNum").asInt()).isEqualTo(98);
    }

    @Test
    @Order(3)
    void createPost() {
        admin.post("/system/post", Map.of(
            "deptId", requireId(deptId, "部门"),
            "postCode", postCode,
            "postName", "E2E岗位-" + suffix,
            "postSort", 99,
            "status", "0")).assertOk("新增岗位");

        postId = admin.get("/system/post/list", Map.of("postCode", postCode, "page", 1, "pageSize", 10))
            .assertOk("按编码查询岗位").items().stream()
            .filter(post -> postCode.equals(post.path("postCode").asString()))
            .map(post -> ApiResponse.id(post.path("postId")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("新增的岗位查询不到：" + postCode));
    }

    @Test
    @Order(4)
    void updatePost() {
        String newName = "E2E岗位-" + suffix + "-改";
        admin.put("/system/post", Map.of(
            "postId", requireId(postId, "岗位"),
            "deptId", deptId,
            "postCode", postCode,
            "postName", newName,
            "postSort", 98,
            "status", "0")).assertOk("修改岗位");

        JsonNode post = admin.get("/system/post/" + postId).assertOk("查询岗位详情").data();
        assertThat(post.path("postName").asString()).isEqualTo(newName);
        assertThat(post.path("postSort").asInt()).isEqualTo(98);
    }

    @Test
    @Order(5)
    void createUserWithEncryptedRequest() {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("deptId", requireId(deptId, "部门"));
        user.put("userName", userName);
        user.put("nickName", "E2E用户");
        user.put("password", userPassword);
        user.put("status", "0");
        user.put("roleIds", List.of(roleId));
        user.put("postIds", List.of(requireId(postId, "岗位")));
        // 新增用户的请求带明文初始口令，接口要求加密传输
        admin.postEncrypted("/system/user", user).assertOk("新增用户（加密请求）");

        userId = findUser().map(row -> ApiResponse.id(row.path("userId")))
            .orElseThrow(() -> new AssertionError("新增的用户查询不到：" + userName));
    }

    @Test
    @Order(6)
    void updateUser() {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("userId", requireId(userId, "用户"));
        user.put("deptId", deptId);
        user.put("userName", userName);
        user.put("nickName", "E2E用户-改");
        user.put("status", "0");
        user.put("roleIds", List.of(roleId));
        user.put("postIds", List.of(postId));
        admin.put("/system/user", user).assertOk("修改用户");

        JsonNode info = admin.get("/system/user/" + userId).assertOk("查询用户详情").data();
        assertThat(info.path("user").path("nickName").asString()).isEqualTo("E2E用户-改");
        assertThat(ApiResponse.id(info.path("user").path("deptId"))).isEqualTo(deptId);
        assertThat(ids(info.path("roleIds"))).contains(roleId);
        assertThat(ids(info.path("postIds"))).contains(postId);
    }

    @Test
    @Order(7)
    void newUserCanLogin() {
        E2eClient user = anonymous.login(userName, userPassword);
        try {
            JsonNode info = user.get("/system/user/getInfo").assertOk("新用户获取用户信息").data();
            assertThat(info.path("user").path("userName").asString()).isEqualTo(userName);

            // 选择用户（工作流转办、抄送等）只要求登录，且只返回识别用户所需的字段，不含联系方式
            List<JsonNode> options = user.get("/system/user/options", Map.of("page", 1, "pageSize", 10, "userName", userName))
                .assertOk("普通用户选择用户").items();
            assertThat(options).as("应能选到刚创建的用户").isNotEmpty();
            assertThat(options.getFirst().has("phoneNumber")).as("选择用户的结果不应含手机号").isFalse();
            assertThat(options.getFirst().has("email")).as("选择用户的结果不应含邮箱").isFalse();
            boolean canManageUsers = false;
            for (JsonNode permission : info.path("permissions")) {
                String code = permission.asString();
                canManageUsers |= "system:user:list".equals(code) || "*:*:*".equals(code);
            }
            if (!canManageUsers) {
                user.get("/system/user/list").assertError(403, "没有用户管理权限时不能查询用户列表");
            }
        } finally {
            user.post("/auth/logout", Map.of()).assertOk("新用户登出");
        }
    }

    @Test
    @Order(8)
    void deleteUser() {
        admin.delete("/system/user/" + requireId(userId, "用户")).assertOk("删除用户");
        assertThat(findUser()).as("删除后不应再查询到用户").isEmpty();
        userId = null;
    }

    @Test
    @Order(9)
    void deletePost() {
        admin.delete("/system/post/" + requireId(postId, "岗位")).assertOk("删除岗位");
        ApiResponse detail = admin.get("/system/post/" + postId);
        assertThat(detail.code() != ApiResponse.SUCCESS || detail.data().isNull() || detail.data().isMissingNode())
            .as("删除后不应再查询到岗位：" + detail).isTrue();
        postId = null;
    }

    @Test
    @Order(10)
    void deleteDept() {
        admin.delete("/system/dept/" + requireId(deptId, "部门")).assertOk("删除部门");
        boolean stillListed = admin.get("/system/dept/list").assertOk("查询部门列表").list().stream()
            .anyMatch(dept -> ApiResponse.id(dept.path("deptId")) == deptId);
        assertThat(stillListed).as("删除后部门列表中不应再有它").isFalse();
        deptId = null;
    }

    @Test
    @Order(11)
    void pagingAndSorting() {
        // 前面的步骤登录过多次，登录日志至少有 2 条。sort 可以重复传多个，按顺序优先（同一秒内的登录再按编号排）
        List<String> sort = List.of("loginTime,desc", "infoId,desc");
        ApiResponse first = admin.get("/monitor/loginInfo/list", Map.of("page", 1, "pageSize", 2, "sort", sort))
            .assertOk("分页查询登录日志");
        List<JsonNode> items = first.items();
        assertThat(items).as("每页 2 条").hasSize(2);
        assertThat(first.total()).as("total 是全部行数").isGreaterThanOrEqualTo(2);
        // 时间格式为 yyyy-MM-dd HH:mm:ss，按字符串比较即按时间比较
        assertThat(items.get(0).path("loginTime").asString())
            .as("按登录时间倒序").isGreaterThanOrEqualTo(items.get(1).path("loginTime").asString());

        ApiResponse second = admin.get("/monitor/loginInfo/list", Map.of("page", 2, "pageSize", 1, "sort", sort))
            .assertOk("查询登录日志第 2 页");
        assertThat(second.total()).as("翻页不影响 total").isGreaterThanOrEqualTo(2);
        assertThat(second.items()).hasSize(1);
        assertThat(second.items().getFirst().path("infoId").asString())
            .as("第 2 页（每页 1 条）是第 1 页（每页 2 条）的第 2 行")
            .isEqualTo(items.get(1).path("infoId").asString());

        // 排序属性只能来自实体，其他一律拒绝
        admin.get("/monitor/loginInfo/list", Map.of("page", 1, "pageSize", 2, "sort", "noSuchField,desc"))
            .assertError(400, "按不存在的属性排序");
    }

    private java.util.Optional<JsonNode> findUser() {
        return admin.get("/system/user/list", Map.of("userName", userName, "page", 1, "pageSize", 10))
            .assertOk("按账号查询用户").items().stream()
            .filter(row -> userName.equals(row.path("userName").asString()))
            .findFirst();
    }

    private static long requireId(Long id, String what) {
        assertThat(id).as("前面的步骤没有创建出" + what + "，无法继续").isNotNull();
        return id;
    }

    private static List<Long> ids(JsonNode array) {
        return array.valueStream().map(ApiResponse::id).toList();
    }

    private static void quietly(String what, java.util.function.Supplier<ApiResponse> action) {
        try {
            ApiResponse response = action.get();
            if (response.code() != ApiResponse.SUCCESS) {
                System.err.println("清理测试数据未成功（" + what + "）：" + response);
            }
        } catch (RuntimeException e) {
            System.err.println("清理测试数据失败（" + what + "）：" + e.getMessage());
        }
    }

    private static String random(int length) {
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return text.toString();
    }
}
