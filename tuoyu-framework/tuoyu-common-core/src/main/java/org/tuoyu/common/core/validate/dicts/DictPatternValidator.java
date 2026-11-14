package org.tuoyu.common.core.validate.dicts;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.ObjectProvider;
import org.tuoyu.common.core.service.DictService;
import org.tuoyu.common.core.utils.StringUtils;

/**
 * {@link DictPattern} 的校验器。
 * <p>
 * 由 Spring 的校验器（{@code LocalValidatorFactoryBean}）经容器创建并注入字典服务。字典服务可能经跨服务调用提供，
 * 所以每次校验时才取：容器中没有字典服务时抛出异常，不当作校验通过。
 * 初始化后只读，线程安全。
 *
 * @author tuoyu
 */
public class DictPatternValidator implements ConstraintValidator<DictPattern, String> {

    private final ObjectProvider<DictService> dictService;

    private String dictType;

    private String separator;

    /**
     * @param dictService 字典服务，校验时才取
     */
    public DictPatternValidator(ObjectProvider<DictService> dictService) {
        this.dictService = dictService;
    }

    @Override
    public void initialize(DictPattern annotation) {
        this.dictType = annotation.dictType();
        this.separator = StringUtils.isBlank(annotation.separator()) ? StringUtils.SEPARATOR : annotation.separator();
    }

    /**
     * 把整个值连同分隔符交给字典服务：多个值的拆分与“全部存在”的判断由字典服务完成。
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (StringUtils.isBlank(value)) {
            return true;
        }
        if (StringUtils.isBlank(dictType)) {
            return false;
        }
        DictService service = dictService.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("容器中没有 DictService，无法校验字典值（字典类型 " + dictType + "）");
        }
        return StringUtils.isNotBlank(service.getDictLabel(dictType, value, separator));
    }

}
