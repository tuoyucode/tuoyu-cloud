package org.tuoyu.mes.support;

import org.springframework.mock.web.MockMultipartFile;
import org.tuoyu.common.excel.utils.ExcelBuilder;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 测试用的导入文件：按导入模板的类型把若干行写成 xlsx（表头即模板表头，第一行数据在 Excel 的第 2 行）
 */
public final class ExcelFiles {

    private ExcelFiles() {
    }

    public static <T> MockMultipartFile xlsx(Class<T> type, List<T> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelBuilder.of(rows, type).sheetName("导入").toStream(out);
        return new MockMultipartFile("file", "import.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

}
