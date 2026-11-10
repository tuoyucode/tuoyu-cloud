package org.tuoyu.mes.equipment.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.equipment.domain.MesCheckItem;
import org.tuoyu.mes.equipment.domain.bo.MesCheckItemBo;
import org.tuoyu.mes.equipment.domain.vo.MesCheckItemVo;
import org.tuoyu.mes.masterdata.domain.MesDevice;

import java.util.Collection;
import java.util.List;

/**
 * 点检项目：按设备或设备类型维护的点检内容、方法、标准与结果类型（判定或数值与上下限）
 *
 * @author tuoyu
 */
public interface IMesCheckItemService {

    /**
     * 查询点检项目，不存在时抛出业务异常
     */
    MesCheckItemVo queryById(Long checkItemId);

    PageResult<MesCheckItemVo> queryPageList(MesCheckItemBo bo, PageParam pageParam);

    void insertByBo(MesCheckItemBo bo);

    void updateByBo(MesCheckItemBo bo);

    /**
     * 删除点检项目：已有的点检记录保存了项目快照，不受影响
     */
    void deleteByIds(Collection<Long> ids);

    /**
     * 设备主数据中已有的设备类型（去重、排序），供选择适用设备类型
     */
    List<String> deviceTypes();

    /**
     * 设备适用的启用项目：指定了该设备的项目，加上未指定设备、设备类型与该设备相同的项目；按排序号排列
     *
     * @param deviceId 设备
     * @return 点检项目
     */
    List<MesCheckItemVo> applicable(Long deviceId);

    /**
     * 设备适用的启用项目（实体），供提交点检记录时校验
     */
    List<MesCheckItem> applicableItems(MesDevice device);

}
