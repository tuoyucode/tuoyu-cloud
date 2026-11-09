package org.tuoyu.mes.masterdata.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.bo.MesDeviceBo;
import org.tuoyu.mes.masterdata.domain.vo.MesDeviceVo;

import java.util.Collection;
import java.util.List;

/**
 * 设备
 *
 * @author tuoyu
 */
public interface IMesDeviceService {

    /**
     * 查询设备，不存在时抛出业务异常
     */
    MesDeviceVo queryById(Long deviceId);

    PageResult<MesDeviceVo> queryPageList(MesDeviceBo bo, PageParam pageParam);

    List<MesDeviceVo> queryList(MesDeviceBo bo);

    long countList(MesDeviceBo bo);

    /**
     * 启用的设备（下拉选项），可按车间过滤；parentId 为车间 ID
     */
    List<MesOptionVo> options(Long workshopId);

    /**
     * 新增设备：工厂、车间、产线、工位必须属于同一条链，按填写的最低一级补齐上级
     */
    void insertByBo(MesDeviceBo bo);

    void updateByBo(MesDeviceBo bo);

    /**
     * 删除设备：已有安灯记录的设备不能删除
     */
    void deleteByIds(Collection<Long> ids);

}
