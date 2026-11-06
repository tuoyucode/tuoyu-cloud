package org.tuoyu.system.mapper;

import org.tuoyu.common.mybatis.core.mapper.BaseMapperPlus;
import org.tuoyu.system.domain.SysUserPost;

/**
 * 用户与岗位关联表 数据层
 *
 * @author Lion Li
 */
public interface SysUserPostMapper extends BaseMapperPlus<SysUserPost, SysUserPost> {

    /**
     * 删除用户的全部岗位关联
     *
     * @param userId 用户ID
     * @return 删除的行数
     */
    default long deleteByUserId(Long userId) {
        return this.lambda().eq(SysUserPost::getUserId, userId).deleteCount();
    }

}
