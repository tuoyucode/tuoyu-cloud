package org.tuoyu.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.system.domain.bo.SysClientBo;
import org.tuoyu.system.domain.vo.SysClientVo;
import org.tuoyu.system.service.ISysClientService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户端管理
 * <p>
 * 客户端所有租户共用：写接口除权限码外还要求超级管理员角色，租户管理员不能改动；以后新增的写接口同样标注
 * {@code @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)}
 *
 * @author Michelle.Chung
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/client")
public class SysClientController {

    private final ISysClientService sysClientService;

    /**
     * 查询客户端管理列表
     */
    @SaCheckPermission("system:client:list")
    @GetMapping("/list")
    public ApiResult<PageResult<SysClientVo>> list(SysClientBo bo, PageParam pageParam) {
        return ApiResult.ok(sysClientService.queryPageList(bo, pageParam));
    }

    /**
     * 导出客户端管理列表
     */
    @SaCheckPermission("system:client:export")
    @OperationLog(module = "客户端管理", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(SysClientBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> sysClientService.countList(bo), () -> sysClientService.queryList(bo), SysClientVo.class)
            .sheetName("客户端管理").toResponse(response);
    }

    /**
     * 获取客户端管理详细信息
     *
     * @param id 主键
     */
    @SaCheckPermission("system:client:query")
    @GetMapping("/{id}")
    public ApiResult<SysClientVo> getInfo(@NotNull(message = "主键不能为空")
                                  @PathVariable Long id) {
        return ApiResult.ok(sysClientService.queryById(id));
    }

    /**
     * 新增客户端管理
     */
    @SaCheckPermission("system:client:add")
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @OperationLog(module = "客户端管理", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping()
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody SysClientBo bo) {
        if (!sysClientService.checkClickKeyUnique(bo)) {
            throw new ServiceException("新增客户端'" + bo.getClientKey() + "'失败，客户端key已存在");
        }
        sysClientService.insertByBo(bo);
        return ApiResult.ok();
    }

    /**
     * 修改客户端管理
     */
    @SaCheckPermission("system:client:edit")
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @OperationLog(module = "客户端管理", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping()
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody SysClientBo bo) {
        if (!sysClientService.checkClickKeyUnique(bo)) {
            throw new ServiceException("修改客户端'" + bo.getClientKey() + "'失败，客户端key已存在");
        }
        sysClientService.updateByBo(bo);
        return ApiResult.ok();
    }

    /**
     * 状态修改
     */
    @SaCheckPermission("system:client:edit")
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @OperationLog(module = "客户端管理", action = AuditAction.UPDATE)
    @PutMapping("/changeStatus")
    public ApiResult<Void> changeStatus(@RequestBody SysClientBo bo) {
        sysClientService.updateClientStatus(bo.getClientId(), bo.getStatus());
        return ApiResult.ok();
    }

    /**
     * 删除客户端管理
     *
     * @param ids 主键串
     */
    @SaCheckPermission("system:client:remove")
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @OperationLog(module = "客户端管理", action = AuditAction.DELETE)
    @DeleteMapping("/{ids}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ids) {
        sysClientService.deleteWithValidByIds(List.of(ids), true);
        return ApiResult.ok();
    }
}
