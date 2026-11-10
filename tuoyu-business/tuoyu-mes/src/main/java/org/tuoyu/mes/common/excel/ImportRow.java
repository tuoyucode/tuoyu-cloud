package org.tuoyu.mes.common.excel;

/**
 * 导入文件中的一行数据
 *
 * @param rowNumber 在 Excel 中的行号（表头为第 1 行，数据从第 2 行开始），用于错误提示
 * @param data      该行数据
 * @param <T>       行的类型
 * @author tuoyu
 */
public record ImportRow<T>(int rowNumber, T data) {
}
