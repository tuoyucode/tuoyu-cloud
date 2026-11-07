package org.tuoyu.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysUserPost;

/**
 * 用户与岗位关联表 数据层
 *
 * @author Lion Li
 */
public interface SysUserPostMapper extends BaseMapper<SysUserPost> {

    /**
     * 删除用户的全部岗位关联
     *
     * @param userId 用户ID
     * @return 删除的行数
     */
    default long deleteByUserId(Long userId) {
        return this.delete(QueryBuilder.lambda(SysUserPost.class).eq(SysUserPost::getUserId, userId).build());
    }

}
