package org.tuoyu.system.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.ObjectUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.redis.utils.CacheUtils;
import org.tuoyu.system.domain.SysConfig;
import org.tuoyu.system.domain.bo.SysConfigBo;
import org.tuoyu.system.domain.vo.SysConfigVo;
import org.tuoyu.system.mapper.SysConfigMapper;
import org.tuoyu.system.service.ISysConfigService;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 参数配置 服务层实现
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysConfigServiceImpl implements ISysConfigService {

    private final SysConfigMapper configMapper;

    /**
     * 分页查询参数配置列表
     *
     * @param config    查询条件
     * @param pageParam 分页参数
     * @return 参数配置分页列表
     */
    @Override
    public PageResult<SysConfigVo> selectPageConfigList(SysConfigBo config, PageParam pageParam) {
        LambdaQueryWrapper<SysConfig> lqw = buildQueryWrapper(config);
        return PageResults.of(configMapper.selectPage(pageParam.toPage(SysConfig.class), lqw), SysConfigVo.class);
    }

    /**
     * 查询参数配置信息
     *
     * @param configId 参数配置ID
     * @return 参数配置信息
     */
    @Override
    public SysConfigVo selectConfigById(Long configId) {
        return BeanMapper.map(configMapper.selectById(configId), SysConfigVo.class);
    }

    /**
     * 根据键名查询参数配置信息
     *
     * @param configKey 参数key
     * @return 参数键值
     */
    @Cacheable(cacheNames = CacheNames.SYS_CONFIG, key = "#configKey")
    @Override
    public String selectConfigByKey(String configKey) {
        SysConfig retConfig = configMapper.selectOne(QueryBuilder.lambda(SysConfig.class).eq(SysConfig::getConfigKey, configKey).build());
        return ObjectUtils.notNullGetter(retConfig, SysConfig::getConfigValue, StringUtils.EMPTY);
    }

    /**
     * 获取注册开关
     *
     * @return true开启，false关闭
     */
    @Override
    public boolean selectRegisterEnabled() {
        String configValue = this.selectConfigByKey("sys.account.registerUser");
        return Convert.toBool(configValue);
    }

    /**
     * 查询参数配置列表
     *
     * @param config 参数配置信息
     * @return 参数配置集合
     */
    @Override
    public List<SysConfigVo> selectConfigList(SysConfigBo config) {
        LambdaQueryWrapper<SysConfig> lqw = buildQueryWrapper(config);
        return BeanMapper.mapList(configMapper.selectList(lqw), SysConfigVo.class);
    }

    /**
     * 统计符合条件的参数配置数量（导出前先统计，查询条件与列表一致）
     */
    @Override
    public long countConfigList(SysConfigBo config) {
        return configMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(config)).getTotal();
    }

    private LambdaQueryWrapper<SysConfig> buildQueryWrapper(SysConfigBo bo) {
        Map<String, Object> params = bo.getParams();
        return QueryBuilder.lambda(SysConfig.class)
            .likeIfText(SysConfig::getConfigName, bo.getConfigName())
            .eqIfText(SysConfig::getConfigType, bo.getConfigType())
            .likeIfText(SysConfig::getConfigKey, bo.getConfigKey())
            .betweenParams(SysConfig::getCreateTime, params, "beginTime", "endTime")
            .orderByAsc(SysConfig::getConfigId)
            .build();
    }

    /**
     * 新增参数配置
     *
     * @param bo 参数配置信息
     * @return 结果
     */
    @CachePut(cacheNames = CacheNames.SYS_CONFIG, key = "#bo.configKey")
    @Override
    public String insertConfig(SysConfigBo bo) {
        SysConfig config = BeanMapper.map(bo, SysConfig.class);
        int row = configMapper.insert(config);
        if (row > 0) {
            return config.getConfigValue();
        }
        throw new ServiceException("新增参数'{}'失败", bo.getConfigName());
    }

    /**
     * 修改参数配置
     *
     * @param bo 参数配置信息
     * @return 结果
     */
    @CachePut(cacheNames = CacheNames.SYS_CONFIG, key = "#bo.configKey")
    @Override
    public String updateConfig(SysConfigBo bo) {
        int row;
        SysConfig config = BeanMapper.map(bo, SysConfig.class);
        if (config.getConfigId() != null) {
            SysConfig temp = configMapper.selectById(config.getConfigId());
            if (ObjectUtil.isNotNull(temp) && !StringUtils.equals(temp.getConfigKey(), config.getConfigKey())) {
                CacheUtils.evict(CacheNames.SYS_CONFIG, temp.getConfigKey());
            }
            row = configMapper.updateById(config);
        } else {
            CacheUtils.evict(CacheNames.SYS_CONFIG, config.getConfigKey());
            row = configMapper.update(config, QueryBuilder.lambda(SysConfig.class)
                .eq(SysConfig::getConfigKey, config.getConfigKey())
                .build());
        }
        if (row > 0) {
            return config.getConfigValue();
        }
        // 按参数键修改时只传了参数键与参数值，没有参数名称
        String name = StringUtils.isNotBlank(bo.getConfigName()) ? bo.getConfigName() : bo.getConfigKey();
        throw new ServiceException("修改参数'{}'失败，参数不存在", name);
    }

    /**
     * 批量删除参数信息
     *
     * @param configIds 需要删除的参数ID
     */
    @Override
    public void deleteConfigByIds(List<Long> configIds) {
        List<SysConfig> list = configMapper.selectByIds(configIds);
        list.forEach(config -> {
            if (StringUtils.equals(SystemConstants.YES, config.getConfigType())) {
                throw new ServiceException("内置参数【{}】不能删除", config.getConfigKey());
            }
            CacheUtils.evict(CacheNames.SYS_CONFIG, config.getConfigKey());
        });
        configMapper.deleteByIds(configIds);
    }

    /**
     * 重置参数缓存数据
     */
    @Override
    public void resetConfigCache() {
        CacheUtils.clear(CacheNames.SYS_CONFIG);
    }

    /**
     * 校验参数键名是否唯一
     *
     * @param config 参数配置信息
     * @return 结果
     */
    @Override
    public boolean checkConfigKeyUnique(SysConfigBo config) {
        boolean exist = configMapper.exists(QueryBuilder.lambda(SysConfig.class)
            .eq(SysConfig::getConfigKey, config.getConfigKey())
            .neIfPresent(SysConfig::getConfigId, config.getConfigId())
            .build());
        return !exist;
    }

}
