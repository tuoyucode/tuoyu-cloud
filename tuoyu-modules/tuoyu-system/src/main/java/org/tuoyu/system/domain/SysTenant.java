package org.tuoyu.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 租户。平台共享的表：主键就是 tenant_id，本身不按租户隔离
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
public class SysTenant extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 租户编码（小写字母、数字、连字符）
     */
    @TableId(value = "tenant_id", type = IdType.INPUT)
    private String tenantId;

    private String tenantName;

    private String contactUser;

    private String contactPhone;

    /**
     * 绑定的访问域名（登录页按它识别租户）
     */
    private String domain;

    /**
     * 租户套餐（可用菜单的集合）
     */
    private Long packageId;

    /**
     * 过期时间，为空表示不过期
     */
    private LocalDateTime expireTime;

    /**
     * 用户数上限，为空表示不限
     */
    private Integer accountCount;

    /**
     * 状态（0 正常 1 停用）
     */
    private String status;

    @TableLogic
    private String delFlag;

    private String remark;

}
