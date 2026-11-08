package org.tuoyu.system.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.xss.Xss;
import org.tuoyu.system.domain.SysNotice;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 通知公告业务对象 sys_notice
 *
 * @author Michelle.Chung
 */

@Data
@AutoMapper(target = SysNotice.class, reverseConvertGenerate = false)
public class SysNoticeBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 公告ID
     */
    private Long noticeId;

    /**
     * 公告标题
     */
    @Xss(message = "公告标题不能包含脚本字符")
    @NotBlank(message = "公告标题不能为空")
    @Size(min = 0, max = 50, message = "公告标题不能超过{max}个字符")
    private String noticeTitle;

    /**
     * 公告类型（1通知 2公告）
     */
    private String noticeType;

    /**
     * 公告内容（纯文本：前端编辑器为多行文本框、按文本展示。改用富文本编辑器时再标注 @RichText）
     */
    private String noticeContent;

    /**
     * 公告状态（0正常 1关闭）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人名称
     */
    private String createByName;

    /**
     * 请求参数
     */
    private Map<String, Object> params = new HashMap<>();

}
