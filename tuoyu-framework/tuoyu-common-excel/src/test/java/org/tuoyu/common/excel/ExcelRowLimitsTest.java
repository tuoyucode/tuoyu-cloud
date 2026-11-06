package org.tuoyu.common.excel;

import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.event.AnalysisEventListener;
import org.apache.fesod.sheet.exception.ExcelDataConvertException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.excel.config.ExcelLimitProperties;
import org.tuoyu.common.excel.core.DefaultExcelResult;
import org.tuoyu.common.excel.core.ExcelListener;
import org.tuoyu.common.excel.core.ExcelResult;
import org.tuoyu.common.excel.core.ExcelRowLimits;
import org.tuoyu.common.excel.utils.ExcelBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Excel 导出 / 导入行数上限")
class ExcelRowLimitsTest {

    @AfterEach
    void restoreDefaults() {
        ExcelRowLimits.configure(ExcelRowLimits.DEFAULT_MAX_EXPORT_ROWS, ExcelRowLimits.DEFAULT_MAX_IMPORT_ROWS);
    }

    @Test
    @DisplayName("默认上限：导出 50000 行，导入 5000 行")
    void shouldUseDefaults() {
        ExcelLimitProperties properties = new ExcelLimitProperties();
        properties.afterPropertiesSet();

        assertEquals(50_000, ExcelRowLimits.maxExportRows());
        assertEquals(5_000, ExcelRowLimits.maxImportRows());
    }

    @Test
    @DisplayName("上限取值不合理时拒绝启动")
    void shouldRejectInvalidLimits() {
        for (int invalid : new int[]{0, -1, ExcelRowLimits.MAX_CONFIGURABLE_ROWS + 1}) {
            ExcelLimitProperties properties = new ExcelLimitProperties();
            properties.setMaxExportRows(invalid);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, properties::afterPropertiesSet);
            assertTrue(e.getMessage().contains("tuoyu.excel.max-export-rows"));
        }
        ExcelLimitProperties properties = new ExcelLimitProperties();
        properties.setMaxImportRows(0);
        assertThrows(IllegalArgumentException.class, properties::afterPropertiesSet);
        assertEquals(ExcelRowLimits.DEFAULT_MAX_IMPORT_ROWS, ExcelRowLimits.maxImportRows(), "校验失败时不改变已生效的上限");
    }

    @Test
    @DisplayName("导出超过上限：先统计行数，不执行查询，提示缩小查询范围")
    void shouldRejectExportBeforeQuery() {
        AtomicBoolean queried = new AtomicBoolean();

        ServiceException e = assertThrows(ServiceException.class, () -> ExcelBuilder.query(() -> 50_001L, () -> {
            queried.set(true);
            return List.<Row>of();
        }, Row.class));

        assertEquals("导出数据超过 50000 行，请缩小查询范围后重试", e.getMessage());
        assertFalse(queried.get(), "超限时不应执行查询");
    }

    @Test
    @DisplayName("导出等于上限时正常导出")
    void shouldExportAtLimit() throws Exception {
        ExcelRowLimits.configure(3, 3);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ExcelBuilder.query(() -> 3L, () -> rows(3), Row.class).toStream(out);

        List<Row> read = ExcelBuilder.read(new ByteArrayInputStream(out.toByteArray()), Row.class).doReadSync();
        assertEquals(3, read.size());
    }

    @Test
    @DisplayName("统计后数据增多：按查询结果再校验一次")
    void shouldRecheckAfterQuery() {
        ExcelRowLimits.configure(3, 3);

        ServiceException e = assertThrows(ServiceException.class, () -> ExcelBuilder.query(() -> 3L, () -> rows(4), Row.class));

        assertEquals("导出数据超过 3 行，请缩小查询范围后重试", e.getMessage());
    }

    @Test
    @DisplayName("兜底：直接传入超限数据时写出前拒绝，不输出文件内容")
    void shouldRejectOversizedDataBeforeWriting() {
        ExcelRowLimits.configure(3, 3);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThrows(ServiceException.class, () -> ExcelBuilder.of(rows(4), Row.class).toStream(out));

        assertEquals(0, out.size());
    }

    @Test
    @DisplayName("模板导出同样受上限约束")
    void shouldLimitTemplateExport() {
        ExcelRowLimits.configure(3, 3);

        assertThrows(ServiceException.class, () -> ExcelBuilder.template("excel/not-used.xlsx").data(rows(4))
            .toStream(new ByteArrayOutputStream()));
        assertThrows(ServiceException.class, () -> ExcelBuilder.template("excel/not-used.xlsx")
            .multiList(Map.of("a", rows(2), "b", rows(2), "title", "标题"))
            .toStream(new ByteArrayOutputStream()));
    }

    @Test
    @DisplayName("导入超过上限（可重复打开的文件）：预读统计，一行都不处理")
    void shouldRejectImportBeforeProcessingAnyRow() {
        ExcelRowLimits.configure(10, 3);
        CountingListener listener = new CountingListener();

        ServiceException e = assertThrows(ServiceException.class,
            () -> ExcelBuilder.read(new ByteArrayResource(workbook(4)), Row.class).listener(listener).doRead());

        assertEquals("导入数据超过 3 行，请拆分为多个文件后重试", e.getMessage());
        assertEquals(0, listener.processed, "超限时不应处理任何一行");
    }

    @Test
    @DisplayName("导入等于上限时正常导入")
    void shouldImportAtLimit() {
        ExcelRowLimits.configure(10, 3);
        CountingListener listener = new CountingListener();

        ExcelBuilder.read(new ByteArrayResource(workbook(3)), Row.class).listener(listener).doRead();

        assertEquals(3, listener.processed);
        assertEquals(3, ExcelBuilder.read(new ByteArrayResource(workbook(3)), Row.class).doReadSync().size());
        assertEquals(3, ExcelBuilder.read(new ByteArrayResource(workbook(3)), Row.class).doReadAllSync().size());
    }

    @Test
    @DisplayName("导入超过默认上限 5000 行")
    void shouldRejectImportOverDefaultLimit() {
        byte[] bytes = workbook(5_001);

        ServiceException e = assertThrows(ServiceException.class,
            () -> ExcelBuilder.read(new ByteArrayResource(bytes), Row.class).doRead());

        assertEquals("导入数据超过 5000 行，请拆分为多个文件后重试", e.getMessage());
        assertEquals(5_000, ExcelBuilder.read(new ByteArrayResource(workbook(5_000)), Row.class).doReadSync().size());
    }

    @Test
    @DisplayName("导入（只能读一遍的文件流）：读到超出的那一行即终止")
    void shouldStopStreamImportAtLimit() {
        ExcelRowLimits.configure(10, 3);
        CountingListener listener = new CountingListener();

        ServiceException e = assertThrows(ServiceException.class,
            () -> ExcelBuilder.read(new ByteArrayInputStream(workbook(5)), Row.class).listener(listener).doRead());

        assertEquals("导入数据超过 3 行，请拆分为多个文件后重试", e.getMessage());
        assertEquals(3, listener.processed);
        assertThrows(ServiceException.class,
            () -> ExcelBuilder.read(new ByteArrayInputStream(workbook(5)), Row.class).doReadSync());
    }

    @Test
    @DisplayName("单元格转换失败的行同样计入行数，且不影响原有的错误收集")
    void shouldCountRowsThatFailConversion() {
        ExcelRowLimits.configure(10, 3);
        List<Object> mixed = new ArrayList<>();
        mixed.add(new TextRow("a", "1"));
        mixed.add(new TextRow("b", "not-a-number"));
        mixed.add(new TextRow("c", "oops"));
        mixed.add(new TextRow("d", "4"));
        byte[] bytes = textWorkbook(mixed);

        assertThrows(ServiceException.class,
            () -> ExcelBuilder.read(new ByteArrayResource(bytes), Row.class).listener(new CountingListener()).doRead());

        CountingListener listener = new CountingListener();
        ExcelBuilder.read(new ByteArrayResource(bytes), Row.class).maxRows(4).listener(listener).doRead();
        assertEquals(2, listener.processed);
        assertEquals(2, listener.conversionErrors, "转换失败的行仍交给业务监听器处理");
    }

    @Test
    @DisplayName("单次导入可单独指定上限")
    void shouldAllowPerImportLimit() {
        ServiceException e = assertThrows(ServiceException.class,
            () -> ExcelBuilder.read(new ByteArrayResource(workbook(3)), Row.class).maxRows(2).doReadSync());

        assertEquals("导入数据超过 2 行，请拆分为多个文件后重试", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ExcelBuilder.read(new ByteArrayResource(workbook(1)), Row.class).maxRows(0));
    }

    private static List<Row> rows(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> new Row("名称" + i, i)).toList();
    }

    private static byte[] workbook(int rows) {
        int saved = ExcelRowLimits.maxExportRows();
        ExcelRowLimits.configure(Math.max(saved, rows), ExcelRowLimits.maxImportRows());
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ExcelBuilder.of(rows(rows), Row.class).toStream(out);
            return out.toByteArray();
        } finally {
            ExcelRowLimits.configure(saved, ExcelRowLimits.maxImportRows());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static byte[] textWorkbook(List<Object> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelBuilder.of((List) rows, (Class) TextRow.class).toStream(out);
        return out.toByteArray();
    }

    /**
     * 记录实际处理行数的导入监听器
     */
    private static final class CountingListener extends AnalysisEventListener<Row> implements ExcelListener<Row> {

        private int processed;

        private int conversionErrors;

        @Override
        public void invoke(Row data, AnalysisContext context) {
            processed++;
        }

        @Override
        public void onException(Exception exception, AnalysisContext context) throws Exception {
            if (!(exception instanceof ExcelDataConvertException)) {
                throw exception;
            }
            conversionErrors++;
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
        }

        @Override
        public ExcelResult<Row> getExcelResult() {
            return new DefaultExcelResult<>();
        }
    }

    public static class Row {

        @ExcelProperty("名称")
        private String name;

        @ExcelProperty("数量")
        private Integer amount;

        public Row() {
        }

        public Row(String name, Integer amount) {
            this.name = name;
            this.amount = amount;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getAmount() {
            return amount;
        }

        public void setAmount(Integer amount) {
            this.amount = amount;
        }
    }

    /**
     * 与 Row 表头相同、但“数量”列为文本，用于构造转换失败的行
     */
    public static class TextRow {

        @ExcelProperty("名称")
        private String name;

        @ExcelProperty("数量")
        private String amount;

        public TextRow() {
        }

        public TextRow(String name, String amount) {
            this.name = name;
            this.amount = amount;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAmount() {
            return amount;
        }

        public void setAmount(String amount) {
            this.amount = amount;
        }
    }

}
