package org.tuoyu.system.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.system.domain.SysDept;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 部门请求对象（新增、修改、列表条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = SysDept.class, reverseConvertGenerate = false)
public class SysDeptBo implements Serializable {

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
    @NotBlank(message = "部门名称不能为空")
    @Size(min = 0, max = 30, message = "部门名称长度不能超过{max}个字符")
    private String deptName;

    /**
     * 部门类别编码
     */
    @Size(min = 0, max = 100, message = "部门类别编码长度不能超过{max}个字符")
    private String deptCategory;

    /**
     * 显示顺序
     */
    @NotNull(message = "显示顺序不能为空")
    private Integer orderNum;

    /**
     * 负责人（用户 ID）
     */
    private Long leader;

    /**
     * 联系电话
     */
    @Size(min = 0, max = 11, message = "联系电话长度不能超过{max}个字符")
    private String phone;

    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确")
    @Size(min = 0, max = 50, message = "邮箱长度不能超过{max}个字符")
    private String email;

    /**
     * 状态（0 正常 1 停用）
     */
    private String status;

    /**
     * 所属部门（按该部门及其下级搜索）
     */
    private Long belongDeptId;

    /**
     * 请求参数（创建时间区间 beginTime、endTime）
     */
    private Map<String, Object> params = new HashMap<>();

}
