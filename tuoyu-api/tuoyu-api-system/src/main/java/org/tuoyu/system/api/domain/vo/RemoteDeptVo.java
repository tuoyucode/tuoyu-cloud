package org.tuoyu.system.api.domain.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 跨模块传递的部门
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
public class RemoteDeptVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 部门 ID
     */
    private Long deptId;

    /**
     * 上级部门 ID
     */
    private Long parentId;

    /**
     * 部门名称
     */
    private String deptName;

}
