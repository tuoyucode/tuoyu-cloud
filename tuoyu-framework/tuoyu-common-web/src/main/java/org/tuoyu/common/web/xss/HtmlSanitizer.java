package org.tuoyu.common.web.xss;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * 富文本白名单清洗（见 docs/adr/0003-上传-XSS-脱敏与字段加密.md）。
 * <p>
 * 只用于明确需要保存 HTML 的字段（例如通知公告内容），普通字段不清洗、不修改用户输入，由前端在输出时转义。
 * 规则：
 * <ul>
 *     <li>标签：常见的排版、列表、表格、引用、代码、图片与链接，不允许 script、style、iframe、form、object 等；</li>
 *     <li>属性：不允许事件属性（onclick 等）与 style；允许 class、链接的 target；</li>
 *     <li>链接只允许 http、https、mailto 与相对地址，图片只允许 http、https、data 与相对地址，
 *     javascript: 等一律去掉；链接强制加上 rel="noopener noreferrer nofollow"。</li>
 * </ul>
 * 线程安全。
 *
 * @author tuoyu
 */
public final class HtmlSanitizer {

    /**
     * 解析相对地址用的基准地址，只用于判断协议，不会出现在结果中
     */
    private static final String BASE_URI = "https://rich-text.invalid/";

    private static final Safelist SAFELIST = Safelist.relaxed()
        .addTags("hr", "s", "del", "ins", "mark", "figure", "figcaption")
        .addAttributes(":all", "class")
        .addAttributes("a", "target")
        .addAttributes("img", "loading")
        .addProtocols("img", "src", "data")
        .addEnforcedAttribute("a", "rel", "noopener noreferrer nofollow")
        .preserveRelativeLinks(true);

    private static final Document.OutputSettings OUTPUT = new Document.OutputSettings().prettyPrint(false);

    private HtmlSanitizer() {
    }

    /**
     * 按白名单清洗 HTML。
     *
     * @param html 用户提交的 HTML
     * @return 清洗后的 HTML；传入 null 时返回 null
     */
    public static String sanitize(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        return Jsoup.clean(html, BASE_URI, SAFELIST, OUTPUT);
    }

}
