package org.tuoyu.mes.material.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.material.domain.bo.MesMaterialRequestBo;
import org.tuoyu.mes.material.domain.bo.MesMaterialRequestCompleteBo;
import org.tuoyu.mes.material.domain.bo.MesMaterialRequestGenerateBo;
import org.tuoyu.mes.material.domain.vo.MesMaterialRequestVo;

import java.util.Collection;
import java.util.List;

/**
 * 领退料单：领料、补料、退料，草稿 →（补料审批中 →）已提交 → 已完成（仓库执行）
 *
 * @author tuoyu
 */
public interface IMesMaterialRequestService {

    /**
     * 查询单据与明细，不存在时抛出业务异常
     */
    MesMaterialRequestVo queryById(Long requestId);

    PageResult<MesMaterialRequestVo> queryPageList(MesMaterialRequestBo bo, PageParam pageParam);

    /**
     * 导出用的列表（条件与数据权限同分页列表，不含明细）
     */
    List<MesMaterialRequestVo> queryList(MesMaterialRequestBo bo);

    /**
     * 导出前统计行数（条件与数据权限同 {@link #queryList}）
     */
    long countList(MesMaterialRequestBo bo);

    /**
     * 新增草稿：工单状态允许领退料、仓库启用、明细物料不重复
     */
    void insertByBo(MesMaterialRequestBo bo);

    /**
     * 修改草稿（表头与明细整体保存，单据类型不能修改）
     */
    void updateByBo(MesMaterialRequestBo bo);

    /**
     * 删除草稿
     */
    void deleteByIds(Collection<Long> ids);

    /**
     * 按工单的物料清单生成领料单草稿
     *
     * @return 新单据的 ID
     */
    Long generateByBom(MesMaterialRequestGenerateBo bo);

    /**
     * 提交：草稿 → 已提交。开启补料审批时，补料单先进入审批中，审批通过后才变为已提交
     *
     * @return 提交后的状态：submitted 已提交，approving 已提交审批
     */
    String submit(Long requestId);

    /**
     * 仓库完成：已提交 → 已完成，写入实发（实退）数量
     */
    void complete(Long requestId, MesMaterialRequestCompleteBo bo);

    /**
     * 取消：草稿、已提交 → 已取消
     */
    void cancel(Long requestId);

    /**
     * 状态履历
     */
    List<MesStatusLogVo> logs(Long requestId);

}
