package org.tuoyu.system.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.domain.bo.SysDictDataBo;
import org.tuoyu.system.domain.vo.SysDictDataVo;

import java.util.List;

/**
 * 字典数据服务。写入后按类型编码更新或清除字典数据的缓存
 *
 * @author tuoyu
 */
public interface ISysDictDataService {

    /**
     * 分页查询字典数据
     *
     * @param dictData  查询条件
     * @param pageParam 分页参数
     * @return 字典数据分页列表
     */
    PageResult<SysDictDataVo> selectPageDictDataList(SysDictDataBo dictData, PageParam pageParam);

    /**
     * 查询字典数据列表（导出用）
     *
     * @param dictData 查询条件
     * @return 字典数据列表
     */
    List<SysDictDataVo> selectDictDataList(SysDictDataBo dictData);

    /**
     * 统计符合条件的字典数据数量（导出前先统计，查询条件与列表一致）
     *
     * @param dictData 查询条件
     * @return 行数
     */
    long countDictDataList(SysDictDataBo dictData);

    /**
     * 按编码查询字典数据
     *
     * @param dictCode 字典编码
     * @return 字典数据；不存在时为 null
     */
    SysDictDataVo selectDictDataById(Long dictCode);

    /**
     * 批量删除字典数据
     *
     * @param dictCodes 字典编码
     */
    void deleteDictDataByIds(List<Long> dictCodes);

    /**
     * 新增字典数据
     *
     * @param bo 字典数据
     * @return 该类型的全部字典数据（放入缓存）
     */
    List<SysDictDataVo> insertDictData(SysDictDataBo bo);

    /**
     * 修改字典数据
     *
     * @param bo 字典数据
     * @return 新类型的全部字典数据（放入缓存）
     */
    List<SysDictDataVo> updateDictData(SysDictDataBo bo);

    /**
     * 同一类型中键值是否唯一（修改时排除自己）
     *
     * @param dictData 字典数据
     * @return 没有其他同类型同键值的数据时为 true
     */
    boolean checkDictDataUnique(SysDictDataBo dictData);

}
