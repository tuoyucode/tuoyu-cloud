package org.tuoyu.common.excel.core;

import org.tuoyu.common.core.exception.ServiceException;

import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Excel 导出 / 导入的行数上限（见 docs/adr/0004-数据库迁移.md 第 2 节）。
 * <p>
 * 超过上限时拒绝并提示，不截断数据：
 * <ul>
 *     <li>导出：先统计行数，超限时不执行查询，避免一次查出整张表；</li>
 *     <li>导入：先预读统计行数，超限时一行都不处理，避免只导入了一部分。</li>
 * </ul>
 * 上限通过 tuoyu.excel.max-export-rows / max-import-rows 配置，Nacos 刷新后生效；
 * 未在 Spring 容器中运行时（例如单元测试）使用默认值。
 */
public final class ExcelRowLimits {

    /**
     * 导出行数上限的默认值
     */
    public static final int DEFAULT_MAX_EXPORT_ROWS = 50_000;

    /**
     * 导入行数上限的默认值
     */
    public static final int DEFAULT_MAX_IMPORT_ROWS = 5_000;

    /**
     * 上限的可配置最大值：单个工作表最多 1,048,576 行，且整份数据会在内存中处理
     */
    public static final int MAX_CONFIGURABLE_ROWS = 1_000_000;

    private static volatile int maxExportRows = DEFAULT_MAX_EXPORT_ROWS;

    private static volatile int maxImportRows = DEFAULT_MAX_IMPORT_ROWS;

    private ExcelRowLimits() {
    }

    /**
     * 设置上限，启动或 Nacos 刷新配置时由 {@code ExcelLimitProperties} 调用。
     *
     * @param exportRows 导出行数上限
     * @param importRows 导入行数上限
     */
    public static void configure(int exportRows, int importRows) {
        validate("tuoyu.excel.max-export-rows", exportRows);
        validate("tuoyu.excel.max-import-rows", importRows);
        maxExportRows = exportRows;
        maxImportRows = importRows;
    }

    /**
     * 校验上限取值：1 ~ {@value #MAX_CONFIGURABLE_ROWS}。
     *
     * @param name 配置项名
     * @param rows 取值
     */
    public static void validate(String name, int rows) {
        if (rows < 1 || rows > MAX_CONFIGURABLE_ROWS) {
            throw new IllegalArgumentException(name + " 应在 1 ~ " + MAX_CONFIGURABLE_ROWS + " 之间：" + rows);
        }
    }

    public static int maxExportRows() {
        return maxExportRows;
    }

    public static int maxImportRows() {
        return maxImportRows;
    }

    /**
     * 校验导出行数，超过上限时抛出业务异常。
     *
     * @param rows 导出行数
     */
    public static void checkExportRows(long rows) {
        int max = maxExportRows;
        if (rows > max) {
            throw exportExceeded(max);
        }
    }

    /**
     * 先统计行数再查询：超过上限时直接拒绝，不执行查询。
     * <p>
     * 统计与查询之间有新数据写入时，查询结果可能略多于统计值，查询后再按实际行数校验一次。
     *
     * @param counter 统计行数（与 loader 使用同一查询条件，例如 {@code mapper.selectPage(PageParam.countOnly(), wrapper).getTotal()}）
     * @param loader  查询全部数据
     * @return 查询结果
     */
    public static <T> List<T> query(LongSupplier counter, Supplier<List<T>> loader) {
        checkExportRows(counter.getAsLong());
        List<T> rows = loader.get();
        if (rows != null) {
            checkExportRows(rows.size());
        }
        return rows;
    }

    /**
     * 导出超限的提示。
     *
     * @param max 上限
     * @return 业务异常
     */
    public static ServiceException exportExceeded(int max) {
        return new ServiceException("导出数据超过 " + max + " 行，请缩小查询范围后重试");
    }

    /**
     * 导入超限的提示。
     *
     * @param max 上限
     * @return 业务异常
     */
    public static ServiceException importExceeded(int max) {
        return new ServiceException("导入数据超过 " + max + " 行，请拆分为多个文件后重试");
    }

}
