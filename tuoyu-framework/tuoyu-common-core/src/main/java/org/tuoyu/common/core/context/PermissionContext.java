package org.tuoyu.common.core.context;

/**
 * 权限上下文：当前请求的接口声明了哪些访问要求。由认证模块实现。
 *
 * @author tuoyu
 */
public interface PermissionContext {

    /**
     * @return 当前接口声明的访问要求；不在请求中、或接口没有声明时为 {@link AccessRequirement#NONE}
     */
    AccessRequirement currentRequirement();

}
