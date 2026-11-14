package org.tuoyu.common.core.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("当前用户与角色的数据类")
class CurrentUserTest {

    @Test
    @DisplayName("新建的当前用户：集合字段为空集合而不是 null，不是超级管理员")
    void defaults() {
        CurrentUser user = new CurrentUser();
        assertThat(user.getUserId()).isNull();
        assertThat(user.getRoles()).isNotNull().isEmpty();
        assertThat(user.getRoleKeys()).isNotNull().isEmpty();
        assertThat(user.getPermissions()).isNotNull().isEmpty();
        assertThat(user.getDataScopeRoleMap()).isNotNull().isEmpty();
        assertThat(user.isSuperAdmin()).isFalse();
        assertThat(user).isInstanceOf(Serializable.class);
    }

    @Test
    @DisplayName("全部字段可读写")
    void properties() {
        CurrentUser user = new CurrentUser();
        user.setUserId(9L);
        user.setUserName("admin");
        user.setNickname("管理员");
        user.setUserType("sys_user");
        user.setDeptId(100L);
        user.setDeptName("研发部");
        user.setDeptCategory("rd");
        user.setTenantId("000000");
        user.setSuperAdmin(true);
        user.setClientKey("pc");
        user.setDeviceType("pc");
        user.setBrowser("Chrome");
        user.setOs("Windows");
        user.setRoles(List.of(new CurrentRole(2L, "worker", "3")));
        user.setRoleKeys(Set.of("worker"));
        user.setPermissions(Set.of("system:user:list"));
        user.setDataScopeRoleMap(Map.of("system:user:list", List.of(2L)));

        assertThat(user.getUserId()).isEqualTo(9L);
        assertThat(user.getUserName()).isEqualTo("admin");
        assertThat(user.getNickname()).isEqualTo("管理员");
        assertThat(user.getUserType()).isEqualTo("sys_user");
        assertThat(user.getDeptId()).isEqualTo(100L);
        assertThat(user.getDeptName()).isEqualTo("研发部");
        assertThat(user.getDeptCategory()).isEqualTo("rd");
        assertThat(user.getTenantId()).isEqualTo("000000");
        assertThat(user.isSuperAdmin()).isTrue();
        assertThat(user.getClientKey()).isEqualTo("pc");
        assertThat(user.getDeviceType()).isEqualTo("pc");
        assertThat(user.getBrowser()).isEqualTo("Chrome");
        assertThat(user.getOs()).isEqualTo("Windows");
        assertThat(user.getRoles()).containsExactly(new CurrentRole(2L, "worker", "3"));
        assertThat(user.getRoleKeys()).containsExactly("worker");
        assertThat(user.getPermissions()).containsExactly("system:user:list");
        assertThat(user.getDataScopeRoleMap()).containsEntry("system:user:list", List.of(2L));
    }

    @Test
    @DisplayName("角色：三参构造器的顺序为 roleId、roleKey、dataScope，按值相等")
    void role() {
        CurrentRole role = new CurrentRole(2L, "planner", "3");
        assertThat(role.getRoleId()).isEqualTo(2L);
        assertThat(role.getRoleKey()).isEqualTo("planner");
        assertThat(role.getDataScope()).isEqualTo("3");
        assertThat(role).isEqualTo(new CurrentRole(2L, "planner", "3")).isInstanceOf(Serializable.class);

        CurrentRole empty = new CurrentRole();
        empty.setRoleId(5L);
        empty.setRoleKey("dept");
        empty.setDataScope("1");
        assertThat(empty).isEqualTo(new CurrentRole(5L, "dept", "1"));
    }
}
