package org.tuoyu.mes.masterdata.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.mes.masterdata.domain.MesLocation;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 库位视图对象 mes_location
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesLocation.class)
public class MesLocationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long locationId;

    private Long warehouseId;

    /**
     * 所属仓库名称（服务层填充）
     */
    private String warehouseName;

    private String locationCode;

    private String locationName;

    private String status;

    private Integer sortNo;

    private String remark;

    private LocalDateTime createTime;

}
