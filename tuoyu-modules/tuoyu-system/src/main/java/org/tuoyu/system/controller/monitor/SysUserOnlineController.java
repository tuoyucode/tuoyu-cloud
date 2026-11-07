package org.tuoyu.system.controller.monitor;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.core.utils.ThreadUtils;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.common.redis.utils.RedisUtils;
import org.tuoyu.system.api.domain.SysUserOnline;
import org.tuoyu.system.domain.vo.OnlineSessionId;
import org.tuoyu.system.domain.vo.SysUserOnlineVo;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 在线用户监控
 * <p>
 * 接口只返回会话编号（令牌的 SHA-256），不返回令牌本身；强退接口按会话编号定位会话。
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/online")
public class SysUserOnlineController {

    /**
     * 获取在线用户监控列表
     *
     * @param ipaddr   IP地址
     * @param userName 用户名
     */
    @SaCheckPermission("monitor:online:list")
    @GetMapping("/list")
    public ApiResult<PageResult<SysUserOnlineVo>> list(String ipaddr, String userName) {
        List<SysUserOnline> onlineList = loadOnline(onlineTokens());
        if (StringUtils.isNotEmpty(ipaddr) && StringUtils.isNotEmpty(userName)) {
            onlineList = StreamUtils.filter(onlineList, userOnline ->
                StringUtils.equals(ipaddr, userOnline.getIpaddr()) &&
                    StringUtils.equals(userName, userOnline.getUserName())
            );
        } else if (StringUtils.isNotEmpty(ipaddr)) {
            onlineList = StreamUtils.filter(onlineList, userOnline ->
                StringUtils.equals(ipaddr, userOnline.getIpaddr())
            );
        } else if (StringUtils.isNotEmpty(userName)) {
            onlineList = StreamUtils.filter(onlineList, userOnline ->
                StringUtils.equals(userName, userOnline.getUserName())
            );
        }
        return ApiResult.ok(PageResult.of(toVo(onlineList)));
    }

    /**
     * 强退用户
     *
     * @param sessionId 会话编号（令牌的 SHA-256）
     */
    @SaCheckPermission("monitor:online:forceLogout")
    @OperationLog(module = "在线用户", action = AuditAction.FORCE_LOGOUT)
    @PreventResubmit
    @DeleteMapping("/{sessionId}")
    public ApiResult<Void> forceLogout(@PathVariable String sessionId) {
        OnlineSessionId.findToken(onlineTokens(), sessionId).ifPresent(this::kickout);
        return ApiResult.ok();
    }

    /**
     * 获取当前用户登录在线设备（仅登录：只查询本人的令牌）
     */
    @SaCheckLogin
    @GetMapping()
    public ApiResult<PageResult<SysUserOnlineVo>> getInfo() {
        List<String> tokens = StpUtil.getTokenValueListByLoginId(StpUtil.getLoginIdAsString());
        return ApiResult.ok(PageResult.of(toVo(loadOnline(tokens))));
    }

    /**
     * 强退当前在线设备（仅登录：只能强退本人的会话）
     *
     * @param sessionId 会话编号（令牌的 SHA-256）
     */
    @SaCheckLogin
    @OperationLog(module = "在线设备", action = AuditAction.FORCE_LOGOUT)
    @PreventResubmit
    @DeleteMapping("/myself/{sessionId}")
    public ApiResult<Void> remove(@PathVariable("sessionId") String sessionId) {
        // 只在本人的令牌中查找
        List<String> tokens = StpUtil.getTokenValueListByLoginId(StpUtil.getLoginIdAsString());
        OnlineSessionId.findToken(tokens, sessionId).ifPresent(this::kickout);
        return ApiResult.ok();
    }

    /**
     * 所有在线会话的令牌
     */
    private List<String> onlineTokens() {
        Collection<String> keys = RedisUtils.keys(CacheNames.ONLINE_TOKEN_KEY + "*");
        return keys.stream().map(key -> StringUtils.substringAfterLast(key, StringUtils.COLON)).toList();
    }

    /**
     * 读取未过期的在线会话，按登录先后倒序
     */
    private List<SysUserOnline> loadOnline(List<String> tokens) {
        List<Supplier<SysUserOnline>> suppliers = tokens.stream().map(token -> (Supplier<SysUserOnline>) () -> {
            // 如果已经过期则跳过
            if (StpUtil.stpLogic.getTokenActiveTimeoutByToken(token) < -1) {
                return null;
            }
            SysUserOnline online = RedisUtils.getCacheObject(CacheNames.ONLINE_TOKEN_KEY + token);
            if (online != null) {
                // 会话编号按缓存键中的令牌计算
                online.setTokenId(token);
            }
            return online;
        }).toList();
        List<SysUserOnline> list = new ArrayList<>(ThreadUtils.virtualSubmitAll(suppliers));
        list.removeAll(Collections.singleton(null));
        Collections.reverse(list);
        return list;
    }

    /**
     * 转为不含令牌的会话信息
     */
    private List<SysUserOnlineVo> toVo(List<SysUserOnline> list) {
        String currentToken = currentToken();
        return list.stream().map(online -> SysUserOnlineVo.of(online, currentToken)).toList();
    }

    private String currentToken() {
        try {
            return StpUtil.getTokenValue();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void kickout(String token) {
        try {
            StpUtil.kickoutByTokenValue(token);
        } catch (NotLoginException ignored) {
        }
    }

}
