package org.tuoyu.system.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.system.domain.SysTenantPackage;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户套餐
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = SysTenantPackage.class)
public class SysTenantPackageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long packageId;

    private String packageName;

    private String status;

    private String remark;

    private LocalDateTime createTime;

    /**
     * 套餐中的菜单（详情接口返回）
     */
    private List<Long> menuIds;

}
