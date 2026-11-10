package org.tuoyu.mes.masterdata.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.bo.MesDeviceBo;
import org.tuoyu.mes.masterdata.domain.vo.MesDeviceImportVo;
import org.tuoyu.mes.masterdata.domain.vo.MesDeviceVo;
import org.tuoyu.mes.masterdata.service.IMesDeviceService;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/device")
public class MesDeviceController {

    private final IMesDeviceService deviceService;

    @SaCheckPermission("mes:device:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesDeviceVo>> list(MesDeviceBo bo, PageParam pageParam) {
        return ApiResult.ok(deviceService.queryPageList(bo, pageParam));
    }

    /**
     * 启用的设备，供其他页面选择。只要求登录：只含 ID、编码与名称
     */
    @SaCheckLogin
    @GetMapping("/options")
    public ApiResult<List<MesOptionVo>> options(@RequestParam(required = false) Long workshopId) {
        return ApiResult.ok(deviceService.options(workshopId));
    }

    @SaCheckPermission("mes:device:export")
    @OperationLog(module = "设备", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesDeviceBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> deviceService.countList(bo), () -> deviceService.queryList(bo), MesDeviceVo.class)
            .sheetName("设备").toResponse(response);
    }

    @SaCheckPermission("mes:device:query")
    @GetMapping("/{deviceId}")
    public ApiResult<MesDeviceVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long deviceId) {
        return ApiResult.ok(deviceService.queryById(deviceId));
    }

    @SaCheckPermission("mes:device:add")
    @OperationLog(module = "设备", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesDeviceBo bo) {
        deviceService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:device:edit")
    @OperationLog(module = "设备", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesDeviceBo bo) {
        deviceService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:device:remove")
    @OperationLog(module = "设备", action = AuditAction.DELETE)
    @DeleteMapping("/{deviceIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] deviceIds) {
        deviceService.deleteByIds(List.of(deviceIds));
        return ApiResult.ok();
    }

    /**
     * 导入设备：有任何一行不合格时一行都不写入，失败原因逐行列在返回的提示中（每行一条）
     */
    @SaCheckPermission("mes:device:import")
    @OperationLog(module = "设备", action = AuditAction.IMPORT)
    @PreventResubmit
    @PostMapping(value = "/importData", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Void> importData(@RequestPart("file") MultipartFile file) {
        int count = deviceService.importData(file);
        return ApiResult.ok("导入成功，共 " + count + " 条设备", null);
    }

    /**
     * 下载导入模板
     */
    @SaCheckPermission("mes:device:import")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        ExcelBuilder.of(new ArrayList<>(), MesDeviceImportVo.class).sheetName("设备").toResponse(response);
    }

}
