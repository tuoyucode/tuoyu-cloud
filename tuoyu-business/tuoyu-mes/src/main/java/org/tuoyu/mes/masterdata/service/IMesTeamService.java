package org.tuoyu.mes.masterdata.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.bo.MesTeamBo;
import org.tuoyu.mes.masterdata.domain.vo.MesTeamMemberVo;
import org.tuoyu.mes.masterdata.domain.vo.MesTeamVo;

import java.util.Collection;
import java.util.List;

/**
 * 班组与班组成员
 *
 * @author tuoyu
 */
public interface IMesTeamService {

    /**
     * 查询班组，不存在时抛出业务异常
     */
    MesTeamVo queryById(Long teamId);

    PageResult<MesTeamVo> queryPageList(MesTeamBo bo, PageParam pageParam);

    List<MesTeamVo> queryList(MesTeamBo bo);

    long countList(MesTeamBo bo);

    /**
     * 启用的班组（下拉选项），可按车间过滤；parentId 为车间 ID
     */
    List<MesOptionVo> options(Long workshopId);

    /**
     * 新增班组：所属工厂由车间带出
     */
    void insertByBo(MesTeamBo bo);

    void updateByBo(MesTeamBo bo);

    /**
     * 删除班组，连同其成员
     */
    void deleteByIds(Collection<Long> ids);

    /**
     * 班组成员（按加入顺序）
     */
    List<MesTeamMemberVo> queryMembers(Long teamId);

    /**
     * 整体覆盖班组成员：空集合表示清空；用户必须存在
     */
    void updateMembers(Long teamId, List<Long> userIds);

}
