package org.tuoyu.common.excel.core;

import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.exception.ExcelDataConvertException;
import org.apache.fesod.sheet.read.listener.ReadListener;

/**
 * 导入行数计数器：注册在其他监听器之前，读到第 {@code maxRows + 1} 行时终止读取。
 * <p>
 * 单元格转换失败的行同样计数（不会触发 invoke）。其他异常不处理，交给后面的监听器，
 * 不改变原有的“快速失败 / 收集错误”行为。
 */
public final class RowLimitReadListener<T> implements ReadListener<T> {

    private final int maxRows;

    private int rows;

    public RowLimitReadListener(int maxRows) {
        this.maxRows = maxRows;
    }

    @Override
    public void invoke(T data, AnalysisContext context) {
        count();
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) throws Exception {
        if (exception instanceof RowLimitExceededException) {
            throw exception;
        }
        if (exception instanceof ExcelDataConvertException) {
            count();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 无需处理
    }

    /**
     * 已读取的数据行数
     */
    public int rows() {
        return rows;
    }

    private void count() {
        rows++;
        if (rows > maxRows) {
            throw new RowLimitExceededException(maxRows);
        }
    }

    /**
     * 行数超过上限。只在读取过程中使用，由 ExcelBuilder 转换为面向用户的业务异常
     */
    public static final class RowLimitExceededException extends RuntimeException {

        private final int maxRows;

        public RowLimitExceededException(int maxRows) {
            super("导入数据超过 " + maxRows + " 行", null, false, false);
            this.maxRows = maxRows;
        }

        public int maxRows() {
            return maxRows;
        }

        /**
         * 在异常链中查找行数超限异常（读取框架可能会包装一层）。
         *
         * @param throwable 异常
         * @return 找到时返回，否则为 null
         */
        public static RowLimitExceededException find(Throwable throwable) {
            Throwable current = throwable;
            for (int depth = 0; current != null && depth < 16; depth++) {
                if (current instanceof RowLimitExceededException exceeded) {
                    return exceeded;
                }
                current = current.getCause();
            }
            return null;
        }
    }

}
