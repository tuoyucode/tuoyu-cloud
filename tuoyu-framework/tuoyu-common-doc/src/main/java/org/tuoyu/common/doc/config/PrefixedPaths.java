package org.tuoyu.common.doc.config;

import io.swagger.v3.oas.models.Paths;

/**
 * 单独使用一个类便于判断 解决springdoc路径拼接重复问题
 *
 * @author Lion Li
 */
public class PrefixedPaths extends Paths {

    /**
     * 构造路径缓存标记对象。
     */
    public PrefixedPaths() {
        super();
    }
}
