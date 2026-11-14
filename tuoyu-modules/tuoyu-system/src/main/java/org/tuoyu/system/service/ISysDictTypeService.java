package org.tuoyu.system.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.domain.bo.SysDictTypeBo;
import org.tuoyu.system.domain.vo.SysDictDataVo;
import org.tuoyu.system.domain.vo.SysDictTypeVo;

import java.util.List;

/**
 * 字典类型服务。字典全平台共享（不分租户），按类型编码缓存字典类型与该类型的字典数据
 *
 * @author tuoyu
 */
public interface ISysDictTypeService {

    /**
     * 分页查询字典类型
     *
     * @param dictType  查询条件
     * @param pageParam 分页参数
     * @return 字典类型分页列表
     */
    PageResult<SysDictTypeVo> selectPageDictTypeList(SysDictTypeBo dictType, PageParam pageParam);

    /**
     * 查询字典类型列表（导出用）
     *
     * @param dictType 查询条件
     * @return 字典类型列表
     */
    List<SysDictTypeVo> selectDictTypeList(SysDictTypeBo dictType);

    /**
     * 统计符合条件的字典类型数量（导出前先统计，查询条件与列表一致）
     *
     * @param dictType 查询条件
     * @return 行数
     */
    long countDictTypeList(SysDictTypeBo dictType);

    /**
     * 全部字典类型
     *
     * @return 字典类型列表
     */
    List<SysDictTypeVo> selectDictTypeAll();

    /**
     * 按类型编码查询该类型的全部字典数据（按排序号升序）
     *
     * @param dictType 字典类型编码
     * @return 字典数据；没有时为空列表
     */
    List<SysDictDataVo> selectDictDataByType(String dictType);

    /**
     * 按 ID 查询字典类型
     *
     * @param dictId 字典类型 ID
     * @return 字典类型；不存在时为 null
     */
    SysDictTypeVo selectDictTypeById(Long dictId);

    /**
     * 按类型编码查询字典类型
     *
     * @param dictType 字典类型编码
     * @return 字典类型；不存在时为 null
     */
    SysDictTypeVo selectDictTypeByType(String dictType);

    /**
     * 批量删除字典类型；其中有类型已有字典数据时整体拒绝
     *
     * @param dictIds 字典类型 ID
     */
    void deleteDictTypeByIds(List<Long> dictIds);

    /**
     * 清空字典类型与字典数据的缓存
     */
    void resetDictCache();

    /**
     * 新增字典类型
     *
     * @param bo 字典类型
     * @return 该类型的字典数据（新类型为空列表，放入缓存）
     */
    List<SysDictDataVo> insertDictType(SysDictTypeBo bo);

    /**
     * 修改字典类型；类型编码有变化时把原类型的字典数据一并改到新类型
     *
     * @param bo 字典类型
     * @return 新类型的全部字典数据（放入缓存）
     */
    List<SysDictDataVo> updateDictType(SysDictTypeBo bo);

    /**
     * 类型编码是否唯一（修改时排除自己）
     *
     * @param dictType 字典类型
     * @return 没有其他同类型编码的字典类型时为 true
     */
    boolean checkDictTypeUnique(SysDictTypeBo dictType);

}
