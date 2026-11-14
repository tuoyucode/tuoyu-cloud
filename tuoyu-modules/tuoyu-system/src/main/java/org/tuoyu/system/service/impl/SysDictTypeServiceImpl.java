package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.redis.utils.CacheUtils;
import org.tuoyu.system.domain.SysDictData;
import org.tuoyu.system.domain.SysDictType;
import org.tuoyu.system.domain.bo.SysDictTypeBo;
import org.tuoyu.system.domain.vo.SysDictDataVo;
import org.tuoyu.system.domain.vo.SysDictTypeVo;
import org.tuoyu.system.mapper.SysDictDataMapper;
import org.tuoyu.system.mapper.SysDictTypeMapper;
import org.tuoyu.system.service.ISysDictTypeService;

import java.util.ArrayList;
import java.util.List;

/**
 * 字典类型服务实现。
 * <p>
 * 字典全平台共享（表中没有租户列）。按类型编码缓存两份数据：{@link CacheNames#SYS_DICT} 是该类型的字典数据，
 * {@link CacheNames#SYS_DICT_TYPE} 是字典类型本身，都是全局缓存。改动类型编码时两份缓存的新旧键都要处理。
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class SysDictTypeServiceImpl implements ISysDictTypeService {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;

    @Override
    public PageResult<SysDictTypeVo> selectPageDictTypeList(SysDictTypeBo dictType, PageParam pageParam) {
        return PageResults.of(dictTypeMapper.selectPage(pageParam.toPage(SysDictType.class),
            buildQueryWrapper(dictType, !pageParam.hasSort())), SysDictTypeVo.class);
    }

    @Override
    public List<SysDictTypeVo> selectDictTypeList(SysDictTypeBo dictType) {
        return BeanMapper.mapList(dictTypeMapper.selectList(buildQueryWrapper(dictType, true)), SysDictTypeVo.class);
    }

    @Override
    public long countDictTypeList(SysDictTypeBo dictType) {
        return dictTypeMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(dictType, false)).getTotal();
    }

    @Override
    public List<SysDictTypeVo> selectDictTypeAll() {
        return BeanMapper.mapList(dictTypeMapper.selectList(QueryBuilder.lambda(SysDictType.class).build()),
            SysDictTypeVo.class);
    }

    @Override
    @Cacheable(cacheNames = CacheNames.SYS_DICT, key = "#dictType")
    public List<SysDictDataVo> selectDictDataByType(String dictType) {
        return BeanMapper.mapList(dictDataMapper.selectDictDataByType(dictType), SysDictDataVo.class);
    }

    @Override
    public SysDictTypeVo selectDictTypeById(Long dictId) {
        return BeanMapper.map(dictTypeMapper.selectById(dictId), SysDictTypeVo.class);
    }

    @Override
    @Cacheable(cacheNames = CacheNames.SYS_DICT_TYPE, key = "#dictType")
    public SysDictTypeVo selectDictTypeByType(String dictType) {
        return BeanMapper.map(dictTypeMapper.selectOne(QueryBuilder.lambda(SysDictType.class)
            .eq(SysDictType::getDictType, dictType)
            .build()), SysDictTypeVo.class);
    }

    @Override
    public void deleteDictTypeByIds(List<Long> dictIds) {
        List<SysDictType> types = dictTypeMapper.selectByIds(dictIds);
        for (SysDictType type : types) {
            boolean assigned = dictDataMapper.exists(QueryBuilder.lambda(SysDictData.class)
                .eq(SysDictData::getDictType, type.getDictType())
                .build());
            if (assigned) {
                throw new ServiceException("{}已分配,不能删除", type.getDictName());
            }
        }
        dictTypeMapper.deleteByIds(dictIds);
        for (SysDictType type : types) {
            CacheUtils.evict(CacheNames.SYS_DICT, type.getDictType());
            CacheUtils.evict(CacheNames.SYS_DICT_TYPE, type.getDictType());
        }
    }

    @Override
    public void resetDictCache() {
        CacheUtils.clear(CacheNames.SYS_DICT);
        CacheUtils.clear(CacheNames.SYS_DICT_TYPE);
    }

    @Override
    @CachePut(cacheNames = CacheNames.SYS_DICT, key = "#bo.dictType")
    public List<SysDictDataVo> insertDictType(SysDictTypeBo bo) {
        if (dictTypeMapper.insert(BeanMapper.map(bo, SysDictType.class)) == 0) {
            throw new ServiceException("新增字典'{}'失败", bo.getDictName());
        }
        // 新类型还没有字典数据
        return new ArrayList<>();
    }

    /**
     * 修改字典类型。类型编码有变化时，原类型的字典数据改到新类型；返回值由注解放入新类型的字典数据缓存，
     * 原类型的字典数据缓存与新旧两个类型的字典类型缓存在这里清除
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CachePut(cacheNames = CacheNames.SYS_DICT, key = "#bo.dictType")
    public List<SysDictDataVo> updateDictType(SysDictTypeBo bo) {
        SysDictType old = dictTypeMapper.selectById(bo.getDictId());
        if (old == null) {
            throw new ServiceException("修改字典'{}'失败，字典不存在", bo.getDictName());
        }
        dictDataMapper.update(Wrappers.<SysDictData>lambdaUpdate()
            .set(SysDictData::getDictType, bo.getDictType())
            .eq(SysDictData::getDictType, old.getDictType()));
        if (dictTypeMapper.updateById(BeanMapper.map(bo, SysDictType.class)) == 0) {
            throw new ServiceException("修改字典'{}'失败，字典不存在", bo.getDictName());
        }
        CacheUtils.evict(CacheNames.SYS_DICT, old.getDictType());
        CacheUtils.evict(CacheNames.SYS_DICT_TYPE, old.getDictType());
        CacheUtils.evict(CacheNames.SYS_DICT_TYPE, bo.getDictType());
        return BeanMapper.mapList(dictDataMapper.selectDictDataByType(bo.getDictType()), SysDictDataVo.class);
    }

    @Override
    public boolean checkDictTypeUnique(SysDictTypeBo dictType) {
        return !dictTypeMapper.exists(QueryBuilder.lambda(SysDictType.class)
            .eq(SysDictType::getDictType, dictType.getDictType())
            .neIfPresent(SysDictType::getDictId, dictType.getDictId())
            .build());
    }

    /**
     * 列表条件：名称、类型编码模糊，创建时间区间；默认按 ID 升序（分页时指定了排序则不加）
     */
    private LambdaQueryWrapper<SysDictType> buildQueryWrapper(SysDictTypeBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(SysDictType.class)
            .likeIfText(SysDictType::getDictName, bo.getDictName())
            .likeIfText(SysDictType::getDictType, bo.getDictType())
            .betweenParams(SysDictType::getCreateTime, bo.getParams(), "beginTime", "endTime")
            .orderByAsc(defaultOrder, SysDictType::getDictId)
            .build();
    }

}
