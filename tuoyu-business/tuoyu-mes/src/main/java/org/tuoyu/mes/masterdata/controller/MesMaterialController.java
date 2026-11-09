package org.tuoyu.mes.masterdata.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
import org.tuoyu.mes.masterdata.domain.bo.MesMaterialBo;
import org.tuoyu.mes.masterdata.domain.vo.MesMaterialVo;
import org.tuoyu.mes.masterdata.service.IMesMaterialService;

import java.util.List;

/**
 * 物料
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/material")
public class MesMaterialController {

    private final IMesMaterialService materialService;

    @SaCheckPermission("mes:material:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesMaterialVo>> list(MesMaterialBo bo, PageParam pageParam) {
        return ApiResult.ok(materialService.queryPageList(bo, pageParam));
    }

    /**
     * 启用的物料，供其他页面选择（最多 200 条）。只要求登录：只含 ID、编码与名称
     *
     * @param materialType 物料类型，逗号分隔多个
     * @param keyword      编码或名称模糊匹配
     */
    @SaCheckLogin
    @GetMapping("/options")
    public ApiResult<List<MesOptionVo>> options(@RequestParam(required = false) String materialType,
                                                @RequestParam(required = false) String keyword) {
        return ApiResult.ok(materialService.options(materialType, keyword));
    }

    @SaCheckPermission("mes:material:export")
    @OperationLog(module = "物料", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesMaterialBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> materialService.countList(bo), () -> materialService.queryList(bo), MesMaterialVo.class)
            .sheetName("物料").toResponse(response);
    }

    @SaCheckPermission("mes:material:query")
    @GetMapping("/{materialId}")
    public ApiResult<MesMaterialVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long materialId) {
        return ApiResult.ok(materialService.queryById(materialId));
    }

    @SaCheckPermission("mes:material:add")
    @OperationLog(module = "物料", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesMaterialBo bo) {
        materialService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:material:edit")
    @OperationLog(module = "物料", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesMaterialBo bo) {
        materialService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:material:remove")
    @OperationLog(module = "物料", action = AuditAction.DELETE)
    @DeleteMapping("/{materialIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] materialIds) {
        materialService.deleteByIds(List.of(materialIds));
        return ApiResult.ok();
    }

}
