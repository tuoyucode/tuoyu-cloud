package org.tuoyu.common.web.xss;

import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.json.enhance.JsonValueEnhancer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("富文本白名单清洗")
class RichTextTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Nested
    @DisplayName("白名单清洗")
    class Sanitizer {

        @Test
        @DisplayName("常见排版标签原样保留")
        void keepFormatting() {
            String html = "<h2>标题</h2><p>你好<b>世界</b>，<em>斜体</em><u>下划线</u></p><ul><li>一</li><li>二</li></ul>"
                + "<blockquote>引用</blockquote><pre><code class=\"language-java\">int a = 1;</code></pre>"
                + "<table><tbody><tr><td colspan=\"2\">单元格</td></tr></tbody></table><hr>";
            assertEquals(html, HtmlSanitizer.sanitize(html));
        }

        @Test
        @DisplayName("去掉脚本、内嵌框架、样式表与 SVG")
        void removeActiveContent() {
            assertEquals("<p>正文</p>", HtmlSanitizer.sanitize("<script>alert(1)</script><p>正文</p>"));
            assertEquals("", HtmlSanitizer.sanitize("<iframe src=\"https://evil.example\"></iframe>"));
            assertEquals("", HtmlSanitizer.sanitize("<style>body{display:none}</style>"));
            assertEquals("", HtmlSanitizer.sanitize("<svg onload=\"alert(1)\"></svg>"));
            assertEquals("", HtmlSanitizer.sanitize("<object data=\"x.swf\"></object><embed src=\"x.swf\">"));
            assertEquals("", HtmlSanitizer.sanitize("<form action=\"https://evil.example\"><input name=\"p\"></form>"));
        }

        @Test
        @DisplayName("去掉事件属性与 style")
        void removeDangerousAttributes() {
            String img = HtmlSanitizer.sanitize("<img src=\"https://cdn.example/a.png\" onerror=\"alert(1)\">");
            assertEquals("<img src=\"https://cdn.example/a.png\">", img);
            assertEquals("<p>文字</p>", HtmlSanitizer.sanitize("<p style=\"position:fixed;top:0\" onclick=\"alert(1)\">文字</p>"));
        }

        @Test
        @DisplayName("链接只允许安全协议，并强制 rel")
        void sanitizeLinks() {
            String js = HtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">点我</a>");
            assertFalse(js.contains("javascript"), js);
            assertEquals("<a href=\"https://tuoyu.example/doc\" target=\"_blank\" rel=\"noopener noreferrer nofollow\">文档</a>",
                HtmlSanitizer.sanitize("<a href=\"https://tuoyu.example/doc\" target=\"_blank\">文档</a>"));
            String encoded = HtmlSanitizer.sanitize("<a href=\"jav&#x09;ascript:alert(1)\">x</a>");
            assertFalse(encoded.toLowerCase().contains("script:"), encoded);
            assertEquals("<a href=\"/system/notice/1\" rel=\"noopener noreferrer nofollow\">详情</a>",
                HtmlSanitizer.sanitize("<a href=\"/system/notice/1\">详情</a>"));
        }

        @Test
        @DisplayName("图片允许 http、https、data 与相对地址")
        void imageSources() {
            String data = "<img src=\"data:image/png;base64,iVBORw0KGgo=\">";
            assertEquals(data, HtmlSanitizer.sanitize(data));
            assertEquals("<img src=\"/resource/oss/1.png\">", HtmlSanitizer.sanitize("<img src=\"/resource/oss/1.png\">"));
            assertEquals("<img>", HtmlSanitizer.sanitize("<img src=\"javascript:alert(1)\">"));
        }

        @Test
        @DisplayName("纯文本中的尖括号被转义，空值原样返回")
        void plainTextAndEmpty() {
            assertEquals("a &lt; b &amp;&amp; c &gt; d", HtmlSanitizer.sanitize("a < b && c > d"));
            assertNull(HtmlSanitizer.sanitize(null));
            assertEquals("", HtmlSanitizer.sanitize(""));
        }

        @Test
        @DisplayName("清洗结果再清洗一次不变")
        void idempotent() {
            String once = HtmlSanitizer.sanitize("<p onclick=x>a<script>b</script><a href=\"https://x.example\">c</a> &amp; &lt;</p>");
            assertEquals(once, HtmlSanitizer.sanitize(once));
        }
    }

    @Nested
    @DisplayName("JSON 入参")
    class Deserialize {

        @Test
        @DisplayName("只清洗标注了 @RichText 的字段，普通字段原样保留")
        void onlyAnnotatedField() {
            Notice notice = MAPPER.readValue("""
                {"title":"<b>标题</b> & <i>x</i>","content":"<p>正文</p><script>alert(1)</script>"}
                """, Notice.class);

            assertEquals("<b>标题</b> & <i>x</i>", notice.getTitle());
            assertEquals("<p>正文</p>", notice.getContent());
        }

        @Test
        @DisplayName("null 保持 null，非字符串报错")
        void nullAndWrongType() {
            assertNull(MAPPER.readValue("{\"content\":null}", Notice.class).getContent());
            assertThrows(Exception.class, () -> MAPPER.readValue("{\"content\":{\"a\":1}}", Notice.class));
            assertThrows(Exception.class, () -> MAPPER.readValue("{\"content\":[\"<p>x</p>\"]}", Notice.class));
        }
    }

    @Nested
    @DisplayName("JSON 响应")
    class Output {

        @Test
        @DisplayName("输出前清洗历史数据中的富文本字段")
        void sanitizeOnOutput() {
            JsonValueEnhancer enhancer = new JsonValueEnhancer(MAPPER, List.of(new RichTextJsonFieldProcessor()));
            Notice notice = new Notice();
            notice.setTitle("<b>标题</b>");
            notice.setContent("<p>旧数据</p><img src=x onerror=alert(1)>");

            Object result = enhancer.enhance(List.of(notice));

            JsonNode node = (JsonNode) result;
            assertEquals("<b>标题</b>", node.get(0).get("title").asString());
            String content = node.get(0).get("content").asString();
            assertTrue(content.startsWith("<p>旧数据</p>"), content);
            assertFalse(content.contains("onerror"), content);
        }

        @Test
        @DisplayName("非字符串值原样返回")
        void nonString() {
            assertEquals(1, new RichTextJsonFieldProcessor().process(null, 1, null));
        }
    }

    @Data
    static class Notice {

        private String title;

        @RichText
        private String content;
    }

}
