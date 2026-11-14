package org.tuoyu.system.event;

import java.util.Collection;
import java.util.List;

/**
 * 清理在线会话的事件：角色、用户授权或租户状态变化后，强制退出受影响的会话（事务提交后异步执行）。
 * <p>
 * 三个范围各用一个静态工厂创建，其余两项为 null。
 *
 * @param roleId   拥有该角色的会话
 * @param userIds  这些用户的会话（复制为不可修改的列表）
 * @param tenantId 该租户的会话
 * @author tuoyu
 */
public record OnlineUserCleanEvent(Long roleId, Collection<Long> userIds, String tenantId) {

    public OnlineUserCleanEvent {
        if (userIds != null && !userIds.isEmpty()) {
            userIds = List.copyOf(userIds);
        }
    }

    /**
     * @param roleId 角色 ID
     * @return 清理拥有该角色的会话
     */
    public static OnlineUserCleanEvent byRole(Long roleId) {
        return new OnlineUserCleanEvent(roleId, null, null);
    }

    /**
     * @param userIds 用户 ID
     * @return 清理这些用户的会话
     */
    public static OnlineUserCleanEvent byUsers(Collection<Long> userIds) {
        return new OnlineUserCleanEvent(null, userIds, null);
    }

    /**
     * @param tenantId 租户编号
     * @return 清理该租户的会话
     */
    public static OnlineUserCleanEvent byTenant(String tenantId) {
        return new OnlineUserCleanEvent(null, null, tenantId);
    }

}
