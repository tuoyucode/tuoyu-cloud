package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.redis.utils.CacheUtils;
import org.tuoyu.system.domain.SysDictData;
import org.tuoyu.system.domain.bo.SysDictDataBo;
import org.tuoyu.system.domain.vo.SysDictDataVo;
import org.tuoyu.system.mapper.SysDictDataMapper;
import org.tuoyu.system.service.ISysDictDataService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 字典数据服务实现。
 * <p>
 * 新增、修改后返回该类型的全部字典数据，由注解放入 {@link CacheNames#SYS_DICT}（按类型编码）；
 * 修改时改了类型、删除时，受影响类型的缓存在这里清除。创建部门等审计字段由框架填充，不取请求中的值
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class SysDictDataServiceImpl implements ISysDictDataService {

    private final SysDictDataMapper dictDataMapper;

    @Override
    public PageResult<SysDictDataVo> selectPageDictDataList(SysDictDataBo dictData, PageParam pageParam) {
        return PageResults.of(dictDataMapper.selectPage(pageParam.toPage(SysDictData.class),
            buildQueryWrapper(dictData, !pageParam.hasSort())), SysDictDataVo.class);
    }

    @Override
    public List<SysDictDataVo> selectDictDataList(SysDictDataBo dictData) {
        return BeanMapper.mapList(dictDataMapper.selectList(buildQueryWrapper(dictData, true)), SysDictDataVo.class);
    }

    @Override
    public long countDictDataList(SysDictDataBo dictData) {
        return dictDataMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(dictData, false)).getTotal();
    }

    @Override
    public SysDictDataVo selectDictDataById(Long dictCode) {
        return BeanMapper.map(dictDataMapper.selectById(dictCode), SysDictDataVo.class);
    }

    @Override
    public void deleteDictDataByIds(List<Long> dictCodes) {
        List<SysDictData> rows = dictDataMapper.selectByIds(dictCodes);
        dictDataMapper.deleteByIds(dictCodes);
        Set<String> types = new LinkedHashSet<>();
        for (SysDictData row : rows) {
            types.add(row.getDictType());
        }
        for (String type : types) {
            CacheUtils.evict(CacheNames.SYS_DICT, type);
        }
    }

    @Override
    @CachePut(cacheNames = CacheNames.SYS_DICT, key = "#bo.dictType")
    public List<SysDictDataVo> insertDictData(SysDictDataBo bo) {
        if (dictDataMapper.insert(toEntity(bo)) == 0) {
            throw new ServiceException("新增字典数据'{}'失败", bo.getDictLabel());
        }
        return BeanMapper.mapList(dictDataMapper.selectDictDataByType(bo.getDictType()), SysDictDataVo.class);
    }

    @Override
    @CachePut(cacheNames = CacheNames.SYS_DICT, key = "#bo.dictType")
    public List<SysDictDataVo> updateDictData(SysDictDataBo bo) {
        SysDictData old = dictDataMapper.selectById(bo.getDictCode());
        if (old == null || dictDataMapper.updateById(toEntity(bo)) == 0) {
            throw new ServiceException("修改字典数据'{}'失败，字典数据不存在", bo.getDictLabel());
        }
        // 改了类型：注解只更新新类型的缓存，原类型的缓存中仍有这条数据
        if (!Objects.equals(old.getDictType(), bo.getDictType())) {
            CacheUtils.evict(CacheNames.SYS_DICT, old.getDictType());
        }
        return BeanMapper.mapList(dictDataMapper.selectDictDataByType(bo.getDictType()), SysDictDataVo.class);
    }

    @Override
    public boolean checkDictDataUnique(SysDictDataBo dictData) {
        return !dictDataMapper.exists(QueryBuilder.lambda(SysDictData.class)
            .eq(SysDictData::getDictType, dictData.getDictType())
            .eq(SysDictData::getDictValue, dictData.getDictValue())
            .neIfPresent(SysDictData::getDictCode, dictData.getDictCode())
            .build());
    }

    /**
     * 请求转实体；创建部门由审计填充决定，不取请求中的值
     */
    private static SysDictData toEntity(SysDictDataBo bo) {
        SysDictData data = BeanMapper.map(bo, SysDictData.class);
        data.setCreateDept(null);
        return data;
    }

    /**
     * 列表条件：排序号相等、标签模糊、类型相等；默认按排序号、编码升序（分页时指定了排序则不加）
     */
    private LambdaQueryWrapper<SysDictData> buildQueryWrapper(SysDictDataBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(SysDictData.class)
            .eqIfPresent(SysDictData::getDictSort, bo.getDictSort())
            .likeIfText(SysDictData::getDictLabel, bo.getDictLabel())
            .eqIfText(SysDictData::getDictType, bo.getDictType())
            .orderByAsc(defaultOrder, SysDictData::getDictSort, SysDictData::getDictCode)
            .build();
    }

}
