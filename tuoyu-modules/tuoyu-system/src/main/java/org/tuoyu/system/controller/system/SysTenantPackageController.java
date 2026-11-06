package org.tuoyu.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.R;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.validate.AddGroup;
import org.tuoyu.common.core.validate.EditGroup;
import org.tuoyu.common.log.annotation.Log;
import org.tuoyu.common.log.enums.BusinessType;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.common.redis.annotation.RepeatSubmit;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.common.tenant.core.TenantProperties;
import org.tuoyu.common.web.core.BaseController;
import org.tuoyu.system.api.model.LoginUser;
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
public class SysTenantPackageController extends BaseController {

    private final ISysTenantPackageService packageService;

    private final TenantProperties tenantProperties;

    @SaCheckPermission("system:tenantPackage:list")
    @GetMapping("/list")
    public R<PageResult<SysTenantPackageVo>> list(SysTenantPackageBo bo, PageQuery pageQuery) {
        checkPlatform();
        return R.ok(packageService.selectPageList(bo, pageQuery));
    }

    /**
     * 启用的套餐（新增、修改租户时选择）
     */
    @SaCheckPermission("system:tenant:list")
    @GetMapping("/optionselect")
    public R<List<SysTenantPackageVo>> optionselect() {
        checkPlatform();
        return R.ok(packageService.selectOptions());
    }

    @SaCheckPermission("system:tenantPackage:query")
    @GetMapping("/{packageId}")
    public R<SysTenantPackageVo> getInfo(@PathVariable Long packageId) {
        checkPlatform();
        return R.ok(packageService.selectById(packageId));
    }

    @SaCheckPermission("system:tenantPackage:add")
    @Log(title = "租户套餐", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody SysTenantPackageBo bo) {
        checkPlatform();
        packageService.insertPackage(bo);
        return R.ok();
    }

    @SaCheckPermission("system:tenantPackage:edit")
    @Log(title = "租户套餐", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody SysTenantPackageBo bo) {
        checkPlatform();
        packageService.updatePackage(bo);
        return R.ok();
    }

    @SaCheckPermission("system:tenantPackage:remove")
    @Log(title = "租户套餐", businessType = BusinessType.DELETE)
    @DeleteMapping("/{packageIds}")
    public R<Void> remove(@PathVariable Long[] packageIds) {
        checkPlatform();
        packageService.deleteByIds(Arrays.asList(packageIds));
        return R.ok();
    }

    private void checkPlatform() {
        if (!TenantHelper.isEnabled()) {
            throw new ServiceException("未开启多租户");
        }
        LoginUser user = LoginHelper.getLoginUser();
        if (user == null || !tenantProperties.getDefaultTenantId().equals(user.getTenantId())) {
            throw new ServiceException("只有平台用户可以管理租户套餐");
        }
    }

}
