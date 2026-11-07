package org.tuoyu.resource.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.ObjectUtils;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.oss.constant.OssConstant;
import org.tuoyu.common.redis.utils.CacheUtils;
import org.tuoyu.common.redis.utils.RedisUtils;
import org.tuoyu.resource.domain.SysOssConfig;
import org.tuoyu.resource.domain.bo.SysOssConfigBo;
import org.tuoyu.resource.domain.vo.SysOssConfigVo;
import org.tuoyu.resource.event.OssConfigChangeEvent;
import org.tuoyu.resource.mapper.SysOssConfigMapper;
import org.tuoyu.resource.service.ISysOssConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * 对象存储配置Service业务层处理
 *
 * @author Lion Li
 * @author 孤舟烟雨
 * @date 2021-08-13
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SysOssConfigServiceImpl implements ISysOssConfigService {

    private final SysOssConfigMapper ossConfigMapper;

    /**
     * 项目启动时，初始化参数到缓存，加载配置类
     */
    @Override
    public void init() {
        List<SysOssConfig> list = ossConfigMapper.selectList(Wrappers.emptyWrapper());
        // 加载OSS初始化配置
        for (SysOssConfig config : list) {
            String configKey = config.getConfigKey();
            if (SystemConstants.YES.equals(config.getStatus())) {
                RedisUtils.setCacheObject(OssConstant.DEFAULT_CONFIG_KEY, configKey);
            }
            CacheUtils.put(CacheNames.SYS_OSS_CONFIG, config.getConfigKey(), JsonUtils.toJsonString(config));
        }
    }

    @Override
    public SysOssConfigVo queryById(Long ossConfigId) {
        return BeanMapper.map(ossConfigMapper.selectById(ossConfigId), SysOssConfigVo.class);
    }

    @Override
    public PageResult<SysOssConfigVo> queryPageList(SysOssConfigBo bo, PageParam pageParam) {
        LambdaQueryWrapper<SysOssConfig> lqw = buildQueryWrapper(bo);
        return PageResults.of(ossConfigMapper.selectPage(pageParam.toPage(SysOssConfig.class), lqw), SysOssConfigVo.class);
    }


    private LambdaQueryWrapper<SysOssConfig> buildQueryWrapper(SysOssConfigBo bo) {
        return QueryBuilder.lambda(SysOssConfig.class)
            .eqIfText(SysOssConfig::getConfigKey, bo.getConfigKey())
            .likeIfText(SysOssConfig::getBucketName, bo.getBucketName())
            .eqIfText(SysOssConfig::getStatus, bo.getStatus())
            .orderByAsc(SysOssConfig::getOssConfigId)
            .build();
    }

    @Override
    public void insertByBo(SysOssConfigBo bo) {
        SysOssConfig config = BeanMapper.map(bo, SysOssConfig.class);
        validEntityBeforeSave(config);
        if (ossConfigMapper.insert(config) <= 0) {
            throw new ServiceException("新增对象存储配置'{}'失败", config.getConfigKey());
        }
        // 从数据库查询完整的数据做缓存
        config = ossConfigMapper.selectById(config.getOssConfigId());
        publishOssConfigSaved(config, null);
    }

    @Override
    public void updateByBo(SysOssConfigBo bo) {
        SysOssConfig config = BeanMapper.map(bo, SysOssConfig.class);
        keepUnchangedKeys(config);
        validEntityBeforeSave(config);
        SysOssConfig oldConfig = ossConfigMapper.selectById(config.getOssConfigId());
        // 可选字段为空时写入空串：全局 NOT_NULL 更新策略会跳过 null，否则无法清空
        int rows = ossConfigMapper.update(config, Wrappers.<SysOssConfig>lambdaUpdate()
            .set(ObjectUtil.isNull(config.getPrefix()), SysOssConfig::getPrefix, "")
            .set(ObjectUtil.isNull(config.getRegion()), SysOssConfig::getRegion, "")
            .set(ObjectUtil.isNull(config.getExt1()), SysOssConfig::getExt1, "")
            .set(ObjectUtil.isNull(config.getRemark()), SysOssConfig::getRemark, "")
            .eq(SysOssConfig::getOssConfigId, config.getOssConfigId()));
        if (rows <= 0) {
            throw new ServiceException("对象存储配置不存在");
        }
        // 从数据库查询完整的数据做缓存
        config = ossConfigMapper.selectById(config.getOssConfigId());
        publishOssConfigSaved(config, ObjectUtils.notNullGetter(oldConfig, SysOssConfig::getConfigKey));
    }

    /**
     * 列表和详情接口不返回密钥，修改时密钥为空白表示不修改：置为 null，按全局 NOT_NULL 更新策略保留原值
     */
    static void keepUnchangedKeys(SysOssConfig config) {
        if (StringUtils.isBlank(config.getAccessKey())) {
            config.setAccessKey(null);
        }
        if (StringUtils.isBlank(config.getSecretKey())) {
            config.setSecretKey(null);
        }
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(SysOssConfig entity) {
        if (StringUtils.isNotEmpty(entity.getConfigKey()) && !checkConfigKeyUnique(entity)) {
            throw new ServiceException("操作配置'{}'失败, 配置key已存在!", entity.getConfigKey());
        }
    }

    @Override
    public void deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            if (CollUtil.containsAny(ids, OssConstant.SYSTEM_DATA_IDS)) {
                throw new ServiceException("系统内置, 不可删除!");
            }
        }
        List<SysOssConfig> list = CollUtil.newArrayList();
        for (Long configId : ids) {
            SysOssConfig config = ossConfigMapper.selectById(configId);
            if (ObjectUtil.isNotNull(config)) {
                list.add(config);
            }
        }
        if (ossConfigMapper.deleteByIds(ids) <= 0) {
            throw new ServiceException("对象存储配置不存在");
        }
        list.forEach(sysOssConfig ->
            SpringUtils.context().publishEvent(OssConfigChangeEvent.remove(sysOssConfig.getConfigKey())));
    }

    /**
     * 判断configKey是否唯一
     */
    private boolean checkConfigKeyUnique(SysOssConfig sysOssConfig) {
        long ossConfigId = ObjectUtils.notNull(sysOssConfig.getOssConfigId(), -1L);
        SysOssConfig info = ossConfigMapper.selectOne(QueryBuilder.lambda(SysOssConfig.class)
            .select(SysOssConfig::getOssConfigId, SysOssConfig::getConfigKey)
            .eq(SysOssConfig::getConfigKey, sysOssConfig.getConfigKey())
            .build());
        return ObjectUtil.isNull(info) || ObjectUtil.equals(info.getOssConfigId(), ossConfigId);
    }

    /**
     * 启用禁用状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOssConfigStatus(SysOssConfigBo bo) {
        SysOssConfig sysOssConfig = BeanMapper.map(bo, SysOssConfig.class);
        int row = ossConfigMapper.update(Wrappers.<SysOssConfig>lambdaUpdate().set(SysOssConfig::getStatus, SystemConstants.NO));
        row += ossConfigMapper.updateById(sysOssConfig);
        if (row <= 0) {
            throw new ServiceException("对象存储配置不存在");
        }
        SpringUtils.context().publishEvent(OssConfigChangeEvent.useDefault(sysOssConfig.getConfigKey()));
    }

    /**
     * 发布 OSS 配置保存事件。
     *
     * @param config       当前配置
     * @param oldConfigKey 变更前配置 key
     */
    private void publishOssConfigSaved(SysOssConfig config, String oldConfigKey) {
        SpringUtils.context().publishEvent(OssConfigChangeEvent.save(
            config.getConfigKey(),
            oldConfigKey,
            JsonUtils.toJsonString(config)
        ));
    }

}
