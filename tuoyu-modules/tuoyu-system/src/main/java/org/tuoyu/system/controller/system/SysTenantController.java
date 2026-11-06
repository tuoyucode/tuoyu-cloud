package org.tuoyu.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.R;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.validate.AddGroup;
import org.tuoyu.common.core.validate.EditGroup;
import org.tuoyu.common.encrypt.annotation.ApiEncrypt;
import org.tuoyu.common.log.annotation.Log;
import org.tuoyu.common.log.enums.BusinessType;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.common.redis.annotation.RepeatSubmit;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.common.tenant.core.DynamicTenantStore;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.common.tenant.core.TenantProperties;
import org.tuoyu.common.web.core.BaseController;
import org.tuoyu.system.api.model.LoginUser;
import org.tuoyu.system.domain.bo.SysTenantBo;
import org.tuoyu.system.domain.vo.SysTenantVo;
import org.tuoyu.system.service.ISysTenantService;

import java.util.Arrays;

/**
 * 租户管理（ADR 0006 第 7 节）。只有平台（默认租户）中的用户可以使用；关闭多租户时拒绝
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/tenant")
public class SysTenantController extends BaseController {

    private final ISysTenantService tenantService;

    private final TenantProperties tenantProperties;

    /**
     * 查询租户列表
     */
    @SaCheckPermission("system:tenant:list")
    @GetMapping("/list")
    public R<PageResult<SysTenantVo>> list(SysTenantBo bo, PageQuery pageQuery) {
        checkPlatform();
        return R.ok(tenantService.selectPageList(bo, pageQuery));
    }

    /**
     * 获取租户详情
     */
    @SaCheckPermission("system:tenant:query")
    @GetMapping("/{tenantId}")
    public R<SysTenantVo> getInfo(@PathVariable String tenantId) {
        checkPlatform();
        return R.ok(tenantService.selectById(tenantId));
    }

    /**
     * 新增租户（同时创建租户管理员，口令加密传输）
     */
    @ApiEncrypt
    @SaCheckPermission("system:tenant:add")
    @Log(title = "租户管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated(AddGroup.class) @RequestBody SysTenantBo bo) {
        checkPlatform();
        tenantService.insertTenant(bo);
        return R.ok();
    }

    /**
     * 修改租户
     */
    @SaCheckPermission("system:tenant:edit")
    @Log(title = "租户管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated(EditGroup.class) @RequestBody SysTenantBo bo) {
        checkPlatform();
        tenantService.updateTenant(bo);
        return R.ok();
    }

    /**
     * 启用、停用租户
     */
    @SaCheckPermission("system:tenant:edit")
    @Log(title = "租户管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody SysTenantBo bo) {
        checkPlatform();
        tenantService.updateStatus(bo.getTenantId(), bo.getStatus());
        return R.ok();
    }

    /**
     * 删除租户
     */
    @SaCheckPermission("system:tenant:remove")
    @Log(title = "租户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{tenantIds}")
    public R<Void> remove(@PathVariable String[] tenantIds) {
        checkPlatform();
        tenantService.deleteByIds(Arrays.asList(tenantIds));
        return R.ok();
    }

    /**
     * 超级管理员切换进入某个租户（之后的请求在该租户中执行，12 小时后自动恢复）。
     * 超级管理员按用户 ID 判断（不是角色），在方法内校验，所以注解只要求登录
     */
    @Log(title = "租户管理", businessType = BusinessType.OTHER)
    @SaCheckLogin
    @GetMapping("/dynamic/{tenantId}")
    public R<Void> switchTenant(@PathVariable String tenantId) {
        checkSuperAdmin();
        if (TenantHelper.ignore(() -> tenantService.selectById(tenantId)) == null) {
            return R.fail("租户不存在");
        }
        DynamicTenantStore.set(LoginHelper.getUserId(), tenantId);
        return R.ok();
    }

    /**
     * 超级管理员退出切换，回到所属租户（在方法内校验超级管理员）
     */
    @SaCheckLogin
    @GetMapping("/dynamic/clear")
    public R<Void> clearSwitch() {
        checkSuperAdmin();
        DynamicTenantStore.clear(LoginHelper.getUserId());
        return R.ok();
    }

    /**
     * 当前所在的租户（前端显示切换状态；仅登录，所有用户的页头都要用）
     *
     * @return tenantId 当前租户；switched 是否处于切换状态
     */
    @SaCheckLogin
    @GetMapping("/dynamic")
    public R<CurrentTenant> currentTenant() {
        if (!TenantHelper.isEnabled()) {
            return R.ok(new CurrentTenant(false, null, false));
        }
        boolean switched = LoginHelper.isSuperAdmin() && DynamicTenantStore.get(LoginHelper.getUserId()) != null;
        return R.ok(new CurrentTenant(true, TenantHelper.currentTenantId(), switched));
    }

    /**
     * @param enabled  是否开启多租户
     * @param tenantId 当前租户
     * @param switched 超级管理员是否切换进入了其他租户
     */
    public record CurrentTenant(boolean enabled, String tenantId, boolean switched) {
    }

    /**
     * 租户管理只在平台（默认租户）中可用，且必须开启多租户
     */
    private void checkPlatform() {
        if (!TenantHelper.isEnabled()) {
            throw new ServiceException("未开启多租户");
        }
        LoginUser user = LoginHelper.getLoginUser();
        if (user == null || !tenantProperties.getDefaultTenantId().equals(user.getTenantId())) {
            throw new ServiceException("只有平台用户可以管理租户");
        }
    }

    private void checkSuperAdmin() {
        if (!TenantHelper.isEnabled()) {
            throw new ServiceException("未开启多租户");
        }
        if (!LoginHelper.isSuperAdmin()) {
            throw new ServiceException("只有超级管理员可以切换租户");
        }
    }

}
