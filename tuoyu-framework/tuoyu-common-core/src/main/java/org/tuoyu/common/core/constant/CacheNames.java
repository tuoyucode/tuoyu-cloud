package org.tuoyu.common.core.constant;

/**
 * 缓存名称。
 * <p>
 * 格式为 {@code 名称#ttl#maxIdleTime#maxSize#local}，由 Redis 缓存管理器解析，后面的各段都可以省略：
 * <ul>
 *     <li>ttl：过期时间，0 表示不过期，例如 {@code 30d}、{@code 10m}；</li>
 *     <li>maxIdleTime：最大空闲时间，超过后清除，0 表示不检测；</li>
 *     <li>maxSize：最大条数，0 表示不限制；</li>
 *     <li>local：1 表示同时启用本地缓存，0 表示只用 Redis。</li>
 * </ul>
 * 例如 {@code test#60s}、{@code test#0#60s}、{@code test#0#1m#1000}、{@code test#1h#0#500#1}。
 * 取值写进 Redis 的键名，修改后已有的缓存数据不再被读取。
 *
 * @author tuoyu
 */
public interface CacheNames {

    /**
     * 参数配置
     */
    String SYS_CONFIG = "sys_config";

    /**
     * 字典数据
     */
    String SYS_DICT = "sys_dict";

    /**
     * 字典类型
     */
    String SYS_DICT_TYPE = "sys_dict_type";

    /**
     * 客户端（全部租户共用）
     */
    String SYS_CLIENT = GlobalConstants.GLOBAL_REDIS_KEY + "sys_client#30d";

    /**
     * 用户账号
     */
    String SYS_USER_NAME = "sys_user_name#30d";

    /**
     * 用户昵称
     */
    String SYS_NICKNAME = "sys_nickname#30d";

    /**
     * 部门
     */
    String SYS_DEPT = "sys_dept#30d";

    /**
     * 对象存储的文件
     */
    String SYS_OSS = "sys_oss#30d";

    /**
     * 角色的自定义数据范围
     */
    String SYS_ROLE_CUSTOM = "sys_role_custom#30d";

    /**
     * 部门及其下级部门
     */
    String SYS_DEPT_AND_CHILD = "sys_dept_and_child#30d";

    /**
     * 对象存储的配置（全部租户共用）
     */
    String SYS_OSS_CONFIG = GlobalConstants.GLOBAL_REDIS_KEY + "sys_oss_config";

    /**
     * 在线用户的令牌
     */
    String ONLINE_TOKEN_KEY = "online_tokens:";

    /**
     * 按账号统计的登录失败次数，完整的键名由 {@code LoginFailureKeys} 生成
     */
    String PWD_ERR_CNT_KEY = "pwd_err_cnt:";

}
