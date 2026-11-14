package org.tuoyu.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色与部门关联 sys_role_dept（角色的自定义数据范围）
 *
 * @author tuoyu
 */
@Data
@TableName("sys_role_dept")
public class SysRoleDept implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 角色 ID
     */
    @TableId(type = IdType.INPUT)
    private Long roleId;

    /**
     * 部门 ID
     */
    private Long deptId;

}
