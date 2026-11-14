package org.tuoyu.common.core.validate.enums;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.BooleanUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.core.utils.reflect.ReflectUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * {@link EnumPattern} 的校验器。
 * <p>
 * 初始化时读取枚举每个常量的属性值（取值为 null 的跳过），第一个非 null 取值的类作为比较时的值类型；
 * 校验时把输入转换为该类型后在取值中查找。初始化后只读，线程安全。
 *
 * @author tuoyu
 */
public class EnumPatternValidator implements ConstraintValidator<EnumPattern, Object> {

    private Set<Object> allowedValues = Set.of();

    /**
     * 比较时的值类型；没有收集到任何取值时为 null
     */
    private Class<?> valueType;

    @Override
    public void initialize(EnumPattern annotation) {
        Set<Object> values = new HashSet<>();
        Class<?> type = null;
        String fieldName = annotation.fieldName();
        if (StringUtils.isNotBlank(fieldName)) {
            for (Enum<?> constant : annotation.type().getEnumConstants()) {
                Object value = ReflectUtils.invokeGetter(constant, fieldName);
                if (value == null) {
                    continue;
                }
                if (type == null) {
                    type = value.getClass();
                }
                values.add(value);
            }
        }
        this.allowedValues = values;
        this.valueType = type;
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (valueType == null) {
            return false;
        }
        if (value instanceof CharSequence text) {
            if (StringUtils.isBlank(text)) {
                // 字符串属性的空白按未填写处理；其他类型的属性不接受空白
                return CharSequence.class.isAssignableFrom(valueType);
            }
            if (valueType == Boolean.class && BooleanUtil.toBooleanObject(text.toString()) == null) {
                // 转换为布尔值时不认识的文本会得到 false，先排除
                return false;
            }
        }
        Object converted = Convert.convertWithCheck(valueType, value, null, true);
        return converted != null && allowedValues.contains(converted);
    }

}
