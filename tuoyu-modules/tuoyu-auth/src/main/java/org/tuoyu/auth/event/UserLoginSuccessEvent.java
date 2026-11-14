package org.tuoyu.auth.event;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;

/**
 * 用户登录成功（Sa-Token 完成登录、令牌已生成）
 *
 * @param loginId        登录 ID（用户类型 + 用户 ID）
 * @param tokenValue     本次登录的令牌
 * @param loginParameter 登录参数，含设备类型、有效期与令牌扩展信息
 * @author tuoyu
 */
public record UserLoginSuccessEvent(Object loginId, String tokenValue, SaLoginParameter loginParameter) {

}
