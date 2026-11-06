package org.tuoyu.common.core.upload;

import org.springframework.core.io.InputStreamSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 常见文件类型的文件头（魔数）判断。
 * <p>
 * 只做“内容是否像声明的类型”的轻量判断，不解析完整格式，也不替代杀毒。
 *
 * @author tuoyu
 */
final class FileSignatures {

    /**
     * 读取文件开头的字节数
     */
    static final int HEAD_SIZE = 8192;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] SEVEN_Z = {'7', 'z', (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
    private static final byte[] RAR = {'R', 'a', 'r', '!', 0x1A, 0x07};
    private static final byte[] ZIP_LOCAL = {'P', 'K', 0x03, 0x04};
    private static final byte[] ZIP_EMPTY = {'P', 'K', 0x05, 0x06};
    private static final byte[] ZIP_SPANNED = {'P', 'K', 0x07, 0x08};
    private static final byte[] OOXML_CONTENT_TYPES = "[Content_Types].xml".getBytes(StandardCharsets.US_ASCII);

    private FileSignatures() {
    }

    static boolean isJpeg(byte[] head) {
        return startsWith(head, 0, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
    }

    static boolean isPng(byte[] head) {
        return startsWith(head, 0, PNG);
    }

    static boolean isGif(byte[] head) {
        return startsWith(head, 0, ascii("GIF87a")) || startsWith(head, 0, ascii("GIF89a"));
    }

    /**
     * BMP：BM 开头，且文件头中的 4 个保留字节为 0（避免把以 BM 开头的文本当成图片）
     */
    static boolean isBmp(byte[] head) {
        return head.length >= 14 && startsWith(head, 0, ascii("BM"))
            && head[6] == 0 && head[7] == 0 && head[8] == 0 && head[9] == 0;
    }

    static boolean isWebp(byte[] head) {
        return startsWith(head, 0, ascii("RIFF")) && startsWith(head, 8, ascii("WEBP"));
    }

    static boolean isPdf(byte[] head) {
        return startsWith(head, 0, ascii("%PDF-"));
    }

    static boolean isOle2(byte[] head) {
        return startsWith(head, 0, OLE2);
    }

    static boolean isZip(byte[] head) {
        return startsWith(head, 0, ZIP_LOCAL) || startsWith(head, 0, ZIP_EMPTY) || startsWith(head, 0, ZIP_SPANNED);
    }

    static boolean isRar(byte[] head) {
        return startsWith(head, 0, RAR);
    }

    static boolean isSevenZip(byte[] head) {
        return startsWith(head, 0, SEVEN_Z);
    }

    /**
     * GZIP：1F 8B，压缩方法为 deflate（08）
     */
    static boolean isGzip(byte[] head) {
        return startsWith(head, 0, (byte) 0x1F, (byte) 0x8B, (byte) 0x08);
    }

    /**
     * MP3：带 ID3 标签，或者直接以 MPEG 音频帧同步字开头（11 位全 1，且 layer 不是保留值）
     */
    static boolean isMp3(byte[] head) {
        if (startsWith(head, 0, ascii("ID3"))) {
            return true;
        }
        return head.length >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0 && (head[1] & 0x06) != 0;
    }

    static boolean isWav(byte[] head) {
        return startsWith(head, 0, ascii("RIFF")) && startsWith(head, 8, ascii("WAVE"));
    }

    /**
     * ISO 基础媒体文件（MP4 等）：第一个 box 是 ftyp
     */
    static boolean isIsoMedia(byte[] head) {
        return startsWith(head, 4, ascii("ftyp"));
    }

    /**
     * 纯文本：开头部分不含 NUL，且除常见空白外的控制字符不超过 1%。
     * <p>
     * 不限定编码（UTF-8、GBK 都常见），因此不包括 UTF-16 文本。
     */
    static boolean isPlainText(byte[] head) {
        if (head.length == 0) {
            return false;
        }
        int controls = 0;
        for (byte b : head) {
            int c = b & 0xFF;
            if (c == 0) {
                return false;
            }
            // 允许 \t \n \v \f \r、DOS 文件结束符（0x1A）与 ESC（0x1B）
            boolean allowed = c == 0x09 || c == 0x0A || c == 0x0B || c == 0x0C || c == 0x0D || c == 0x1A || c == 0x1B;
            if ((c < 0x20 && !allowed) || c == 0x7F) {
                controls++;
            }
        }
        return controls * 100 <= head.length;
    }

    /**
     * OOXML（docx、xlsx、pptx）：ZIP 文件头，并且包含 [Content_Types].xml 与对应的部件目录。
     * <p>
     * ZIP 中的文件名以明文保存在本地文件头与中央目录中，这里直接按字节搜索文件名，不解压任何内容，
     * 因此不会受到压缩炸弹的影响。
     *
     * @param head       文件开头
     * @param source     完整内容
     * @param partPrefix 部件目录，如 {@code word/}
     */
    static boolean isOoxml(byte[] head, InputStreamSource source, String partPrefix) throws IOException {
        if (!startsWith(head, 0, ZIP_LOCAL)) {
            return false;
        }
        byte[] part = partPrefix.getBytes(StandardCharsets.US_ASCII);
        return containsAll(source, OOXML_CONTENT_TYPES, part);
    }

    /**
     * 流式搜索：内容中是否同时包含全部字节序列
     */
    static boolean containsAll(InputStreamSource source, byte[]... patterns) throws IOException {
        boolean[] found = new boolean[patterns.length];
        int remaining = patterns.length;
        int overlap = Arrays.stream(patterns).mapToInt(p -> p.length).max().orElse(1) - 1;
        byte[] buffer = new byte[64 * 1024 + overlap];
        int carried = 0;
        try (InputStream in = source.getInputStream()) {
            int read;
            while ((read = in.read(buffer, carried, buffer.length - carried)) != -1) {
                int length = carried + read;
                for (int i = 0; i < patterns.length; i++) {
                    if (!found[i] && indexOf(buffer, length, patterns[i]) >= 0) {
                        found[i] = true;
                        remaining--;
                    }
                }
                if (remaining == 0) {
                    return true;
                }
                // 保留末尾若干字节，避免漏掉跨越两次读取的匹配
                carried = Math.min(overlap, length);
                System.arraycopy(buffer, length - carried, buffer, 0, carried);
            }
        }
        return false;
    }

    /**
     * 读取文件开头至多 {@link #HEAD_SIZE} 个字节
     */
    static byte[] readHead(InputStreamSource source) throws IOException {
        try (InputStream in = source.getInputStream()) {
            return in.readNBytes(HEAD_SIZE);
        }
    }

    private static int indexOf(byte[] data, int length, byte[] pattern) {
        outer:
        for (int i = 0; i <= length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static boolean startsWith(byte[] data, int offset, byte... prefix) {
        if (data.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

}
