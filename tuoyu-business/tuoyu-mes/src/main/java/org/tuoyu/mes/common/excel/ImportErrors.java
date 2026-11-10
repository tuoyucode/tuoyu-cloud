package org.tuoyu.mes.common.excel;

import org.tuoyu.common.core.exception.ServiceException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 导入时逐行收集的错误。整份文件校验完再统一判断：有任何错误时一行都不写入，并把全部错误（第几行、哪个字段、原因）返回给用户
 *
 * @author tuoyu
 */
public final class ImportErrors {

    /**
     * 提示中最多列出的错误数，其余只给出总数
     */
    static final int MAX_LISTED = 100;

    private final List<String> messages = new ArrayList<>();

    private final Set<Integer> rowsWithErrors = new HashSet<>();

    /**
     * 记录一行的错误
     *
     * @param rowNumber Excel 中的行号
     * @param message   原因，例如“物料编码 M001 已存在”
     */
    public void add(int rowNumber, String message) {
        rowsWithErrors.add(rowNumber);
        messages.add("第 " + rowNumber + " 行：" + message);
    }

    /**
     * 记录与具体行无关的错误（例如文件中没有数据）
     */
    public void addGeneral(String message) {
        messages.add(message);
    }

    /**
     * 该行是否已有错误（有错误的行不再做依赖它的后续校验，避免重复提示）
     */
    public boolean hasError(int rowNumber) {
        return rowsWithErrors.contains(rowNumber);
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public List<String> messages() {
        return List.copyOf(messages);
    }

    /**
     * 绑定到某一行的字段解析器
     */
    public ImportFields row(int rowNumber) {
        return new ImportFields(this, rowNumber);
    }

    /**
     * 有错误时抛出业务异常，提示中逐行列出（每行一条），一行都不写入
     */
    public void throwIfAny() {
        if (messages.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder("导入失败，没有写入任何数据。共 ").append(messages.size()).append(" 处错误");
        if (messages.size() > MAX_LISTED) {
            text.append("，以下是前 ").append(MAX_LISTED).append(" 处");
        }
        text.append('：');
        messages.stream().limit(MAX_LISTED).forEach(message -> text.append('\n').append(message));
        throw new ServiceException(text.toString());
    }

}
