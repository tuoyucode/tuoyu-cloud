package org.tuoyu.resource.service;


import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.resource.domain.bo.SysOssConfigBo;
import org.tuoyu.resource.domain.vo.SysOssConfigVo;

import java.util.Collection;

/**
 * 对象存储配置Service接口
 *
 * @author Lion Li
 * @author 孤舟烟雨
 * @date 2021-08-13
 */
public interface ISysOssConfigService {

    /**
     * 初始化OSS配置
     */
    void init();

    /**
     * 查询单个
     */
    SysOssConfigVo queryById(Long ossConfigId);

    /**
     * 查询列表
     */
    PageResult<SysOssConfigVo> queryPageList(SysOssConfigBo bo, PageParam pageParam);


    /**
     * 根据新增业务对象插入对象存储配置
     *
     * @param bo 对象存储配置新增业务对象
     * @throws org.tuoyu.common.core.exception.ServiceException 配置 key 已存在
     */
    void insertByBo(SysOssConfigBo bo);

    /**
     * 根据编辑业务对象修改对象存储配置
     *
     * @param bo 对象存储配置编辑业务对象
     * @throws org.tuoyu.common.core.exception.ServiceException 配置 key 已存在、配置不存在
     */
    void updateByBo(SysOssConfigBo bo);

    /**
     * 校验并删除数据
     *
     * @param ids     主键集合
     * @param isValid 是否校验,true-删除前校验,false-不校验
     * @throws org.tuoyu.common.core.exception.ServiceException 包含系统内置配置、配置不存在
     */
    void deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 启用停用状态
     */
    void updateOssConfigStatus(SysOssConfigBo bo);

}
