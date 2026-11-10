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
import org.tuoyu.mes.masterdata.domain.bo.MesFactoryBo;
import org.tuoyu.mes.masterdata.domain.vo.MesFactoryVo;
import org.tuoyu.mes.masterdata.service.IMesFactoryService;

import java.util.List;

/**
 * 工厂
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/factory")
public class MesFactoryController {

    private final IMesFactoryService factoryService;

    @SaCheckPermission("mes:factory:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesFactoryVo>> list(MesFactoryBo bo, PageParam pageParam) {
        return ApiResult.ok(factoryService.queryPageList(bo, pageParam));
    }

    /**
     * 启用的工厂，供其他页面选择。只要求登录：只含 ID、编码与名称
     */
    @SaCheckLogin
    @GetMapping("/options")
    public ApiResult<List<MesOptionVo>> options() {
        return ApiResult.ok(factoryService.options());
    }

    @SaCheckPermission("mes:factory:export")
    @OperationLog(module = "工厂", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesFactoryBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> factoryService.countList(bo), () -> factoryService.queryList(bo), MesFactoryVo.class)
            .sheetName("工厂").toResponse(response);
    }

    @SaCheckPermission("mes:factory:query")
    @GetMapping("/{factoryId}")
    public ApiResult<MesFactoryVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long factoryId) {
        return ApiResult.ok(factoryService.queryById(factoryId));
    }

    @SaCheckPermission("mes:factory:add")
    @OperationLog(module = "工厂", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesFactoryBo bo) {
        factoryService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:factory:edit")
    @OperationLog(module = "工厂", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesFactoryBo bo) {
        factoryService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:factory:remove")
    @OperationLog(module = "工厂", action = AuditAction.DELETE)
    @DeleteMapping("/{factoryIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] factoryIds) {
        factoryService.deleteByIds(List.of(factoryIds));
        return ApiResult.ok();
    }

}
