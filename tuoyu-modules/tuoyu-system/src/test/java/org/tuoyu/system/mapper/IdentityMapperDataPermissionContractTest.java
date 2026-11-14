package org.tuoyu.system.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.bo.SysUserBo;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户、角色、部门、岗位 Mapper 的数据权限声明：注解在接口方法上，
 * 由 Spring 代理在调用时生效
 */
@DisplayName("Mapper 数据权限声明")
class IdentityMapperDataPermissionContractTest {

    private static Map<String, String> columns(Class<?> type, String name, Class<?>... params) throws Exception {
        Method method = type.getMethod(name, params);
        DataPermission permission = method.getAnnotation(DataPermission.class);
        assertThat(permission).as(type.getSimpleName() + "." + name + " 应标注 @DataPermission").isNotNull();
        Map<String, String> map = new HashMap<>();
        for (DataColumn column : permission.value()) {
            for (int i = 0; i < column.key().length; i++) {
                map.put(column.key()[i], column.value()[i]);
            }
        }
        return map;
    }

    private static void assertNone(Class<?> type, String name, Class<?>... params) throws Exception {
        assertThat(type.getMethod(name, params).getAnnotation(DataPermission.class))
            .as(type.getSimpleName() + "." + name + " 不应标注 @DataPermission").isNull();
    }

    @Test
    @DisplayName("用户：列表、分页、计数、导出、已分配与未分配列表、三个更新方法")
    void userMapper() throws Exception {
        Map<String, String> plain = Map.of("deptName", "dept_id", "userName", "create_by");
        Map<String, String> joined = Map.of("deptName", "d.dept_id", "userName", "u.create_by");
        assertThat(columns(SysUserMapper.class, "selectPageUserList", Page.class, Wrapper.class)).isEqualTo(plain);
        assertThat(columns(SysUserMapper.class, "selectUserList", Wrapper.class)).isEqualTo(plain);
        assertThat(columns(SysUserMapper.class, "countUserById", Long.class)).isEqualTo(plain);
        assertThat(columns(SysUserMapper.class, "update", SysUser.class, Wrapper.class)).isEqualTo(plain);
        assertThat(columns(SysUserMapper.class, "update", Wrapper.class)).isEqualTo(plain);
        assertThat(columns(SysUserMapper.class, "updateById", SysUser.class)).isEqualTo(plain);
        assertThat(columns(SysUserMapper.class, "selectUserExportList", SysUserBo.class, List.class)).isEqualTo(joined);
        assertThat(columns(SysUserMapper.class, "countUserExportList", SysUserBo.class, List.class)).isEqualTo(joined);
        assertThat(columns(SysUserMapper.class, "selectAllocatedList", Page.class, SysUserBo.class)).isEqualTo(joined);
        assertThat(columns(SysUserMapper.class, "selectUnallocatedList", Page.class, SysUserBo.class, List.class)).isEqualTo(joined);
    }

    @Test
    @DisplayName("角色：按创建部门与创建人；用户的角色不受数据权限限制")
    void roleMapper() throws Exception {
        Map<String, String> cols = Map.of("deptName", "create_dept", "userName", "create_by");
        assertThat(columns(SysRoleMapper.class, "selectPageRoleList", Page.class, Wrapper.class)).isEqualTo(cols);
        assertThat(columns(SysRoleMapper.class, "selectRoleList", Wrapper.class)).isEqualTo(cols);
        assertThat(columns(SysRoleMapper.class, "selectRoleCount", Collection.class)).isEqualTo(cols);
        assertThat(columns(SysRoleMapper.class, "selectRoleById", Long.class)).isEqualTo(cols);
        assertNone(SysRoleMapper.class, "selectRolesByUserId", Long.class);
    }

    @Test
    @DisplayName("部门：只按部门列；部门及下级的计算不受数据权限限制（数据权限本身依赖它）")
    void deptMapper() throws Exception {
        Map<String, String> cols = Map.of("deptName", "dept_id");
        assertThat(columns(SysDeptMapper.class, "selectDeptList", Wrapper.class)).isEqualTo(cols);
        assertThat(columns(SysDeptMapper.class, "selectPageDeptList", Page.class, Wrapper.class)).isEqualTo(cols);
        assertThat(columns(SysDeptMapper.class, "countDeptById", Long.class)).isEqualTo(cols);
        assertNone(SysDeptMapper.class, "selectDeptAndChildById", Long.class);
        assertNone(SysDeptMapper.class, "selectDeptListByRoleId", Long.class, boolean.class);
    }

    @Test
    @DisplayName("岗位：按部门与创建人；用户的岗位不受数据权限限制")
    void postMapper() throws Exception {
        Map<String, String> cols = Map.of("deptName", "dept_id", "userName", "create_by");
        assertThat(columns(SysPostMapper.class, "selectPagePostList", Page.class, Wrapper.class)).isEqualTo(cols);
        assertThat(columns(SysPostMapper.class, "selectPostList", Wrapper.class)).isEqualTo(cols);
        assertThat(columns(SysPostMapper.class, "selectPostCount", Collection.class)).isEqualTo(cols);
        assertThat(columns(SysPostMapper.class, "selectPostOptions", Collection.class)).isEqualTo(cols);
        assertNone(SysPostMapper.class, "selectPostsByUserId", Long.class);
    }
}
