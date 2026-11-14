package org.tuoyu.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 前端路由：null、空串与空集合都不输出
 *
 * @author tuoyu
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RouterVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 路由名称
     */
    private String name;

    /**
     * 路由地址
     */
    private String path;

    /**
     * 是否在侧边栏隐藏
     */
    private Boolean hidden;

    /**
     * 重定向地址（noRedirect 表示面包屑中不可点击）
     */
    private String redirect;

    /**
     * 组件
     */
    private String component;

    /**
     * 路由参数（JSON）
     */
    private String query;

    /**
     * 扩展字段
     */
    private String ext;

    /**
     * 只有一个子路由时是否仍显示上级
     */
    private Boolean alwaysShow;

    /**
     * 显示信息
     */
    private MetaVo meta;

    /**
     * 子路由
     */
    private List<RouterVo> children;

}
