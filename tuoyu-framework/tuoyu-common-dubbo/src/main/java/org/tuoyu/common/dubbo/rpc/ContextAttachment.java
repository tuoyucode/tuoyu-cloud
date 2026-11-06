package org.tuoyu.common.dubbo.rpc;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 上下文在 Dubbo 附件中的编码：全部上下文放在一个附件里（{@code k=v&k=v}，键值 URL 编码），
 * 避免不同协议对附件键大小写的处理不同
 *
 * @author tuoyu
 */
final class ContextAttachment {

    static final String KEY = "tuoyu-context";

    private ContextAttachment() {
    }

    static String encode(Map<String, String> values) {
        return values.entrySet().stream()
            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
            .collect(Collectors.joining("&"));
    }

    static Map<String, String> decode(String value) {
        Map<String, String> values = new HashMap<>();
        if (value == null || value.isEmpty()) {
            return values;
        }
        for (String pair : value.split("&")) {
            int separator = pair.indexOf('=');
            if (separator > 0) {
                values.put(decode0(pair.substring(0, separator)), decode0(pair.substring(separator + 1)));
            }
        }
        return values;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String decode0(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

}
