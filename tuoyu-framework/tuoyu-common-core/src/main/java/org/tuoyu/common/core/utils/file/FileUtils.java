package org.tuoyu.common.core.utils.file;

import jakarta.servlet.http.HttpServletResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件下载的响应头。
 *
 * @author tuoyu
 */
public final class FileUtils {

    private FileUtils() {
    }

    /**
     * 百分号编码：按 UTF-8 做 URL 编码，空格编码为 {@code %20}（而不是表单编码的 {@code +}）。
     *
     * @param s 文本，不能为 null
     * @return 编码后的文本
     */
    public static String percentEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * 设置下载文件的响应头：文件名按百分号编码写进 {@code Content-disposition} 与 {@code download-filename}，
     * 并允许前端读取这两个头。
     *
     * @param response     响应
     * @param realFileName 文件名
     */
    public static void setAttachmentResponseHeader(HttpServletResponse response, String realFileName) {
        String encoded = percentEncode(realFileName);
        response.addHeader("Access-Control-Expose-Headers", "Content-Disposition,download-filename");
        response.setHeader("Content-disposition", "attachment; filename=" + encoded + ";filename*=utf-8''" + encoded);
        response.setHeader("download-filename", encoded);
    }

}
