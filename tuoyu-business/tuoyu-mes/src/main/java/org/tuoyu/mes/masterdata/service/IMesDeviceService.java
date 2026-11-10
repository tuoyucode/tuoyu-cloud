package org.tuoyu.mes.masterdata.service;

import org.springframework.web.multipart.MultipartFile;
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

    /**
     * 从 Excel 导入设备：先整体校验，有任何一行不合格时一行都不写入，并逐行列出错误（第几行、哪个字段、原因）；
     * 全部合格时在一个事务中写入。行数超过 tuoyu.excel.max-import-rows 时直接拒绝
     *
     * @param file 按导入模板填写的文件
     * @return 导入的设备数量
     */
    int importData(MultipartFile file);

}
