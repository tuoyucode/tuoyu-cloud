package org.tuoyu.common.mybatis.utils;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.tuoyu.common.core.utils.SpringUtils;

/**
 * 生成数字主键：使用容器中的 MyBatis-Plus 主键生成器，与插入数据时自动生成主键的是同一个，
 * 所以手工生成的主键与自动生成的主键不会冲突。
 *
 * @author tuoyu
 */
public final class IdGeneratorUtil {

    private IdGeneratorUtil() {
    }

    /**
     * 生成一个主键。每次调用时从容器取生成器，不在类加载时访问容器
     *
     * @return 主键
     */
    public static Long nextLongId() {
        return SpringUtils.getBean(IdentifierGenerator.class).nextId(null).longValue();
    }

}
