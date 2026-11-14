package org.tuoyu.system.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.system.domain.SysRole;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色视图（列表、详情与导出）
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SysRole.class)
public class SysRoleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 角色 ID
     */
    @ExcelProperty(value = "角色序号")
    private Long roleId;

    /**
     * 角色名称
     */
    @ExcelProperty(value = "角色名称")
    private String roleName;

    /**
     * 角色标识
     */
    @ExcelProperty(value = "角色权限")
    private String roleKey;

    /**
     * 显示顺序
     */
    @ExcelProperty(value = "角色排序")
    private Integer roleSort;

    /**
     * 数据范围
     */
    @ExcelProperty(value = "数据范围", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "1=全部数据权限,2=自定义数据权限,3=本部门数据权限,4=本部门及以下数据权限,5=仅本人数据权限,6=部门及以下或本人数据权限")
    private String dataScope;

    /**
     * 菜单树选择项是否父子联动
     */
    @ExcelProperty(value = "菜单树选择项是否关联显示")
    private Boolean menuCheckStrictly;

    /**
     * 部门树选择项是否父子联动
     */
    @ExcelProperty(value = "部门树选择项是否关联显示")
    private Boolean deptCheckStrictly;

    /**
     * 状态（0 正常 1 停用）
     */
    @ExcelProperty(value = "角色状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "sys_normal_disable")
    private String status;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 创建时间
     */
    @ExcelProperty(value = "创建时间")
    private LocalDateTime createTime;

    /**
     * 用户是否拥有该角色（授权页回显）
     */
    private boolean flag = false;

    /**
     * 是否超级管理员角色：JSON 输出为 superAdmin，前端据此禁用该行的操作
     *
     * @return 是否超级管理员角色
     */
    public boolean isSuperAdmin() {
        return SystemConstants.SUPER_ADMIN_ROLE_ID.equals(this.roleId);
    }

}
