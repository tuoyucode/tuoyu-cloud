package org.tuoyu.system.dubbo;

import cn.hutool.core.convert.Convert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.api.RemoteTaskAssigneeService;
import org.tuoyu.system.api.domain.bo.RemoteTaskAssigneeBo;
import org.tuoyu.system.api.domain.vo.RemoteTaskAssigneeVo;
import org.tuoyu.system.domain.bo.SysDeptBo;
import org.tuoyu.system.domain.bo.SysPostBo;
import org.tuoyu.system.domain.bo.SysRoleBo;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.vo.SysDeptVo;
import org.tuoyu.system.domain.vo.SysPostVo;
import org.tuoyu.system.domain.vo.SysRoleVo;
import org.tuoyu.system.domain.vo.SysUserVo;
import org.tuoyu.system.service.ISysDeptService;
import org.tuoyu.system.service.ISysPostService;
import org.tuoyu.system.service.ISysRoleService;
import org.tuoyu.system.service.ISysUserService;

import java.util.Map;

/**
 * 工作流设计器选择办理人的跨模块实现：只查正常状态的角色、岗位、部门、用户，
 * 分页查询带数据权限（与当前操作者一致）
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class RemoteTaskAssigneeServiceImpl implements RemoteTaskAssigneeService {

    private final ISysPostService postService;
    private final ISysDeptService deptService;
    private final ISysUserService userService;
    private final ISysRoleService roleService;

    @Override
    public RemoteTaskAssigneeVo selectRolesByTaskAssigneeList(RemoteTaskAssigneeBo taskQuery) {
        SysRoleBo bo = new SysRoleBo();
        bo.setRoleKey(taskQuery.getHandlerCode());
        bo.setRoleName(taskQuery.getHandlerName());
        bo.setStatus(SystemConstants.NORMAL);
        putTimeRange(bo.getParams(), taskQuery);
        PageResult<SysRoleVo> page = roleService.selectPageRoleList(bo, pageParam(taskQuery));
        return new RemoteTaskAssigneeVo(page.getTotal(), RemoteTaskAssigneeVo.convertToHandlerList(page.getItems(),
            role -> Convert.toStr(role.getRoleId()), SysRoleVo::getRoleKey, SysRoleVo::getRoleName,
            role -> "", SysRoleVo::getCreateTime));
    }

    @Override
    public RemoteTaskAssigneeVo selectPostsByTaskAssigneeList(RemoteTaskAssigneeBo taskQuery) {
        SysPostBo bo = new SysPostBo();
        bo.setPostCategory(taskQuery.getHandlerCode());
        bo.setPostName(taskQuery.getHandlerName());
        bo.setBelongDeptId(Convert.toLong(taskQuery.getGroupId()));
        bo.setStatus(SystemConstants.NORMAL);
        putTimeRange(bo.getParams(), taskQuery);
        PageResult<SysPostVo> page = postService.selectPagePostList(bo, pageParam(taskQuery));
        return new RemoteTaskAssigneeVo(page.getTotal(), RemoteTaskAssigneeVo.convertToHandlerList(page.getItems(),
            post -> Convert.toStr(post.getPostId()), SysPostVo::getPostCategory, SysPostVo::getPostName,
            post -> Convert.toStr(post.getDeptId()), SysPostVo::getCreateTime));
    }

    @Override
    public RemoteTaskAssigneeVo selectDeptsByTaskAssigneeList(RemoteTaskAssigneeBo taskQuery) {
        SysDeptBo bo = new SysDeptBo();
        bo.setDeptCategory(taskQuery.getHandlerCode());
        bo.setDeptName(taskQuery.getHandlerName());
        bo.setBelongDeptId(Convert.toLong(taskQuery.getGroupId()));
        bo.setStatus(SystemConstants.NORMAL);
        putTimeRange(bo.getParams(), taskQuery);
        PageResult<SysDeptVo> page = deptService.selectPageDeptList(bo, pageParam(taskQuery));
        return new RemoteTaskAssigneeVo(page.getTotal(), RemoteTaskAssigneeVo.convertToHandlerList(page.getItems(),
            dept -> Convert.toStr(dept.getDeptId()), SysDeptVo::getDeptCategory, SysDeptVo::getDeptName,
            dept -> Convert.toStr(dept.getParentId()), SysDeptVo::getCreateTime));
    }

    @Override
    public RemoteTaskAssigneeVo selectUsersByTaskAssigneeList(RemoteTaskAssigneeBo taskQuery) {
        SysUserBo bo = new SysUserBo();
        bo.setUserName(taskQuery.getHandlerCode());
        bo.setNickName(taskQuery.getHandlerName());
        bo.setDeptId(Convert.toLong(taskQuery.getGroupId()));
        bo.setStatus(SystemConstants.NORMAL);
        putTimeRange(bo.getParams(), taskQuery);
        PageResult<SysUserVo> page = userService.selectPageUserList(bo, pageParam(taskQuery));
        return new RemoteTaskAssigneeVo(page.getTotal(), RemoteTaskAssigneeVo.convertToHandlerList(page.getItems(),
            user -> Convert.toStr(user.getUserId()), SysUserVo::getUserName, SysUserVo::getNickName,
            user -> Convert.toStr(user.getDeptId()), SysUserVo::getCreateTime));
    }

    private static PageParam pageParam(RemoteTaskAssigneeBo taskQuery) {
        return new PageParam(taskQuery.getPage(), taskQuery.getPageSize());
    }

    private static void putTimeRange(Map<String, Object> params, RemoteTaskAssigneeBo taskQuery) {
        params.put("beginTime", taskQuery.getBeginTime());
        params.put("endTime", taskQuery.getEndTime());
    }

}
