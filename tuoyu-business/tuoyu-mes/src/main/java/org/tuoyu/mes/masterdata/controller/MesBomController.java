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
import org.tuoyu.mes.masterdata.domain.bo.MesBomBo;
import org.tuoyu.mes.masterdata.domain.vo.MesBomImportVo;
import org.tuoyu.mes.masterdata.domain.vo.MesBomVo;
import org.tuoyu.mes.masterdata.service.IMesBomService;

import java.util.ArrayList;
import java.util.List;

/**
 * 物料清单
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/bom")
public class MesBomController {

    private final IMesBomService bomService;

    @SaCheckPermission("mes:bom:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesBomVo>> list(MesBomBo bo, PageParam pageParam) {
        return ApiResult.ok(bomService.queryPageList(bo, pageParam));
    }

    /**
     * 启用的物料清单，供其他页面选择。只要求登录：只含 ID、编码与名称
     */
    @SaCheckLogin
    @GetMapping("/options")
    public ApiResult<List<MesOptionVo>> options(@RequestParam(required = false) Long materialId) {
        return ApiResult.ok(bomService.options(materialId));
    }

    @SaCheckPermission("mes:bom:export")
    @OperationLog(module = "物料清单", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesBomBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> bomService.countList(bo), () -> bomService.queryList(bo), MesBomVo.class)
            .sheetName("物料清单").toResponse(response);
    }

    @SaCheckPermission("mes:bom:query")
    @GetMapping("/{bomId}")
    public ApiResult<MesBomVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long bomId) {
        return ApiResult.ok(bomService.queryById(bomId));
    }

    @SaCheckPermission("mes:bom:add")
    @OperationLog(module = "物料清单", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesBomBo bo) {
        bomService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:bom:edit")
    @OperationLog(module = "物料清单", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesBomBo bo) {
        bomService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:bom:remove")
    @OperationLog(module = "物料清单", action = AuditAction.DELETE)
    @DeleteMapping("/{bomIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] bomIds) {
        bomService.deleteByIds(List.of(bomIds));
        return ApiResult.ok();
    }

    /**
     * 导入物料清单：有任何一行不合格时一行都不写入，失败原因逐行列在返回的提示中（每行一条）
     */
    @SaCheckPermission("mes:bom:import")
    @OperationLog(module = "物料清单", action = AuditAction.IMPORT)
    @PreventResubmit
    @PostMapping(value = "/importData", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Void> importData(@RequestPart("file") MultipartFile file) {
        int count = bomService.importData(file);
        return ApiResult.ok("导入成功，共 " + count + " 条物料清单", null);
    }

    /**
     * 下载导入模板
     */
    @SaCheckPermission("mes:bom:import")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        ExcelBuilder.of(new ArrayList<>(), MesBomImportVo.class).sheetName("物料清单").toResponse(response);
    }

}
