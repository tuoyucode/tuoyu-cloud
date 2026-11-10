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
import org.tuoyu.mes.masterdata.domain.bo.MesTeamBo;
import org.tuoyu.mes.masterdata.domain.bo.MesTeamMembersBo;
import org.tuoyu.mes.masterdata.domain.vo.MesTeamMemberVo;
import org.tuoyu.mes.masterdata.domain.vo.MesTeamVo;
import org.tuoyu.mes.masterdata.service.IMesTeamService;

import java.util.List;

/**
 * 班组与班组成员
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/team")
public class MesTeamController {

    private final IMesTeamService teamService;

    @SaCheckPermission("mes:team:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesTeamVo>> list(MesTeamBo bo, PageParam pageParam) {
        return ApiResult.ok(teamService.queryPageList(bo, pageParam));
    }

    /**
     * 启用的班组，供其他页面选择。只要求登录：只含 ID、编码与名称
     */
    @SaCheckLogin
    @GetMapping("/options")
    public ApiResult<List<MesOptionVo>> options(@RequestParam(required = false) Long workshopId) {
        return ApiResult.ok(teamService.options(workshopId));
    }

    @SaCheckPermission("mes:team:export")
    @OperationLog(module = "班组", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesTeamBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> teamService.countList(bo), () -> teamService.queryList(bo), MesTeamVo.class)
            .sheetName("班组").toResponse(response);
    }

    @SaCheckPermission("mes:team:query")
    @GetMapping("/{teamId}")
    public ApiResult<MesTeamVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long teamId) {
        return ApiResult.ok(teamService.queryById(teamId));
    }

    @SaCheckPermission("mes:team:add")
    @OperationLog(module = "班组", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesTeamBo bo) {
        teamService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:team:edit")
    @OperationLog(module = "班组", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesTeamBo bo) {
        teamService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:team:remove")
    @OperationLog(module = "班组", action = AuditAction.DELETE)
    @DeleteMapping("/{teamIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] teamIds) {
        teamService.deleteByIds(List.of(teamIds));
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:team:query")
    @GetMapping("/{teamId}/members")
    public ApiResult<List<MesTeamMemberVo>> members(@NotNull(message = "主键不能为空") @PathVariable Long teamId) {
        return ApiResult.ok(teamService.queryMembers(teamId));
    }

    /**
     * 整体覆盖班组成员：userIds 为空数组表示清空
     */
    @SaCheckPermission("mes:team:edit")
    @OperationLog(module = "班组", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping("/{teamId}/members")
    public ApiResult<Void> updateMembers(@NotNull(message = "主键不能为空") @PathVariable Long teamId,
                                         @Validated @RequestBody MesTeamMembersBo bo) {
        teamService.updateMembers(teamId, bo.getUserIds());
        return ApiResult.ok();
    }

}
