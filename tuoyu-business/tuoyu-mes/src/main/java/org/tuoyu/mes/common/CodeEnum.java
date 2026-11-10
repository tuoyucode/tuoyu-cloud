package org.tuoyu.mes.common;

import org.tuoyu.common.core.exception.ServiceException;

import java.util.Arrays;
import java.util.Optional;

/**
 * 带编码与文案的枚举：状态、类型等在数据库与接口中保存编码，界面按编码显示文案。
 * <p>
 * 核心流程的状态（工单、工序任务、报工、检验、安灯等）是产品规则，用枚举而不是字典，避免被改成任意值破坏流转规则。
 *
 * @author tuoyu
 */
public interface CodeEnum {

    /**
     * 编码（保存到数据库，接口中传递）
     */
    String code();

    /**
     * 中文文案（用于提示信息）
     */
    String label();

    /**
     * 按编码查找
     *
     * @param type 枚举类型
     * @param code 编码
     * @return 没有对应的值时为空
     */
    static <E extends Enum<E> & CodeEnum> Optional<E> find(Class<E> type, String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(type.getEnumConstants()).filter(e -> e.code().equals(code)).findFirst();
    }

    /**
     * 按编码查找，不存在时抛出带原因的业务异常
     *
     * @param type  枚举类型
     * @param code  编码
     * @param field 字段名称（用于提示，例如“物料类型”）
     * @return 枚举值
     */
    static <E extends Enum<E> & CodeEnum> E of(Class<E> type, String code, String field) {
        return find(type, code).orElseThrow(() -> new ServiceException(field + "不正确：" + code));
    }

    /**
     * 编码是否有效（为空视为有效，由必填校验处理）
     */
    static <E extends Enum<E> & CodeEnum> boolean validOrEmpty(Class<E> type, String code) {
        return code == null || code.isEmpty() || find(type, code).isPresent();
    }

    /**
     * 是否为给定值之一
     */
    @SafeVarargs
    static <E extends Enum<E> & CodeEnum> boolean in(String code, E... values) {
        for (E value : values) {
            if (value.code().equals(code)) {
                return true;
            }
        }
        return false;
    }

}
