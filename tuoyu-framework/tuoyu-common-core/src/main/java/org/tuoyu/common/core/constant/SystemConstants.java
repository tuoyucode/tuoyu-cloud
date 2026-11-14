package org.tuoyu.common.core.constant;

/**
 * 系统管理使用的常量。
 *
 * @author tuoyu
 */
public interface SystemConstants {

    /**
     * 正常状态
     */
    String NORMAL = "0";

    /**
     * 停用状态
     */
    String DISABLE = "1";

    /**
     * 是
     */
    String YES = "Y";

    /**
     * 否
     */
    String NO = "N";

    /**
     * 菜单类型：目录
     */
    String TYPE_DIR = "M";

    /**
     * 菜单类型：菜单
     */
    String TYPE_MENU = "C";

    /**
     * 菜单类型：按钮
     */
    String TYPE_BUTTON = "F";

    /**
     * 前端布局组件
     */
    String LAYOUT = "Layout";

    /**
     * 前端多级菜单的中间层组件
     */
    String PARENT_VIEW = "ParentView";

    /**
     * 前端内嵌外链的组件
     */
    String INNER_LINK = "InnerLink";

    /**
     * 超级管理员的用户 ID
     */
    Long SUPER_ADMIN_USER_ID = 1761100000000000001L;

    /**
     * 超级管理员的角色 ID
     */
    Long SUPER_ADMIN_ROLE_ID = 1761300000000000001L;

    /**
     * 超级管理员的角色权限字符串
     */
    String SUPER_ADMIN_ROLE_KEY = "superadmin";

    /**
     * 根部门的祖级列表
     */
    String ROOT_DEPT_ANCESTORS = "0";

    /**
     * 写日志时要去掉的口令字段
     */
    String[] EXCLUDE_PROPERTIES = {"password", "oldPassword", "newPassword", "confirmPassword"};

}
