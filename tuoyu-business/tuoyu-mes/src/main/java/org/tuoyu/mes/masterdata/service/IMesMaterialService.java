package org.tuoyu.mes.masterdata.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.bo.MesMaterialBo;
import org.tuoyu.mes.masterdata.domain.vo.MesMaterialVo;

import java.util.Collection;
import java.util.List;

/**
 * 物料
 *
 * @author tuoyu
 */
public interface IMesMaterialService {

    /**
     * 选项接口最多返回的条数
     */
    int OPTION_LIMIT = 200;

    /**
     * 查询物料，不存在时抛出业务异常
     */
    MesMaterialVo queryById(Long materialId);

    PageResult<MesMaterialVo> queryPageList(MesMaterialBo bo, PageParam pageParam);

    List<MesMaterialVo> queryList(MesMaterialBo bo);

    long countList(MesMaterialBo bo);

    /**
     * 启用的物料（下拉选项），最多 {@link #OPTION_LIMIT} 条
     *
     * @param materialTypes 物料类型，逗号分隔多个；为空时不限
     * @param keyword       编码或名称模糊匹配；为空时不限
     */
    List<MesOptionVo> options(String materialTypes, String keyword);

    /**
     * 新增物料：物料类型、追溯方式必须是有效的编码
     */
    void insertByBo(MesMaterialBo bo);

    void updateByBo(MesMaterialBo bo);

    /**
     * 删除物料：被物料清单、工艺路线或工单引用的物料不能删除
     */
    void deleteByIds(Collection<Long> ids);

}
