package org.tuoyu.common.core.upload;

import org.springframework.core.io.InputStreamSource;
import org.tuoyu.common.core.exception.ServiceException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 上传文件校验：扩展名白名单 + 文件头校验 + 文件名检查。
 * <p>
 * 规则：
 * <ol>
 *     <li>文件名去掉路径部分与首尾空白后不能为空，不能含控制字符，不能以点结尾；</li>
 *     <li>最后一个扩展名（忽略大小写）必须在白名单中；白名单只能取 {@link FileType} 中登记的类型；</li>
 *     <li>中间的扩展名不能是可执行或可被浏览器、服务器解释的类型（如 {@code a.jsp.png}）；</li>
 *     <li>文件内容必须与扩展名相符（文件头校验），空文件拒绝。</li>
 * </ol>
 * 本类不可变、线程安全。
 *
 * @author tuoyu
 */
public final class UploadFileValidator {

    /**
     * 默认白名单：常见的图片、文档、文本、压缩包与音视频
     */
    public static final List<String> DEFAULT_ALLOWED_EXTENSIONS = List.of(
        "jpg", "jpeg", "png", "gif", "bmp", "webp",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "txt", "csv",
        "zip", "rar", "7z", "gz",
        "mp3", "wav", "mp4");

    /**
     * 出现在文件名中间时拒绝的扩展名：可执行文件、脚本、服务端页面与浏览器会解释执行的类型。
     * <p>
     * 部分 Web 服务器（如 Apache 的多扩展名处理）会按中间的扩展名解释文件，{@code a.php.png} 可能被当作脚本执行
     */
    static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
        "exe", "dll", "com", "scr", "msi", "bat", "cmd", "ps1", "vbs", "vbe", "js", "jse", "mjs", "wsf", "wsh",
        "hta", "jar", "war", "class", "sh", "bash", "py", "pl", "rb", "cgi", "php", "php3", "php4", "php5",
        "phtml", "phar", "asp", "aspx", "ashx", "asmx", "jsp", "jspx", "jspf", "html", "htm", "xhtml", "shtml",
        "svg", "svgz", "swf", "so", "htaccess");

    private final Set<String> allowedExtensions;

    private UploadFileValidator(Set<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    /**
     * 按白名单创建校验器。
     *
     * @param allowedExtensions 允许的扩展名（忽略大小写，可带前导点）
     * @return 校验器
     * @throws IllegalArgumentException 白名单为空，或含有未登记文件头规则的类型
     */
    public static UploadFileValidator of(Collection<String> allowedExtensions) {
        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            throw new IllegalArgumentException("上传白名单不能为空");
        }
        Set<String> normalized = new LinkedHashSet<>();
        List<String> unsupported = new ArrayList<>();
        for (String extension : allowedExtensions) {
            FileType type = FileType.ofExtension(extension);
            if (type == null) {
                unsupported.add(extension);
            } else {
                normalized.add(type.extension());
            }
        }
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException("上传白名单中的类型 " + unsupported
                + " 没有文件头校验规则，不能放行。支持的类型：" + supportedExtensions());
        }
        return new UploadFileValidator(Collections.unmodifiableSet(normalized));
    }

    /**
     * 使用默认白名单创建校验器
     */
    public static UploadFileValidator defaults() {
        return of(DEFAULT_ALLOWED_EXTENSIONS);
    }

    /**
     * 可以加入白名单的全部类型
     */
    public static List<String> supportedExtensions() {
        List<String> list = new ArrayList<>();
        for (FileType type : FileType.values()) {
            list.add(type.extension());
        }
        return list;
    }

    /**
     * 当前白名单
     */
    public Set<String> allowedExtensions() {
        return allowedExtensions;
    }

    /**
     * 校验上传文件。
     *
     * @param originalFilename 客户端提供的文件名
     * @param content          文件内容（可多次打开）
     * @return 校验通过的文件信息：去掉路径的文件名、规范化的扩展名与服务端认定的媒体类型
     * @throws ServiceException 校验不通过，消息可直接展示给用户
     */
    public ValidatedFile validate(String originalFilename, InputStreamSource content) {
        String name = baseName(originalFilename);
        if (name.isEmpty()) {
            throw new ServiceException("上传文件名不能为空");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isISOControl(c)) {
                throw new ServiceException("上传文件名包含非法字符");
            }
        }
        // Windows 会忽略结尾的点，a.jsp. 在磁盘上就是 a.jsp
        if (name.charAt(name.length() - 1) == '.') {
            throw new ServiceException("上传文件名不能以点结尾");
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            throw new ServiceException("上传文件缺少扩展名，允许的类型：" + String.join("、", allowedExtensions));
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        FileType type = FileType.ofExtension(extension);
        if (type == null || !allowedExtensions.contains(type.extension())) {
            throw new ServiceException("不允许上传 ." + extension + " 文件，允许的类型：" + String.join("、", allowedExtensions));
        }
        checkInnerExtensions(name.substring(0, dot));
        checkContent(type, content);
        return new ValidatedFile(name, type);
    }

    private static void checkInnerExtensions(String stem) {
        String[] segments = stem.split("\\.");
        // 第一段是文件名本身（含隐藏文件的空段），从第二段开始检查
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i].trim().toLowerCase(Locale.ROOT);
            // IIS 会忽略分号之后的部分（a.asp;.png）
            int semicolon = segment.indexOf(';');
            if (semicolon >= 0) {
                segment = segment.substring(0, semicolon);
            }
            if (DANGEROUS_EXTENSIONS.contains(segment)) {
                throw new ServiceException("上传文件名不能包含 ." + segment + " 这类可执行的扩展名");
            }
        }
    }

    private static void checkContent(FileType type, InputStreamSource content) {
        try {
            byte[] head = FileSignatures.readHead(content);
            if (head.length == 0) {
                throw new ServiceException("上传文件不能为空");
            }
            if (!type.matches(head, content)) {
                throw new ServiceException("文件内容与扩展名 ." + type.extension() + " 不符，请确认文件没有被改过扩展名");
            }
        } catch (IOException e) {
            throw new ServiceException("读取上传文件失败", e);
        }
    }

    /**
     * 去掉客户端可能带上的路径（部分浏览器会提交完整路径）
     */
    static String baseName(String filename) {
        if (filename == null) {
            return "";
        }
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        return filename.substring(slash + 1).strip();
    }

    /**
     * 校验通过的文件
     *
     * @param filename 去掉路径的原始文件名
     * @param type     文件类型
     */
    public record ValidatedFile(String filename, FileType type) {

        /**
         * 规范化的扩展名（小写，不带点）
         */
        public String extension() {
            return type.extension();
        }

        /**
         * 服务端认定的媒体类型
         */
        public String mediaType() {
            return type.mediaType();
        }
    }

}
