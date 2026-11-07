package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.tuoyu.common.mybatis.core.page.PageResults;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.mybatis.core.page.PageParam;
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
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 字典 业务层处理
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysDictTypeServiceImpl implements ISysDictTypeService {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;

    /**
     * 分页查询字典类型列表
     *
     * @param dictType  查询条件
     * @param pageParam 分页参数
     * @return 字典类型分页列表
     */
    @Override
    public PageResult<SysDictTypeVo> selectPageDictTypeList(SysDictTypeBo dictType, PageParam pageParam) {
        LambdaQueryWrapper<SysDictType> lqw = buildQueryWrapper(dictType);
        return PageResults.of(dictTypeMapper.selectPage(pageParam.toPage(SysDictType.class), lqw), SysDictTypeVo.class);
    }

    /**
     * 根据条件分页查询字典类型
     *
     * @param dictType 字典类型信息
     * @return 字典类型集合信息
     */
    @Override
    public List<SysDictTypeVo> selectDictTypeList(SysDictTypeBo dictType) {
        LambdaQueryWrapper<SysDictType> lqw = buildQueryWrapper(dictType);
        return BeanMapper.mapList(dictTypeMapper.selectList(lqw), SysDictTypeVo.class);
    }

    /**
     * 统计符合条件的字典类型数量（导出前先统计，查询条件与列表一致）
     */
    @Override
    public long countDictTypeList(SysDictTypeBo dictType) {
        return dictTypeMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(dictType)).getTotal();
    }

    private LambdaQueryWrapper<SysDictType> buildQueryWrapper(SysDictTypeBo bo) {
        Map<String, Object> params = bo.getParams();
        return QueryBuilder.lambda(SysDictType.class)
            .likeIfText(SysDictType::getDictName, bo.getDictName())
            .likeIfText(SysDictType::getDictType, bo.getDictType())
            .betweenParams(SysDictType::getCreateTime, params, "beginTime", "endTime")
            .orderByAsc(SysDictType::getDictId)
            .build();
    }

    /**
     * 根据所有字典类型
     *
     * @return 字典类型集合信息
     */
    @Override
    public List<SysDictTypeVo> selectDictTypeAll() {
        return BeanMapper.mapList(dictTypeMapper.selectList(Wrappers.emptyWrapper()), SysDictTypeVo.class);
    }

    /**
     * 根据字典类型查询字典数据
     *
     * @param dictType 字典类型
     * @return 字典数据集合信息
     */
    @Cacheable(cacheNames = CacheNames.SYS_DICT, key = "#dictType")
    @Override
    public List<SysDictDataVo> selectDictDataByType(String dictType) {
        return BeanMapper.mapList(dictDataMapper.selectDictDataByType(dictType), SysDictDataVo.class);
    }

    /**
     * 根据字典类型ID查询信息
     *
     * @param dictId 字典类型ID
     * @return 字典类型
     */
    @Override
    public SysDictTypeVo selectDictTypeById(Long dictId) {
        return BeanMapper.map(dictTypeMapper.selectById(dictId), SysDictTypeVo.class);
    }

    /**
     * 根据字典类型查询信息
     *
     * @param dictType 字典类型
     * @return 字典类型
     */
    @Cacheable(cacheNames = CacheNames.SYS_DICT_TYPE, key = "#dictType")
    @Override
    public SysDictTypeVo selectDictTypeByType(String dictType) {
        return BeanMapper.map(dictTypeMapper.selectOne(QueryBuilder.lambda(SysDictType.class).eq(SysDictType::getDictType, dictType).build()), SysDictTypeVo.class);
    }

    /**
     * 批量删除字典类型信息
     *
     * @param dictIds 需要删除的字典ID
     */
    @Override
    public void deleteDictTypeByIds(List<Long> dictIds) {
        List<SysDictType> list = dictTypeMapper.selectByIds(dictIds);
        list.forEach(x -> {
            boolean assigned = dictDataMapper.exists(QueryBuilder.lambda(SysDictData.class).eq(SysDictData::getDictType, x.getDictType()).build());
            if (assigned) {
                throw new ServiceException("{}已分配,不能删除", x.getDictName());
            }
        });
        dictTypeMapper.deleteByIds(dictIds);
        list.forEach(x -> {
            CacheUtils.evict(CacheNames.SYS_DICT, x.getDictType());
            CacheUtils.evict(CacheNames.SYS_DICT_TYPE, x.getDictType());
        });
    }

    /**
     * 重置字典缓存数据
     */
    @Override
    public void resetDictCache() {
        CacheUtils.clear(CacheNames.SYS_DICT);
        CacheUtils.clear(CacheNames.SYS_DICT_TYPE);
    }

    /**
     * 新增保存字典类型信息
     *
     * @param bo 字典类型信息
     * @return 结果
     */
    @CachePut(cacheNames = CacheNames.SYS_DICT, key = "#bo.dictType")
    @Override
    public List<SysDictDataVo> insertDictType(SysDictTypeBo bo) {
        SysDictType dict = BeanMapper.map(bo, SysDictType.class);
        int row = dictTypeMapper.insert(dict);
        if (row > 0) {
            // 新增 type 下无 data 数据 返回空防止缓存穿透
            return new ArrayList<>();
        }
        throw new ServiceException("新增字典'{}'失败", bo.getDictName());
    }

    /**
     * 修改保存字典类型信息
     *
     * @param bo 字典类型信息
     * @return 结果
     */
    @CachePut(cacheNames = CacheNames.SYS_DICT, key = "#bo.dictType")
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SysDictDataVo> updateDictType(SysDictTypeBo bo) {
        SysDictType dict = BeanMapper.map(bo, SysDictType.class);
        SysDictType oldDict = dictTypeMapper.selectById(dict.getDictId());
        dictDataMapper.update(Wrappers.<SysDictData>lambdaUpdate()
            .set(SysDictData::getDictType, dict.getDictType())
            .eq(SysDictData::getDictType, oldDict.getDictType()));
        int row = dictTypeMapper.updateById(dict);
        if (row > 0) {
            CacheUtils.evict(CacheNames.SYS_DICT, oldDict.getDictType());
            return BeanMapper.mapList(dictDataMapper.selectDictDataByType(dict.getDictType()), SysDictDataVo.class);
        }
        throw new ServiceException("修改字典'{}'失败，字典不存在", bo.getDictName());
    }

    /**
     * 校验字典类型称是否唯一
     *
     * @param dictType 字典类型
     * @return 结果
     */
    @Override
    public boolean checkDictTypeUnique(SysDictTypeBo dictType) {
        boolean exist = dictTypeMapper.exists(QueryBuilder.lambda(SysDictType.class)
            .eq(SysDictType::getDictType, dictType.getDictType())
            .neIfPresent(SysDictType::getDictId, dictType.getDictId())
            .build());
        return !exist;
    }

}
