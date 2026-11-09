package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.MesFactory;
import org.tuoyu.mes.masterdata.domain.MesTeam;
import org.tuoyu.mes.masterdata.domain.MesTeamMember;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.domain.bo.MesTeamBo;
import org.tuoyu.mes.masterdata.domain.vo.MesTeamMemberVo;
import org.tuoyu.mes.masterdata.domain.vo.MesTeamVo;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMemberMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.masterdata.service.IMesTeamService;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.vo.RemoteUserVo;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 班组与班组成员
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesTeamServiceImpl implements IMesTeamService {

    private final MesTeamMapper teamMapper;
    private final MesTeamMemberMapper memberMapper;
    private final MesWorkshopMapper workshopMapper;
    private final MesFactoryMapper factoryMapper;
    private final RemoteUserService remoteUserService;

    @Override
    public MesTeamVo queryById(Long teamId) {
        MesTeam team = MasterdataSupport.require(teamMapper.selectById(teamId), "班组不存在");
        MesTeamVo vo = BeanMapper.map(team, MesTeamVo.class);
        fillNames(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesTeamVo> queryPageList(MesTeamBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesTeam> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        PageResult<MesTeamVo> result = PageResults.of(teamMapper.selectPage(pageParam.toPage(MesTeam.class), lqw),
            MesTeamVo.class);
        fillNames(result.getItems());
        return result;
    }

    @Override
    public List<MesTeamVo> queryList(MesTeamBo bo) {
        List<MesTeamVo> list = BeanMapper.mapList(teamMapper.selectList(buildQueryWrapper(bo, true)), MesTeamVo.class);
        fillNames(list);
        return list;
    }

    @Override
    public long countList(MesTeamBo bo) {
        return teamMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    @Override
    public List<MesOptionVo> options(Long workshopId) {
        return teamMapper.selectList(QueryBuilder.lambda(MesTeam.class)
                .select(MesTeam::getTeamId, MesTeam::getTeamCode, MesTeam::getTeamName, MesTeam::getWorkshopId)
                .eq(MesTeam::getStatus, SystemConstants.NORMAL)
                .eqIfPresent(MesTeam::getWorkshopId, workshopId)
                .orderByAsc(MesTeam::getSortNo, MesTeam::getTeamId)
                .build())
            .stream()
            .map(t -> new MesOptionVo(t.getTeamId(), t.getTeamCode(), t.getTeamName(), t.getWorkshopId()))
            .toList();
    }

    private LambdaQueryWrapper<MesTeam> buildQueryWrapper(MesTeamBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesTeam.class)
            .eqIfPresent(MesTeam::getFactoryId, bo.getFactoryId())
            .eqIfPresent(MesTeam::getWorkshopId, bo.getWorkshopId())
            .likeIfText(MesTeam::getTeamCode, bo.getTeamCode())
            .likeIfText(MesTeam::getTeamName, bo.getTeamName())
            .eqIfPresent(MesTeam::getLeaderUserId, bo.getLeaderUserId())
            .eqIfText(MesTeam::getStatus, bo.getStatus())
            .orderByAsc(defaultOrder, MesTeam::getSortNo)
            .orderByAsc(defaultOrder, MesTeam::getTeamId)
            .build();
    }

    private void fillNames(List<MesTeamVo> list) {
        if (list.isEmpty()) {
            return;
        }
        MasterdataSupport.fill(list, MesTeamVo::getFactoryId, factoryMapper, MesFactory::getFactoryId,
            (vo, factory) -> vo.setFactoryName(factory.getFactoryName()));
        MasterdataSupport.fill(list, MesTeamVo::getWorkshopId, workshopMapper, MesWorkshop::getWorkshopId,
            (vo, workshop) -> vo.setWorkshopName(workshop.getWorkshopName()));
        Set<Long> teamIds = list.stream().map(MesTeamVo::getTeamId).collect(Collectors.toSet());
        Map<Long, Long> counts = memberMapper.selectList(QueryBuilder.lambda(MesTeamMember.class)
                .select(MesTeamMember::getTeamId)
                .in(MesTeamMember::getTeamId, teamIds)
                .build())
            .stream()
            .collect(Collectors.groupingBy(MesTeamMember::getTeamId, Collectors.counting()));
        list.forEach(vo -> vo.setMemberCount(counts.getOrDefault(vo.getTeamId(), 0L).intValue()));
    }

    @Override
    public void insertByBo(MesTeamBo bo) {
        checkCodeUnique(bo.getTeamCode(), null);
        MesWorkshop workshop = requireWorkshop(bo.getWorkshopId());
        checkLeader(bo.getLeaderUserId());
        MesTeam team = BeanMapper.map(bo, MesTeam.class);
        team.setTeamId(null);
        team.setFactoryId(workshop.getFactoryId());
        team.setStatus(MasterdataSupport.statusOrNormal(team.getStatus()));
        team.setSortNo(MasterdataSupport.sortOrZero(team.getSortNo()));
        teamMapper.insert(team);
        bo.setTeamId(team.getTeamId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateByBo(MesTeamBo bo) {
        MasterdataSupport.require(teamMapper.selectById(bo.getTeamId()), "修改班组失败，班组不存在");
        checkCodeUnique(bo.getTeamCode(), bo.getTeamId());
        MesWorkshop workshop = requireWorkshop(bo.getWorkshopId());
        checkLeader(bo.getLeaderUserId());
        MesTeam team = BeanMapper.map(bo, MesTeam.class);
        team.setFactoryId(workshop.getFactoryId());
        if (teamMapper.updateById(team) == 0) {
            throw new ServiceException("修改班组失败，班组不存在");
        }
        if (bo.getLeaderUserId() == null) {
            // 实体中为 null 的字段不会更新，未选班组长表示清空
            teamMapper.update(null, Wrappers.<MesTeam>lambdaUpdate()
                .set(MesTeam::getLeaderUserId, null)
                .eq(MesTeam::getTeamId, bo.getTeamId()));
        }
    }

    private MesWorkshop requireWorkshop(Long workshopId) {
        return MasterdataSupport.require(workshopMapper.selectById(workshopId), "所属车间不存在");
    }

    private void checkLeader(Long leaderUserId) {
        if (leaderUserId != null && remoteUserService.selectListByIds(List.of(leaderUserId)).isEmpty()) {
            throw new ServiceException("班组长不存在或已停用");
        }
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = teamMapper.exists(QueryBuilder.lambda(MesTeam.class)
            .eq(MesTeam::getTeamCode, code)
            .neIfPresent(MesTeam::getTeamId, excludeId)
            .build());
        if (exists) {
            throw new ServiceException("班组编码“" + code + "”已存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<Long> ids) {
        if (teamMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除班组失败，班组不存在");
        }
        memberMapper.delete(QueryBuilder.lambda(MesTeamMember.class).in(MesTeamMember::getTeamId, ids).build());
    }

    @Override
    public List<MesTeamMemberVo> queryMembers(Long teamId) {
        MasterdataSupport.require(teamMapper.selectById(teamId), "班组不存在");
        return memberMapper.selectList(QueryBuilder.lambda(MesTeamMember.class)
                .eq(MesTeamMember::getTeamId, teamId)
                .orderByAsc(MesTeamMember::getMemberId)
                .build())
            .stream()
            .map(member -> new MesTeamMemberVo(member.getUserId()))
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMembers(Long teamId, List<Long> userIds) {
        if (userIds == null) {
            throw new ServiceException("成员列表不能为空，清空成员请传空数组");
        }
        MasterdataSupport.require(teamMapper.selectById(teamId), "班组不存在");
        Set<Long> distinct = new LinkedHashSet<>(userIds);
        if (!distinct.isEmpty()) {
            Map<Long, RemoteUserVo> users = remoteUserService.selectListByIds(distinct).stream()
                .collect(Collectors.toMap(RemoteUserVo::getUserId, Function.identity(), (a, b) -> a));
            List<Long> missing = distinct.stream().filter(id -> !users.containsKey(id)).toList();
            if (!missing.isEmpty()) {
                throw new ServiceException("用户不存在或已停用：" + missing);
            }
        }
        memberMapper.delete(QueryBuilder.lambda(MesTeamMember.class).eq(MesTeamMember::getTeamId, teamId).build());
        if (distinct.isEmpty()) {
            return;
        }
        List<MesTeamMember> members = distinct.stream().map(userId -> {
            MesTeamMember member = new MesTeamMember();
            member.setTeamId(teamId);
            member.setUserId(userId);
            return member;
        }).toList();
        memberMapper.insert(members);
    }

}
