package org.tuoyu.mes.common.excel;

import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.event.AnalysisEventListener;
import org.apache.fesod.sheet.exception.ExcelDataConvertException;
import org.springframework.core.io.InputStreamSource;
import org.tuoyu.common.excel.core.ExcelListener;
import org.tuoyu.common.excel.core.ExcelResult;
import org.tuoyu.common.excel.utils.ExcelBuilder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 读取主数据导入文件：按 {@code tuoyu.excel.max-import-rows} 先预读统计行数，超限时直接拒绝；
 * 每行带上 Excel 中的行号，单元格格式错误与缺少的表头列记入 {@link ImportErrors}，由调用方与其他校验错误一起返回。
 *
 * @author tuoyu
 */
public final class MesExcelImport {

    private MesExcelImport() {
    }

    /**
     * 读取第一个工作表
     *
     * @param file   上传的文件
     * @param type   行的类型（列由 {@link ExcelProperty} 声明）
     * @param errors 收集错误
     * @return 读到的行（格式错误的行不在其中）；文件中没有数据时记一条错误
     */
    public static <T> List<ImportRow<T>> read(InputStreamSource file, Class<T> type, ImportErrors errors) {
        RowCollector<T> collector = new RowCollector<>(errors);
        ExcelBuilder.read(file, type).listener(collector).doRead();
        Set<String> missing = missingColumns(type, collector.heads);
        if (!missing.isEmpty()) {
            errors.addGeneral("文件与导入模板不一致，缺少列：" + String.join("、", missing) + "，请下载导入模板后填写");
        } else if (collector.rows.isEmpty() && errors.isEmpty()) {
            errors.addGeneral("导入文件中没有数据");
        }
        return collector.rows;
    }

    /**
     * 模板中有、文件表头中没有的列
     */
    static Set<String> missingColumns(Class<?> type, Collection<String> heads) {
        Set<String> present = new HashSet<>();
        heads.stream().filter(Objects::nonNull).map(String::strip).forEach(present::add);
        Set<String> missing = new LinkedHashSet<>();
        for (Field field : type.getDeclaredFields()) {
            ExcelProperty property = field.getAnnotation(ExcelProperty.class);
            if (property == null || property.value().length == 0) {
                continue;
            }
            String head = property.value()[property.value().length - 1];
            if (!present.contains(head)) {
                missing.add(head);
            }
        }
        return missing;
    }

    /**
     * 收集各行与行号；单元格转换失败时记错误并继续读取，其他异常（例如超过行数上限）向上抛出
     */
    private static final class RowCollector<T> extends AnalysisEventListener<T> implements ExcelListener<T> {

        private final ImportErrors errors;

        private final List<ImportRow<T>> rows = new ArrayList<>();

        private final List<String> heads = new ArrayList<>();

        private Map<Integer, String> headMap = Map.of();

        RowCollector(ImportErrors errors) {
            this.errors = errors;
        }

        @Override
        public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
            this.headMap = headMap;
            heads.addAll(headMap.values());
        }

        @Override
        public void invoke(T data, AnalysisContext context) {
            rows.add(new ImportRow<>(context.readRowHolder().getRowIndex() + 1, data));
        }

        @Override
        public void onException(Exception exception, AnalysisContext context) throws Exception {
            if (exception instanceof ExcelDataConvertException convert) {
                String head = headMap.get(convert.getColumnIndex());
                errors.add(convert.getRowIndex() + 1, (head == null ? "第 " + (convert.getColumnIndex() + 1) + " 列" : head)
                    + "的格式不正确");
                return;
            }
            throw exception;
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            // 读取完成后由调用方统一校验
        }

        @Override
        public ExcelResult<T> getExcelResult() {
            List<T> list = rows.stream().map(ImportRow::data).toList();
            return new ExcelResult<>() {
                @Override
                public List<T> getList() {
                    return list;
                }

                @Override
                public List<String> getErrorList() {
                    return errors.messages();
                }

                @Override
                public String getAnalysis() {
                    return "";
                }
            };
        }
    }

}
