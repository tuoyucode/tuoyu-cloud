package org.tuoyu.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import org.tuoyu.mes.material.support.UserNicknames;
import org.tuoyu.mes.material.support.WorkorderFilters;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.enums.WorkorderStatus;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.production.domain.MesProductionReport;
import org.tuoyu.mes.production.mapper.MesProductionReportMapper;
import org.tuoyu.mes.quality.domain.MesDefectRecord;
import org.tuoyu.mes.quality.domain.MesInspection;
import org.tuoyu.mes.quality.domain.bo.MesDefectHandleBo;
import org.tuoyu.mes.quality.domain.bo.MesDefectRecordBo;
import org.tuoyu.mes.quality.domain.vo.MesDefectRecordVo;
import org.tuoyu.mes.quality.enums.DefectHandleMethod;
import org.tuoyu.mes.quality.enums.DefectHandleStatus;
import org.tuoyu.mes.quality.enums.DefectLevel;
import org.tuoyu.mes.quality.enums.DefectSource;
import org.tuoyu.mes.quality.mapper.MesDefectRecordMapper;
import org.tuoyu.mes.quality.mapper.MesInspectionMapper;
import org.tuoyu.mes.quality.service.IMesDefectRecordService;
import org.tuoyu.system.api.RemoteUserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 不良记录
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesDefectRecordServiceImpl implements IMesDefectRecordService {

    private static final String OBJECT = "不良记录";

    private final MesDefectRecordMapper defectMapper;
    private final MesWorkorderMapper workorderMapper;
    private final MesOperationTaskMapper taskMapper;
    private final MesMaterialMapper materialMapper;
    private final MesInspectionMapper inspectionMapper;
    private final MesProductionReportMapper reportMapper;
    private final IMesCodeService codeService;
    private final IMesStatusLogService statusLogService;
    private final RemoteUserService userService;

    @Override
    public MesDefectRecordVo queryById(Long defectId) {
        MesDefectRecordVo vo = BeanMapper.map(requireDefect(defectId), MesDefectRecordVo.class);
        fill(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesDefectRecordVo> queryPageList(MesDefectRecordBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesDefectRecord> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        PageResult<MesDefectRecordVo> result = PageResults.of(
            defectMapper.selectPage(pageParam.toPage(MesDefectRecord.class), lqw), MesDefectRecordVo.class);
        fill(result.getItems());
        return result;
    }

    @Override
    public List<MesDefectRecordVo> queryList(MesDefectRecordBo bo) {
        List<MesDefectRecordVo> vos = BeanMapper.mapList(defectMapper.selectList(buildQueryWrapper(bo, true)), MesDefectRecordVo.class);
        fill(vos);
        Map<Long, String> names = UserNicknames.of(userService, MesLookups.ids(vos, MesDefectRecordVo::getHandleUserId));
        vos.forEach(vo -> vo.setHandleUserName(SafeLookup.get(names, vo.getHandleUserId())));
        return vos;
    }

    @Override
    public long countList(MesDefectRecordBo bo) {
        return defectMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    private LambdaQueryWrapper<MesDefectRecord> buildQueryWrapper(MesDefectRecordBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesDefectRecord.class)
            .likeIfText(MesDefectRecord::getDefectCode, bo.getDefectCode())
            .eqIfText(MesDefectRecord::getSourceType, bo.getSourceType())
            .eqIfText(MesDefectRecord::getDefectLevel, bo.getDefectLevel())
            .eqIfText(MesDefectRecord::getHandleStatus, bo.getHandleStatus())
            .eqIfPresent(MesDefectRecord::getWorkorderId, bo.getWorkorderId())
            .eqIfPresent(MesDefectRecord::getTaskId, bo.getTaskId())
            .inIfNotEmpty(MesDefectRecord::getWorkorderId, WorkorderFilters.idsByCode(workorderMapper, bo.getWorkorderCode()))
            .orderByDesc(defaultOrder, MesDefectRecord::getDefectId)
            .build();
    }

    /**
     * 工单编号、工序名称、产品与来源单据编号
     */
    private void fill(List<MesDefectRecordVo> vos) {
        if (vos.isEmpty()) {
            return;
        }
        Map<Long, MesWorkorder> workorders = MesLookups.byIds(workorderMapper,
            MesLookups.ids(vos, MesDefectRecordVo::getWorkorderId), MesWorkorder::getWorkorderId);
        Map<Long, MesOperationTask> tasks = MesLookups.byIds(taskMapper,
            MesLookups.ids(vos, MesDefectRecordVo::getTaskId), MesOperationTask::getTaskId);
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper,
            MesLookups.ids(vos, MesDefectRecordVo::getMaterialId), MesMaterial::getMaterialId);
        Map<Long, MesInspection> inspections = MesLookups.byIds(inspectionMapper,
            MesLookups.ids(vos.stream().filter(v -> DefectSource.INSPECTION.code().equals(v.getSourceType())).toList(),
                MesDefectRecordVo::getSourceId), MesInspection::getInspectionId);
        Map<Long, MesProductionReport> reports = MesLookups.byIds(reportMapper,
            MesLookups.ids(vos.stream().filter(v -> DefectSource.REPORT.code().equals(v.getSourceType())).toList(),
                MesDefectRecordVo::getSourceId), MesProductionReport::getReportId);
        for (MesDefectRecordVo vo : vos) {
            MesWorkorder workorder = SafeLookup.get(workorders, vo.getWorkorderId());
            vo.setWorkorderCode(workorder == null ? null : workorder.getWorkorderCode());
            MesOperationTask task = SafeLookup.get(tasks, vo.getTaskId());
            vo.setOperationName(task == null ? null : task.getOperationName());
            MesMaterial material = SafeLookup.get(materials, vo.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getMaterialCode());
                vo.setMaterialName(material.getMaterialName());
            }
            if (DefectSource.INSPECTION.code().equals(vo.getSourceType())) {
                MesInspection inspection = SafeLookup.get(inspections, vo.getSourceId());
                vo.setSourceCode(inspection == null ? null : inspection.getInspectionCode());
            } else if (DefectSource.REPORT.code().equals(vo.getSourceType())) {
                MesProductionReport report = SafeLookup.get(reports, vo.getSourceId());
                vo.setSourceCode(report == null ? null : report.getReportCode());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertByBo(MesDefectRecordBo bo) {
        DefectLevel level = CodeEnum.of(DefectLevel.class, bo.getDefectLevel(), "不良等级");
        MesWorkorder workorder = WorkorderFilters.require(workorderMapper, bo.getWorkorderId());
        StateGuard.require("工单", WorkorderStatus.class, workorder.getWorkorderStatus(), "登记不良",
            WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS, WorkorderStatus.PAUSED, WorkorderStatus.COMPLETED);
        if (bo.getTaskId() != null) {
            MesOperationTask task = taskMapper.selectById(bo.getTaskId());
            if (task == null || !Objects.equals(task.getWorkorderId(), workorder.getWorkorderId())) {
                throw new ServiceException("工序任务不属于该工单");
            }
        }
        MesDefectRecord record = new MesDefectRecord();
        record.setSourceType(DefectSource.MANUAL.code());
        record.setWorkorderId(workorder.getWorkorderId());
        record.setTaskId(bo.getTaskId());
        record.setMaterialId(workorder.getMaterialId());
        record.setDefectQty(bo.getDefectQty());
        record.setDefectLevel(level.code());
        record.setDescription(bo.getDescription());
        record.setRemark(bo.getRemark());
        insertPending(record);
    }

    @Override
    public void createFromInspection(MesInspection inspection, String description) {
        MesDefectRecord record = new MesDefectRecord();
        record.setSourceType(DefectSource.INSPECTION.code());
        record.setSourceId(inspection.getInspectionId());
        record.setWorkorderId(inspection.getWorkorderId());
        record.setTaskId(inspection.getTaskId());
        record.setMaterialId(inspection.getMaterialId());
        record.setDefectQty(inspection.getDefectQty());
        // 检验判定不合格的批量不良按严重处理，处置时再决定返工、报废或让步接收
        record.setDefectLevel(DefectLevel.MAJOR.code());
        record.setDescription(StringUtils.substring(description, 0, 500));
        insertPending(record);
    }

    private void insertPending(MesDefectRecord record) {
        record.setDefectCode(codeService.next(CodeRules.DEFECT));
        record.setHandleStatus(DefectHandleStatus.PENDING.code());
        defectMapper.insert(record);
        statusLogService.record(BizType.DEFECT_RECORD, record.getDefectId(), record.getDefectCode(), "create",
            null, DefectHandleStatus.PENDING.code(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long defectId, MesDefectHandleBo bo) {
        DefectHandleMethod method = CodeEnum.of(DefectHandleMethod.class, bo.getHandleMethod(), "处置方式");
        MesDefectRecord record = requireDefect(defectId);
        StateGuard.require(OBJECT, DefectHandleStatus.class, record.getHandleStatus(), "处置", DefectHandleStatus.PENDING);
        MesDefectRecord change = new MesDefectRecord();
        change.setHandleStatus(DefectHandleStatus.HANDLED.code());
        change.setHandleMethod(method.code());
        change.setHandleRemark(bo.getHandleRemark());
        change.setHandleUserId(UserContext.userId());
        change.setHandleTime(LocalDateTime.now());
        int rows = defectMapper.update(change, Wrappers.<MesDefectRecord>lambdaUpdate()
            .eq(MesDefectRecord::getDefectId, record.getDefectId())
            .eq(MesDefectRecord::getHandleStatus, DefectHandleStatus.PENDING.code()));
        if (rows == 0) {
            throw new ServiceException("不良记录状态已变化，请刷新后重试");
        }
        statusLogService.record(BizType.DEFECT_RECORD, record.getDefectId(), record.getDefectCode(), "handle",
            record.getHandleStatus(), DefectHandleStatus.HANDLED.code(), method.label());
    }

    @Override
    public List<MesStatusLogVo> logs(Long defectId) {
        requireDefect(defectId);
        return statusLogService.list(BizType.DEFECT_RECORD, defectId);
    }

    private MesDefectRecord requireDefect(Long defectId) {
        MesDefectRecord record = defectId == null ? null : defectMapper.selectById(defectId);
        if (record == null) {
            throw new ServiceException("不良记录不存在");
        }
        return record;
    }

}
