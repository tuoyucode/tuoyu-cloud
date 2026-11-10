package org.tuoyu.mes.plan.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.plan.domain.MesWorkorder;

import java.util.Collection;
import java.util.List;

/**
 * 生产工单
 * <p>
 * 名称带 {@code InScope} 的方法受数据权限约束（按创建部门与创建者），供列表、导出、下拉、看板与按 ID 的接口使用；
 * BaseMapper 自带的方法不带数据权限，只用于单据之间的内部读取与按条件的更新。
 * 数据权限由 Spring 代理在调用这里的方法时设置，方法内部再调用的 BaseMapper 方法在同一次调用中执行，同样受约束。
 *
 * @author tuoyu
 */
public interface MesWorkorderMapper extends BaseMapper<MesWorkorder> {

    /**
     * 分页查询（数据权限）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<MesWorkorder> selectPageInScope(Page<MesWorkorder> page, Wrapper<MesWorkorder> wrapper) {
        return this.selectPage(page, wrapper);
    }

    /**
     * 列表查询（数据权限）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<MesWorkorder> selectListInScope(Wrapper<MesWorkorder> wrapper) {
        return this.selectList(wrapper);
    }

    /**
     * 统计行数（数据权限）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default long countInScope(Wrapper<MesWorkorder> wrapper) {
        Long count = this.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    /**
     * 按 ID 查询工单，不存在或不在当前用户的数据权限内时为空（数据权限）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default MesWorkorder selectByIdInScope(Long workorderId) {
        if (workorderId == null) {
            return null;
        }
        List<MesWorkorder> rows = this.selectList(QueryBuilder.lambda(MesWorkorder.class)
            .eq(MesWorkorder::getWorkorderId, workorderId)
            .build());
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 按 ID 批量查询工单，只返回在当前用户数据权限内的（数据权限）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<MesWorkorder> selectByIdsInScope(Collection<Long> workorderIds) {
        if (workorderIds == null || workorderIds.isEmpty()) {
            return List.of();
        }
        return this.selectList(QueryBuilder.lambda(MesWorkorder.class)
            .in(MesWorkorder::getWorkorderId, workorderIds)
            .build());
    }

    /**
     * 按 ID 读取并锁定工单行（{@code select ... for update}，MySQL 与 PostgreSQL 都支持），锁持有到事务结束。
     * <p>
     * 同一工单的状态动作、派工、报工与撤销报工都先锁工单行，再读取工序任务，彼此依次执行，
     * 数量上限与状态的判断不会被并发的操作打破。锁的顺序固定为：报工记录 → 工单 → 工序任务 → 入库单。
     * 不加数据权限：报工等单据之间的内部读取也要锁工单，接口入口另行用 {@link #selectByIdInScope(Long)} 校验
     */
    default MesWorkorder selectByIdForUpdate(Long workorderId) {
        if (workorderId == null) {
            return null;
        }
        List<MesWorkorder> rows = this.selectList(QueryBuilder.lambda(MesWorkorder.class)
            .eq(MesWorkorder::getWorkorderId, workorderId)
            .last("for update")
            .build());
        return rows.isEmpty() ? null : rows.get(0);
    }

}
