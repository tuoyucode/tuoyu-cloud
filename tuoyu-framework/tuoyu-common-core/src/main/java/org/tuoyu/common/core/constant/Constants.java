package org.tuoyu.common.core.constant;

/**
 * 通用常量。
 *
 * @author tuoyu
 */
public interface Constants {

    /**
     * www 主域
     */
    String WWW = "www.";

    /**
     * http 请求
     */
    String HTTP = "http://";

    /**
     * https 请求
     */
    String HTTPS = "https://";

    /**
     * 成功标记
     */
    String SUCCESS = "0";

    /**
     * 失败标记
     */
    String FAIL = "1";

    /**
     * 登录成功
     */
    String LOGIN_SUCCESS = "Success";

    /**
     * 退出登录
     */
    String LOGOUT = "Logout";

    /**
     * 注册
     */
    String REGISTER = "Register";

    /**
     * 登录失败
     */
    String LOGIN_FAIL = "Error";

    /**
     * 验证码的有效期（分钟）
     */
    Integer CAPTCHA_EXPIRATION = 2;

    /**
     * 树形数据的顶级父节点 ID
     */
    Long TOP_PARENT_ID = 0L;

    /**
     * 加密字段值的前缀
     */
    String ENCRYPT_HEADER = "ENC_";

}
