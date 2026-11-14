package org.tuoyu.common.core.validate.dicts;

import cn.hutool.extra.spring.SpringUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.tuoyu.common.core.service.DictService;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 通过 Spring 的校验器（LocalValidatorFactoryBean）校验：校验器由容器创建，字典服务从同一个容器取得。
 * <p>
 * 测试同时把 hutool 的静态容器指向被测容器，结束时复原，使从容器注入与经静态容器读取两种取法都能工作。
 */
@DisplayName("DictPattern：值必须是字典中的值")
class DictPatternValidatorTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(LocalValidatorFactoryBean.class);

    private ApplicationContext previous;

    @BeforeEach
    void savePrevious() {
        previous = SpringUtil.getApplicationContext();
    }

    @AfterEach
    void restore() {
        new SpringUtil().setApplicationContext(previous);
    }

    private interface Check {

        void accept(Validator validator, RecordingDictService dict);
    }

    private void withDictService(Check check) {
        RecordingDictService dict = new RecordingDictService();
        runner.withBean(DictService.class, () -> dict).run(context -> {
            new SpringUtil().setApplicationContext(context.getSourceApplicationContext());
            check.accept(context.getBean(Validator.class), dict);
        });
    }

    private static Set<String> messages(Validator validator, String property, Object value) {
        Set<ConstraintViolation<Form>> violations = validator.validateValue(Form.class, property, value);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("字典中的值通过，不在字典中的值不通过并给出默认提示")
    void singleValue() {
        withDictService((validator, dict) -> {
            assertThat(messages(validator, "status", "0")).isEmpty();
            assertThat(messages(validator, "status", "9")).containsExactly("字典值无效");
            assertThat(dict.calls).contains("sys_status|0|,", "sys_status|9|,");
        });
    }

    @Test
    @DisplayName("null 与空白不校验，也不访问字典服务")
    void blank() {
        withDictService((validator, dict) -> {
            assertThat(messages(validator, "status", null)).isEmpty();
            assertThat(messages(validator, "status", "")).isEmpty();
            assertThat(messages(validator, "status", "  ")).isEmpty();
            assertThat(dict.calls).isEmpty();
        });
    }

    @Test
    @DisplayName("多个值：整个值连同注解的分隔符交给字典服务；分隔符为空时用逗号")
    void separators() {
        withDictService((validator, dict) -> {
            assertThat(messages(validator, "status", "0,1")).isEmpty();
            assertThat(messages(validator, "statuses", "0|1")).isEmpty();
            assertThat(messages(validator, "statuses", "0,1")).containsExactly("字典值无效");
            assertThat(messages(validator, "blankSeparator", "0,1")).isEmpty();
            assertThat(dict.calls).contains("sys_status|0,1|,", "sys_status|0|1||", "sys_status|0,1||");
        });
    }

    @Test
    @DisplayName("没有字典类型时一律不通过；字典服务返回 null 时不通过；可以覆盖提示")
    void otherFailures() {
        withDictService((validator, dict) -> {
            assertThat(messages(validator, "noType", "0")).containsExactly("字典值无效");
            assertThat(dict.calls).isEmpty();
            assertThat(messages(validator, "unknownType", "0")).containsExactly("字典值无效");
            assertThat(messages(validator, "custom", "9")).containsExactly("状态不正确");
            assertThat(messages(validator, "custom", "1")).isEmpty();
        });
    }

    @Test
    @DisplayName("容器中没有 DictService 时校验抛出异常，不能当作通过")
    void missingDictService() {
        runner.run(context -> {
            new SpringUtil().setApplicationContext(context.getSourceApplicationContext());
            Validator validator = context.getBean(Validator.class);
            assertThatThrownBy(() -> validator.validateValue(Form.class, "status", "0"))
                .isInstanceOf(ValidationException.class);
        });
    }

    @Test
    @DisplayName("注解：目标为字段与参数，运行时保留，默认分隔符逗号")
    void annotation() throws NoSuchMethodException {
        assertThat(DictPattern.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(DictPattern.class.getAnnotation(Target.class).value())
            .containsExactlyInAnyOrder(ElementType.FIELD, ElementType.PARAMETER);
        assertThat(DictPattern.class.getMethod("separator").getDefaultValue()).isEqualTo(",");
        assertThat(DictPattern.class.getMethod("message").getDefaultValue()).isEqualTo("字典值无效");
        assertThat(DictPattern.class.getMethod("dictType").getDefaultValue()).isNull();
    }

    static class Form {

        @DictPattern(dictType = "sys_status")
        String status;

        @DictPattern(dictType = "sys_status", separator = "|")
        String statuses;

        @DictPattern(dictType = "sys_status", separator = "")
        String blankSeparator;

        @DictPattern(dictType = " ")
        String noType;

        @DictPattern(dictType = "unknown_type")
        String unknownType;

        @DictPattern(dictType = "sys_status", message = "状态不正确")
        String custom;
    }

    /**
     * 只有 sys_status 一种字典（0 正常、1 停用）；多个值全部存在时返回对应的标签，否则返回空串；
     * 其他字典类型返回 null。记录每次调用的参数（类型|值|分隔符）。
     */
    static final class RecordingDictService implements DictService {

        private static final Map<String, String> STATUS = Map.of("0", "正常", "1", "停用");

        final List<String> calls = new ArrayList<>();

        @Override
        public String getDictLabel(String dictType, String dictValue, String separator) {
            calls.add(dictType + "|" + dictValue + "|" + separator);
            if (!"sys_status".equals(dictType)) {
                return null;
            }
            List<String> labels = new ArrayList<>();
            for (String value : dictValue.split(Pattern.quote(separator))) {
                String label = STATUS.get(value);
                if (label == null) {
                    return "";
                }
                labels.add(label);
            }
            return String.join(separator, labels);
        }

        @Override
        public String getDictValue(String dictType, String dictLabel, String separator) {
            return null;
        }

        @Override
        public Map<String, String> getAllDictByDictType(String dictType) {
            return Map.of();
        }
    }
}
