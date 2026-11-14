package org.tuoyu.common.core.xss;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * {@link Xss} 的校验器：出现 {@code <} 之后、下一个 {@code <} 之前又出现 {@code >} 的片段时不通过。
 * <p>
 * 这样的片段可能被浏览器当作标签解析；单独出现的 {@code <} 或 {@code >}（例如 {@code 1 < 2}）通过。
 *
 * @author tuoyu
 */
public class XssValidator implements ConstraintValidator<Xss, String> {

    private static final Pattern TAG = Pattern.compile("<[^<>]*>");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (StringUtils.isBlank(value)) {
            return true;
        }
        return !TAG.matcher(value).find();
    }

}
