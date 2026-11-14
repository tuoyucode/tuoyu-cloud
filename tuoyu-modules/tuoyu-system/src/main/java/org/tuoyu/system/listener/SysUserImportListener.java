package org.tuoyu.system.listener;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.event.AnalysisEventListener;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.exception.base.BaseException;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.core.utils.ValidatorUtils;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.excel.core.ExcelListener;
import org.tuoyu.common.excel.core.ExcelResult;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.vo.SysUserImportVo;
import org.tuoyu.system.domain.vo.SysUserVo;
import org.tuoyu.system.service.ISysConfigService;
import org.tuoyu.system.service.ISysUserService;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 用户导入：逐行新增或更新用户，汇总成功与失败的明细。
 * <p>
 * 由接口在请求线程中创建（构造时取当前用户与初始密码）。失败明细只写校验与业务提示；意外异常的原始消息可能含
 * SQL 或表名，只写进日志，明细中统一为“系统异常，请联系管理员”。
 *
 * @author tuoyu
 */
@Slf4j
public class SysUserImportListener extends AnalysisEventListener<SysUserImportVo> implements ExcelListener<SysUserImportVo> {

    private static final String UNEXPECTED_FAILURE = "系统异常，请联系管理员";

    /**
     * HTML 标签（失败明细中的账号去掉标签后再拼接）
     */
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");

    private final ISysUserService userService;

    /**
     * 初始密码的哈希（构造时计算一次）
     */
    private final String initPasswordHash;

    /**
     * 执行导入的用户（写进失败日志）
     */
    private final Long operUserId;

    private final Boolean isUpdateSupport;

    private int successNum;
    private int failureNum;
    private final StringBuilder successMsg = new StringBuilder();
    private final StringBuilder failureMsg = new StringBuilder();

    /**
     * @param isUpdateSupport 账号已存在时是否更新
     */
    public SysUserImportListener(Boolean isUpdateSupport) {
        ISysConfigService configService = SpringUtils.getBean(ISysConfigService.class);
        PasswordHasher passwordHasher = SpringUtils.getBean(PasswordHasher.class);
        this.userService = SpringUtils.getBean(ISysUserService.class);
        this.initPasswordHash = passwordHasher.hash(configService.selectConfigByKey("sys.user.initPassword"));
        this.operUserId = UserContext.userId();
        this.isUpdateSupport = isUpdateSupport;
    }

    @Override
    public void invoke(SysUserImportVo row, AnalysisContext context) {
        try {
            SysUserVo existing = userService.selectUserByUserName(row.getUserName());
            if (existing == null) {
                SysUserBo user = toBo(row);
                ValidatorUtils.validate(user);
                user.setPassword(initPasswordHash);
                userService.insertUser(user);
                successNum++;
                successMsg.append('\n').append(successNum).append("、账号 ").append(user.getUserName()).append(" 导入成功");
            } else if (Boolean.TRUE.equals(isUpdateSupport)) {
                SysUserBo user = toBo(row);
                user.setUserId(existing.getUserId());
                ValidatorUtils.validate(user);
                userService.checkUserAllowed(existing.getUserId());
                userService.checkUserDataScope(existing.getUserId());
                userService.updateUser(user);
                successNum++;
                successMsg.append('\n').append(successNum).append("、账号 ").append(user.getUserName()).append(" 更新成功");
            } else {
                failureNum++;
                failureMsg.append('\n').append(failureNum).append("、账号 ").append(existing.getUserName()).append(" 已存在");
            }
        } catch (Exception e) {
            failureNum++;
            failureMsg.append('\n').append(failureNum).append("、账号 ").append(stripTags(row.getUserName()))
                .append(" 导入失败：").append(reason(e, row.getUserName()));
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 每行已在 invoke 中处理完，汇总结果由 getExcelResult 给出
    }

    @Override
    public ExcelResult<SysUserImportVo> getExcelResult() {
        return new ExcelResult<>() {

            @Override
            public List<SysUserImportVo> getList() {
                return null;
            }

            @Override
            public List<String> getErrorList() {
                return null;
            }

            @Override
            public String getAnalysis() {
                if (failureNum > 0) {
                    throw new ServiceException("很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下："
                        + failureMsg);
                }
                return "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：" + successMsg;
            }
        };
    }

    /**
     * 失败原因：校验提示与业务提示原样给出，其他异常只记日志
     */
    private String reason(Exception e, String userName) {
        if (e instanceof ConstraintViolationException violation) {
            return StreamUtils.join(violation.getConstraintViolations(), ConstraintViolation::getMessage, ", ");
        }
        if (e instanceof ServiceException || e instanceof BaseException) {
            return e.getMessage();
        }
        log.error("导入用户 {} 失败（操作人 {}）", userName, operUserId, e);
        return UNEXPECTED_FAILURE;
    }

    private static String stripTags(String text) {
        return text == null ? null : HTML_TAG.matcher(text).replaceAll("");
    }

    /**
     * 把导入的行复制为用户请求对象（同名字段）
     */
    private static SysUserBo toBo(SysUserImportVo row) {
        SysUserBo user = new SysUserBo();
        user.setUserId(row.getUserId());
        user.setDeptId(row.getDeptId());
        user.setUserName(row.getUserName());
        user.setNickName(row.getNickName());
        user.setEmail(row.getEmail());
        user.setPhoneNumber(row.getPhoneNumber());
        user.setGender(row.getGender());
        user.setStatus(row.getStatus());
        return user;
    }

}
