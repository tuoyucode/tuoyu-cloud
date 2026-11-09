package org.tuoyu.mes.masterdata.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;

/**
 * 工位 mes_station
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_station")
public class MesStation extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "station_id")
    private Long stationId;

    /**
     * 所属工厂
     */
    private Long factoryId;

    /**
     * 所属车间
     */
    private Long workshopId;

    /**
     * 所属产线
     */
    private Long lineId;

    /**
     * 工位编码
     */
    private String stationCode;

    /**
     * 工位名称
     */
    private String stationName;

    /**
     * 工位类型（与工序的建议工位类型对应）
     */
    private String stationType;

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
