package org.tuoyu.system.api.domain.bo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工作流设计器选择办理人的查询条件
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
public class RemoteTaskAssigneeBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 办理人编码（角色标识、岗位类别、部门类别或账号）
     */
    private String handlerCode;

    /**
     * 办理人名称
     */
    private String handlerName;

    /**
     * 分组 ID（部门）
     */
    private String groupId;

    /**
     * 创建时间起
     */
    private String beginTime;

    /**
     * 创建时间止
     */
    private String endTime;

    /**
     * 页码，从 1 开始
     */
    private Integer page = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 10;

}
