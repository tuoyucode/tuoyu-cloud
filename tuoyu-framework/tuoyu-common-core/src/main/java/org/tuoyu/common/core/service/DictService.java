package org.tuoyu.common.core.service;

import org.tuoyu.common.core.utils.StringUtils;

import java.util.Map;

/**
 * 字典数据。
 *
 * @author tuoyu
 */
public interface DictService {

    /**
     * 按字典值取标签，多个值用逗号分隔。
     *
     * @param dictType  字典类型
     * @param dictValue 字典值
     * @return 字典标签
     */
    default String getDictLabel(String dictType, String dictValue) {
        return getDictLabel(dictType, dictValue, StringUtils.SEPARATOR);
    }

    /**
     * 按字典值取标签。
     *
     * @param dictType  字典类型
     * @param dictValue 字典值，多个值用分隔符连接
     * @param separator 分隔符
     * @return 字典标签，多个标签用分隔符连接
     */
    String getDictLabel(String dictType, String dictValue, String separator);

    /**
     * 按字典标签取值。
     *
     * @param dictType  字典类型
     * @param dictLabel 字典标签，多个标签用分隔符连接
     * @param separator 分隔符
     * @return 字典值，多个值用分隔符连接
     */
    String getDictValue(String dictType, String dictLabel, String separator);

    /**
     * @param dictType 字典类型
     * @return 该类型的全部字典，键为字典值、值为标签
     */
    Map<String, String> getAllDictByDictType(String dictType);

}
