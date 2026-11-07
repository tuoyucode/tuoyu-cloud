package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.tuoyu.common.mybatis.core.page.PageResults;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.redis.utils.CacheUtils;
import org.tuoyu.system.domain.SysDictData;
import org.tuoyu.system.domain.bo.SysDictDataBo;
import org.tuoyu.system.domain.vo.SysDictDataVo;
import org.tuoyu.system.mapper.SysDictDataMapper;
import org.tuoyu.system.service.ISysDictDataService;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典 业务层处理
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysDictDataServiceImpl implements ISysDictDataService {

    private final SysDictDataMapper dictDataMapper;

    /**
     * 分页查询字典数据列表
     *
     * @param dictData  查询条件
     * @param pageParam 分页参数
     * @return 字典数据分页列表
     */
    @Override
    public PageResult<SysDictDataVo> selectPageDictDataList(SysDictDataBo dictData, PageParam pageParam) {
        LambdaQueryWrapper<SysDictData> lqw = buildQueryWrapper(dictData);
        return PageResults.of(dictDataMapper.selectPage(pageParam.toPage(SysDictData.class), lqw), SysDictDataVo.class);
    }

    /**
     * 根据条件分页查询字典数据
     *
     * @param dictData 字典数据信息
     * @return 字典数据集合信息
     */
    @Override
    public List<SysDictDataVo> selectDictDataList(SysDictDataBo dictData) {
        LambdaQueryWrapper<SysDictData> lqw = buildQueryWrapper(dictData);
        return BeanMapper.mapList(dictDataMapper.selectList(lqw), SysDictDataVo.class);
    }

    /**
     * 统计符合条件的字典数据数量（导出前先统计，查询条件与列表一致）
     */
    @Override
    public long countDictDataList(SysDictDataBo dictData) {
        return dictDataMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(dictData)).getTotal();
    }

    private LambdaQueryWrapper<SysDictData> buildQueryWrapper(SysDictDataBo bo) {
        return QueryBuilder.lambda(SysDictData.class)
            .eqIfPresent(SysDictData::getDictSort, bo.getDictSort())
            .likeIfText(SysDictData::getDictLabel, bo.getDictLabel())
            .eqIfText(SysDictData::getDictType, bo.getDictType())
            .orderByAsc(SysDictData::getDictSort, SysDictData::getDictCode)
            .build();
    }

    /**
     * 根据字典类型和字典键值查询字典数据信息
     *
     * @param dictType  字典类型
     * @param dictValue 字典键值
     * @return 字典标签
     */
    @Override
    public String selectDictLabel(String dictType, String dictValue) {
        SysDictData data = dictDataMapper.selectOne(QueryBuilder.lambda(SysDictData.class)
            .select(SysDictData::getDictLabel)
            .eq(SysDictData::getDictType, dictType)
            .eq(SysDictData::getDictValue, dictValue)
            .build());
        // 字典值不存在时返回空串，不抛空指针
        return data == null ? StringUtils.EMPTY : data.getDictLabel();
    }

    /**
     * 根据字典数据ID查询信息
     *
     * @param dictCode 字典数据ID
     * @return 字典数据
     */
    @Override
    public SysDictDataVo selectDictDataById(Long dictCode) {
        return BeanMapper.map(dictDataMapper.selectById(dictCode), SysDictDataVo.class);
    }

    /**
     * 批量删除字典数据信息
     *
     * @param dictCodes 需要删除的字典数据ID
     */
    @Override
    public void deleteDictDataByIds(List<Long> dictCodes) {
        List<SysDictData> list = dictDataMapper.selectByIds(dictCodes);
        dictDataMapper.deleteByIds(dictCodes);
        list.forEach(x -> CacheUtils.evict(CacheNames.SYS_DICT, x.getDictType()));
    }

    /**
     * 新增保存字典数据信息
     *
     * @param bo 字典数据信息
     * @return 结果
     */
    @CachePut(cacheNames = CacheNames.SYS_DICT, key = "#bo.dictType")
    @Override
    public List<SysDictDataVo> insertDictData(SysDictDataBo bo) {
        SysDictData data = BeanMapper.map(bo, SysDictData.class);
        int row = dictDataMapper.insert(data);
        if (row > 0) {
            return BeanMapper.mapList(dictDataMapper.selectDictDataByType(data.getDictType()), SysDictDataVo.class);
        }
        throw new ServiceException("新增字典数据'{}'失败", bo.getDictLabel());
    }

    /**
     * 修改保存字典数据信息
     *
     * @param bo 字典数据信息
     * @return 结果
     */
    @CachePut(cacheNames = CacheNames.SYS_DICT, key = "#bo.dictType")
    @Override
    public List<SysDictDataVo> updateDictData(SysDictDataBo bo) {
        SysDictData data = BeanMapper.map(bo, SysDictData.class);
        int row = dictDataMapper.updateById(data);
        if (row > 0) {
            return BeanMapper.mapList(dictDataMapper.selectDictDataByType(data.getDictType()), SysDictDataVo.class);
        }
        throw new ServiceException("修改字典数据'{}'失败，字典数据不存在", bo.getDictLabel());
    }

    /**
     * 校验字典键值是否唯一
     *
     * @param dict 字典数据
     * @return 结果
     */
    @Override
    public boolean checkDictDataUnique(SysDictDataBo dict) {
        boolean exist = dictDataMapper.exists(QueryBuilder.lambda(SysDictData.class)
            .eq(SysDictData::getDictType, dict.getDictType())
            .eq(SysDictData::getDictValue, dict.getDictValue())
            .neIfPresent(SysDictData::getDictCode, dict.getDictCode())
            .build());
        return !exist;
    }

}
