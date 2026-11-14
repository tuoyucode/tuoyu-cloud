package org.tuoyu.common.core.utils.file;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("FileUtils：下载文件名")
class FileUtilsTest {

    @Test
    @DisplayName("百分号编码：UTF-8，空格为 %20，加号为 %2B")
    void percentEncode() {
        assertThat(FileUtils.percentEncode("测试 report+1.xlsx")).isEqualTo("%E6%B5%8B%E8%AF%95%20report%2B1.xlsx");
        assertThat(FileUtils.percentEncode("a b&c=d.txt")).isEqualTo("a%20b%26c%3Dd.txt");
        assertThat(FileUtils.percentEncode("plain-name_1.csv")).isEqualTo("plain-name_1.csv");
    }

    @Test
    @DisplayName("下载响应头：暴露给前端读取，头名与取值逐字一致")
    void attachmentHeaders() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        String encoded = "%E6%B5%8B%E8%AF%95%20report%2B1.xlsx";

        FileUtils.setAttachmentResponseHeader(response, "测试 report+1.xlsx");

        var order = inOrder(response);
        order.verify(response).addHeader("Access-Control-Expose-Headers", "Content-Disposition,download-filename");
        order.verify(response).setHeader("Content-disposition",
            "attachment; filename=" + encoded + ";filename*=utf-8''" + encoded);
        order.verify(response).setHeader("download-filename", encoded);
        verifyNoMoreInteractions(response);
    }
}
