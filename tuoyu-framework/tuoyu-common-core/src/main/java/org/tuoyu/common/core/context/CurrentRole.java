package org.tuoyu.common.core.context;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前用户拥有的一个角色。
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentRole implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 角色 ID
     */
    private Long roleId;

    /**
     * 角色权限字符串，可能用逗号分隔多个
     */
    private String roleKey;

    /**
     * 数据范围编码
     */
    private String dataScope;

}
