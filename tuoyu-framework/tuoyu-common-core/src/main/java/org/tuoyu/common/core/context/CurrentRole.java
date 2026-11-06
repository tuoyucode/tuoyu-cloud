package org.tuoyu.common.core.context;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前用户的一个角色（只包含框架需要的字段：数据权限按它计算）
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentRole implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long roleId;

    /**
     * 角色权限字符串，多个用逗号分隔
     */
    private String roleKey;

    /**
     * 数据范围编码（见 tuoyu-common-mybatis 的 DataScopeType）
     */
    private String dataScope;

}
