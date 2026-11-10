package org.tuoyu.mes.common.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 下拉选项：只含 ID、编码与名称（以及可选的上级 ID），供其他页面选择主数据，不含联系方式等明细
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MesOptionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String code;

    private String name;

    /**
     * 上级 ID（例如车间所属的工厂、产线所属的车间），没有时为空
     */
    private Long parentId;

    public MesOptionVo(Long id, String code, String name) {
        this(id, code, name, null);
    }

}
