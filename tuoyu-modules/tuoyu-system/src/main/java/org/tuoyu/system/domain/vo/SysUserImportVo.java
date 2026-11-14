package org.tuoyu.system.domain.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.annotation.ExcelDynamicOptions;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.system.listener.DeptExcelConverter;
import org.tuoyu.system.listener.DeptExcelOptions;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户导入的行（也用于生成导入模板）：部门列填部门的完整路径，模板中给出下拉选项
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
public class SysUserImportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    @ExcelProperty(value = "用户序号")
    private Long userId;

    /**
     * 部门 ID（导入时由部门路径转换）
     */
    @ExcelProperty(value = "部门名称", converter = DeptExcelConverter.class)
    @ExcelDynamicOptions(providerClass = DeptExcelOptions.class)
    private Long deptId;

    /**
     * 账号
     */
    @ExcelProperty(value = "用户账号")
    private String userName;

    /**
     * 昵称
     */
    @ExcelProperty(value = "用户昵称")
    private String nickName;

    /**
     * 邮箱
     */
    @ExcelProperty(value = "用户邮箱")
    private String email;

    /**
     * 手机号
     */
    @ExcelProperty(value = "手机号码")
    private String phoneNumber;

    /**
     * 性别
     */
    @ExcelProperty(value = "用户性别", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "sys_user_gender")
    private String gender;

    /**
     * 状态（0 正常 1 停用）
     */
    @ExcelProperty(value = "账号状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "sys_normal_disable")
    private String status;

}
