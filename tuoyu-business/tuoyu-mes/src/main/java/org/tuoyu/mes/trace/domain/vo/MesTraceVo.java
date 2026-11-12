package org.tuoyu.mes.trace.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 追溯查询结果：按关键字识别出的类型、匹配到的工单及其生产记录、按时间排序的事件
 *
 * @author tuoyu
 */
@Data
public class MesTraceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 查询的关键字
     */
    private String keyword;

    /**
     * 识别出的类型：sn 序列号、batch 生产批次号、material_batch 物料批次号、workorder 工单编号；没有匹配时为空
     */
    private String matchType;

    /**
     * 匹配到的工单数超过返回上限时为 true（只返回最近的工单）
     */
    private boolean truncated;

    /**
     * 匹配到的工单
     */
    private List<MesTraceWorkorderVo> workorders = new ArrayList<>();

    /**
     * 事件时间线（按时间先后），不含没有权限的段的事件
     */
    private List<MesTraceEventVo> timeline = new ArrayList<>();

    /**
     * 当前用户没有查询权限的段（reports 报工、materials 用料、inspections 检验、defects 不良、stockIns 入库），
     * 这些段在各工单中为空，时间线中也没有它们的事件
     */
    private List<String> deniedSections = new ArrayList<>();

}
