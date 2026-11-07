package org.tuoyu.system.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.domain.bo.SysDictDataBo;
import org.tuoyu.system.domain.vo.SysDictDataVo;

import java.util.List;

/**
 * 字典 业务层
 *
 * @author Lion Li
 */
public interface ISysDictDataService {

    /**
     * 分页查询字典数据列表
     *
     * @param dictData  查询条件
     * @param pageParam 分页参数
     * @return 字典数据分页列表
     */
    PageResult<SysDictDataVo> selectPageDictDataList(SysDictDataBo dictData, PageParam pageParam);

    /**
     * 根据条件分页查询字典数据
     *
     * @param dictData 字典数据信息
     * @return 字典数据集合信息
     */
    List<SysDictDataVo> selectDictDataList(SysDictDataBo dictData);

    /**
     * 统计符合条件的字典数据数量（导出前先统计，查询条件与列表一致）
     *
     * @param dictData 字典数据信息
     * @return 行数
     */
    long countDictDataList(SysDictDataBo dictData);

    /**
     * 根据字典类型和字典键值查询字典数据信息
     *
     * @param dictType  字典类型
     * @param dictValue 字典键值
     * @return 字典标签
     */
    String selectDictLabel(String dictType, String dictValue);

    /**
     * 根据字典数据ID查询信息
     *
     * @param dictCode 字典数据ID
     * @return 字典数据
     */
    SysDictDataVo selectDictDataById(Long dictCode);

    /**
     * 批量删除字典数据信息
     *
     * @param dictCodes 需要删除的字典数据ID
     */
    void deleteDictDataByIds(List<Long> dictCodes);

    /**
     * 新增保存字典数据信息
     *
     * @param bo 字典数据信息
     * @return 结果
     */
    List<SysDictDataVo> insertDictData(SysDictDataBo bo);

    /**
     * 修改保存字典数据信息
     *
     * @param bo 字典数据信息
     * @return 结果
     */
    List<SysDictDataVo> updateDictData(SysDictDataBo bo);

    /**
     * 校验字典键值是否唯一
     *
     * @param dict 字典数据
     * @return 结果
     */
    boolean checkDictDataUnique(SysDictDataBo dict);

}
