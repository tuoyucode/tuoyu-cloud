package org.tuoyu.mes.common.excel;

import org.tuoyu.mes.common.MesLookups;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 表头与明细写在同一张表中的导入（例如物料清单的每行是一个子件）：按单据编码把行分组，
 * 每组的第一行提供表头信息，后续行的表头列可以留空，填写时必须与第一行一致
 *
 * @author tuoyu
 */
public final class ImportGroups {

    private ImportGroups() {
    }

    /**
     * 按编码分组，保持文件中的先后顺序；编码为空或超长的行记错误、不归入任何组
     *
     * @param rows      全部行
     * @param code      取编码
     * @param field     编码的字段名称
     * @param maxLength 编码的最大长度
     * @param errors    收集错误
     * @return 编码键（不区分大小写）→ 该编码的各行
     */
    public static <T> Map<String, List<ImportRow<T>>> byCode(List<ImportRow<T>> rows, Function<T, String> code,
                                                             String field, int maxLength, ImportErrors errors) {
        Map<String, List<ImportRow<T>>> groups = new LinkedHashMap<>();
        for (ImportRow<T> row : rows) {
            String value = errors.row(row.rowNumber()).text(field, code.apply(row.data()), maxLength, true);
            if (value != null) {
                groups.computeIfAbsent(MesLookups.codeKey(value), key -> new ArrayList<>()).add(row);
            }
        }
        return groups;
    }

    /**
     * 后续行填写的表头列必须与第一行一致（留空表示沿用第一行）
     *
     * @param group  同一编码的各行
     * @param value  取该列
     * @param field  该列的字段名称
     * @param owner  单据的说明，例如“BOM 编码 B001”
     * @param errors 收集错误
     */
    public static <T> void sameAsFirst(List<ImportRow<T>> group, Function<T, String> value, String field, String owner,
                                       ImportErrors errors) {
        ImportRow<T> first = group.get(0);
        String expected = ImportFields.blankToNull(value.apply(first.data()));
        for (ImportRow<T> row : group.subList(1, group.size())) {
            String actual = ImportFields.blankToNull(value.apply(row.data()));
            if (actual != null && !Objects.equals(actual, expected)) {
                errors.add(row.rowNumber(), owner + "的" + field + "与第 " + first.rowNumber() + " 行不一致");
            }
        }
    }

}
