package org.tuoyu.system.domain;

import org.tuoyu.common.mybatis.core.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept_demo")
public class DeptDemo extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "dept_id")
    private Long deptId;

    private Long parentId;

    private String ancestors;

    private String deptName;

    private Integer orderNum;

    private String status;

}
