package org.tuoyu.mes.material.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.material.domain.bo.MesStockInBo;
import org.tuoyu.mes.material.domain.vo.MesStockInVo;

import java.util.List;

/**
 * 完工入库：已提交 → 已入库（累加工单的已入库数量）
 *
 * @author tuoyu
 */
public interface IMesStockInService {

    /**
     * 查询入库单，不存在时抛出业务异常
     */
    MesStockInVo queryById(Long stockInId);

    PageResult<MesStockInVo> queryPageList(MesStockInBo bo, PageParam pageParam);

    /**
     * 新增入库单（已提交）：工单生产中或已完工、成品仓、未取消的入库合计不超过工单完工数
     */
    void insertByBo(MesStockInBo bo);

    /**
     * 入库完成：已提交 → 已入库，累加工单的已入库数量
     */
    void complete(Long stockInId);

    /**
     * 取消：已提交 → 已取消
     */
    void cancel(Long stockInId);

    List<MesStatusLogVo> logs(Long stockInId);

}
