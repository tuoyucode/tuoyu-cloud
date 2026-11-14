package org.tuoyu.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户与岗位关联 sys_user_post
 *
 * @author tuoyu
 */
@Data
@TableName("sys_user_post")
public class SysUserPost implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    @TableId(type = IdType.INPUT)
    private Long userId;

    /**
     * 岗位 ID
     */
    private Long postId;

}
