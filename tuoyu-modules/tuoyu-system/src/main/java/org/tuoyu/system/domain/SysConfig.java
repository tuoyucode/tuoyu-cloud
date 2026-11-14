package org.tuoyu.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 参数配置 sys_config。全平台共享（表中没有租户列）
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfig extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 参数主键
     */
    @TableId("config_id")
    private Long configId;

    /**
     * 参数名称
     */
    private String configName;

    /**
     * 参数键名
     */
    private String configKey;

    /**
     * 参数键值
     */
    private String configValue;

    /**
     * 是否系统内置（Y 是 N 否），内置参数不能删除
     */
    private String configType;

    /**
     * 备注
     */
    private String remark;

}
