package org.tuoyu.system.domain;

import org.tuoyu.common.mybatis.core.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_post_demo")
public class PostDemo extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "post_id")
    private Long postId;

    private String postCode;

    private String postName;

    private String postCategory;

    private Integer postSort;

    private String status;

    private String remark;

}
