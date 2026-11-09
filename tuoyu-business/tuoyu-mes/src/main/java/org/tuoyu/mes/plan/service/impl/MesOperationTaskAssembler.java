package org.tuoyu.mes.plan.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.domain.MesTeam;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.domain.vo.MesOperationTaskVo;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工序任务的视图组装：批量填充工单、产品、车间、产线、工位、班组的名称，并计算本工序还能报工的数量
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Component
public class MesOperationTaskAssembler {

    private final MesWorkorderMapper workorderMapper;
    private final MesOperationTaskMapper taskMapper;
    private final MesMaterialMapper materialMapper;
    private final MesWorkshopMapper workshopMapper;
    private final MesLineMapper lineMapper;
    private final MesStationMapper stationMapper;
    private final MesTeamMapper teamMapper;

    public List<MesOperationTaskVo> toVoList(Collection<MesOperationTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<MesOperationTaskVo> vos = BeanMapper.mapList(tasks, MesOperationTaskVo.class);
        Set<Long> workorderIds = MesLookups.ids(tasks, MesOperationTask::getWorkorderId);
        Map<Long, MesWorkorder> workorders = MesLookups.byIds(workorderMapper, workorderIds, MesWorkorder::getWorkorderId);
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper,
            MesLookups.ids(tasks, MesOperationTask::getMaterialId), MesMaterial::getMaterialId);
        Map<Long, MesWorkshop> workshops = MesLookups.byIds(workshopMapper,
            MesLookups.ids(tasks, MesOperationTask::getWorkshopId), MesWorkshop::getWorkshopId);
        Map<Long, MesLine> lines = MesLookups.byIds(lineMapper,
            MesLookups.ids(tasks, MesOperationTask::getLineId), MesLine::getLineId);
        Map<Long, MesStation> stations = MesLookups.byIds(stationMapper,
            MesLookups.ids(tasks, MesOperationTask::getStationId), MesStation::getStationId);
        Map<Long, MesTeam> teams = MesLookups.byIds(teamMapper,
            MesLookups.ids(tasks, MesOperationTask::getTeamId), MesTeam::getTeamId);
        Map<Long, List<MesOperationTask>> siblings = siblings(workorderIds);
        for (MesOperationTaskVo vo : vos) {
            MesWorkorder workorder = PlanLookups.get(workorders, vo.getWorkorderId());
            if (workorder != null) {
                vo.setWorkorderCode(workorder.getWorkorderCode());
                vo.setWorkorderStatus(workorder.getWorkorderStatus());
                vo.setBatchCode(workorder.getBatchCode());
            }
            MesMaterial material = PlanLookups.get(materials, vo.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getMaterialCode());
                vo.setMaterialName(material.getMaterialName());
                vo.setUnit(material.getUnit());
            }
            MesWorkshop workshop = PlanLookups.get(workshops, vo.getWorkshopId());
            vo.setWorkshopName(workshop == null ? null : workshop.getWorkshopName());
            MesLine line = PlanLookups.get(lines, vo.getLineId());
            vo.setLineName(line == null ? null : line.getLineName());
            MesStation station = PlanLookups.get(stations, vo.getStationId());
            vo.setStationName(station == null ? null : station.getStationName());
            MesTeam team = PlanLookups.get(teams, vo.getTeamId());
            vo.setTeamName(team == null ? null : team.getTeamName());
            fillQuantities(vo, workorder, siblings.getOrDefault(vo.getWorkorderId(), List.of()));
        }
        return vos;
    }

    private Map<Long, List<MesOperationTask>> siblings(Set<Long> workorderIds) {
        if (workorderIds.isEmpty()) {
            return Map.of();
        }
        return taskMapper.selectList(QueryBuilder.lambda(MesOperationTask.class)
                .select(MesOperationTask::getTaskId, MesOperationTask::getWorkorderId, MesOperationTask::getSeqNo,
                    MesOperationTask::getQualifiedQty, MesOperationTask::getDefectQty)
                .in(MesOperationTask::getWorkorderId, workorderIds)
                .orderByAsc(MesOperationTask::getSeqNo)
                .build())
            .stream().collect(Collectors.groupingBy(MesOperationTask::getWorkorderId));
    }

    private static void fillQuantities(MesOperationTaskVo vo, MesWorkorder workorder, List<MesOperationTask> siblings) {
        MesOperationTask self = new MesOperationTask();
        self.setTaskId(vo.getTaskId());
        self.setSeqNo(vo.getSeqNo());
        self.setQualifiedQty(vo.getQualifiedQty());
        self.setDefectQty(vo.getDefectQty());
        vo.setLastOperation(TaskSequence.isLast(siblings, self));
        vo.setAvailableQty(workorder == null ? null
            : TaskSequence.available(workorder.getPlanQty(), siblings, self));
    }

}
