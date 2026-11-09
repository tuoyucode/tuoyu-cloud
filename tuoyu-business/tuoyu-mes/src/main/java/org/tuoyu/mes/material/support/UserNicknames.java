package org.tuoyu.mes.material.support;

import org.tuoyu.common.rpc.fallback.RemoteFallback;
import org.tuoyu.system.api.RemoteUserService;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 导出时的用户昵称：接口返回时由翻译注解填写，Excel 导出不经过翻译，需要在服务层批量查出
 *
 * @author tuoyu
 */
public final class UserNicknames {

    private UserNicknames() {
    }

    /**
     * 批量查询用户昵称；系统服务不可用时降级为空（导出中昵称列为空，不影响导出）
     */
    public static Map<Long, String> of(RemoteUserService userService, Collection<Long> userIds) {
        Set<Long> ids = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = RemoteFallback.call("system.user.nickname", () -> userService.selectUserNicksByIds(ids), Map.of());
        return names == null ? Map.of() : names;
    }

}
