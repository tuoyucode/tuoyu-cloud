package org.tuoyu.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.system.domain.bo.SysConfigBo;
import org.tuoyu.system.domain.vo.SysConfigVo;
import org.tuoyu.system.service.ISysConfigService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
/**
 * 参数配置 信息操作处理
 * <p>
 * 参数全平台共享：写接口除权限码外还要求超级管理员角色，租户管理员不能改动；以后新增的写接口同样标注
 * {@code @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)}
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/config")
public class SysConfigController {

    private final ISysConfigService configService;

    /**
     * 获取参数配置列表
     */
    @SaCheckPermission("system:config:list")
    @GetMapping("/list")
    public ApiResult<PageResult<SysConfigVo>> list(SysConfigBo config, PageParam pageParam) {
        return ApiResult.ok(configService.selectPageConfigList(config, pageParam));
    }

    /**
     * 导出参数配置列表
     */
    @OperationLog(module = "参数管理", action = AuditAction.EXPORT)
    @SaCheckPermission("system:config:export")
    @PostMapping("/export")
    public void export(SysConfigBo config, HttpServletResponse response) {
        ExcelBuilder.query(() -> configService.countConfigList(config), () -> configService.selectConfigList(config), SysConfigVo.class)
            .sheetName("参数数据").toResponse(response);
    }

    /**
     * 根据参数编号获取详细信息
     *
     * @param configId 参数ID
     */
    @SaCheckPermission("system:config:query")
    @GetMapping(value = "/{configId}")
    public ApiResult<SysConfigVo> getInfo(@PathVariable Long configId) {
        return ApiResult.ok(configService.selectConfigById(configId));
    }

    /**
     * 根据参数键名查询参数值（参数中可能有初始密码等敏感值，需要参数查询权限）
     *
     * @param configKey 参数Key
     */
    @SaCheckPermission("system:config:query")
    @GetMapping(value = "/configKey/{configKey}")
    public ApiResult<String> getConfigKey(@PathVariable String configKey) {
        return ApiResult.ok(configService.selectConfigByKey(configKey));
    }

    /**
     * 新增参数配置
     */
    @SaCheckPermission("system:config:add")
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @OperationLog(module = "参数管理", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated @RequestBody SysConfigBo config) {
        if (!configService.checkConfigKeyUnique(config)) {
            throw new ServiceException("新增参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        configService.insertConfig(config);
        return ApiResult.ok();
    }

    /**
     * 修改参数配置
     */
    @SaCheckPermission("system:config:edit")
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @OperationLog(module = "参数管理", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated @RequestBody SysConfigBo config) {
        if (!configService.checkConfigKeyUnique(config)) {
            throw new ServiceException("修改参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        configService.updateConfig(config);
        return ApiResult.ok();
    }

    /**
     * 根据参数键名修改参数配置
     */
    @SaCheckPermission("system:config:edit")
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @OperationLog(module = "参数管理", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping("/updateByKey")
    public ApiResult<Void> updateByKey(@RequestBody SysConfigBo config) {
        configService.updateConfig(config);
        return ApiResult.ok();
    }

    /**
     * 删除参数配置
     *
     * @param configIds 参数ID串
     */
    @SaCheckPermission("system:config:remove")
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @OperationLog(module = "参数管理", action = AuditAction.DELETE)
    @DeleteMapping("/{configIds}")
    public ApiResult<Void> remove(@PathVariable Long[] configIds) {
        configService.deleteConfigByIds(Arrays.asList(configIds));
        return ApiResult.ok();
    }

    /**
     * 刷新参数缓存
     */
    @SaCheckPermission("system:config:remove")
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @OperationLog(module = "参数管理", action = AuditAction.CLEAN)
    @DeleteMapping("/refreshCache")
    public ApiResult<Void> refreshCache() {
        configService.resetConfigCache();
        return ApiResult.ok();
    }
}
