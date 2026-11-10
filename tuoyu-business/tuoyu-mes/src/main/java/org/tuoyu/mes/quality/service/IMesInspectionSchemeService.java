package org.tuoyu.mes.quality.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.quality.domain.bo.MesInspectionSchemeBo;
import org.tuoyu.mes.quality.domain.vo.MesInspectionSchemeVo;

import java.util.Collection;
import java.util.List;

/**
 * 检验方案
 *
 * @author tuoyu
 */
public interface IMesInspectionSchemeService {

    /**
     * 查询检验方案，不存在时抛出业务异常
     */
    MesInspectionSchemeVo queryById(Long schemeId);

    PageResult<MesInspectionSchemeVo> queryPageList(MesInspectionSchemeBo bo, PageParam pageParam);

    /**
     * 导出用的列表（条件同分页列表）
     */
    List<MesInspectionSchemeVo> queryList(MesInspectionSchemeBo bo);

    /**
     * 导出前统计行数（条件同 {@link #queryList}）
     */
    long countList(MesInspectionSchemeBo bo);

    /**
     * 启用的方案（下拉选项），可按检验类型与产品过滤（产品为空的通用方案始终包含）
     */
    List<MesOptionVo> options(String inspectionType, Long materialId);

    /**
     * 最匹配的启用方案：产品与工序都匹配的优先，其次只匹配产品（不限工序），再次通用（不限产品）
     *
     * @param inspectionType 检验类型
     * @param materialId     产品，可为空
     * @param operationCode  工序编码，可为空
     * @return 没有匹配的方案时为 null
     */
    MesInspectionSchemeVo match(String inspectionType, Long materialId, String operationCode);

    void insertByBo(MesInspectionSchemeBo bo);

    void updateByBo(MesInspectionSchemeBo bo);

    /**
     * 删除方案：还有待检验的检验单使用时不能删除
     */
    void deleteByIds(Collection<Long> ids);

}
