package org.tuoyu.system.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.domain.bo.SysConfigBo;
import org.tuoyu.system.domain.vo.SysConfigVo;

import java.util.List;

/**
 * 参数配置 服务层
 *
 * @author Lion Li
 */
public interface ISysConfigService {

    /**
     * 分页查询参数配置列表
     *
     * @param config    查询条件
     * @param pageParam 分页参数
     * @return 参数配置分页列表
     */
    PageResult<SysConfigVo> selectPageConfigList(SysConfigBo config, PageParam pageParam);

    /**
     * 查询参数配置信息
     *
     * @param configId 参数配置ID
     * @return 参数配置信息
     */
    SysConfigVo selectConfigById(Long configId);

    /**
     * 根据键名查询参数配置信息
     *
     * @param configKey 参数键名
     * @return 参数键值
     */
    String selectConfigByKey(String configKey);

    /**
     * 获取注册开关
     *
     * @return true开启，false关闭
     */
    boolean selectRegisterEnabled();

    /**
     * 查询参数配置列表
     *
     * @param config 参数配置信息
     * @return 参数配置集合
     */
    List<SysConfigVo> selectConfigList(SysConfigBo config);

    /**
     * 统计符合条件的参数配置数量（导出前先统计，查询条件与列表一致）
     *
     * @param config 参数配置信息
     * @return 行数
     */
    long countConfigList(SysConfigBo config);

    /**
     * 新增参数配置
     *
     * @param bo 参数配置信息
     * @return 结果
     */
    String insertConfig(SysConfigBo bo);

    /**
     * 修改参数配置
     *
     * @param bo 参数配置信息
     * @return 结果
     */
    String updateConfig(SysConfigBo bo);

    /**
     * 批量删除参数信息
     *
     * @param configIds 需要删除的参数ID
     */
    void deleteConfigByIds(List<Long> configIds);

    /**
     * 重置参数缓存数据
     */
    void resetConfigCache();

    /**
     * 校验参数键名是否唯一
     *
     * @param config 参数信息
     * @return 结果
     */
    boolean checkConfigKeyUnique(SysConfigBo config);

}
