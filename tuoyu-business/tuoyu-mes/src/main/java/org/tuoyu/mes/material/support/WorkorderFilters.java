package org.tuoyu.mes.material.support;

import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;

import java.util.List;

/**
 * 物料与质量单据共用的工单读取：按编号筛选列表、按 ID 取工单
 *
 * @author tuoyu
 */
public final class WorkorderFilters {

    /**
     * 没有匹配的工单时使用的 ID：不存在这样的主键，条件查不到任何数据
     */
    private static final List<Long> NO_MATCH = List.of(-1L);

    private WorkorderFilters() {
    }

    /**
     * 按工单编号模糊匹配的工单 ID，用于单据列表的“工单编号”条件
     *
     * @param mapper        工单 Mapper
     * @param workorderCode 工单编号（模糊）
     * @return 编号为空时为 null（不过滤）；没有匹配时为只含 -1 的列表（查不到任何数据）
     */
    public static List<Long> idsByCode(MesWorkorderMapper mapper, String workorderCode) {
        if (StringUtils.isBlank(workorderCode)) {
            return null;
        }
        List<Long> ids = mapper.selectList(QueryBuilder.lambda(MesWorkorder.class)
                .select(MesWorkorder::getWorkorderId)
                .like(MesWorkorder::getWorkorderCode, workorderCode.trim())
                .build())
            .stream().map(MesWorkorder::getWorkorderId).toList();
        return ids.isEmpty() ? NO_MATCH : ids;
    }

    /**
     * 按 ID 取工单，不存在时抛出业务异常
     */
    public static MesWorkorder require(MesWorkorderMapper mapper, Long workorderId) {
        MesWorkorder workorder = workorderId == null ? null : mapper.selectById(workorderId);
        if (workorder == null) {
            throw new ServiceException("工单不存在");
        }
        return workorder;
    }

}
