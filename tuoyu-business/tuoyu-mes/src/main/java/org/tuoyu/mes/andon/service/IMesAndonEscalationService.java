package org.tuoyu.mes.andon.service;

/**
 * 安灯超时升级：由定时任务调用，检查所有租户中超时未响应、超时未解决的安灯事件
 *
 * @author tuoyu
 */
public interface IMesAndonEscalationService {

    /**
     * 执行一次检查：先为待计算的事件按升级规则算出截止时间，再升级超时的事件并提醒相关人员。
     * 多个实例同时执行时，同一事件的同一时限只会被其中一个升级
     *
     * @return 本次升级（含只提醒）的事件数
     */
    int checkOverdue();

}
