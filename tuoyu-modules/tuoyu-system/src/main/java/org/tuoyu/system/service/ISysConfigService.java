package org.tuoyu.system.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.domain.bo.SysConfigBo;
import org.tuoyu.system.domain.vo.SysConfigVo;

import java.util.List;

/**
 * 参数配置服务。参数全平台共享（不分租户），按键名缓存参数值
 *
 * @author tuoyu
 */
public interface ISysConfigService {

    /**
     * 分页查询参数
     *
     * @param config    查询条件
     * @param pageParam 分页参数
     * @return 参数分页列表
     */
    PageResult<SysConfigVo> selectPageConfigList(SysConfigBo config, PageParam pageParam);

    /**
     * 查询参数列表（导出用）
     *
     * @param config 查询条件
     * @return 参数列表
     */
    List<SysConfigVo> selectConfigList(SysConfigBo config);

    /**
     * 统计符合条件的参数数量（导出前先统计，查询条件与列表一致）
     *
     * @param config 查询条件
     * @return 行数
     */
    long countConfigList(SysConfigBo config);

    /**
     * 按 ID 查询参数
     *
     * @param configId 参数 ID
     * @return 参数；不存在时为 null
     */
    SysConfigVo selectConfigById(Long configId);

    /**
     * 按键名读取参数值
     *
     * @param configKey 参数键名
     * @return 参数值；参数不存在时为空字符串
     */
    String selectConfigByKey(String configKey);

    /**
     * 是否开放用户注册（参数 {@code sys.account.registerUser}）
     *
     * @return 参数值按布尔值解析为真时为 true；不存在、为空或不能解析时为 false
     */
    boolean selectRegisterEnabled();

    /**
     * 新增参数
     *
     * @param bo 参数
     * @return 参数值（放入缓存）
     */
    String insertConfig(SysConfigBo bo);

    /**
     * 修改参数：有 ID 时按 ID 修改，没有 ID 时按键名修改
     *
     * @param bo 参数
     * @return 提交的参数值
     */
    String updateConfig(SysConfigBo bo);

    /**
     * 批量删除参数；其中有内置参数时整体拒绝
     *
     * @param configIds 参数 ID
     */
    void deleteConfigByIds(List<Long> configIds);

    /**
     * 清空参数缓存
     */
    void resetConfigCache();

    /**
     * 键名是否唯一（修改时排除自己）
     *
     * @param config 参数
     * @return 没有其他同键名的参数时为 true
     */
    boolean checkConfigKeyUnique(SysConfigBo config);

}
