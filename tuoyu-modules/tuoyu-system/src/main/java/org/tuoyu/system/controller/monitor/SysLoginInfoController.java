package org.tuoyu.system.controller.monitor;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.lock.annotation.Lock4j;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.utils.LoginFailureKeys;
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.common.redis.utils.RedisUtils;
import org.tuoyu.system.domain.bo.SysLoginInfoBo;
import org.tuoyu.system.domain.vo.SysLoginInfoVo;
import org.tuoyu.system.service.ISysLoginInfoService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 系统访问记录
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/loginInfo")
public class SysLoginInfoController {

    private final ISysLoginInfoService loginInfoService;

    /**
     * 获取系统访问记录列表
     */
    @SaCheckPermission("monitor:logininfo:list")
    @GetMapping("/list")
    public ApiResult<PageResult<SysLoginInfoVo>> list(SysLoginInfoBo loginInfo, PageParam pageParam) {
        return ApiResult.ok(loginInfoService.selectPageLoginInfoList(loginInfo, pageParam));
    }

    /**
     * 导出系统访问记录列表
     */
    @OperationLog(module = "登录日志", action = AuditAction.EXPORT)
    @SaCheckPermission("monitor:logininfo:export")
    @PostMapping("/export")
    public void export(SysLoginInfoBo loginInfo, HttpServletResponse response) {
        ExcelBuilder.query(() -> loginInfoService.countLoginInfoList(loginInfo),
                () -> loginInfoService.selectLoginInfoList(loginInfo), SysLoginInfoVo.class)
            .sheetName("登录日志").toResponse(response);
    }

    /**
     * 批量删除登录日志
     *
     * @param infoIds 日志ids
     */
    @SaCheckPermission("monitor:logininfo:remove")
    @OperationLog(module = "登录日志", action = AuditAction.DELETE)
    @DeleteMapping("/{infoIds}")
    public ApiResult<Void> remove(@PathVariable Long[] infoIds) {
        loginInfoService.deleteLoginInfoByIds(infoIds);
        return ApiResult.ok();
    }

    /**
     * 清理系统访问记录
     */
    @SaCheckPermission("monitor:logininfo:remove")
    @OperationLog(module = "登录日志", action = AuditAction.CLEAN)
    @Lock4j
    @DeleteMapping("/clean")
    public ApiResult<Void> clean() {
        loginInfoService.cleanLoginInfo();
        return ApiResult.ok();
    }

    @SaCheckPermission("monitor:logininfo:unlock")
    @OperationLog(module = "账户解锁", action = AuditAction.OTHER)
    @PreventResubmit
    @GetMapping("/unlock/{userName}")
    public ApiResult<Void> unlock(@PathVariable("userName") String userName) {
        // 清除该账号（登录日志中的账号：用户名、手机号或邮箱）的登录失败计数
        RedisUtils.deleteObject(LoginFailureKeys.account(userName));
        return ApiResult.ok();
    }

}
