package org.tuoyu.system.dubbo;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.api.RemoteDataScopeService;
import org.tuoyu.system.domain.SysRoleDept;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysRoleDeptMapper;

import java.util.List;

/**
 * 数据权限服务的跨模块实现：数据权限改写 SQL 时取部门范围。
 * <p>
 * 结果原样拼进数据权限 SQL，只会是逗号分隔的整数或 {@code -1}（查不到任何数据）。
 * 本类只调用不带数据权限的 Mapper 方法，否则查询本身会再被数据权限改写而递归。
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class RemoteDataScopeServiceImpl implements RemoteDataScopeService {

    /**
     * 没有部门时的取值：拼进 SQL 后查不到任何数据
     */
    private static final String NONE = "-1";

    private final SysRoleDeptMapper roleDeptMapper;
    private final SysDeptMapper deptMapper;

    @Override
    @Cacheable(cacheNames = CacheNames.SYS_ROLE_CUSTOM, key = "#roleId", condition = "#roleId != null")
    public String getRoleCustom(Long roleId) {
        if (roleId == null) {
            return NONE;
        }
        List<SysRoleDept> rows = roleDeptMapper.selectList(QueryBuilder.lambda(SysRoleDept.class)
            .select(SysRoleDept::getDeptId)
            .eq(SysRoleDept::getRoleId, roleId)
            .build());
        return joinIds(StreamUtils.toList(rows, row -> row == null ? null : row.getDeptId()));
    }

    @Override
    @Cacheable(cacheNames = CacheNames.SYS_DEPT_AND_CHILD, key = "#deptId", condition = "#deptId != null")
    public String getDeptAndChild(Long deptId) {
        if (deptId == null) {
            return NONE;
        }
        return joinIds(deptMapper.selectDeptAndChildById(deptId));
    }

    /**
     * 只由整数组成的逗号连接串；没有时为 {@value #NONE}
     */
    private static String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return NONE;
        }
        String joined = StreamUtils.join(ids, id -> id == null ? null : String.valueOf(id.longValue()));
        return StringUtils.isEmpty(joined) ? NONE : joined;
    }

}
