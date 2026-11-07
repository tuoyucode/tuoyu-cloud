package org.tuoyu.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.ObjectUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.system.domain.bo.SysDictDataBo;
import org.tuoyu.system.domain.vo.SysDictDataVo;
import org.tuoyu.system.service.ISysDictDataService;
import org.tuoyu.system.service.ISysDictTypeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 数据字典信息
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/dict/data")
public class SysDictDataController {

    private final ISysDictDataService dictDataService;
    private final ISysDictTypeService dictTypeService;

    /**
     * 查询字典数据列表
     */
    @SaCheckPermission("system:dict:list")
    @GetMapping("/list")
    public ApiResult<PageResult<SysDictDataVo>> list(SysDictDataBo dictData, PageParam pageParam) {
        return ApiResult.ok(dictDataService.selectPageDictDataList(dictData, pageParam));
    }

    /**
     * 导出字典数据列表
     */
    @OperationLog(module = "字典数据", action = AuditAction.EXPORT)
    @SaCheckPermission("system:dict:export")
    @PostMapping("/export")
    public void export(SysDictDataBo dictData, HttpServletResponse response) {
        ExcelBuilder.query(() -> dictDataService.countDictDataList(dictData),
                () -> dictDataService.selectDictDataList(dictData), SysDictDataVo.class)
            .sheetName("字典数据").toResponse(response);
    }

    /**
     * 查询字典数据详细
     *
     * @param dictCode 字典code
     */
    @SaCheckPermission("system:dict:query")
    @GetMapping(value = "/{dictCode}")
    public ApiResult<SysDictDataVo> getInfo(@PathVariable Long dictCode) {
        return ApiResult.ok(dictDataService.selectDictDataById(dictCode));
    }

    /**
     * 根据字典类型查询字典数据信息（仅登录：各页面的下拉、标签都要用字典，不属于字典管理）
     *
     * @param dictType 字典类型
     */
    @SaCheckLogin
    @GetMapping(value = "/type/{dictType}")
    public ApiResult<List<SysDictDataVo>> dictType(@PathVariable String dictType) {
        List<SysDictDataVo> data = dictTypeService.selectDictDataByType(dictType);
        if (ObjectUtil.isNull(data)) {
            data = new ArrayList<>();
        }
        return ApiResult.ok(data);
    }

    /**
     * 新增字典数据
     */
    @SaCheckPermission("system:dict:add")
    @OperationLog(module = "字典数据", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated @RequestBody SysDictDataBo dict) {
        if (!dictDataService.checkDictDataUnique(dict)) {
            throw new ServiceException("新增字典数据'" + dict.getDictValue() + "'失败，字典键值已存在");
        }
        dictDataService.insertDictData(dict);
        return ApiResult.ok();
    }

    /**
     * 修改保存字典数据
     */
    @SaCheckPermission("system:dict:edit")
    @OperationLog(module = "字典数据", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated @RequestBody SysDictDataBo dict) {
        if (!dictDataService.checkDictDataUnique(dict)) {
            throw new ServiceException("修改字典数据'" + dict.getDictValue() + "'失败，字典键值已存在");
        }
        dictDataService.updateDictData(dict);
        return ApiResult.ok();
    }

    /**
     * 删除字典数据
     *
     * @param dictCodes 字典code串
     */
    @SaCheckPermission("system:dict:remove")
    @OperationLog(module = "字典数据", action = AuditAction.DELETE)
    @DeleteMapping("/{dictCodes}")
    public ApiResult<Void> remove(@PathVariable Long[] dictCodes) {
        dictDataService.deleteDictDataByIds(Arrays.asList(dictCodes));
        return ApiResult.ok();
    }
}
