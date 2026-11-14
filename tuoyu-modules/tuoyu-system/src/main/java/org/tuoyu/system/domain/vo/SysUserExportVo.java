package org.tuoyu.system.domain.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.system.listener.DeptExcelConverter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户导出的行：按字段顺序成列，部门列写部门的完整路径
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
public class SysUserExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    @ExcelProperty(value = "用户序号")
    private Long userId;

    /**
     * 账号
     */
    @ExcelProperty(value = "用户账号")
    private String userName;

    /**
     * 部门 ID（导出为部门路径）
     */
    @ExcelProperty(value = "部门名称", converter = DeptExcelConverter.class)
    private Long deptId;

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

    /**
     * 最后登录 IP
     */
    @ExcelProperty(value = "最后登录IP")
    private String loginIp;

    /**
     * 最后登录时间
     */
    @ExcelProperty(value = "最后登录时间")
    private LocalDateTime loginDate;

    /**
     * 部门负责人的账号
     */
    @ExcelProperty(value = "部门负责人")
    private String leaderName;

}
