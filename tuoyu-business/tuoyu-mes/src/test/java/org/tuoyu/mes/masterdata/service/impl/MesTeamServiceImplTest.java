package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.masterdata.domain.MesTeam;
import org.tuoyu.mes.masterdata.domain.MesTeamMember;
import org.tuoyu.mes.masterdata.domain.bo.MesTeamBo;
import org.tuoyu.mes.masterdata.domain.vo.MesTeamMemberVo;
import org.tuoyu.mes.masterdata.domain.vo.MesTeamVo;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMemberMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.vo.RemoteUserVo;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("班组：工厂由车间带出、班组长校验与成员整体覆盖")
class MesTeamServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesTeamMapper teamMapper = MesTestSupport.mapper(MesTeamMapper.class);
    private final MesTeamMemberMapper memberMapper = MesTestSupport.mapper(MesTeamMemberMapper.class);
    private final MesWorkshopMapper workshopMapper = MesTestSupport.mapper(MesWorkshopMapper.class);
    private final MesFactoryMapper factoryMapper = MesTestSupport.mapper(MesFactoryMapper.class);
    private final RemoteUserService remoteUserService = mock(RemoteUserService.class);

    private final MesTeamServiceImpl service = new MesTeamServiceImpl(teamMapper, memberMapper, workshopMapper,
        factoryMapper, remoteUserService);

    private static MesTeam team(long id) {
        MesTeam team = new MesTeam();
        team.setTeamId(id);
        team.setWorkshopId(5L);
        team.setFactoryId(1L);
        team.setTeamCode("T" + id);
        team.setTeamName("甲班");
        return team;
    }

    private static MesTeamBo bo() {
        MesTeamBo bo = new MesTeamBo();
        bo.setWorkshopId(5L);
        bo.setTeamCode("T01");
        bo.setTeamName("甲班");
        return bo;
    }

    private static RemoteUserVo user(long id) {
        RemoteUserVo user = new RemoteUserVo();
        user.setUserId(id);
        return user;
    }

    private static MesTeamMember member(long teamId, long userId) {
        MesTeamMember member = new MesTeamMember();
        member.setTeamId(teamId);
        member.setUserId(userId);
        return member;
    }

    @Test
    @DisplayName("新增班组时工厂由车间带出")
    void insertDerivesFactory() {
        when(workshopMapper.selectById(5L)).thenReturn(MesWorkshopServiceImplTest.workshop(5L, 1L));

        service.insertByBo(bo());

        ArgumentCaptor<MesTeam> captor = ArgumentCaptor.forClass(MesTeam.class);
        verify(teamMapper).insert(captor.capture());
        assertThat(captor.getValue().getFactoryId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("班组长不存在或已停用时拒绝保存")
    void rejectsMissingLeader() {
        when(workshopMapper.selectById(5L)).thenReturn(MesWorkshopServiceImplTest.workshop(5L, 1L));
        MesTeamBo bo = bo();
        bo.setLeaderUserId(3L);

        assertThatThrownBy(() -> service.insertByBo(bo)).hasMessageContaining("班组长");
        verify(teamMapper, never()).insert(any(MesTeam.class));
    }

    @Test
    @DisplayName("修改时未选班组长表示清空")
    void updateClearsLeader() {
        when(teamMapper.selectById(8L)).thenReturn(team(8L));
        when(workshopMapper.selectById(5L)).thenReturn(MesWorkshopServiceImplTest.workshop(5L, 1L));
        when(teamMapper.updateById(any(MesTeam.class))).thenReturn(1);
        MesTeamBo bo = bo();
        bo.setTeamId(8L);

        service.updateByBo(bo);

        verify(teamMapper).update(any(), any(Wrapper.class));
    }

    @Test
    @DisplayName("修改有班组长的班组时不额外清空")
    void updateKeepsLeader() {
        when(teamMapper.selectById(8L)).thenReturn(team(8L));
        when(workshopMapper.selectById(5L)).thenReturn(MesWorkshopServiceImplTest.workshop(5L, 1L));
        when(teamMapper.updateById(any(MesTeam.class))).thenReturn(1);
        when(remoteUserService.selectListByIds(anyCollection())).thenReturn(List.of(user(3L)));
        MesTeamBo bo = bo();
        bo.setTeamId(8L);
        bo.setLeaderUserId(3L);

        service.updateByBo(bo);

        verify(teamMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    @DisplayName("成员列表为 null 时拒绝，不会清空")
    void rejectsNullMembers() {
        assertThatThrownBy(() -> service.updateMembers(8L, null)).hasMessageContaining("空数组");
        verify(memberMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("有用户不存在或已停用时拒绝覆盖成员")
    void rejectsUnknownMembers() {
        when(teamMapper.selectById(8L)).thenReturn(team(8L));
        when(remoteUserService.selectListByIds(anyCollection())).thenReturn(List.of(user(1L)));

        assertThatThrownBy(() -> service.updateMembers(8L, List.of(1L, 2L))).hasMessageContaining("[2]");
        verify(memberMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("覆盖成员：先删后建，重复的用户只保存一次")
    @SuppressWarnings("unchecked")
    void replacesMembers() {
        when(teamMapper.selectById(8L)).thenReturn(team(8L));
        when(remoteUserService.selectListByIds(anyCollection())).thenReturn(List.of(user(1L), user(2L)));

        service.updateMembers(8L, Arrays.asList(1L, 2L, 1L));

        verify(memberMapper).delete(any(Wrapper.class));
        ArgumentCaptor<Collection<MesTeamMember>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(memberMapper).insert(captor.capture());
        assertThat(captor.getValue()).extracting(MesTeamMember::getUserId).containsExactly(1L, 2L);
        assertThat(captor.getValue()).allMatch(m -> m.getTeamId() == 8L);
    }

    @Test
    @DisplayName("空数组表示清空成员")
    @SuppressWarnings("unchecked")
    void emptyClearsMembers() {
        when(teamMapper.selectById(8L)).thenReturn(team(8L));

        service.updateMembers(8L, List.of());

        verify(memberMapper).delete(any(Wrapper.class));
        verify(memberMapper, never()).insert(any(Collection.class));
        verify(remoteUserService, never()).selectListByIds(anyCollection());
    }

    @Test
    @DisplayName("查询成员；班组不存在时提示")
    void queryMembers() {
        assertThatThrownBy(() -> service.queryMembers(8L)).hasMessageContaining("不存在");

        when(teamMapper.selectById(8L)).thenReturn(team(8L));
        when(memberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(member(8L, 3L)));
        assertThat(service.queryMembers(8L)).extracting(MesTeamMemberVo::getUserId).containsExactly(3L);
    }

    @Test
    @DisplayName("删除班组时连同成员一起删除")
    void deleteRemovesMembers() {
        when(teamMapper.deleteByIds(anyCollection())).thenReturn(1);

        service.deleteByIds(List.of(8L));

        verify(memberMapper).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("删除不存在的班组时提示，且不删除成员")
    void deleteMissing() {
        assertThatThrownBy(() -> service.deleteByIds(List.of(8L))).hasMessageContaining("不存在");
        verify(memberMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("列表填充成员人数")
    void listFillsMemberCount() {
        when(teamMapper.selectList(any(IPage.class), any(Wrapper.class))).thenReturn(List.of(team(8L), team(9L)));
        when(memberMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(member(8L, 1L), member(8L, 2L)));

        List<MesTeamVo> items = service.queryPageList(new MesTeamBo(), new PageParam()).getItems();

        assertThat(items).extracting(MesTeamVo::getMemberCount).containsExactly(2, 0);
    }

    @Test
    @DisplayName("详情、导出与下拉选项")
    void otherQueries() {
        when(teamMapper.selectById(8L)).thenReturn(team(8L));
        when(teamMapper.selectList(any(Wrapper.class))).thenReturn(List.of(team(8L)));

        assertThat(service.queryById(8L).getTeamCode()).isEqualTo("T8");
        assertThat(service.queryList(new MesTeamBo())).hasSize(1);
        assertThat(service.countList(new MesTeamBo())).isZero();
        assertThat(service.options(5L)).singleElement().extracting("parentId").isEqualTo(5L);
    }

}
