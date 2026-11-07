package org.tuoyu.workflow.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.validation.annotation.Validated;
import org.tuoyu.workflow.domain.bo.FlowCancelBo;
import org.tuoyu.workflow.domain.bo.FlowInvalidBo;
import org.tuoyu.workflow.domain.bo.FlowTerminationBo;
import org.tuoyu.workflow.domain.bo.FlowUrgeTaskBo;
import org.tuoyu.workflow.domain.bo.FlowVariableBo;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求体的约束按接口上 {@code @Validated} 声明的分组执行（与 Spring MVC 相同）：
 * 接口没有 {@code @Validated}，或约束放在接口没有指定的分组中，约束都不会执行。
 */
@DisplayName("工作流接口：请求体的必填约束实际执行")
class WorkflowRequestValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    static Stream<Arguments> endpoints() {
        return Stream.of(
            Arguments.of(FlwInstanceController.class, "cancelProcessApply", FlowCancelBo.class,
                Set.of("业务ID不能为空")),
            Arguments.of(FlwInstanceController.class, "updateVariable", FlowVariableBo.class,
                Set.of("流程实例id为空", "流程变量key为空", "流程变量value为空")),
            Arguments.of(FlwInstanceController.class, "invalid", FlowInvalidBo.class,
                Set.of("流程实例id为空")),
            Arguments.of(FlwTaskController.class, "terminationTask", FlowTerminationBo.class,
                Set.of("任务id为空")),
            Arguments.of(FlwTaskController.class, "urgeTask", FlowUrgeTaskBo.class,
                Set.of("任务id为空", "催办内容为空")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("endpoints")
    @DisplayName("空请求体按接口声明的分组校验，所有必填项都报错")
    void emptyBodyIsRejected(Class<?> controller, String methodName, Class<?> bodyType, Set<String> messages)
        throws ReflectiveOperationException {
        Method method = Arrays.stream(controller.getDeclaredMethods())
            .filter(m -> m.getName().equals(methodName))
            .findFirst().orElseThrow();
        Parameter body = Arrays.stream(method.getParameters())
            .filter(p -> p.getType() == bodyType)
            .findFirst().orElseThrow();
        Validated validated = body.getAnnotation(Validated.class);
        assertThat(validated).as(methodName + " 的请求体没有 @Validated").isNotNull();

        Object empty = bodyType.getDeclaredConstructor().newInstance();
        Set<String> actual = VALIDATOR.validate(empty, validated.value()).stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.toSet());

        assertThat(actual).containsExactlyInAnyOrderElementsOf(messages);
    }

    @Test
    @DisplayName("催办的任务列表为空时同样拒绝")
    void emptyTaskListIsRejected() {
        FlowUrgeTaskBo bo = new FlowUrgeTaskBo();
        bo.setTaskIdList(List.of());
        bo.setMessage("请尽快处理");
        assertThat(VALIDATOR.validate(bo)).extracting(ConstraintViolation::getMessage).containsExactly("任务id为空");
    }

}
