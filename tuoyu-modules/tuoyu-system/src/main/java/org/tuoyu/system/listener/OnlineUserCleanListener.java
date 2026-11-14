package org.tuoyu.system.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.tuoyu.system.event.OnlineUserCleanEvent;
import org.tuoyu.system.service.ISysRoleService;

import java.util.List;

/**
 * 清理在线会话：事务提交后异步执行（事务回滚时不清理；不在事务中时直接执行）
 *
 * @author tuoyu
 */
@Component
@RequiredArgsConstructor
public class OnlineUserCleanListener {

    private final ISysRoleService roleService;

    /**
     * 按事件中的角色、用户、租户分别清理
     *
     * @param event 事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void cleanOnlineUser(OnlineUserCleanEvent event) {
        if (event.roleId() != null) {
            roleService.cleanOnlineUserByRole(event.roleId());
        }
        if (event.userIds() != null && !event.userIds().isEmpty()) {
            roleService.cleanOnlineUser(List.copyOf(event.userIds()));
        }
        if (event.tenantId() != null) {
            roleService.cleanOnlineUserByTenant(event.tenantId());
        }
    }

}
