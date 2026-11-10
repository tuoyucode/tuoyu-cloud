package org.tuoyu.mes.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tuoyu.mes.equipment.domain.MesCheckRecordDetail;

/**
 * 点检记录明细：只随点检记录读取（读取前已按数据权限确认记录在范围内）
 *
 * @author tuoyu
 */
public interface MesCheckRecordDetailMapper extends BaseMapper<MesCheckRecordDetail> {

}
