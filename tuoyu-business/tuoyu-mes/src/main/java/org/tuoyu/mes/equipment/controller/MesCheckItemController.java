package org.tuoyu.mes.equipment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
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
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.mes.equipment.domain.bo.MesCheckItemBo;
import org.tuoyu.mes.equipment.domain.vo.MesCheckItemVo;
import org.tuoyu.mes.equipment.service.IMesCheckItemService;

import java.util.List;

/**
 * 点检项目
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/check-item")
public class MesCheckItemController {

    private static final String MODULE = "点检项目";

    private final IMesCheckItemService checkItemService;

    @SaCheckPermission("mes:checkItem:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesCheckItemVo>> list(MesCheckItemBo bo, PageParam pageParam) {
        return ApiResult.ok(checkItemService.queryPageList(bo, pageParam));
    }

    /**
     * 设备主数据中已有的设备类型，供选择适用设备类型
     */
    @SaCheckPermission("mes:checkItem:list")
    @GetMapping("/device-types")
    public ApiResult<List<String>> deviceTypes() {
        return ApiResult.ok(checkItemService.deviceTypes());
    }

    /**
     * 设备适用的启用项目，供填写点检记录
     */
    @SaCheckPermission("mes:checkRecord:add")
    @GetMapping("/applicable")
    public ApiResult<List<MesCheckItemVo>> applicable(@RequestParam Long deviceId) {
        return ApiResult.ok(checkItemService.applicable(deviceId));
    }

    @SaCheckPermission("mes:checkItem:query")
    @GetMapping("/{checkItemId}")
    public ApiResult<MesCheckItemVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long checkItemId) {
        return ApiResult.ok(checkItemService.queryById(checkItemId));
    }

    @SaCheckPermission("mes:checkItem:add")
    @OperationLog(module = MODULE, action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesCheckItemBo bo) {
        checkItemService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:checkItem:edit")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesCheckItemBo bo) {
        checkItemService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:checkItem:remove")
    @OperationLog(module = MODULE, action = AuditAction.DELETE)
    @DeleteMapping("/{checkItemIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] checkItemIds) {
        checkItemService.deleteByIds(List.of(checkItemIds));
        return ApiResult.ok();
    }

}
