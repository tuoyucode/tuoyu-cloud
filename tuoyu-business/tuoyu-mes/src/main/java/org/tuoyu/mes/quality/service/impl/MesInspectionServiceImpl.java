package org.tuoyu.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.common.StateGuard;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.material.support.SafeLookup;
import org.tuoyu.mes.material.support.Quantities;
import org.tuoyu.mes.material.support.UserNicknames;
import org.tuoyu.mes.material.support.WorkorderFilters;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.enums.TaskStatus;
import org.tuoyu.mes.plan.enums.WorkorderStatus;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.quality.domain.MesInspection;
import org.tuoyu.mes.quality.domain.MesInspectionScheme;
import org.tuoyu.mes.quality.domain.bo.MesInspectionBo;
import org.tuoyu.mes.quality.domain.bo.MesInspectionResultBo;
import org.tuoyu.mes.quality.domain.vo.MesInspectionSchemeVo;
import org.tuoyu.mes.quality.domain.vo.MesInspectionVo;
import org.tuoyu.mes.quality.enums.InspectionResult;
import org.tuoyu.mes.quality.enums.InspectionStatus;
import org.tuoyu.mes.quality.enums.InspectionType;
import org.tuoyu.mes.quality.mapper.MesInspectionMapper;
import org.tuoyu.mes.quality.mapper.MesInspectionSchemeMapper;
import org.tuoyu.mes.quality.service.IMesDefectRecordService;
import org.tuoyu.mes.quality.service.IMesInspectionSchemeService;
import org.tuoyu.mes.quality.service.IMesInspectionService;
import org.tuoyu.system.api.RemoteUserService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 检验单
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesInspectionServiceImpl implements IMesInspectionService {

    private static final String OBJECT = "检验单";

    private final MesInspectionMapper inspectionMapper;
    private final MesInspectionSchemeMapper schemeMapper;
    private final MesWorkorderMapper workorderMapper;
    private final MesOperationTaskMapper taskMapper;
    private final MesMaterialMapper materialMapper;
    private final IMesInspectionSchemeService schemeService;
    private final IMesDefectRecordService defectService;
    private final IMesCodeService codeService;
    private final IMesStatusLogService statusLogService;
    private final RemoteUserService userService;

    @Override
    public MesInspectionVo queryById(Long inspectionId) {
        MesInspectionVo vo = BeanMapper.map(requireInspection(inspectionId), MesInspectionVo.class);
        fill(List.of(vo));
        if (vo.getSchemeId() != null) {
            MesInspectionScheme scheme = schemeMapper.selectById(vo.getSchemeId());
            vo.setCheckItems(scheme == null ? null : scheme.getCheckItems());
        }
        return vo;
    }

    @Override
    public PageResult<MesInspectionVo> queryPageList(MesInspectionBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesInspection> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        PageResult<MesInspectionVo> result = PageResults.of(
            inspectionMapper.selectPage(pageParam.toPage(MesInspection.class), lqw), MesInspectionVo.class);
        fill(result.getItems());
        return result;
    }

    @Override
    public List<MesInspectionVo> queryList(MesInspectionBo bo) {
        List<MesInspectionVo> vos = BeanMapper.mapList(inspectionMapper.selectList(buildQueryWrapper(bo, true)), MesInspectionVo.class);
        fill(vos);
        Map<Long, String> names = UserNicknames.of(userService, MesLookups.ids(vos, MesInspectionVo::getInspectorUserId));
        vos.forEach(vo -> vo.setInspectorName(SafeLookup.get(names, vo.getInspectorUserId())));
        return vos;
    }

    @Override
    public long countList(MesInspectionBo bo) {
        return inspectionMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    private LambdaQueryWrapper<MesInspection> buildQueryWrapper(MesInspectionBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesInspection.class)
            .likeIfText(MesInspection::getInspectionCode, bo.getInspectionCode())
            .eqIfText(MesInspection::getInspectionType, bo.getInspectionType())
            .eqIfText(MesInspection::getInspectionStatus, bo.getInspectionStatus())
            .eqIfText(MesInspection::getInspectionResult, bo.getInspectionResult())
            .eqIfPresent(MesInspection::getWorkorderId, bo.getWorkorderId())
            .eqIfPresent(MesInspection::getTaskId, bo.getTaskId())
            .inIfNotEmpty(MesInspection::getWorkorderId, WorkorderFilters.idsByCode(workorderMapper, bo.getWorkorderCode()))
            .orderByDesc(defaultOrder, MesInspection::getInspectionId)
            .build();
    }

    /**
     * 工单编号、工序名称、产品与方案名称
     */
    private void fill(List<MesInspectionVo> vos) {
        if (vos.isEmpty()) {
            return;
        }
        Map<Long, MesWorkorder> workorders = MesLookups.byIds(workorderMapper,
            MesLookups.ids(vos, MesInspectionVo::getWorkorderId), MesWorkorder::getWorkorderId);
        Map<Long, MesOperationTask> tasks = MesLookups.byIds(taskMapper,
            MesLookups.ids(vos, MesInspectionVo::getTaskId), MesOperationTask::getTaskId);
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper,
            MesLookups.ids(vos, MesInspectionVo::getMaterialId), MesMaterial::getMaterialId);
        Map<Long, MesInspectionScheme> schemes = MesLookups.byIds(schemeMapper,
            MesLookups.ids(vos, MesInspectionVo::getSchemeId), MesInspectionScheme::getSchemeId);
        for (MesInspectionVo vo : vos) {
            MesWorkorder workorder = SafeLookup.get(workorders, vo.getWorkorderId());
            vo.setWorkorderCode(workorder == null ? null : workorder.getWorkorderCode());
            MesOperationTask task = SafeLookup.get(tasks, vo.getTaskId());
            vo.setOperationName(task == null ? null : task.getOperationName());
            MesMaterial material = SafeLookup.get(materials, vo.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getMaterialCode());
                vo.setMaterialName(material.getMaterialName());
            }
            MesInspectionScheme scheme = SafeLookup.get(schemes, vo.getSchemeId());
            vo.setSchemeName(scheme == null ? null : scheme.getSchemeName());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertByBo(MesInspectionBo bo) {
        InspectionType type = CodeEnum.of(InspectionType.class, bo.getInspectionType(), "检验类型");
        MesWorkorder workorder = WorkorderFilters.require(workorderMapper, bo.getWorkorderId());
        if (type == InspectionType.FINAL) {
            StateGuard.require("工单", WorkorderStatus.class, workorder.getWorkorderStatus(), type.label(),
                WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS, WorkorderStatus.PAUSED, WorkorderStatus.COMPLETED);
        } else {
            StateGuard.require("工单", WorkorderStatus.class, workorder.getWorkorderStatus(), type.label(),
                WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS, WorkorderStatus.PAUSED);
        }
        MesOperationTask task = requireTask(bo.getTaskId(), type, workorder);

        Long schemeId = bo.getSchemeId();
        BigDecimal sampleQty;
        if (schemeId != null) {
            sampleQty = requireUsableScheme(schemeId, type, workorder.getMaterialId()).getSampleQty();
        } else {
            MesInspectionSchemeVo matched = schemeService.match(type.code(), workorder.getMaterialId(),
                task == null ? null : task.getOperationCode());
            schemeId = matched == null ? null : matched.getSchemeId();
            sampleQty = matched == null ? null : matched.getSampleQty();
        }

        MesInspection inspection = new MesInspection();
        inspection.setInspectionCode(codeService.next(CodeRules.INSPECTION));
        inspection.setInspectionType(type.code());
        inspection.setWorkorderId(workorder.getWorkorderId());
        inspection.setTaskId(task == null ? null : task.getTaskId());
        inspection.setSchemeId(schemeId);
        inspection.setMaterialId(workorder.getMaterialId());
        inspection.setInspectQty(bo.getInspectQty() != null ? bo.getInspectQty() : sampleQty);
        inspection.setInspectionStatus(InspectionStatus.PENDING.code());
        inspection.setRemark(bo.getRemark());
        inspectionMapper.insert(inspection);
        statusLogService.record(BizType.INSPECTION, inspection.getInspectionId(), inspection.getInspectionCode(), "create",
            null, InspectionStatus.PENDING.code(), null);
    }

    /**
     * 首检、巡检必须指定属于该工单、尚未完成的工序任务；完工检可以不指定
     */
    private MesOperationTask requireTask(Long taskId, InspectionType type, MesWorkorder workorder) {
        if (taskId == null) {
            if (type != InspectionType.FINAL) {
                throw new ServiceException(type.label() + "必须指定工序任务");
            }
            return null;
        }
        MesOperationTask task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getWorkorderId(), workorder.getWorkorderId())) {
            throw new ServiceException("工序任务不属于该工单");
        }
        if (type != InspectionType.FINAL) {
            StateGuard.require("工序任务", TaskStatus.class, task.getTaskStatus(), type.label(),
                TaskStatus.PENDING, TaskStatus.DISPATCHED, TaskStatus.IN_PROGRESS);
        }
        return task;
    }

    /**
     * 指定的方案必须启用、检验类型一致，指定了适用产品时必须是工单的产品
     */
    private MesInspectionScheme requireUsableScheme(Long schemeId, InspectionType type, Long productId) {
        MesInspectionScheme scheme = schemeMapper.selectById(schemeId);
        if (scheme == null) {
            throw new ServiceException("检验方案不存在");
        }
        if (!SystemConstants.NORMAL.equals(scheme.getStatus())) {
            throw new ServiceException("检验方案“" + scheme.getSchemeName() + "”已停用");
        }
        if (!type.code().equals(scheme.getInspectionType())) {
            throw new ServiceException("检验方案“" + scheme.getSchemeName() + "”不适用于" + type.label());
        }
        if (scheme.getMaterialId() != null && !scheme.getMaterialId().equals(productId)) {
            throw new ServiceException("检验方案“" + scheme.getSchemeName() + "”不适用于该工单的产品");
        }
        return scheme;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inspect(Long inspectionId, MesInspectionResultBo bo) {
        InspectionResult result = CodeEnum.of(InspectionResult.class, bo.getInspectionResult(), "检验结论");
        MesInspection inspection = requireInspection(inspectionId);
        StateGuard.require(OBJECT, InspectionStatus.class, inspection.getInspectionStatus(), "检验", InspectionStatus.PENDING);
        BigDecimal total = bo.getQualifiedQty().add(bo.getDefectQty());
        if (total.compareTo(bo.getInspectQty()) != 0) {
            throw new ServiceException("合格数量与不良数量之和（" + Quantities.format(total) + "）必须等于检验数量（"
                + Quantities.format(bo.getInspectQty()) + "）");
        }
        MesInspection change = new MesInspection();
        change.setInspectQty(bo.getInspectQty());
        change.setQualifiedQty(bo.getQualifiedQty());
        change.setDefectQty(bo.getDefectQty());
        change.setInspectionResult(result.code());
        change.setResultRemark(bo.getResultRemark());
        change.setInspectorUserId(UserContext.userId());
        change.setInspectionTime(LocalDateTime.now());
        change.setInspectionStatus(InspectionStatus.COMPLETED.code());
        int rows = inspectionMapper.update(change, Wrappers.<MesInspection>lambdaUpdate()
            .eq(MesInspection::getInspectionId, inspection.getInspectionId())
            .eq(MesInspection::getInspectionStatus, InspectionStatus.PENDING.code()));
        if (rows == 0) {
            throw new ServiceException("检验单状态已变化，请刷新后重试");
        }
        statusLogService.record(BizType.INSPECTION, inspection.getInspectionId(), inspection.getInspectionCode(), "inspect",
            inspection.getInspectionStatus(), InspectionStatus.COMPLETED.code(), result.label());

        if (result == InspectionResult.REJECTED && bo.getDefectQty().signum() > 0) {
            inspection.setInspectQty(bo.getInspectQty());
            inspection.setQualifiedQty(bo.getQualifiedQty());
            inspection.setDefectQty(bo.getDefectQty());
            String description = "检验单 " + inspection.getInspectionCode() + " 检验不合格"
                + (StringUtils.isBlank(bo.getResultRemark()) ? "" : "：" + bo.getResultRemark());
            defectService.createFromInspection(inspection, description);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<Long> ids) {
        Set<Long> distinct = new HashSet<>(ids);
        List<MesInspection> inspections = inspectionMapper.selectByIds(distinct);
        if (inspections.size() != distinct.size()) {
            throw new ServiceException("检验单不存在");
        }
        for (MesInspection inspection : inspections) {
            StateGuard.require(OBJECT + "“" + inspection.getInspectionCode() + "”", InspectionStatus.class,
                inspection.getInspectionStatus(), "删除", InspectionStatus.PENDING);
        }
        int rows = inspectionMapper.delete(QueryBuilder.lambda(MesInspection.class)
            .in(MesInspection::getInspectionId, distinct)
            .eq(MesInspection::getInspectionStatus, InspectionStatus.PENDING.code())
            .build());
        if (rows != distinct.size()) {
            throw new ServiceException("检验单状态已变化，请刷新后重试");
        }
    }

    @Override
    public List<MesStatusLogVo> logs(Long inspectionId) {
        requireInspection(inspectionId);
        return statusLogService.list(BizType.INSPECTION, inspectionId);
    }

    private MesInspection requireInspection(Long inspectionId) {
        MesInspection inspection = inspectionId == null ? null : inspectionMapper.selectById(inspectionId);
        if (inspection == null) {
            throw new ServiceException("检验单不存在");
        }
        return inspection;
    }

}
