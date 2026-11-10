package org.tuoyu.mes.masterdata.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;

/**
 * 工艺路线 mes_route
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_route")
public class MesRoute extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "route_id")
    private Long routeId;

    /**
     * 产品
     */
    private Long materialId;

    /**
     * 工艺路线编码
     */
    private String routeCode;

    /**
     * 工艺路线名称
     */
    private String routeName;

    /**
     * 版本
     */
    private String routeVersion;

    /**
     * 是否默认版本（Y 是 N 否）
     */
    private String isDefault;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 删除标志（0 存在 1 删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
