package org.tuoyu.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.common.tenant.core.TenantProperties;
import org.tuoyu.system.domain.bo.SysTenantPackageBo;
import org.tuoyu.system.domain.vo.SysTenantPackageVo;
import org.tuoyu.system.service.ISysTenantPackageService;

import java.util.Arrays;
import java.util.List;

/**
 * 租户套餐管理（ADR 0006 第 7 节）。只有平台（默认租户）中的用户可以使用；关闭多租户时拒绝
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/tenant/package")
public class SysTenantPackageController {

    private final ISysTenantPackageService packageService;

    private final TenantProperties tenantProperties;

    @SaCheckPermission("system:tenantPackage:list")
    @GetMapping("/list")
    public ApiResult<PageResult<SysTenantPackageVo>> list(SysTenantPackageBo bo, PageParam pageParam) {
        checkPlatform();
        return ApiResult.ok(packageService.selectPageList(bo, pageParam));
    }

    /**
     * 启用的套餐（新增、修改租户时选择）
     */
    @SaCheckPermission("system:tenant:list")
    @GetMapping("/optionselect")
    public ApiResult<List<SysTenantPackageVo>> optionselect() {
        checkPlatform();
        return ApiResult.ok(packageService.selectOptions());
    }

    @SaCheckPermission("system:tenantPackage:query")
    @GetMapping("/{packageId}")
    public ApiResult<SysTenantPackageVo> getInfo(@PathVariable Long packageId) {
        checkPlatform();
        return ApiResult.ok(packageService.selectById(packageId));
    }

    @SaCheckPermission("system:tenantPackage:add")
    @OperationLog(module = "租户套餐", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody SysTenantPackageBo bo) {
        checkPlatform();
        packageService.insertPackage(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("system:tenantPackage:edit")
    @OperationLog(module = "租户套餐", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody SysTenantPackageBo bo) {
        checkPlatform();
        packageService.updatePackage(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("system:tenantPackage:remove")
    @OperationLog(module = "租户套餐", action = AuditAction.DELETE)
    @DeleteMapping("/{packageIds}")
    public ApiResult<Void> remove(@PathVariable Long[] packageIds) {
        checkPlatform();
        packageService.deleteByIds(Arrays.asList(packageIds));
        return ApiResult.ok();
    }

    private void checkPlatform() {
        if (!TenantHelper.isEnabled()) {
            throw new ServiceException("未开启多租户");
        }
        CurrentUser user = UserContext.current().orElse(null);
        if (user == null || !tenantProperties.getDefaultTenantId().equals(user.getTenantId())) {
            throw new ServiceException("只有平台用户可以管理租户套餐");
        }
    }

}
