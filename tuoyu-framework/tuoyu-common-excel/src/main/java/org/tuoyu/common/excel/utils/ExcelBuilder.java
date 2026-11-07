package org.tuoyu.common.excel.utils;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ZipUtil;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.converters.Converter;
import org.apache.fesod.sheet.read.builder.ExcelReaderBuilder;
import org.apache.fesod.sheet.read.builder.ExcelReaderSheetBuilder;
import org.apache.fesod.sheet.write.builder.ExcelWriterBuilder;
import org.apache.fesod.sheet.write.builder.ExcelWriterSheetBuilder;
import org.apache.fesod.sheet.write.handler.WriteHandler;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.apache.fesod.sheet.write.metadata.fill.FillConfig;
import org.apache.fesod.sheet.write.metadata.fill.FillWrapper;
import org.apache.fesod.sheet.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.apache.fesod.sheet.write.style.column.SimpleColumnWidthStyleStrategy;
import org.apache.fesod.sheet.write.style.row.SimpleRowHeightStyleStrategy;
import org.springframework.core.io.InputStreamSource;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.core.utils.file.FileUtils;
import org.tuoyu.common.excel.convert.ExcelBigNumberConvert;
import org.tuoyu.common.excel.core.*;
import org.tuoyu.common.excel.handler.DataWriteHandler;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Excel 导出构造器。
 *
 * @author Lion Li
 */
public final class ExcelBuilder<T> {

    private static final String DEFAULT_SHEET_NAME = "sheet1";

    private static final int DEFAULT_ZIP_PAGE_SIZE = 999;

    private final List<T> data;

    private final Class<T> headType;

    private String sheetName = DEFAULT_SHEET_NAME;

    private Integer sheetNo;

    private boolean merge;

    private List<DropDownOptions> options;

    private boolean zip;

    private int pageSize = DEFAULT_ZIP_PAGE_SIZE;

    private String password;

    private Boolean needHead;

    private Boolean automaticMergeHead;

    private Collection<String> includeFields;

    private Collection<String> excludeFields;

    private Collection<Integer> includeIndexes;

    private Collection<Integer> excludeIndexes;

    private Boolean orderByIncludeColumn;

    private Integer columnWidth;

    private Short headRowHeight;

    private Short contentRowHeight;

    private List<WriteHandler> writeHandlers;

    private List<Converter<?>> converters;

    private ExcelBuilder(List<T> data, Class<T> headType) {
        this.data = data;
        this.headType = headType;
    }

    /**
     * 创建导出构造器。
     *
     * @param data     导出数据
     * @param headType 表头类型
     * @return 导出构造器
     */
    public static <T> ExcelBuilder<T> of(List<T> data, Class<T> headType) {
        return new ExcelBuilder<>(data, headType);
    }

    /**
     * 创建先统计行数再查询的导出构造器。
     * <p>
     * 行数超过 tuoyu.excel.max-export-rows（默认 50000）时直接拒绝并提示缩小查询范围，不执行查询。
     *
     * @param counter  统计行数，查询条件须与 loader 一致
     * @param loader   查询导出数据
     * @param headType 表头类型
     * @return 导出构造器
     */
    public static <T> ExcelBuilder<T> query(LongSupplier counter, Supplier<List<T>> loader, Class<T> headType) {
        return new ExcelBuilder<>(ExcelRowLimits.query(counter, loader), headType);
    }

    /**
     * 创建自定义写出构造器。
     *
     * @param headType 表头类型
     * @return 导出构造器
     */
    public static <T> ExcelBuilder<T> writer(Class<T> headType) {
        return new ExcelBuilder<>(null, headType);
    }

    /**
     * 创建模板导出构造器。
     *
     * @param templatePath 模板路径
     * @return 模板导出构造器
     */
    public static TemplateBuilder template(String templatePath) {
        return new TemplateBuilder(templatePath);
    }

    /**
     * 创建导入读取构造器。
     * <p>
     * 行数超过 tuoyu.excel.max-import-rows（默认 5000）时，读到超出的那一行即终止并提示。
     * 文件流只能读一遍，无法预先统计：超限前已读到的行可能已被自定义监听器处理。
     * 逐行写库的导入请使用 {@link #read(InputStreamSource, Class)}。
     *
     * @param is    文件流
     * @param clazz 接收实体类
     * @return 导入读取构造器
     */
    public static <T> ReadBuilder<T> read(InputStream is, Class<T> clazz) {
        return new ReadBuilder<>(is, null, clazz);
    }

    /**
     * 创建导入读取构造器（可重复打开的文件，例如 MultipartFile）。
     * <p>
     * 先预读一遍统计行数，超过 tuoyu.excel.max-import-rows（默认 5000）时直接拒绝并提示，一行都不处理；
     * 未超限时再正式读取。
     *
     * @param source 文件
     * @param clazz  接收实体类
     * @return 导入读取构造器
     */
    public static <T> ReadBuilder<T> read(InputStreamSource source, Class<T> clazz) {
        return new ReadBuilder<>(null, source, clazz);
    }

    /**
     * 设置工作表名称。
     */
    public ExcelBuilder<T> sheetName(String sheetName) {
        this.sheetName = StringUtils.blankToDefault(sheetName, DEFAULT_SHEET_NAME);
        return this;
    }

    /**
     * 设置工作表编号。
     */
    public ExcelBuilder<T> sheetNo(Integer sheetNo) {
        this.sheetNo = sheetNo;
        return this;
    }

    /**
     * 开启单元格合并。
     */
    public ExcelBuilder<T> merge() {
        return merge(true);
    }

    /**
     * 设置是否合并单元格。
     */
    public ExcelBuilder<T> merge(boolean merge) {
        this.merge = merge;
        return this;
    }

    /**
     * 设置下拉选项。
     */
    public ExcelBuilder<T> options(List<DropDownOptions> options) {
        this.options = options;
        return this;
    }

    /**
     * 设置导出文件密码。
     */
    public ExcelBuilder<T> password(String password) {
        this.password = password;
        return this;
    }

    /**
     * 设置是否写出表头。
     */
    public ExcelBuilder<T> needHead(boolean needHead) {
        this.needHead = needHead;
        return this;
    }

    /**
     * 设置是否自动合并多级表头。
     */
    public ExcelBuilder<T> automaticMergeHead(boolean automaticMergeHead) {
        this.automaticMergeHead = automaticMergeHead;
        return this;
    }

    /**
     * 仅导出指定字段。
     */
    public ExcelBuilder<T> includeFields(Collection<String> includeFields) {
        this.includeFields = includeFields;
        return this;
    }

    /**
     * 排除指定字段。
     */
    public ExcelBuilder<T> excludeFields(Collection<String> excludeFields) {
        this.excludeFields = excludeFields;
        return this;
    }

    /**
     * 仅导出指定列索引。
     */
    public ExcelBuilder<T> includeIndexes(Collection<Integer> includeIndexes) {
        this.includeIndexes = includeIndexes;
        return this;
    }

    /**
     * 排除指定列索引。
     */
    public ExcelBuilder<T> excludeIndexes(Collection<Integer> excludeIndexes) {
        this.excludeIndexes = excludeIndexes;
        return this;
    }

    /**
     * 设置是否按 include 列顺序导出。
     */
    public ExcelBuilder<T> orderByIncludeColumn(boolean orderByIncludeColumn) {
        this.orderByIncludeColumn = orderByIncludeColumn;
        return this;
    }

    /**
     * 设置固定列宽。
     */
    public ExcelBuilder<T> columnWidth(Integer columnWidth) {
        if (columnWidth != null && columnWidth <= 0) {
            throw new IllegalArgumentException("columnWidth 必须大于 0");
        }
        this.columnWidth = columnWidth;
        return this;
    }

    /**
     * 设置固定行高。
     */
    public ExcelBuilder<T> rowHeight(short headRowHeight, short contentRowHeight) {
        if (headRowHeight <= 0 || contentRowHeight <= 0) {
            throw new IllegalArgumentException("rowHeight 必须大于 0");
        }
        this.headRowHeight = headRowHeight;
        this.contentRowHeight = contentRowHeight;
        return this;
    }

    /**
     * 注册自定义写处理器。
     */
    public ExcelBuilder<T> registerWriteHandler(WriteHandler writeHandler) {
        if (writeHandler == null) {
            return this;
        }
        if (writeHandlers == null) {
            writeHandlers = new ArrayList<>();
        }
        writeHandlers.add(writeHandler);
        return this;
    }

    /**
     * 注册自定义转换器。
     */
    public ExcelBuilder<T> registerConverter(Converter<?> converter) {
        if (converter == null) {
            return this;
        }
        if (converters == null) {
            converters = new ArrayList<>();
        }
        converters.add(converter);
        return this;
    }

    /**
     * 开启 ZIP 分页导出。
     */
    public ExcelBuilder<T> zip() {
        return zip(DEFAULT_ZIP_PAGE_SIZE);
    }

    /**
     * 开启 ZIP 分页导出。
     *
     * @param pageSize 每个 Excel 文件的数据量
     */
    public ExcelBuilder<T> zip(int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize 必须大于 0");
        }
        this.zip = true;
        this.pageSize = pageSize;
        return this;
    }

    /**
     * 写入 HTTP 响应。
     */
    public void toResponse(HttpServletResponse response) {
        checkDataRows();
        if (zip) {
            exportZipToResponse(response);
            return;
        }
        try {
            resetResponse(sheetName, response);
            ServletOutputStream os = response.getOutputStream();
            writeSheet(os);
        } catch (IOException e) {
            throw new RuntimeException("导出Excel异常", e);
        }
    }

    /**
     * 写入输出流。
     */
    public void toStream(OutputStream outputStream) {
        if (zip) {
            throw new UnsupportedOperationException("ZIP导出请使用 toResponse(HttpServletResponse)");
        }
        checkDataRows();
        writeSheet(outputStream);
    }

    /**
     * 兜底校验：没有先统计行数、直接查出全部数据的导出，在写出前按上限拒绝（返回提示，而不是半个文件）
     */
    private void checkDataRows() {
        if (data != null) {
            ExcelRowLimits.checkExportRows(data.size());
        }
    }

    /**
     * 使用自定义写出逻辑写入输出流。
     */
    public void toStream(OutputStream outputStream, Consumer<ExcelWriterWrapper<T>> consumer) {
        try (ExcelWriter writer = createWriter(outputStream)) {
            consumer.accept(ExcelWriterWrapper.of(writer));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 使用自定义写出逻辑写入 HTTP 响应。
     */
    public void toResponse(HttpServletResponse response, Consumer<ExcelWriterWrapper<T>> consumer) {
        try {
            resetResponse(sheetName, response);
            toStream(response.getOutputStream(), consumer);
        } catch (IOException e) {
            throw new RuntimeException("导出Excel异常", e);
        }
    }

    private void writeSheet(OutputStream outputStream) {
        ExcelWriterSheetBuilder builder = createSheetBuilder(createWriterBuilder(outputStream));
        if (merge) {
            // 合并处理器
            builder.registerWriteHandler(new CellMergeStrategy(data, true));
        }
        builder.doWrite(data);
    }

    private ExcelWriter createWriter(OutputStream outputStream) {
        return createWriterBuilder(outputStream).build();
    }

    private ExcelWriterBuilder createWriterBuilder(OutputStream outputStream) {
        ExcelWriterBuilder builder = FesodSheet.write(outputStream, headType)
            .autoCloseStream(false)
            // 大数值自动转换 防止失真
            .registerConverter(new ExcelBigNumberConvert())
            // 批注必填项处理
            .registerWriteHandler(new DataWriteHandler(headType));
        if (columnWidth == null) {
            // 自动适配
            builder.registerWriteHandler(new LongestMatchColumnWidthStyleStrategy());
        } else {
            builder.registerWriteHandler(new SimpleColumnWidthStyleStrategy(columnWidth));
        }
        if (headRowHeight != null || contentRowHeight != null) {
            builder.registerWriteHandler(new SimpleRowHeightStyleStrategy(headRowHeight, contentRowHeight));
        }
        // 添加下拉框操作
        builder.registerWriteHandler(new ExcelDownHandler(options));
        if (StringUtils.isNotBlank(password)) {
            builder.password(password);
        }
        if (needHead != null) {
            builder.needHead(needHead);
        }
        if (automaticMergeHead != null) {
            builder.automaticMergeHead(automaticMergeHead);
        }
        if (CollUtil.isNotEmpty(includeFields)) {
            builder.includeColumnFieldNames(includeFields);
        }
        if (CollUtil.isNotEmpty(excludeFields)) {
            builder.excludeColumnFieldNames(excludeFields);
        }
        if (CollUtil.isNotEmpty(includeIndexes)) {
            builder.includeColumnIndexes(includeIndexes);
        }
        if (CollUtil.isNotEmpty(excludeIndexes)) {
            builder.excludeColumnIndexes(excludeIndexes);
        }
        if (orderByIncludeColumn != null) {
            builder.orderByIncludeColumn(orderByIncludeColumn);
        }
        if (CollUtil.isNotEmpty(converters)) {
            converters.forEach(builder::registerConverter);
        }
        if (CollUtil.isNotEmpty(writeHandlers)) {
            writeHandlers.forEach(builder::registerWriteHandler);
        }
        return builder;
    }

    private ExcelWriterSheetBuilder createSheetBuilder(ExcelWriterBuilder builder) {
        if (sheetNo != null) {
            return builder.sheet(sheetNo, sheetName);
        }
        return builder.sheet(sheetName);
    }

    private void exportZipToResponse(HttpServletResponse response) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize 必须大于 0");
        }
        List<List<T>> pageList = ListUtil.partition(data, pageSize);
        if (pageList.size() <= 1) {
            zip = false;
            toResponse(response);
            return;
        }
        try {
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition",
                "attachment;filename*=UTF-8''" + URLEncoder.encode(sheetName, StandardCharsets.UTF_8) + ".zip");

            try (ZipOutputStream zipOut = ZipUtil.getZipOutputStream(response.getOutputStream(), CharsetUtil.CHARSET_UTF_8)) {
                for (int i = 0; i < pageList.size(); i++) {
                    int part = i + 1;
                    String exportSheetName = sheetName + "_第" + part + "页";
                    byte[] bytes = buildZipEntry(pageList.get(i), exportSheetName);
                    zipOut.putNextEntry(new ZipEntry(exportSheetName + ".xlsx"));
                    zipOut.write(bytes);
                    zipOut.closeEntry();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("导出Zip异常", e);
        }
    }

    private byte[] buildZipEntry(List<T> pageData, String exportSheetName) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ExcelBuilder<T> builder = ExcelBuilder.of(pageData, headType)
                .sheetName(exportSheetName);
            copyOptionsTo(builder);
            builder.toStream(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(exportSheetName + "Excel生成失败", e);
        }
    }

    private void copyOptionsTo(ExcelBuilder<T> builder) {
        builder.merge = merge;
        builder.options = options;
        builder.password = password;
        builder.needHead = needHead;
        builder.automaticMergeHead = automaticMergeHead;
        builder.includeFields = includeFields;
        builder.excludeFields = excludeFields;
        builder.includeIndexes = includeIndexes;
        builder.excludeIndexes = excludeIndexes;
        builder.orderByIncludeColumn = orderByIncludeColumn;
        builder.columnWidth = columnWidth;
        builder.headRowHeight = headRowHeight;
        builder.contentRowHeight = contentRowHeight;
        builder.writeHandlers = writeHandlers;
        builder.converters = converters;
    }

    /**
     * 重置响应体。
     */
    private static void resetResponse(String filename, HttpServletResponse response) throws UnsupportedEncodingException {
        FileUtils.setAttachmentResponseHeader(response, encodingFilename(filename));
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
    }

    private static String encodingFilename(String filename) {
        return IdUtil.fastSimpleUUID() + "_" + filename + ".xlsx";
    }

    /**
     * Excel 导入读取构造器。
     */
    public static final class ReadBuilder<T> {

        private final InputStream inputStream;

        private final InputStreamSource source;

        private final Class<T> headType;

        private Integer maxRows;

        private boolean validate = true;

        private boolean failFast = true;

        private ExcelListener<T> listener;

        private Integer sheetNo;

        private String sheetName;

        private Integer headRowNumber;

        private Boolean ignoreEmptyRow;

        private String password;

        private Boolean autoTrim;

        private Boolean autoStrip;

        private Integer numRows;

        private List<Converter<?>> converters;

        private ReadBuilder(InputStream inputStream, InputStreamSource source, Class<T> headType) {
            if (inputStream == null && source == null) {
                throw new IllegalArgumentException("导入文件不能为空");
            }
            this.inputStream = inputStream;
            this.source = source;
            this.headType = headType;
        }

        /**
         * 设置本次导入的行数上限，不设置时取 tuoyu.excel.max-import-rows。
         *
         * @param maxRows 行数上限，1 ~ {@value ExcelRowLimits#MAX_CONFIGURABLE_ROWS}
         */
        public ReadBuilder<T> maxRows(int maxRows) {
            ExcelRowLimits.validate("maxRows", maxRows);
            this.maxRows = maxRows;
            return this;
        }

        /**
         * 设置是否校验导入数据。
         */
        public ReadBuilder<T> validate(boolean validate) {
            this.validate = validate;
            return this;
        }

        /**
         * 设置解析异常时是否立即终止读取。
         */
        public ReadBuilder<T> failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        /**
         * 设置自定义导入监听器。
         */
        public ReadBuilder<T> listener(ExcelListener<T> listener) {
            this.listener = listener;
            return this;
        }

        /**
         * 设置读取的工作表编号。
         */
        public ReadBuilder<T> sheetNo(Integer sheetNo) {
            this.sheetNo = sheetNo;
            return this;
        }

        /**
         * 设置读取的工作表名称。
         */
        public ReadBuilder<T> sheetName(String sheetName) {
            this.sheetName = sheetName;
            return this;
        }

        /**
         * 设置表头行数。
         */
        public ReadBuilder<T> headRowNumber(Integer headRowNumber) {
            if (headRowNumber != null && headRowNumber < 0) {
                throw new IllegalArgumentException("headRowNumber 不能小于 0");
            }
            this.headRowNumber = headRowNumber;
            return this;
        }

        /**
         * 设置是否忽略空行。
         */
        public ReadBuilder<T> ignoreEmptyRow(boolean ignoreEmptyRow) {
            this.ignoreEmptyRow = ignoreEmptyRow;
            return this;
        }

        /**
         * 设置读取文件密码。
         */
        public ReadBuilder<T> password(String password) {
            this.password = password;
            return this;
        }

        /**
         * 设置是否自动 trim 字符串。
         */
        public ReadBuilder<T> autoTrim(boolean autoTrim) {
            this.autoTrim = autoTrim;
            return this;
        }

        /**
         * 设置是否自动 strip 字符串。
         */
        public ReadBuilder<T> autoStrip(boolean autoStrip) {
            this.autoStrip = autoStrip;
            return this;
        }

        /**
         * 设置最多读取行数。
         */
        public ReadBuilder<T> numRows(Integer numRows) {
            if (numRows != null && numRows <= 0) {
                throw new IllegalArgumentException("numRows 必须大于 0");
            }
            this.numRows = numRows;
            return this;
        }

        /**
         * 注册自定义转换器。
         */
        public ReadBuilder<T> registerConverter(Converter<?> converter) {
            if (converter == null) {
                return this;
            }
            if (converters == null) {
                converters = new ArrayList<>();
            }
            converters.add(converter);
            return this;
        }

        /**
         * 读取Excel并返回对象集合。
         */
        public List<T> doReadSync() {
            return limited(false, (is, guard) -> createSheetBuilder(createReaderBuilder(is, guard, null)).doReadSync());
        }

        /**
         * 读取Excel并返回解析结果。
         */
        public ExcelResult<T> doRead() {
            ExcelListener<T> readListener = listener;
            if (readListener == null) {
                readListener = new DefaultExcelListener<>(validate, failFast);
            }
            ExcelListener<T> finalListener = readListener;
            return limited(false, (is, guard) -> {
                createSheetBuilder(createReaderBuilder(is, guard, finalListener)).doRead();
                return finalListener.getExcelResult();
            });
        }

        /**
         * 读取所有工作表并返回解析结果。
         */
        public ExcelResult<T> doReadAll() {
            ExcelListener<T> readListener = listener;
            if (readListener == null) {
                readListener = new DefaultExcelListener<>(validate, failFast);
            }
            ExcelListener<T> finalListener = readListener;
            return limited(true, (is, guard) -> {
                createReaderBuilder(is, guard, finalListener).doReadAll();
                return finalListener.getExcelResult();
            });
        }

        /**
         * 读取所有工作表并返回对象集合。
         */
        public List<T> doReadAllSync() {
            return limited(true, (is, guard) -> createReaderBuilder(is, guard, null).doReadAllSync());
        }

        /**
         * 按行数上限读取：可重复打开的文件先预读统计行数，超限时不做正式读取；
         * 正式读取时再计数一次，兜底只能读一遍的文件流。
         *
         * @param allSheets 是否读取全部工作表
         * @param read      正式读取
         */
        private <R> R limited(boolean allSheets, ReadAction<T, R> read) {
            int limit = maxRows != null ? maxRows : ExcelRowLimits.maxImportRows();
            try {
                if (source == null) {
                    return read.apply(inputStream, new RowLimitReadListener<>(limit));
                }
                try (InputStream counting = source.getInputStream()) {
                    ExcelReaderBuilder builder = createReaderBuilder(counting, new RowLimitReadListener<>(limit), null);
                    if (allSheets) {
                        builder.doReadAll();
                    } else {
                        createSheetBuilder(builder).doRead();
                    }
                }
                try (InputStream is = source.getInputStream()) {
                    return read.apply(is, new RowLimitReadListener<>(limit));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("读取导入文件失败", e);
            } catch (RuntimeException e) {
                RowLimitReadListener.RowLimitExceededException exceeded = RowLimitReadListener.RowLimitExceededException.find(e);
                if (exceeded != null) {
                    throw ExcelRowLimits.importExceeded(exceeded.maxRows());
                }
                throw e;
            }
        }

        private ExcelReaderBuilder createReaderBuilder(InputStream is, RowLimitReadListener<T> guard, ExcelListener<T> readListener) {
            ExcelReaderBuilder builder = FesodSheet.read(is)
                .head(headType)
                .autoCloseStream(false)
                // 行数计数器最先注册：超限异常先于其他监听器的异常处理抛出
                .registerReadListener(guard);
            if (readListener != null) {
                builder.registerReadListener(readListener);
            }
            if (headRowNumber != null) {
                builder.headRowNumber(headRowNumber);
            }
            if (ignoreEmptyRow != null) {
                builder.ignoreEmptyRow(ignoreEmptyRow);
            }
            if (StringUtils.isNotBlank(password)) {
                builder.password(password);
            }
            if (autoTrim != null) {
                builder.autoTrim(autoTrim);
            }
            if (autoStrip != null) {
                builder.autoStrip(autoStrip);
            }
            if (numRows != null) {
                builder.numRows(numRows);
            }
            if (CollUtil.isNotEmpty(converters)) {
                converters.forEach(builder::registerConverter);
            }
            return builder;
        }

        private ExcelReaderSheetBuilder createSheetBuilder(ExcelReaderBuilder builder) {
            ExcelReaderSheetBuilder sheetBuilder;
            if (sheetNo != null && StringUtils.isNotBlank(sheetName)) {
                sheetBuilder = builder.sheet(sheetNo, sheetName);
            } else if (sheetNo != null) {
                sheetBuilder = builder.sheet(sheetNo);
            } else if (StringUtils.isNotBlank(sheetName)) {
                sheetBuilder = builder.sheet(sheetName);
            } else {
                sheetBuilder = builder.sheet();
            }
            if (numRows != null) {
                sheetBuilder.numRows(numRows);
            }
            return sheetBuilder;
        }
    }

    /**
     * 正式读取
     */
    @FunctionalInterface
    private interface ReadAction<T, R> {

        R apply(InputStream inputStream, RowLimitReadListener<T> guard);
    }

    /**
     * Excel 模板导出构造器。
     */
    public static final class TemplateBuilder {

        private final String templatePath;

        private String filename = DEFAULT_SHEET_NAME;

        private TemplateMode mode;

        private Object data;

        private TemplateBuilder(String templatePath) {
            this.templatePath = templatePath;
        }

        /**
         * 设置下载文件名。
         */
        public TemplateBuilder filename(String filename) {
            this.filename = StringUtils.blankToDefault(filename, DEFAULT_SHEET_NAME);
            return this;
        }

        /**
         * 设置单表多数据模板数据，模板格式为 {.属性}。
         */
        public <T> TemplateBuilder data(List<T> data) {
            this.mode = TemplateMode.LIST;
            this.data = data;
            return this;
        }

        /**
         * 设置多表多数据模板数据，模板格式为 {key.属性}。
         */
        public TemplateBuilder multiList(Map<String, Object> data) {
            this.mode = TemplateMode.MULTI_LIST;
            this.data = data;
            return this;
        }

        /**
         * 设置多 sheet 模板数据，模板格式为 {key.属性}。
         */
        public TemplateBuilder multiSheet(List<Map<String, Object>> data) {
            this.mode = TemplateMode.MULTI_SHEET;
            this.data = data;
            return this;
        }

        /**
         * 写入 HTTP 响应。
         */
        public void toResponse(HttpServletResponse response) {
            try {
                validateData();
                resetResponse(filename, response);
                ServletOutputStream os = response.getOutputStream();
                toStream(os);
            } catch (IOException e) {
                throw new RuntimeException("导出Excel异常", e);
            }
        }

        /**
         * 写入输出流。
         */
        public void toStream(OutputStream outputStream) {
            validateData();
            ClassPathResource templateResource = new ClassPathResource(templatePath);
            try (InputStream templateStream = templateResource.getStream()) {
                ExcelWriter excelWriter = FesodSheet.write(outputStream)
                    .withTemplate(templateStream)
                    .autoCloseStream(false)
                    // 大数值自动转换 防止失真
                    .registerConverter(new ExcelBigNumberConvert())
                    .build();
                try {
                    fill(excelWriter);
                } finally {
                    excelWriter.finish();
                }
            } catch (IOException e) {
                throw new RuntimeException("读取Excel模板异常", e);
            }
        }

        @SuppressWarnings("unchecked")
        private void fill(ExcelWriter excelWriter) {
            if (mode == TemplateMode.LIST) {
                WriteSheet writeSheet = FesodSheet.writerSheet().build();
                FillConfig fillConfig = FillConfig.builder().forceNewRow(Boolean.TRUE).build();
                for (Object item : (List<?>) data) {
                    excelWriter.fill(item, fillConfig, writeSheet);
                }
                return;
            }
            if (mode == TemplateMode.MULTI_LIST) {
                WriteSheet writeSheet = FesodSheet.writerSheet().build();
                for (Map.Entry<String, Object> map : ((Map<String, Object>) data).entrySet()) {
                    FillConfig fillConfig = FillConfig.builder().forceNewRow(Boolean.TRUE).build();
                    if (map.getValue() instanceof Collection) {
                        excelWriter.fill(new FillWrapper(map.getKey(), (Collection<?>) map.getValue()), fillConfig, writeSheet);
                    } else {
                        excelWriter.fill(map.getValue(), fillConfig, writeSheet);
                    }
                }
                return;
            }
            List<Map<String, Object>> sheetData = (List<Map<String, Object>>) data;
            for (int i = 0; i < sheetData.size(); i++) {
                WriteSheet writeSheet = FesodSheet.writerSheet(i).build();
                for (Map.Entry<String, Object> map : sheetData.get(i).entrySet()) {
                    FillConfig fillConfig = FillConfig.builder().forceNewRow(Boolean.TRUE).build();
                    if (map.getValue() instanceof Collection) {
                        excelWriter.fill(new FillWrapper(map.getKey(), (Collection<?>) map.getValue()), fillConfig, writeSheet);
                    } else {
                        excelWriter.fill(map.getValue(), writeSheet);
                    }
                }
            }
        }

        private void validateData() {
            if (mode == null || data == null) {
                throw new IllegalArgumentException("数据为空");
            }
            if (data instanceof Collection<?> collection && CollUtil.isEmpty(collection)) {
                throw new IllegalArgumentException("数据为空");
            }
            if (data instanceof Map<?, ?> map && CollUtil.isEmpty(map)) {
                throw new IllegalArgumentException("数据为空");
            }
            ExcelRowLimits.checkExportRows(countRows(data));
        }

        /**
         * 模板数据中列表的行数之和（单个对象不计）
         */
        private static long countRows(Object value) {
            long rows = 0;
            if (value instanceof Collection<?> collection) {
                for (Object item : collection) {
                    rows += item instanceof Map<?, ?> ? countRows(item) : 1;
                }
            } else if (value instanceof Map<?, ?> map) {
                for (Object item : map.values()) {
                    rows += item instanceof Collection<?> || item instanceof Map<?, ?> ? countRows(item) : 0;
                }
            }
            return rows;
        }

        private enum TemplateMode {
            LIST,
            MULTI_LIST,
            MULTI_SHEET
        }
    }
}
