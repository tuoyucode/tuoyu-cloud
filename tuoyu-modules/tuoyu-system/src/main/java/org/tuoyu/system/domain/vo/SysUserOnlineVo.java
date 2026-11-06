package org.tuoyu.system.domain.vo;

import lombok.Data;
import org.tuoyu.system.api.domain.SysUserOnline;

import java.io.Serial;
import java.io.Serializable;

/**
 * 在线会话（返回给管理界面）。
 * <p>
 * 不包含令牌原值：令牌就是登录凭证，有“在线用户”权限的管理员拿到它就能冒用该会话。
 * 用令牌的 SHA-256 作为会话编号，强退接口按编号定位会话。
 *
 * @author tuoyu
 */
@Data
public class SysUserOnlineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会话编号：令牌的 SHA-256（十六进制），不能反推出令牌
     */
    private String sessionId;

    /**
     * 是否为发起本次请求的会话
     */
    private boolean current;

    /**
     * 部门名称
     */
    private String deptName;

    /**
     * 用户账号
     */
    private String userName;

    /**
     * 客户端
     */
    private String clientKey;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 登录IP地址
     */
    private String ipaddr;

    /**
     * 登录地址
     */
    private String loginLocation;

    /**
     * 浏览器类型
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 登录时间
     */
    private Long loginTime;

    /**
     * 由缓存中的在线会话生成，令牌只用于计算会话编号
     *
     * @param online       缓存中的在线会话
     * @param currentToken 本次请求的令牌，可以为空
     * @return 会话信息
     */
    public static SysUserOnlineVo of(SysUserOnline online, String currentToken) {
        SysUserOnlineVo vo = new SysUserOnlineVo();
        vo.setSessionId(OnlineSessionId.of(online.getTokenId()));
        vo.setCurrent(currentToken != null && currentToken.equals(online.getTokenId()));
        vo.setDeptName(online.getDeptName());
        vo.setUserName(online.getUserName());
        vo.setClientKey(online.getClientKey());
        vo.setDeviceType(online.getDeviceType());
        vo.setIpaddr(online.getIpaddr());
        vo.setLoginLocation(online.getLoginLocation());
        vo.setBrowser(online.getBrowser());
        vo.setOs(online.getOs());
        vo.setLoginTime(online.getLoginTime());
        return vo;
    }

}
