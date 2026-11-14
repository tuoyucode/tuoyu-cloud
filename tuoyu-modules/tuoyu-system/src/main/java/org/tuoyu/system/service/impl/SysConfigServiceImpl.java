package org.tuoyu.system.service.impl;

import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
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

import java.util.List;
import java.util.Objects;

/**
 * 参数配置服务实现。
 * <p>
 * 参数全平台共享（表中没有租户列），参数值按键名缓存在 {@link CacheNames#SYS_CONFIG}（全局缓存）。
 * 修改后清除该键的缓存、下次读取时从库中重新加载，不把请求中的值写入缓存：按键修改时请求可能只带部分字段。
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl implements ISysConfigService {

    /**
     * 是否开放用户注册的参数键名
     */
    private static final String REGISTER_USER_KEY = "sys.account.registerUser";

    private final SysConfigMapper configMapper;

    @Override
    public PageResult<SysConfigVo> selectPageConfigList(SysConfigBo config, PageParam pageParam) {
        return PageResults.of(configMapper.selectPage(pageParam.toPage(SysConfig.class),
            buildQueryWrapper(config, !pageParam.hasSort())), SysConfigVo.class);
    }

    @Override
    public List<SysConfigVo> selectConfigList(SysConfigBo config) {
        return BeanMapper.mapList(configMapper.selectList(buildQueryWrapper(config, true)), SysConfigVo.class);
    }

    @Override
    public long countConfigList(SysConfigBo config) {
        return configMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(config, false)).getTotal();
    }

    @Override
    public SysConfigVo selectConfigById(Long configId) {
        return BeanMapper.map(configMapper.selectById(configId), SysConfigVo.class);
    }

    @Override
    @Cacheable(cacheNames = CacheNames.SYS_CONFIG, key = "#configKey")
    public String selectConfigByKey(String configKey) {
        return readValue(configKey);
    }

    @Override
    public boolean selectRegisterEnabled() {
        return Boolean.TRUE.equals(Convert.toBool(readValue(REGISTER_USER_KEY), false));
    }

    @Override
    @CachePut(cacheNames = CacheNames.SYS_CONFIG, key = "#bo.configKey")
    public String insertConfig(SysConfigBo bo) {
        if (configMapper.insert(BeanMapper.map(bo, SysConfig.class)) == 0) {
            throw new ServiceException("新增参数'{}'失败", bo.getConfigName());
        }
        return bo.getConfigValue();
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.SYS_CONFIG, key = "#bo.configKey")
    public String updateConfig(SysConfigBo bo) {
        SysConfig config = BeanMapper.map(bo, SysConfig.class);
        int rows;
        String renamedFrom = null;
        if (bo.getConfigId() != null) {
            SysConfig old = configMapper.selectById(bo.getConfigId());
            if (old != null && !Objects.equals(old.getConfigKey(), bo.getConfigKey())) {
                renamedFrom = old.getConfigKey();
            }
            rows = configMapper.updateById(config);
        } else if (StringUtils.isBlank(bo.getConfigKey())) {
            rows = 0;
        } else {
            rows = configMapper.update(config, QueryBuilder.lambda(SysConfig.class)
                .eq(SysConfig::getConfigKey, bo.getConfigKey())
                .build());
        }
        if (rows == 0) {
            String name = StringUtils.isNotBlank(bo.getConfigName()) ? bo.getConfigName() : bo.getConfigKey();
            throw new ServiceException("修改参数'{}'失败，参数不存在", name);
        }
        // 改了键名：注解只清除新键名的缓存，原键名的缓存在这里清除
        if (renamedFrom != null) {
            CacheUtils.evict(CacheNames.SYS_CONFIG, renamedFrom);
        }
        return bo.getConfigValue();
    }

    @Override
    public void deleteConfigByIds(List<Long> configIds) {
        List<SysConfig> configs = configMapper.selectByIds(configIds);
        for (SysConfig config : configs) {
            if (SystemConstants.YES.equals(config.getConfigType())) {
                throw new ServiceException("内置参数【{}】不能删除", config.getConfigKey());
            }
        }
        configMapper.deleteByIds(configIds);
        for (SysConfig config : configs) {
            CacheUtils.evict(CacheNames.SYS_CONFIG, config.getConfigKey());
        }
    }

    @Override
    public void resetConfigCache() {
        CacheUtils.clear(CacheNames.SYS_CONFIG);
    }

    @Override
    public boolean checkConfigKeyUnique(SysConfigBo config) {
        return !configMapper.exists(QueryBuilder.lambda(SysConfig.class)
            .eq(SysConfig::getConfigKey, config.getConfigKey())
            .neIfPresent(SysConfig::getConfigId, config.getConfigId())
            .build());
    }

    /**
     * 按键名从库中读取参数值（不经缓存）
     *
     * @param configKey 参数键名
     * @return 参数值；参数不存在时为空字符串
     */
    private String readValue(String configKey) {
        SysConfig config = configMapper.selectOne(QueryBuilder.lambda(SysConfig.class)
            .eq(SysConfig::getConfigKey, configKey)
            .build());
        return config == null ? StringUtils.EMPTY : StringUtils.defaultString(config.getConfigValue());
    }

    /**
     * 列表条件：名称模糊、类型相等、键名模糊，创建时间区间；默认按 ID 升序（分页时指定了排序则不加）
     */
    private LambdaQueryWrapper<SysConfig> buildQueryWrapper(SysConfigBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(SysConfig.class)
            .likeIfText(SysConfig::getConfigName, bo.getConfigName())
            .eqIfText(SysConfig::getConfigType, bo.getConfigType())
            .likeIfText(SysConfig::getConfigKey, bo.getConfigKey())
            .betweenParams(SysConfig::getCreateTime, bo.getParams(), "beginTime", "endTime")
            .orderByAsc(defaultOrder, SysConfig::getConfigId)
            .build();
    }

}
