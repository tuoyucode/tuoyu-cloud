package org.tuoyu.mes.andon.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.andon.domain.bo.MesAndonEventBo;
import org.tuoyu.mes.andon.domain.vo.MesAndonEventVo;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;

import java.util.List;

/**
 * 安灯事件：现场异常的上报、派单、处理、解决与关闭
 *
 * @author tuoyu
 */
public interface IMesAndonEventService {

    /**
     * 查询安灯事件，不存在时抛出业务异常
     */
    MesAndonEventVo queryById(Long eventId);

    PageResult<MesAndonEventVo> queryPageList(MesAndonEventBo bo, PageParam pageParam);

    List<MesAndonEventVo> queryList(MesAndonEventBo bo);

    long countList(MesAndonEventBo bo);

    /**
     * 上报：上报人为当前用户；产线、工位、设备必须属于所选车间
     */
    void report(MesAndonEventBo bo);

    /**
     * 派单（或改派）：已上报、已派单 → 已派单，并给处理人推送消息
     */
    void assign(Long eventId, Long handlerUserId);

    /**
     * 开始处理：已派单 → 处理中；只有处理人本人或有派单权限的人可以操作
     */
    void process(Long eventId);

    /**
     * 解决：处理中 → 已解决；停线或填写了停机原因时记录停机分钟数
     */
    void resolve(Long eventId, String resolveRemark, Long downtimeReasonId);

    /**
     * 关闭：已解决 → 已关闭（上报人确认）；已上报的误报可以直接关闭，此时必须填写原因
     */
    void close(Long eventId, String reason);

    List<MesStatusLogVo> logs(Long eventId);

}
