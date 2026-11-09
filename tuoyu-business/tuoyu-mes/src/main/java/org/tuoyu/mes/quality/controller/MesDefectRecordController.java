package org.tuoyu.mes.quality.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.quality.domain.bo.MesDefectHandleBo;
import org.tuoyu.mes.quality.domain.bo.MesDefectRecordBo;
import org.tuoyu.mes.quality.domain.vo.MesDefectRecordVo;
import org.tuoyu.mes.quality.service.IMesDefectRecordService;

import java.util.List;

/**
 * 不良记录
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/defect")
public class MesDefectRecordController {

    private static final String MODULE = "不良记录";

    private final IMesDefectRecordService defectService;

    @SaCheckPermission("mes:defect:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesDefectRecordVo>> list(MesDefectRecordBo bo, PageParam pageParam) {
        return ApiResult.ok(defectService.queryPageList(bo, pageParam));
    }

    @SaCheckPermission("mes:defect:export")
    @OperationLog(module = MODULE, action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesDefectRecordBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> defectService.countList(bo), () -> defectService.queryList(bo), MesDefectRecordVo.class)
            .sheetName(MODULE).toResponse(response);
    }

    @SaCheckPermission("mes:defect:query")
    @GetMapping("/{defectId}")
    public ApiResult<MesDefectRecordVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long defectId) {
        return ApiResult.ok(defectService.queryById(defectId));
    }

    @SaCheckPermission("mes:defect:query")
    @GetMapping("/{defectId}/logs")
    public ApiResult<List<MesStatusLogVo>> logs(@NotNull(message = "主键不能为空") @PathVariable Long defectId) {
        return ApiResult.ok(defectService.logs(defectId));
    }

    /**
     * 手工登记不良
     */
    @SaCheckPermission("mes:defect:add")
    @OperationLog(module = MODULE, action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesDefectRecordBo bo) {
        defectService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:defect:handle")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{defectId}/handle")
    public ApiResult<Void> handle(@NotNull(message = "主键不能为空") @PathVariable Long defectId,
                                  @Validated @RequestBody MesDefectHandleBo bo) {
        defectService.handle(defectId, bo);
        return ApiResult.ok();
    }

}
