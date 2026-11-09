package org.tuoyu.mes.common.statuslog;

import org.tuoyu.mes.common.enums.BizType;

import java.util.List;

/**
 * 状态履历：记录每次状态变化（谁、何时、从什么状态到什么状态、因为什么），供单据详情查看与追溯
 *
 * @author tuoyu
 */
public interface IMesStatusLogService {

    /**
     * 记录一次状态变化，操作人为当前用户（后台任务中为空）
     *
     * @param bizType 单据类型
     * @param bizId   单据 ID
     * @param bizCode 单据编号
     * @param action  动作，例如 release
     * @param from    变更前状态，新建时为空
     * @param to      变更后状态
     * @param reason  原因或说明，可为空
     */
    void record(BizType bizType, Long bizId, String bizCode, String action, String from, String to, String reason);

    /**
     * 查询某个单据的状态履历，按时间先后排列
     *
     * @param bizType 单据类型
     * @param bizId   单据 ID
     * @return 状态履历
     */
    List<MesStatusLogVo> list(BizType bizType, Long bizId);

}
