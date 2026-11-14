package org.tuoyu.system.api;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import org.tuoyu.common.rpc.annotation.RemoteApi;

import java.math.BigDecimal;
import java.util.List;

/**
 * 参数配置服务
 *
 * @author tuoyu
 */
@RemoteApi
public interface RemoteConfigService {

    /**
     * 是否开放用户注册（参数 {@code sys.account.registerUser}）
     *
     * @return 参数值按布尔值解析为真时为 true；参数不存在、为空或不能解析时为 false
     */
    boolean selectRegisterEnabled();

    /**
     * 按参数键名读取参数值
     *
     * @param configKey 参数键名
     * @return 参数值；参数不存在时为空字符串
     */
    String getConfigValue(String configKey);

    /**
     * 按参数键名读取布尔值
     *
     * @param configKey 参数键名
     * @return 按 hutool {@code Convert.toBool} 转换：true、yes、1 等为真，其他文字（含参数不存在时的空字符串）为假；
     * 参数值为 null 时为 null
     */
    default Boolean getConfigBool(String configKey) {
        return Convert.toBool(getConfigValue(configKey));
    }

    /**
     * 按参数键名读取整数
     *
     * @param configKey 参数键名
     * @return 整数；参数不存在或不能转换时为 null
     */
    default Integer getConfigInt(String configKey) {
        return Convert.toInt(getConfigValue(configKey));
    }

    /**
     * 按参数键名读取长整数
     *
     * @param configKey 参数键名
     * @return 长整数；参数不存在或不能转换时为 null
     */
    default Long getConfigLong(String configKey) {
        return Convert.toLong(getConfigValue(configKey));
    }

    /**
     * 按参数键名读取小数
     *
     * @param configKey 参数键名
     * @return 小数；参数不存在或不能转换时为 null
     */
    default BigDecimal getConfigDecimal(String configKey) {
        return Convert.toBigDecimal(getConfigValue(configKey));
    }

    /**
     * 按参数键名读取 JSON 对象
     *
     * @param configKey 参数键名
     * @return 解析结果；参数值为空或不是合法的 JSON 对象时为空的 Dict（不为 null）
     */
    Dict getConfigMap(String configKey);

    /**
     * 按参数键名读取 JSON 对象数组
     *
     * @param configKey 参数键名
     * @return 解析结果；参数值为空或不是合法的 JSON 数组时为空列表（不为 null）
     */
    List<Dict> getConfigArrayMap(String configKey);

    /**
     * 按参数键名读取 JSON 并转换为指定类型的对象
     *
     * @param configKey 参数键名
     * @param clazz     目标类型
     * @return 对象；参数值为空或不能解析时为 null
     */
    <T> T getConfigObject(String configKey, Class<T> clazz);

    /**
     * 按参数键名读取 JSON 数组并转换为指定类型的列表
     *
     * @param configKey 参数键名
     * @param clazz     元素类型
     * @return 列表；参数值为空或不能解析时为空列表（不为 null）
     */
    <T> List<T> getConfigArray(String configKey, Class<T> clazz);

}
