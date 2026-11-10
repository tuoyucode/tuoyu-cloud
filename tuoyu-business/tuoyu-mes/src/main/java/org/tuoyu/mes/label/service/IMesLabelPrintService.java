package org.tuoyu.mes.label.service;

import org.tuoyu.mes.label.domain.bo.MesSnLabelBo;
import org.tuoyu.mes.label.domain.vo.MesLabelDataVo;

import java.util.List;

/**
 * 标签的打印数据：后端只给出每个标签的字段，排版与打印在浏览器中完成
 *
 * @author tuoyu
 */
public interface IMesLabelPrintService {

    /**
     * 序列号标签：勾选的序列号或某个工单的全部未报废序列号，最多 500 个；
     * 只能打印数据权限内工单的序列号，有任何一个不存在或无权访问时整体拒绝
     */
    List<MesLabelDataVo> snLabels(MesSnLabelBo bo);

    /**
     * 批次标签：每个工单一个，工单须有批次号且在数据权限内，最多 500 个
     */
    List<MesLabelDataVo> batchLabels(List<Long> workorderIds);

}
