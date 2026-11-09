package org.tuoyu.mes.quality.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.quality.domain.bo.MesInspectionBo;
import org.tuoyu.mes.quality.domain.bo.MesInspectionResultBo;
import org.tuoyu.mes.quality.domain.vo.MesInspectionVo;

import java.util.Collection;
import java.util.List;

/**
 * 检验单：首检、巡检、完工检，待检验 → 已完成
 * <p>
 * 首检合格（已完成且结论为合格）的首检单是需要首检的工序报工的前提，由报工读取判断。
 *
 * @author tuoyu
 */
public interface IMesInspectionService {

    /**
     * 查询检验单（含方案的检验项目），不存在时抛出业务异常
     */
    MesInspectionVo queryById(Long inspectionId);

    PageResult<MesInspectionVo> queryPageList(MesInspectionBo bo, PageParam pageParam);

    /**
     * 导出用的列表（检验员昵称已填好）
     */
    List<MesInspectionVo> queryList(MesInspectionBo bo);

    long countList(MesInspectionBo bo);

    /**
     * 新增检验单（待检验）：未指定方案时自动匹配
     */
    void insertByBo(MesInspectionBo bo);

    /**
     * 录入检验结果：待检验 → 已完成；结论为不合格且有不良数时登记不良记录
     */
    void inspect(Long inspectionId, MesInspectionResultBo bo);

    /**
     * 删除待检验的检验单
     */
    void deleteByIds(Collection<Long> ids);

    List<MesStatusLogVo> logs(Long inspectionId);

}
