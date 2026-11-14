package org.tuoyu.system.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.system.domain.SysPost;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 岗位请求对象（新增、修改、列表条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = SysPost.class, reverseConvertGenerate = false)
public class SysPostBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位 ID
     */
    private Long postId;

    /**
     * 所属部门 ID
     */
    @NotNull(message = "部门id不能为空")
    private Long deptId;

    /**
     * 按该部门及其下级搜索
     */
    private Long belongDeptId;

    /**
     * 岗位编码
     */
    @NotBlank(message = "岗位编码不能为空")
    @Size(min = 0, max = 64, message = "岗位编码长度不能超过{max}个字符")
    private String postCode;

    /**
     * 岗位名称
     */
    @NotBlank(message = "岗位名称不能为空")
    @Size(min = 0, max = 50, message = "岗位名称长度不能超过{max}个字符")
    private String postName;

    /**
     * 岗位类别编码
     */
    @Size(min = 0, max = 100, message = "类别编码长度不能超过{max}个字符")
    private String postCategory;

    /**
     * 显示顺序
     */
    @NotNull(message = "显示顺序不能为空")
    private Integer postSort;

    /**
     * 状态（0 正常 1 停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 请求参数（创建时间区间 beginTime、endTime）
     */
    private Map<String, Object> params = new HashMap<>();

}
