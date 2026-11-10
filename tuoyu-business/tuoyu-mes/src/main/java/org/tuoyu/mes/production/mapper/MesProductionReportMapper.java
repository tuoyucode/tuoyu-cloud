package org.tuoyu.mes.production.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.production.domain.MesProductionReport;

import java.util.List;

/**
 * 报工记录
 * <p>
 * 名称带 {@code InScope} 的方法受数据权限约束（按创建部门与创建者，即报工的人），供列表、导出、看板与按 ID 的接口使用；
 * 单据之间的内部读取（例如判断工单是否已有报工）用 BaseMapper 自带的方法，不带数据权限。
 *
 * @author tuoyu
 */
public interface MesProductionReportMapper extends BaseMapper<MesProductionReport> {

    /**
     * 分页查询（数据权限）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<MesProductionReport> selectPageInScope(Page<MesProductionReport> page,
                                                        Wrapper<MesProductionReport> wrapper) {
        return this.selectPage(page, wrapper);
    }

    /**
     * 列表查询（数据权限），也用于看板的分组汇总
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<MesProductionReport> selectListInScope(Wrapper<MesProductionReport> wrapper) {
        return this.selectList(wrapper);
    }

    /**
     * 按 ID 查询报工记录，不存在或不在当前用户的数据权限内时为空（数据权限）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default MesProductionReport selectByIdInScope(Long reportId) {
        if (reportId == null) {
            return null;
        }
        List<MesProductionReport> rows = this.selectList(QueryBuilder.lambda(MesProductionReport.class)
            .eq(MesProductionReport::getReportId, reportId)
            .build());
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 按 ID 读取并锁定报工记录（{@code select ... for update}），撤销报工时最先执行，锁的顺序见
     * {@code MesWorkorderMapper#selectByIdForUpdate}。不加数据权限：调用方另行校验
     */
    default MesProductionReport selectByIdForUpdate(Long reportId) {
        if (reportId == null) {
            return null;
        }
        List<MesProductionReport> rows = this.selectList(QueryBuilder.lambda(MesProductionReport.class)
            .eq(MesProductionReport::getReportId, reportId)
            .last("for update")
            .build());
        return rows.isEmpty() ? null : rows.get(0);
    }

}
