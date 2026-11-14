package org.tuoyu.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 字典类型 sys_dict_type。全平台共享（表中没有租户列）
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 字典主键
     */
    @TableId("dict_id")
    private Long dictId;

    /**
     * 字典名称
     */
    private String dictName;

    /**
     * 字典类型编码
     */
    private String dictType;

    /**
     * 备注
     */
    private String remark;

}
