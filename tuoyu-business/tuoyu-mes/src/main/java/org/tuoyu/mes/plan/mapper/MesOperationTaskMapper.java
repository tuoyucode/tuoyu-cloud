package org.tuoyu.mes.plan.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.plan.domain.MesOperationTask;

import java.util.List;

/**
 * 工序任务
 * <p>
 * 名称带 {@code InScope} 的方法受数据权限约束（按创建部门与创建者，即下达工单的人），供列表、下拉与按 ID 的接口使用；
 * “我的任务”与单据之间的内部读取用 BaseMapper 自带的方法，不带数据权限。
 *
 * @author tuoyu
 */
public interface MesOperationTaskMapper extends BaseMapper<MesOperationTask> {

    /**
     * 分页查询（数据权限）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<MesOperationTask> selectPageInScope(Page<MesOperationTask> page, Wrapper<MesOperationTask> wrapper) {
        return this.selectPage(page, wrapper);
    }

    /**
     * 列表查询（数据权限）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<MesOperationTask> selectListInScope(Wrapper<MesOperationTask> wrapper) {
        return this.selectList(wrapper);
    }

    /**
     * 按 ID 查询工序任务，不存在或不在当前用户的数据权限内时为空（数据权限）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default MesOperationTask selectByIdInScope(Long taskId) {
        if (taskId == null) {
            return null;
        }
        List<MesOperationTask> rows = this.selectList(QueryBuilder.lambda(MesOperationTask.class)
            .eq(MesOperationTask::getTaskId, taskId)
            .build());
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 读取并锁定工单的全部工序任务（{@code select ... for update}），按工序顺序排列。
     * 调用前应已锁定工单行（见 {@link MesWorkorderMapper#selectByIdForUpdate(Long)}）；
     * 加锁读取总是读到已提交的最新数据，数量上限按它判断。不加数据权限：属于单据之间的内部读取
     */
    default List<MesOperationTask> selectByWorkorderForUpdate(Long workorderId) {
        return this.selectList(QueryBuilder.lambda(MesOperationTask.class)
            .eq(MesOperationTask::getWorkorderId, workorderId)
            .orderByAsc(MesOperationTask::getSeqNo, MesOperationTask::getTaskId)
            .last("for update")
            .build());
    }

}
