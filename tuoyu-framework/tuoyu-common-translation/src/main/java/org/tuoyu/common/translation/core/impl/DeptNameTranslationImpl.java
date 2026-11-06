package org.tuoyu.common.translation.core.impl;

import cn.hutool.core.convert.Convert;
import lombok.AllArgsConstructor;
import org.tuoyu.common.translation.annotation.TranslationType;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.common.translation.core.TranslationInterface;
import org.tuoyu.system.api.RemoteDeptService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 部门翻译实现
 *
 * @author Lion Li
 */
@AllArgsConstructor
@TranslationType(type = TransConstant.DEPT_ID_TO_NAME)
public class DeptNameTranslationImpl implements TranslationInterface<String> {

    private final RemoteDeptService remoteDeptService;

    /**
     * 将部门 ID 或 ID 集合翻译为部门名称。
     *
     * @param key   部门 ID 或逗号分隔的 ID 字符串
     * @param other 额外参数
     * @return 部门名称
     */
    @Override
    public String translation(Object key, String other) {
        return remoteDeptService.selectDeptNameByIds(key.toString());
    }

    /**
     * 批量将部门 ID 翻译为部门名称。
     *
     * @param keys  部门 ID 集合
     * @param other 额外参数
     * @return 部门 ID 与部门名称映射
     */
    @Override
    public Map<Object, String> translationBatch(Set<Object> keys, String other) {
        Set<Long> deptIds = collectLongIds(keys);
        if (deptIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> deptNames = remoteDeptService.selectDeptNamesByIds(deptIds);
        Map<Object, String> result = new LinkedHashMap<>(keys.size());
        for (Object key : keys) {
            result.put(key, buildValue(key, deptNames));
        }
        return result;
    }

    /**
     * 根据原始键构建部门名称翻译值。
     *
     * @param source    原始键
     * @param deptNames 部门 ID 与部门名称映射
     * @return 部门名称
     */
    private String buildValue(Object source, Map<Long, String> deptNames) {
        if (source instanceof String ids) {
            return joinMappedValues(ids, deptNames::get);
        }
        return source == null ? null : deptNames.get(Convert.toLong(source));
    }

}
