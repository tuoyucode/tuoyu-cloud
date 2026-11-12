package org.tuoyu.mes.masterdata.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;

/**
 * 车间 mes_workshop
 * <p>
 * 表中的 dept_id 列（对应的组织）目前不使用，实体不映射，读写都不涉及它
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_workshop")
public class MesWorkshop extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "workshop_id")
    private Long workshopId;

    /**
     * 所属工厂
     */
    private Long factoryId;

    /**
     * 车间编码
     */
    private String workshopCode;

    /**
     * 车间名称
     */
    private String workshopName;

    /**
     * 负责人
     */
    private String manager;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 排序号
     */
    private Integer sortNo;

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
