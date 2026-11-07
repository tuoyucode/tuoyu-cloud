package org.tuoyu.common.core.validate;

/**
 * 校验分组：修改。用法 {@code @Validated(OnUpdate.class)}，字段上 {@code @NotNull(groups = OnUpdate.class)}。
 * <p>
 * 不继承 {@link jakarta.validation.groups.Default}：按本分组校验时，没有声明分组的约束不参与校验。
 *
 * @author tuoyu
 */
public interface OnUpdate {
}
