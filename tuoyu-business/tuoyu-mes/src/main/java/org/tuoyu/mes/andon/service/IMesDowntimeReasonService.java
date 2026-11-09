package org.tuoyu.mes.andon.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.andon.domain.bo.MesDowntimeReasonBo;
import org.tuoyu.mes.andon.domain.vo.MesDowntimeReasonVo;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;

import java.util.Collection;
import java.util.List;

/**
 * 停机原因
 *
 * @author tuoyu
 */
public interface IMesDowntimeReasonService {

    /**
     * 查询停机原因，不存在时抛出业务异常
     */
    MesDowntimeReasonVo queryById(Long reasonId);

    PageResult<MesDowntimeReasonVo> queryPageList(MesDowntimeReasonBo bo, PageParam pageParam);

    /**
     * 启用的停机原因（下拉选项）
     */
    List<MesOptionVo> options();

    void insertByBo(MesDowntimeReasonBo bo);

    void updateByBo(MesDowntimeReasonBo bo);

    /**
     * 删除停机原因：已被安灯事件引用的不能删除（可以停用）
     */
    void deleteByIds(Collection<Long> ids);

}
