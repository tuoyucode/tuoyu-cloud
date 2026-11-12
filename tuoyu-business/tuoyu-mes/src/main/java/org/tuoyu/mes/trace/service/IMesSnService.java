package org.tuoyu.mes.trace.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.trace.domain.bo.MesSnBo;
import org.tuoyu.mes.trace.domain.bo.MesSnGenerateBo;
import org.tuoyu.mes.trace.domain.vo.MesSnVo;

import java.util.List;

/**
 * 产品序列号
 *
 * @author tuoyu
 */
public interface IMesSnService {

    /**
     * 查询序列号，不存在或不在当前用户的数据权限内时抛出业务异常
     */
    MesSnVo queryById(Long snId);

    PageResult<MesSnVo> queryPageList(MesSnBo bo, PageParam pageParam);

    List<MesSnVo> queryList(MesSnBo bo);

    long countList(MesSnBo bo);

    /**
     * 按工单批量生成序列号：工单须为已下达、生产中或已暂停，产品按序列号追溯，未报废的序列号总数不超过计划数
     */
    void generate(MesSnGenerateBo bo);

    /**
     * 报废序列号：已生成或已完工 → 已报废
     */
    void scrap(Long snId, String reason);

    /**
     * 工单完工后调用：工单下已生成的序列号改为已完工，并逐个记录履历。调用方已锁定工单行
     *
     * @param workorderId 工单
     * @return 改为已完工的序列号个数
     */
    int completeByWorkorder(Long workorderId);

    /**
     * 状态履历（生成、完工、报废）
     */
    List<MesStatusLogVo> logs(Long snId);

}
