package org.tuoyu.mes.quality.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.quality.domain.MesInspection;
import org.tuoyu.mes.quality.domain.bo.MesDefectHandleBo;
import org.tuoyu.mes.quality.domain.bo.MesDefectRecordBo;
import org.tuoyu.mes.quality.domain.vo.MesDefectRecordVo;

import java.util.List;

/**
 * 不良记录：来自报工、检验或手工登记，待处置 → 已处置
 *
 * @author tuoyu
 */
public interface IMesDefectRecordService {

    /**
     * 查询不良记录，不存在时抛出业务异常
     */
    MesDefectRecordVo queryById(Long defectId);

    PageResult<MesDefectRecordVo> queryPageList(MesDefectRecordBo bo, PageParam pageParam);

    /**
     * 导出用的列表（处置人昵称已填好）
     */
    List<MesDefectRecordVo> queryList(MesDefectRecordBo bo);

    long countList(MesDefectRecordBo bo);

    /**
     * 手工登记不良
     */
    void insertByBo(MesDefectRecordBo bo);

    /**
     * 检验不合格时登记不良（来源为检验单），由检验单的检验动作调用，与检验在同一事务中
     *
     * @param inspection  已完成的检验单（含不良数量）
     * @param description 不良描述
     */
    void createFromInspection(MesInspection inspection, String description);

    /**
     * 处置：待处置 → 已处置
     */
    void handle(Long defectId, MesDefectHandleBo bo);

    List<MesStatusLogVo> logs(Long defectId);

}
