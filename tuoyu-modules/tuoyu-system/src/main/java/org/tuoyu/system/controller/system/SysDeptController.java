package org.tuoyu.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.convert.Convert;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.system.domain.bo.SysDeptBo;
import org.tuoyu.system.domain.vo.SysDeptVo;
import org.tuoyu.system.service.ISysDeptService;
import org.tuoyu.system.service.ISysPostService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门信息
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/dept")
public class SysDeptController {

    private final ISysDeptService deptService;
    private final ISysPostService postService;

    /**
     * 获取部门列表
     */
    @SaCheckPermission("system:dept:list")
    @GetMapping("/list")
    public ApiResult<List<SysDeptVo>> list(SysDeptBo dept) {
        List<SysDeptVo> depts = deptService.selectDeptList(dept);
        return ApiResult.ok(depts);
    }

    /**
     * 查询部门列表（排除节点）
     *
     * @param deptId 部门ID
     */
    @SaCheckPermission("system:dept:list")
    @GetMapping("/list/exclude/{deptId}")
    public ApiResult<List<SysDeptVo>> excludeChild(@PathVariable(value = "deptId") Long deptId) {
        List<SysDeptVo> depts = deptService.selectDeptList(new SysDeptBo());
        depts.removeIf(d -> d.getDeptId().equals(deptId)
            || StringUtils.splitList(d.getAncestors()).contains(Convert.toStr(deptId)));
        return ApiResult.ok(depts);
    }

    /**
     * 根据部门编号获取详细信息
     *
     * @param deptId 部门ID
     */
    @SaCheckPermission("system:dept:query")
    @GetMapping(value = "/{deptId}")
    public ApiResult<SysDeptVo> getInfo(@PathVariable Long deptId) {
        deptService.checkDeptDataScope(deptId);
        return ApiResult.ok(deptService.selectDeptById(deptId));
    }

    /**
     * 新增部门
     */
    @SaCheckPermission("system:dept:add")
    @OperationLog(module = "部门管理", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated @RequestBody SysDeptBo dept) {
        if (!deptService.checkDeptNameUnique(dept)) {
            throw new ServiceException("新增部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        }
        deptService.insertDept(dept);
        return ApiResult.ok();
    }

    /**
     * 修改部门
     */
    @SaCheckPermission("system:dept:edit")
    @OperationLog(module = "部门管理", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated @RequestBody SysDeptBo dept) {
        Long deptId = dept.getDeptId();
        deptService.checkDeptDataScope(deptId);
        if (!deptService.checkDeptNameUnique(dept)) {
            throw new ServiceException("修改部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        } else if (dept.getParentId().equals(deptId)) {
            throw new ServiceException("修改部门'" + dept.getDeptName() + "'失败，上级部门不能是自己");
        } else if (StringUtils.equals(SystemConstants.DISABLE, dept.getStatus())) {
            if (deptService.selectNormalChildrenDeptById(deptId) > 0) {
                throw new ServiceException("该部门包含未停用的子部门!");
            } else if (deptService.checkDeptExistUser(deptId)) {
                throw new ServiceException("该部门下存在已分配用户，不能禁用!");
            }
        }
        deptService.updateDept(dept);
        return ApiResult.ok();
    }

    /**
     * 删除部门
     *
     * @param deptId 部门ID
     */
    @SaCheckPermission("system:dept:remove")
    @OperationLog(module = "部门管理", action = AuditAction.DELETE)
    @DeleteMapping("/{deptId}")
    public ApiResult<Void> remove(@PathVariable Long deptId) {
        if (StringUtils.equals(SystemConstants.ROOT_DEPT_ANCESTORS, String.valueOf(deptId))) {
            throw new ServiceException("根部门不允许删除");
        }
        if (deptService.hasChildByDeptId(deptId)) {
            throw new ServiceException("存在下级部门,不允许删除");
        }
        if (deptService.checkDeptExistUser(deptId)) {
            throw new ServiceException("部门存在用户,不允许删除");
        }
        if (postService.countPostByDeptId(deptId) > 0) {
            throw new ServiceException("部门存在岗位,不允许删除");
        }
        deptService.checkDeptDataScope(deptId);
        deptService.deleteDeptById(deptId);
        return ApiResult.ok();
    }

    /**
     * 获取部门选择框列表
     *
     * @param deptIds 部门ID串
     */
    @SaCheckPermission("system:dept:query")
    @GetMapping("/optionselect")
    public ApiResult<List<SysDeptVo>> optionselect(@RequestParam(required = false) Long[] deptIds) {
        return ApiResult.ok(deptService.selectDeptByIds(deptIds == null ? null : List.of(deptIds)));
    }

}
