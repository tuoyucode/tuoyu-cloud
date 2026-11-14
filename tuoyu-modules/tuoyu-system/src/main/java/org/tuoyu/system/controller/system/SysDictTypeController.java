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
import org.tuoyu.system.domain.bo.SysDictTypeBo;
import org.tuoyu.system.domain.vo.SysDictTypeVo;
import org.tuoyu.system.service.ISysDictTypeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 数据字典信息
 * <p>
 * 字典全平台共享：写接口除权限码外还要求超级管理员角色，租户管理员不能改动；以后新增的写接口同样标注
 * {@code @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)}
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/dict/type")
public class SysDictTypeController {

    private final ISysDictTypeService dictTypeService;

    /**
     * 查询字典类型列表
     */
    @SaCheckPermission("system:dict:list")
    @GetMapping("/list")
    public ApiResult<PageResult<SysDictTypeVo>> list(SysDictTypeBo dictType, PageParam pageParam) {
        return ApiResult.ok(dictTypeService.selectPageDictTypeList(dictType, pageParam));
    }

    /**
     * 导出字典类型列表
     */
    @OperationLog(module = "字典类型", action = AuditAction.EXPORT)
    @SaCheckPermission("system:dict:export")
    @PostMapping("/export")
    public void export(SysDictTypeBo dictType, HttpServletResponse response) {
        ExcelBuilder.query(() -> dictTypeService.countDictTypeList(dictType),
                () -> dictTypeService.selectDictTypeList(dictType), SysDictTypeVo.class)
            .sheetName("字典类型").toResponse(response);
    }

    /**
     * 查询字典类型详细
     *
     * @param dictId 字典ID
     */
    @SaCheckPermission("system:dict:query")
    @GetMapping(value = "/{dictId}")
    public ApiResult<SysDictTypeVo> getInfo(@PathVariable Long dictId) {
        return ApiResult.ok(dictTypeService.selectDictTypeById(dictId));
    }

    /**
     * 新增字典类型
     */
    @SaCheckPermission("system:dict:add")
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @OperationLog(module = "字典类型", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated @RequestBody SysDictTypeBo dict) {
        if (!dictTypeService.checkDictTypeUnique(dict)) {
            throw new ServiceException("新增字典'" + dict.getDictName() + "'失败，字典类型已存在");
        }
        dictTypeService.insertDictType(dict);
        return ApiResult.ok();
    }

    /**
     * 修改字典类型
     */
    @SaCheckPermission("system:dict:edit")
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @OperationLog(module = "字典类型", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated @RequestBody SysDictTypeBo dict) {
        if (!dictTypeService.checkDictTypeUnique(dict)) {
            throw new ServiceException("修改字典'" + dict.getDictName() + "'失败，字典类型已存在");
        }
        dictTypeService.updateDictType(dict);
        return ApiResult.ok();
    }

    /**
     * 删除字典类型
     *
     * @param dictIds 字典ID串
     */
    @SaCheckPermission("system:dict:remove")
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @OperationLog(module = "字典类型", action = AuditAction.DELETE)
    @DeleteMapping("/{dictIds}")
    public ApiResult<Void> remove(@PathVariable Long[] dictIds) {
        dictTypeService.deleteDictTypeByIds(Arrays.asList(dictIds));
        return ApiResult.ok();
    }

    /**
     * 刷新字典缓存
     */
    @SaCheckPermission("system:dict:remove")
    @SaCheckRole(SystemConstants.SUPER_ADMIN_ROLE_KEY)
    @OperationLog(module = "字典类型", action = AuditAction.CLEAN)
    @PreventResubmit
    @DeleteMapping("/refreshCache")
    public ApiResult<Void> refreshCache() {
        dictTypeService.resetDictCache();
        return ApiResult.ok();
    }

    /**
     * 获取字典选择框列表
     */
    @SaCheckPermission("system:dict:query")
    @GetMapping("/optionselect")
    public ApiResult<List<SysDictTypeVo>> optionselect() {
        List<SysDictTypeVo> dictTypes = dictTypeService.selectDictTypeAll();
        return ApiResult.ok(dictTypes);
    }
}
