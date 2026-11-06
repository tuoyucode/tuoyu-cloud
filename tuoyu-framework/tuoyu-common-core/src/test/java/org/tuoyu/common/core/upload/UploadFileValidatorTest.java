package org.tuoyu.common.core.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.tuoyu.common.core.exception.ServiceException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("上传文件校验")
class UploadFileValidatorTest {

    private static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R');
    private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10, 'J', 'F', 'I', 'F');
    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] OLE2 = bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1, 0, 0, 0, 0);
    /** Windows 可执行文件（MZ 头，含 NUL） */
    private static final byte[] EXE = bytes('M', 'Z', 0x90, 0, 0x03, 0, 0, 0, 0x04, 0, 0, 0, 0xFF, 0xFF);
    private static final byte[] MP4 = bytes(0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm');

    private final UploadFileValidator validator = UploadFileValidator.defaults();

    @Nested
    @DisplayName("放行内容与扩展名相符的常见文件")
    class Accepted {

        @Test
        @DisplayName("图片、PDF、旧版 Office、音视频")
        void binaryTypes() {
            assertEquals(FileType.PNG, validator.validate("logo.png", content(PNG)).type());
            assertEquals(FileType.JPG, validator.validate("photo.jpg", content(JPEG)).type());
            assertEquals(FileType.JPEG, validator.validate("photo.jpeg", content(JPEG)).type());
            assertEquals(FileType.PDF, validator.validate("合同.pdf", content(PDF)).type());
            assertEquals(FileType.XLS, validator.validate("旧报表.xls", content(OLE2)).type());
            assertEquals(FileType.MP4, validator.validate("video.mp4", content(MP4)).type());
        }

        @Test
        @DisplayName("新版 Office 按部件目录区分 docx、xlsx、pptx")
        void officeOpenXml() throws IOException {
            assertEquals(FileType.DOCX, validator.validate("方案.docx", content(ooxml("word/document.xml"))).type());
            assertEquals(FileType.XLSX, validator.validate("数据.xlsx", content(ooxml("xl/workbook.xml"))).type());
            assertEquals(FileType.PPTX, validator.validate("汇报.pptx", content(ooxml("ppt/presentation.xml"))).type());
        }

        @Test
        @DisplayName("纯文本支持 UTF-8 与 GBK")
        void plainText() {
            assertEquals(FileType.TXT, validator.validate("说明.txt", content("第一行\r\n第二行\t制表".getBytes(StandardCharsets.UTF_8))).type());
            assertEquals(FileType.CSV, validator.validate("导出.csv", content("姓名,部门\n张三,研发".getBytes(Charset.forName("GBK")))).type());
        }

        @Test
        @DisplayName("媒体类型由服务端按扩展名认定，与客户端声明无关")
        void mediaTypeIsServerSide() {
            UploadFileValidator.ValidatedFile file = validator.validate("logo.png", content(PNG));
            assertEquals("image/png", file.mediaType());
            assertEquals("png", file.extension());
        }

        @Test
        @DisplayName("去掉客户端提交的路径")
        void stripPath() {
            assertEquals("x.png", validator.validate("C:\\fakepath\\x.png", content(PNG)).filename());
            assertEquals("y.png", validator.validate("../../etc/y.png", content(PNG)).filename());
        }
    }

    @Nested
    @DisplayName("大小写")
    class CaseInsensitive {

        @Test
        @DisplayName("扩展名忽略大小写，并规范为小写")
        void upperCaseExtension() {
            UploadFileValidator.ValidatedFile file = validator.validate("PHOTO.PNG", content(PNG));
            assertEquals("png", file.extension());
            assertEquals("PHOTO.PNG", file.filename());
            assertEquals(FileType.JPG, validator.validate("a.JpG", content(JPEG)).type());
        }

        @Test
        @DisplayName("白名单配置忽略大小写与前导点")
        void whitelistNormalized() {
            UploadFileValidator custom = UploadFileValidator.of(List.of("PNG", ".Jpg"));
            assertEquals(List.of("png", "jpg"), List.copyOf(custom.allowedExtensions()));
            assertEquals(FileType.PNG, custom.validate("a.png", content(PNG)).type());
            assertThrows(ServiceException.class, () -> custom.validate("a.pdf", content(PDF)));
        }

        @Test
        @DisplayName("大写的危险扩展名同样拒绝")
        void upperCaseDangerous() {
            assertRejected("不允许上传 .jsp", "shell.JSP", content("<% out.println(1); %>".getBytes(StandardCharsets.UTF_8)));
            assertRejected("可执行的扩展名", "shell.PHP.png", content(PNG));
        }
    }

    @Nested
    @DisplayName("扩展名伪装与魔数不符")
    class Disguised {

        @Test
        @DisplayName("可执行文件改名为图片或文本")
        void executableRenamed() {
            assertRejected("文件内容与扩展名 .png 不符", "cat.png", content(EXE));
            assertRejected("文件内容与扩展名 .txt 不符", "readme.txt", content(EXE));
            assertRejected("文件内容与扩展名 .pdf 不符", "invoice.pdf", content(EXE));
        }

        @Test
        @DisplayName("HTML 改名为图片")
        void htmlRenamedAsImage() {
            byte[] html = "<html><script>alert(document.cookie)</script></html>".getBytes(StandardCharsets.UTF_8);
            assertRejected("文件内容与扩展名 .jpg 不符", "avatar.jpg", content(html));
            assertRejected("文件内容与扩展名 .gif 不符", "avatar.gif", content(html));
        }

        @Test
        @DisplayName("一种图片冒充另一种")
        void wrongMagic() {
            assertRejected("文件内容与扩展名 .pdf 不符", "a.pdf", content(PNG));
            assertRejected("文件内容与扩展名 .jpg 不符", "a.jpg", content(PNG));
        }

        @Test
        @DisplayName("普通 ZIP 改名为 docx，docx 改名为 xlsx")
        void zipRenamedAsOffice() throws IOException {
            byte[] plainZip = zip("readme.txt", "hello");
            assertEquals(FileType.ZIP, validator.validate("a.zip", content(plainZip)).type());
            assertRejected("文件内容与扩展名 .docx 不符", "a.docx", content(plainZip));
            assertRejected("文件内容与扩展名 .xlsx 不符", "a.xlsx", content(ooxml("word/document.xml")));
        }

        @Test
        @DisplayName("BM 开头的文本不是 BMP")
        void textStartingWithBm() {
            assertRejected("文件内容与扩展名 .bmp 不符", "a.bmp", content("BMW,Benz,Audi\n1,2,3".getBytes(StandardCharsets.US_ASCII)));
        }

        @Test
        @DisplayName("空文件")
        void emptyFile() {
            assertRejected("上传文件不能为空", "a.png", content(new byte[0]));
        }
    }

    @Nested
    @DisplayName("双扩展名与文件名")
    class FileNames {

        @Test
        @DisplayName("中间是可执行扩展名时拒绝")
        void dangerousInnerExtension() {
            assertRejected("不能包含 .jsp", "shell.jsp.png", content(PNG));
            assertRejected("不能包含 .php", "a.php.jpg", content(JPEG));
            assertRejected("不能包含 .html", "index.html.pdf", content(PDF));
            assertRejected("不能包含 .exe", "setup.exe.zip", content(zipQuietly()));
            assertRejected("不能包含 .asp", "a.asp;.png", content(PNG));
        }

        @Test
        @DisplayName("中间是普通文字时放行")
        void harmlessInnerSegments() {
            assertEquals("report.2024.final.pdf", validator.validate("report.2024.final.pdf", content(PDF)).filename());
            assertEquals(FileType.PNG, validator.validate("v1.2.3.png", content(PNG)).type());
        }

        @Test
        @DisplayName("最后的扩展名不在白名单")
        void notWhitelisted() {
            byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8);
            assertRejected("不允许上传 .svg", "logo.svg", content(svg));
            assertRejected("不允许上传 .html", "page.html", content("<p>x</p>".getBytes(StandardCharsets.UTF_8)));
            assertRejected("不允许上传 .exe", "setup.exe", content(EXE));
            // 结尾的空白会被去掉，按 .jpg 校验内容
            assertRejected("文件内容与扩展名 .jpg 不符", "a.png.jpg ", content(PNG));
        }

        @Test
        @DisplayName("缺少扩展名、以点结尾、含控制字符、为空")
        void malformedNames() {
            assertRejected("缺少扩展名", "README", content(PDF));
            assertRejected("不能以点结尾", "shell.jsp.", content(PNG));
            assertRejected("非法字符", "a\u0000.png", content(PNG));
            assertRejected("文件名不能为空", "  ", content(PNG));
            assertRejected("文件名不能为空", null, content(PNG));
        }
    }

    @Nested
    @DisplayName("白名单配置")
    class Whitelist {

        @Test
        @DisplayName("没有文件头规则的类型不能加入白名单")
        void unsupportedTypeRejected() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> UploadFileValidator.of(List.of("png", "svg", "html")));
            assertTrue(e.getMessage().contains("[svg, html]"), e.getMessage());
            assertThrows(IllegalArgumentException.class, () -> UploadFileValidator.of(List.of()));
            assertThrows(IllegalArgumentException.class, () -> UploadFileValidator.of(null));
        }

        @Test
        @DisplayName("默认白名单全部有文件头规则，且不含可执行或可脚本化的类型")
        void defaultsAreSafe() {
            UploadFileValidator defaults = UploadFileValidator.defaults();
            assertEquals(UploadFileValidator.DEFAULT_ALLOWED_EXTENSIONS, List.copyOf(defaults.allowedExtensions()));
            for (String extension : defaults.allowedExtensions()) {
                assertFalse(UploadFileValidator.DANGEROUS_EXTENSIONS.contains(extension), extension);
            }
        }
    }

    @Test
    @DisplayName("流式搜索能找到跨越两次读取的字节序列")
    void streamingSearchAcrossChunks() throws IOException {
        byte[] data = new byte[200 * 1024];
        Arrays.fill(data, (byte) 'a');
        byte[] marker = "[Content_Types].xml".getBytes(StandardCharsets.US_ASCII);
        // 64KB 边界前 5 个字节开始
        System.arraycopy(marker, 0, data, 64 * 1024 - 5, marker.length);
        byte[] tail = "word/".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(tail, 0, data, data.length - tail.length, tail.length);

        assertTrue(FileSignatures.containsAll(content(data), marker, tail));
        assertFalse(FileSignatures.containsAll(content(data), marker, "xl/".getBytes(StandardCharsets.US_ASCII)));
    }

    private void assertRejected(String expectedMessage, String filename, ByteArrayResource content) {
        ServiceException e = assertThrows(ServiceException.class, () -> validator.validate(filename, content));
        assertTrue(e.getMessage().contains(expectedMessage), "实际提示：" + e.getMessage());
    }

    private static ByteArrayResource content(byte[] data) {
        return new ByteArrayResource(data);
    }

    private static byte[] ooxml(String mainPart) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("_rels/.rels"));
            zip.write("<Relationships/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(mainPart));
            zip.write("<document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static byte[] zip(String name, String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(text.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static byte[] zipQuietly() {
        try {
            return zip("a.txt", "a");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

}
