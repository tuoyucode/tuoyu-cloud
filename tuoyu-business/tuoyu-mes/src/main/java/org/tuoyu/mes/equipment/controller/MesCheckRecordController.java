package org.tuoyu.mes.equipment.controller;

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
import org.tuoyu.mes.equipment.domain.bo.MesCheckAndonBo;
import org.tuoyu.mes.equipment.domain.bo.MesCheckRecordBo;
import org.tuoyu.mes.equipment.domain.bo.MesEquipmentReasonBo;
import org.tuoyu.mes.equipment.domain.vo.MesCheckRecordVo;
import org.tuoyu.mes.equipment.service.IMesCheckRecordService;

import java.util.List;

/**
 * 点检记录
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/check-record")
public class MesCheckRecordController {

    private static final String MODULE = "点检记录";

    private final IMesCheckRecordService checkRecordService;

    @SaCheckPermission("mes:checkRecord:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesCheckRecordVo>> list(MesCheckRecordBo bo, PageParam pageParam) {
        return ApiResult.ok(checkRecordService.queryPageList(bo, pageParam));
    }

    @SaCheckPermission("mes:checkRecord:export")
    @OperationLog(module = MODULE, action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesCheckRecordBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> checkRecordService.countList(bo), () -> checkRecordService.queryList(bo),
            MesCheckRecordVo.class).sheetName(MODULE).toResponse(response);
    }

    @SaCheckPermission("mes:checkRecord:query")
    @GetMapping("/{checkRecordId}")
    public ApiResult<MesCheckRecordVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long checkRecordId) {
        return ApiResult.ok(checkRecordService.queryById(checkRecordId));
    }

    @SaCheckPermission("mes:checkRecord:query")
    @GetMapping("/{checkRecordId}/logs")
    public ApiResult<List<MesStatusLogVo>> logs(@NotNull(message = "主键不能为空") @PathVariable Long checkRecordId) {
        return ApiResult.ok(checkRecordService.logs(checkRecordId));
    }

    @SaCheckPermission("mes:checkRecord:add")
    @OperationLog(module = MODULE, action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesCheckRecordBo bo) {
        checkRecordService.insertByBo(bo);
        return ApiResult.ok();
    }

    /**
     * 作废点检记录（只增不改，作废后保留记录与履历）
     */
    @SaCheckPermission("mes:checkRecord:void")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{checkRecordId}/void")
    public ApiResult<Void> voidRecord(@NotNull(message = "主键不能为空") @PathVariable Long checkRecordId,
                                      @Validated @RequestBody MesEquipmentReasonBo bo) {
        checkRecordService.voidRecord(checkRecordId, bo.getReason());
        return ApiResult.ok();
    }

    /**
     * 点检异常发起安灯（设备异常类）
     */
    @SaCheckPermission("mes:checkRecord:andon")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{checkRecordId}/andon")
    public ApiResult<Void> andon(@NotNull(message = "主键不能为空") @PathVariable Long checkRecordId,
                                 @Validated @RequestBody MesCheckAndonBo bo) {
        checkRecordService.raiseAndon(checkRecordId, bo);
        return ApiResult.ok();
    }

}
