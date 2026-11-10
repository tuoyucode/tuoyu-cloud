package org.tuoyu.mes.common.excel;

import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.mes.common.CodeEnum;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 按行解析导入的单元格：必填、长度、数字与名称转换。解析失败时把“第几行：哪个字段、原因”记入 {@link ImportErrors}，
 * 返回 null（调用方在整行校验完后用 {@link #ok()} 判断该行是否可用）
 *
 * @author tuoyu
 */
public final class ImportFields {

    /**
     * 状态列：正常
     */
    public static final String STATUS_NORMAL = "正常";

    /**
     * 状态列：停用
     */
    public static final String STATUS_DISABLED = "停用";

    public static final String YES = "是";

    public static final String NO = "否";

    private final ImportErrors errors;

    private final int rowNumber;

    ImportFields(ImportErrors errors, int rowNumber) {
        this.errors = errors;
        this.rowNumber = rowNumber;
    }

    public int rowNumber() {
        return rowNumber;
    }

    /**
     * 该行到目前为止没有错误
     */
    public boolean ok() {
        return !errors.hasError(rowNumber);
    }

    /**
     * 记录该行的错误
     */
    public void error(String message) {
        errors.add(rowNumber, message);
    }

    /**
     * 文本：去掉首尾空白，空白视为未填写
     *
     * @param field     字段名称（与模板表头一致）
     * @param value     单元格内容
     * @param maxLength 最大长度
     * @param required  是否必填
     * @return 文本；未填写或不合格时为 null
     */
    public String text(String field, String value, int maxLength, boolean required) {
        String text = blankToNull(value);
        if (text == null) {
            if (required) {
                error(field + "不能为空");
            }
            return null;
        }
        if (text.length() > maxLength) {
            error(field + "不能超过 " + maxLength + " 个字符");
            return null;
        }
        return text;
    }

    /**
     * 数字
     *
     * @return 数字；未填写或不是数字时为 null
     */
    public BigDecimal decimal(String field, String value, boolean required) {
        String text = blankToNull(value);
        if (text == null) {
            if (required) {
                error(field + "不能为空");
            }
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            error(field + "“" + text + "”不是数字");
            return null;
        }
    }

    /**
     * 整数（选填）
     *
     * @return 整数；未填写或不是整数时为 null
     */
    public Integer integer(String field, String value, boolean required) {
        BigDecimal number = decimal(field, value, required);
        if (number == null) {
            return null;
        }
        try {
            return number.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException e) {
            error(field + "“" + blankToNull(value) + "”不是整数");
            return null;
        }
    }

    /**
     * 按名称（模板中填写的中文名称）取枚举的编码，也接受编码本身
     *
     * @param field        字段名称
     * @param value        单元格内容
     * @param type         枚举类型
     * @param defaultValue 未填写时的取值；为 null 表示必填
     * @return 编码；未填写且必填、或名称不正确时为 null
     */
    public <E extends Enum<E> & CodeEnum> String label(String field, String value, Class<E> type, E defaultValue) {
        String text = blankToNull(value);
        if (text == null) {
            if (defaultValue == null) {
                error(field + "不能为空");
                return null;
            }
            return defaultValue.code();
        }
        Optional<E> found = Arrays.stream(type.getEnumConstants())
            .filter(e -> e.label().equals(text) || e.code().equals(text))
            .findFirst();
        if (found.isEmpty()) {
            String expected = Arrays.stream(type.getEnumConstants()).map(CodeEnum::label)
                .collect(Collectors.joining("、"));
            error(field + "“" + text + "”不正确，应为：" + expected);
            return null;
        }
        return found.get().code();
    }

    /**
     * 状态：正常或停用，未填写时为正常
     *
     * @return 0 正常、1 停用；不正确时为 null
     */
    public String status(String field, String value) {
        String text = blankToNull(value);
        if (text == null || STATUS_NORMAL.equals(text)) {
            return SystemConstants.NORMAL;
        }
        if (STATUS_DISABLED.equals(text)) {
            return SystemConstants.DISABLE;
        }
        error(field + "“" + text + "”不正确，应为：" + STATUS_NORMAL + "、" + STATUS_DISABLED);
        return null;
    }

    /**
     * 是或否，未填写时为否
     *
     * @return Y 是、N 否；不正确时为 null
     */
    public String yesNo(String field, String value) {
        String text = blankToNull(value);
        if (text == null || NO.equals(text)) {
            return "N";
        }
        if (YES.equals(text)) {
            return "Y";
        }
        error(field + "“" + text + "”不正确，应为：" + YES + "、" + NO);
        return null;
    }

    /**
     * 去掉首尾空白，空白时为 null
     */
    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.strip();
        return text.isEmpty() ? null : text;
    }

}
