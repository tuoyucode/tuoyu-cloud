package org.tuoyu.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.util.ObjectUtil;
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
import org.tuoyu.system.domain.bo.SysDeptBo;
import org.tuoyu.system.domain.bo.SysPostBo;
import org.tuoyu.system.domain.vo.SysPostVo;
import org.tuoyu.system.service.ISysDeptService;
import org.tuoyu.system.service.ISysPostService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 岗位信息操作处理
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/post")
public class SysPostController {

    private final ISysPostService postService;
    private final ISysDeptService deptService;

    /**
     * 获取岗位列表
     */
    @SaCheckPermission("system:post:list")
    @GetMapping("/list")
    public ApiResult<PageResult<SysPostVo>> list(SysPostBo post, PageParam pageParam) {
        return ApiResult.ok(postService.selectPagePostList(post, pageParam));
    }

    /**
     * 导出岗位列表
     */
    @OperationLog(module = "岗位管理", action = AuditAction.EXPORT)
    @SaCheckPermission("system:post:export")
    @PostMapping("/export")
    public void export(SysPostBo post, HttpServletResponse response) {
        ExcelBuilder.query(() -> postService.countPostList(post), () -> postService.selectPostList(post), SysPostVo.class)
            .sheetName("岗位数据").toResponse(response);
    }

    /**
     * 根据岗位编号获取详细信息
     *
     * @param postId 岗位ID
     */
    @SaCheckPermission("system:post:query")
    @GetMapping(value = "/{postId}")
    public ApiResult<SysPostVo> getInfo(@PathVariable Long postId) {
        return ApiResult.ok(postService.selectPostById(postId));
    }

    /**
     * 新增岗位
     */
    @SaCheckPermission("system:post:add")
    @OperationLog(module = "岗位管理", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated @RequestBody SysPostBo post) {
        if (!postService.checkPostNameUnique(post)) {
            throw new ServiceException("新增岗位'" + post.getPostName() + "'失败，岗位名称已存在");
        } else if (!postService.checkPostCodeUnique(post)) {
            throw new ServiceException("新增岗位'" + post.getPostName() + "'失败，岗位编码已存在");
        }
        postService.insertPost(post);
        return ApiResult.ok();
    }

    /**
     * 修改岗位
     */
    @SaCheckPermission("system:post:edit")
    @OperationLog(module = "岗位管理", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated @RequestBody SysPostBo post) {
        if (!postService.checkPostNameUnique(post)) {
            throw new ServiceException("修改岗位'" + post.getPostName() + "'失败，岗位名称已存在");
        } else if (!postService.checkPostCodeUnique(post)) {
            throw new ServiceException("修改岗位'" + post.getPostName() + "'失败，岗位编码已存在");
        } else if (SystemConstants.DISABLE.equals(post.getStatus())
            && postService.countUserPostById(post.getPostId()) > 0) {
            throw new ServiceException("该岗位下存在已分配用户，不能禁用!");
        }
        postService.updatePost(post);
        return ApiResult.ok();
    }

    /**
     * 删除岗位
     *
     * @param postIds 岗位ID串
     */
    @SaCheckPermission("system:post:remove")
    @OperationLog(module = "岗位管理", action = AuditAction.DELETE)
    @DeleteMapping("/{postIds}")
    public ApiResult<Void> remove(@PathVariable Long[] postIds) {
        postService.deletePostByIds(Arrays.asList(postIds));
        return ApiResult.ok();
    }

    /**
     * 获取岗位选择框列表
     *
     * @param postIds 岗位ID串
     * @param deptId  部门id
     */
    @SaCheckPermission("system:post:query")
    @GetMapping("/optionselect")
    public ApiResult<List<SysPostVo>> optionselect(@RequestParam(required = false) Long[] postIds, @RequestParam(required = false) Long deptId) {
        List<SysPostVo> list = new ArrayList<>();
        if (ObjectUtil.isNotNull(deptId)) {
            SysPostBo post = new SysPostBo();
            post.setDeptId(deptId);
            list = postService.selectPostList(post);
        } else if (postIds != null) {
            list = postService.selectPostByIds(List.of(postIds));
        }
        return ApiResult.ok(list);
    }

    /**
     * 获取部门树列表
     */
    @SaCheckPermission("system:post:list")
    @GetMapping("/deptTree")
    public ApiResult<List<Tree<Long>>> deptTree(SysDeptBo dept) {
        return ApiResult.ok(deptService.selectDeptTreeList(dept));
    }

}
