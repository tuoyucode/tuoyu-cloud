package org.tuoyu.mes.equipment.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.equipment.domain.bo.MesCheckAndonBo;
import org.tuoyu.mes.equipment.domain.bo.MesCheckRecordBo;
import org.tuoyu.mes.equipment.domain.vo.MesCheckRecordVo;

import java.util.List;

/**
 * 点检记录：按设备的点检项目逐项填写结果，只增不改（作废用状态表示）；有不合格项时记录为“异常”，可以发起安灯
 *
 * @author tuoyu
 */
public interface IMesCheckRecordService {

    /**
     * 查询点检记录（含逐项结果），不存在或不在数据权限内时抛出业务异常
     */
    MesCheckRecordVo queryById(Long checkRecordId);

    PageResult<MesCheckRecordVo> queryPageList(MesCheckRecordBo bo, PageParam pageParam);

    /**
     * 导出用的列表（点检人名称已填好）
     */
    List<MesCheckRecordVo> queryList(MesCheckRecordBo bo);

    long countList(MesCheckRecordBo bo);

    /**
     * 提交点检：点检人为当前用户，时间为提交时间；数值超出上下限自动判为不合格
     */
    void insertByBo(MesCheckRecordBo bo);

    /**
     * 作废：已提交 → 已作废，原因必填
     */
    void voidRecord(Long checkRecordId, String reason);

    /**
     * 异常的点检记录发起安灯（设备异常类），每条记录只能发起一次
     */
    void raiseAndon(Long checkRecordId, MesCheckAndonBo bo);

    List<MesStatusLogVo> logs(Long checkRecordId);

}
