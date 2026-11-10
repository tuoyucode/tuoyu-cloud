package org.tuoyu.mes.equipment.service;

import org.tuoyu.mes.equipment.domain.bo.MesOeeConfigBo;
import org.tuoyu.mes.equipment.domain.bo.MesOeeQueryBo;
import org.tuoyu.mes.equipment.domain.vo.MesOeeConfigVo;
import org.tuoyu.mes.equipment.domain.vo.MesOeeExportVo;
import org.tuoyu.mes.equipment.domain.vo.MesOeeVo;

import java.util.List;

/**
 * 设备综合效率（OEE）：按设备或产线、按日期范围计算可用率、性能、质量与 OEE，给出按日趋势与汇总
 *
 * @author tuoyu
 */
public interface IMesOeeService {

    /**
     * 计算 OEE；报工与安灯停机都限定在当前用户的数据权限内
     */
    MesOeeVo calculate(MesOeeQueryBo bo);

    /**
     * 导出用的行：第一行为汇总，其后按日期
     */
    List<MesOeeExportVo> exportRows(MesOeeQueryBo bo);

    /**
     * 查询设备或产线的 OEE 配置；没有配置时各项为空
     */
    MesOeeConfigVo getConfig(String targetType, Long targetId);

    /**
     * 保存设备或产线的 OEE 配置（每日计划生产时间、标准节拍）
     */
    void saveConfig(MesOeeConfigBo bo);

}
