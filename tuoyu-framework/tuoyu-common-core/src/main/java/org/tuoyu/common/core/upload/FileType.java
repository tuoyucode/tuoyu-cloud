package org.tuoyu.common.core.upload;

import org.springframework.core.io.InputStreamSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 允许上传的文件类型表：扩展名、服务端认定的媒体类型，以及按文件头判断内容是否相符的规则。
 * <p>
 * 只有登记在这张表里的类型才能加入上传白名单。没有可靠文件头的类型（HTML、SVG、脚本等）不登记，
 * 因而也无法通过配置放行；确需支持新类型时，在此新增一项并补充测试。
 *
 * @author tuoyu
 */
public enum FileType {

    /* ---------- 图片 ---------- */
    JPG("jpg", "image/jpeg", head(FileSignatures::isJpeg)),
    JPEG("jpeg", "image/jpeg", head(FileSignatures::isJpeg)),
    PNG("png", "image/png", head(FileSignatures::isPng)),
    GIF("gif", "image/gif", head(FileSignatures::isGif)),
    BMP("bmp", "image/bmp", head(FileSignatures::isBmp)),
    WEBP("webp", "image/webp", head(FileSignatures::isWebp)),

    /* ---------- 文档 ---------- */
    PDF("pdf", "application/pdf", head(FileSignatures::isPdf)),
    /** 旧版 Office（OLE2 复合文档）：文件头无法区分 doc、xls、ppt */
    DOC("doc", "application/msword", head(FileSignatures::isOle2)),
    XLS("xls", "application/vnd.ms-excel", head(FileSignatures::isOle2)),
    PPT("ppt", "application/vnd.ms-powerpoint", head(FileSignatures::isOle2)),
    /** 新版 Office（OOXML）：ZIP 文件头，且包含 [Content_Types].xml 与对应的部件目录 */
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        (head, source) -> FileSignatures.isOoxml(head, source, "word/")),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        (head, source) -> FileSignatures.isOoxml(head, source, "xl/")),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        (head, source) -> FileSignatures.isOoxml(head, source, "ppt/")),

    /* ---------- 纯文本 ---------- */
    TXT("txt", "text/plain", head(FileSignatures::isPlainText)),
    CSV("csv", "text/csv", head(FileSignatures::isPlainText)),

    /* ---------- 压缩包 ---------- */
    ZIP("zip", "application/zip", head(FileSignatures::isZip)),
    RAR("rar", "application/vnd.rar", head(FileSignatures::isRar)),
    SEVEN_Z("7z", "application/x-7z-compressed", head(FileSignatures::isSevenZip)),
    GZ("gz", "application/gzip", head(FileSignatures::isGzip)),

    /* ---------- 音视频 ---------- */
    MP3("mp3", "audio/mpeg", head(FileSignatures::isMp3)),
    WAV("wav", "audio/wav", head(FileSignatures::isWav)),
    MP4("mp4", "video/mp4", head(FileSignatures::isIsoMedia));

    private static final Map<String, FileType> BY_EXTENSION;

    static {
        Map<String, FileType> map = new LinkedHashMap<>();
        Arrays.stream(values()).forEach(type -> map.put(type.extension, type));
        BY_EXTENSION = Collections.unmodifiableMap(map);
    }

    private final String extension;

    private final String mediaType;

    private final ContentRule rule;

    FileType(String extension, String mediaType, ContentRule rule) {
        this.extension = extension;
        this.mediaType = mediaType;
        this.rule = rule;
    }

    /**
     * 扩展名（小写，不带点）
     */
    public String extension() {
        return extension;
    }

    /**
     * 服务端认定的媒体类型。存储与下载都使用它，不采信客户端声明的 Content-Type
     */
    public String mediaType() {
        return mediaType;
    }

    /**
     * 判断文件内容是否与本类型相符。
     *
     * @param head   文件开头的若干字节（至多 {@link FileSignatures#HEAD_SIZE}）
     * @param source 完整内容，部分类型需要扫描全文（如 OOXML 的部件目录）
     * @return 相符返回 true
     * @throws IOException 读取内容失败
     */
    public boolean matches(byte[] head, InputStreamSource source) throws IOException {
        return rule.test(head, source);
    }

    /**
     * 按扩展名查找类型（忽略大小写，可带前导点）。
     *
     * @param extension 扩展名
     * @return 未登记时返回 null
     */
    public static FileType ofExtension(String extension) {
        if (extension == null) {
            return null;
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return BY_EXTENSION.get(normalized);
    }

    private static ContentRule head(Predicate<byte[]> predicate) {
        return (head, source) -> predicate.test(head);
    }

    /**
     * 内容校验规则
     */
    @FunctionalInterface
    interface ContentRule {

        boolean test(byte[] head, InputStreamSource source) throws IOException;
    }

}
