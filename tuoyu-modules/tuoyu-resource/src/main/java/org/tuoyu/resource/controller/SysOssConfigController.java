package org.tuoyu.resource.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.resource.domain.bo.SysOssConfigBo;
import org.tuoyu.resource.domain.vo.SysOssConfigVo;
import org.tuoyu.resource.service.ISysOssConfigService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

/**
 * 对象存储配置Controller
 *
 * @author Lion Li
 * @author 孤舟烟雨
 * @date 2021-08-13
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/oss/config")
public class SysOssConfigController {

    private final ISysOssConfigService ossConfigService;

    /**
     * 查询对象存储配置列表
     */
    @SaCheckPermission("system:ossConfig:list")
    @GetMapping("/list")
    public ApiResult<PageResult<SysOssConfigVo>> list(SysOssConfigBo bo, PageParam pageParam) {
        return ApiResult.ok(ossConfigService.queryPageList(bo, pageParam));
    }

    /**
     * 获取对象存储配置详细信息
     *
     * @param ossConfigId OSS配置ID
     */
    @SaCheckPermission("system:ossConfig:list")
    @GetMapping("/{ossConfigId}")
    public ApiResult<SysOssConfigVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable("ossConfigId") Long ossConfigId) {
        return ApiResult.ok(ossConfigService.queryById(ossConfigId));
    }

    /**
     * 新增对象存储配置
     */
    @SaCheckPermission("system:ossConfig:add")
    @OperationLog(module = "对象存储配置", action = AuditAction.CREATE)
    @PostMapping()
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody SysOssConfigBo bo) {
        ossConfigService.insertByBo(bo);
        return ApiResult.ok();
    }

    /**
     * 修改对象存储配置
     */
    @SaCheckPermission("system:ossConfig:edit")
    @OperationLog(module = "对象存储配置", action = AuditAction.UPDATE)
    @PutMapping()
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody SysOssConfigBo bo) {
        ossConfigService.updateByBo(bo);
        return ApiResult.ok();
    }

    /**
     * 删除对象存储配置
     *
     * @param ossConfigIds OSS配置ID串
     */
    @SaCheckPermission("system:ossConfig:remove")
    @OperationLog(module = "对象存储配置", action = AuditAction.DELETE)
    @DeleteMapping("/{ossConfigIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] ossConfigIds) {
        ossConfigService.deleteWithValidByIds(Arrays.asList(ossConfigIds), true);
        return ApiResult.ok();
    }

    /**
     * 状态修改
     */
    @SaCheckPermission("system:ossConfig:edit")
    @OperationLog(module = "对象存储状态修改", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping("/changeStatus")
    public ApiResult<Void> changeStatus(@RequestBody SysOssConfigBo bo) {
        ossConfigService.updateOssConfigStatus(bo);
        return ApiResult.ok();
    }
}
