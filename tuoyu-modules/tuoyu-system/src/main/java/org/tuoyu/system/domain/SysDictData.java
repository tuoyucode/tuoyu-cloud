package org.tuoyu.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 字典数据 sys_dict_data。全平台共享（表中没有租户列）
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class SysDictData extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 字典编码
     */
    @TableId("dict_code")
    private Long dictCode;

    /**
     * 排序
     */
    private Integer dictSort;

    /**
     * 字典标签
     */
    private String dictLabel;

    /**
     * 字典键值
     */
    private String dictValue;

    /**
     * 字典类型编码
     */
    private String dictType;

    /**
     * 样式属性
     */
    private String cssClass;

    /**
     * 表格回显样式
     */
    private String listClass;

    /**
     * 是否默认（Y 是 N 否）
     */
    private String isDefault;

    /**
     * 备注
     */
    private String remark;

}
