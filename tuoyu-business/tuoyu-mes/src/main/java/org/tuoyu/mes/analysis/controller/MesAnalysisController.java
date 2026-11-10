package org.tuoyu.mes.analysis.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.analysis.domain.bo.MesAnalysisQueryBo;
import org.tuoyu.mes.analysis.domain.vo.MesDefectParetoRowVo;
import org.tuoyu.mes.analysis.domain.vo.MesDefectParetoVo;
import org.tuoyu.mes.analysis.domain.vo.MesDowntimeAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesDowntimeRowVo;
import org.tuoyu.mes.analysis.domain.vo.MesProgressAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesProgressRowVo;
import org.tuoyu.mes.analysis.domain.vo.MesTeamAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesTeamRowVo;
import org.tuoyu.mes.analysis.domain.vo.MesWipAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesWipRowVo;
import org.tuoyu.mes.analysis.domain.vo.MesWorkhourAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesWorkhourRowVo;
import org.tuoyu.mes.analysis.domain.vo.MesYieldAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesYieldRowVo;
import org.tuoyu.mes.analysis.service.IMesAnalysisService;

import java.util.List;

/**
 * 分析报表：生产进度、在制品、良率与直通率、不良柏拉图、停机分析、工时、班组绩效。
 * <p>
 * 查询条件见 {@link MesAnalysisQueryBo}（时间范围必填，最长 366 天）。每个报表一个查看权限 {@code mes:analysis:<报表>}；
 * 导出要求同时有该报表的查看权限与 {@code mes:analysis:export}。
 * 生产进度的明细按工单分页，导出前先统计行数；其余报表的明细是聚合后的行（按日期、工序、班组等，行数有限），
 * 导出时由同一次查询的结果统计行数，同样受导出行数上限约束。
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/analysis")
public class MesAnalysisController {

    private static final String EXPORT = "mes:analysis:export";

    private final IMesAnalysisService analysisService;

    @SaCheckPermission("mes:analysis:progress")
    @GetMapping("/progress")
    public ApiResult<MesProgressAnalysisVo> progress(MesAnalysisQueryBo bo, PageParam pageParam) {
        return ApiResult.ok(analysisService.progress(bo, pageParam));
    }

    @SaCheckPermission(value = {"mes:analysis:progress", EXPORT}, mode = SaMode.AND)
    @OperationLog(module = "生产进度", action = AuditAction.EXPORT)
    @PostMapping("/progress/export")
    public void exportProgress(MesAnalysisQueryBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> analysisService.countProgressRows(bo), () -> analysisService.progressRows(bo),
            MesProgressRowVo.class).sheetName("生产进度").toResponse(response);
    }

    @SaCheckPermission("mes:analysis:wip")
    @GetMapping("/wip")
    public ApiResult<MesWipAnalysisVo> wip(MesAnalysisQueryBo bo) {
        return ApiResult.ok(analysisService.wip(bo));
    }

    @SaCheckPermission(value = {"mes:analysis:wip", EXPORT}, mode = SaMode.AND)
    @OperationLog(module = "在制品", action = AuditAction.EXPORT)
    @PostMapping("/wip/export")
    public void exportWip(MesAnalysisQueryBo bo, HttpServletResponse response) {
        export(analysisService.wip(bo).getRows(), MesWipRowVo.class, "在制品", response);
    }

    @SaCheckPermission("mes:analysis:yield")
    @GetMapping("/yield")
    public ApiResult<MesYieldAnalysisVo> yield(MesAnalysisQueryBo bo) {
        return ApiResult.ok(analysisService.yield(bo));
    }

    @SaCheckPermission(value = {"mes:analysis:yield", EXPORT}, mode = SaMode.AND)
    @OperationLog(module = "良率与直通率", action = AuditAction.EXPORT)
    @PostMapping("/yield/export")
    public void exportYield(MesAnalysisQueryBo bo, HttpServletResponse response) {
        export(analysisService.yield(bo).getRows(), MesYieldRowVo.class, "良率与直通率", response);
    }

    @SaCheckPermission("mes:analysis:defect")
    @GetMapping("/defect")
    public ApiResult<MesDefectParetoVo> defect(MesAnalysisQueryBo bo) {
        return ApiResult.ok(analysisService.defect(bo));
    }

    @SaCheckPermission(value = {"mes:analysis:defect", EXPORT}, mode = SaMode.AND)
    @OperationLog(module = "不良柏拉图", action = AuditAction.EXPORT)
    @PostMapping("/defect/export")
    public void exportDefect(MesAnalysisQueryBo bo, HttpServletResponse response) {
        export(analysisService.defect(bo).getRows(), MesDefectParetoRowVo.class, "不良柏拉图", response);
    }

    @SaCheckPermission("mes:analysis:downtime")
    @GetMapping("/downtime")
    public ApiResult<MesDowntimeAnalysisVo> downtime(MesAnalysisQueryBo bo) {
        return ApiResult.ok(analysisService.downtime(bo));
    }

    @SaCheckPermission(value = {"mes:analysis:downtime", EXPORT}, mode = SaMode.AND)
    @OperationLog(module = "停机分析", action = AuditAction.EXPORT)
    @PostMapping("/downtime/export")
    public void exportDowntime(MesAnalysisQueryBo bo, HttpServletResponse response) {
        export(analysisService.downtime(bo).getRows(), MesDowntimeRowVo.class, "停机分析", response);
    }

    @SaCheckPermission("mes:analysis:workhour")
    @GetMapping("/workhour")
    public ApiResult<MesWorkhourAnalysisVo> workhour(MesAnalysisQueryBo bo) {
        return ApiResult.ok(analysisService.workhour(bo));
    }

    @SaCheckPermission(value = {"mes:analysis:workhour", EXPORT}, mode = SaMode.AND)
    @OperationLog(module = "工时", action = AuditAction.EXPORT)
    @PostMapping("/workhour/export")
    public void exportWorkhour(MesAnalysisQueryBo bo, HttpServletResponse response) {
        export(analysisService.workhour(bo).getRows(), MesWorkhourRowVo.class, "工时", response);
    }

    @SaCheckPermission("mes:analysis:team")
    @GetMapping("/team")
    public ApiResult<MesTeamAnalysisVo> team(MesAnalysisQueryBo bo) {
        return ApiResult.ok(analysisService.team(bo));
    }

    @SaCheckPermission(value = {"mes:analysis:team", EXPORT}, mode = SaMode.AND)
    @OperationLog(module = "班组绩效", action = AuditAction.EXPORT)
    @PostMapping("/team/export")
    public void exportTeam(MesAnalysisQueryBo bo, HttpServletResponse response) {
        export(analysisService.team(bo).getRows(), MesTeamRowVo.class, "班组绩效", response);
    }

    /**
     * 导出聚合后的明细：行数由已经算出的结果给出，超过导出行数上限时同样拒绝
     */
    private static <T> void export(List<T> rows, Class<T> type, String sheetName, HttpServletResponse response) {
        ExcelBuilder.query(rows::size, () -> rows, type).sheetName(sheetName).toResponse(response);
    }

}
