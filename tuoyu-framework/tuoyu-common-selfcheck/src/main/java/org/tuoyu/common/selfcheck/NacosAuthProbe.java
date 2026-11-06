package org.tuoyu.common.selfcheck;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * P6：探测 Nacos 服务端是否开启了鉴权。
 * <p>
 * 客户端是否配置了口令由 C1、P1 检查；服务端有没有开鉴权只能实际请求一次才知道：不带令牌读取本服务已加载的公共配置，
 * 被拒绝（401 / 403）说明已开启鉴权，读取成功（200）说明未开启。
 *
 * @author tuoyu
 */
@FunctionalInterface
interface NacosAuthProbe {

    /**
     * 探测结果
     */
    enum State {
        /**
         * 匿名请求被拒绝
         */
        ENABLED,
        /**
         * 匿名请求读取成功
         */
        DISABLED,
        /**
         * 无法判断（连接失败、接口不存在等），只警告，不阻止启动
         */
        UNKNOWN
    }

    /**
     * @param state  结果
     * @param detail 说明（请求的接口与响应码、无法判断的原因），不包含任何口令或令牌
     */
    record Result(State state, String detail) {
    }

    /**
     * 探测目标
     *
     * @param baseUrl   Nacos 地址，含上下文路径，例如 http://127.0.0.1:8848/nacos
     * @param namespace 命名空间
     * @param group     配置分组
     * @param dataId    已加载的公共配置
     */
    record Target(String baseUrl, String namespace, String group, String dataId) {
    }

    Result probe(Target target);

    /**
     * 通过 HTTP 探测：先用 2.x 的 v1 接口，接口不存在时再用 3.x 的 v3 客户端接口
     */
    NacosAuthProbe HTTP = new HttpProbe(Duration.ofSeconds(3));

    final class HttpProbe implements NacosAuthProbe {

        private final Duration timeout;

        HttpProbe(Duration timeout) {
            this.timeout = timeout;
        }

        @Override
        public Result probe(Target target) {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
            List<String> inconclusive = new ArrayList<>();
            for (URI uri : candidates(target)) {
                int status;
                try {
                    status = client.send(HttpRequest.newBuilder(uri).timeout(timeout).GET().build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode();
                } catch (IOException e) {
                    return new Result(State.UNKNOWN, "请求 " + uri.getPath() + " 失败：" + e.getClass().getSimpleName());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new Result(State.UNKNOWN, "探测被中断");
                }
                if (status == 401 || status == 403) {
                    return new Result(State.ENABLED, uri.getPath() + " 拒绝匿名请求（" + status + "）");
                }
                if (status == 200) {
                    return new Result(State.DISABLED, "不带令牌即可读取配置 " + target.dataId() + "（" + uri.getPath() + "）");
                }
                inconclusive.add(uri.getPath() + " 返回 " + status);
            }
            return new Result(State.UNKNOWN, String.join("；", inconclusive));
        }

        static List<URI> candidates(Target target) {
            boolean publicNamespace = target.namespace() == null || target.namespace().isBlank()
                || "public".equals(target.namespace());
            Map<String, String> v1 = new LinkedHashMap<>();
            v1.put("dataId", target.dataId());
            v1.put("group", target.group());
            if (!publicNamespace) {
                v1.put("tenant", target.namespace());
            }
            Map<String, String> v3 = new LinkedHashMap<>();
            v3.put("dataId", target.dataId());
            v3.put("groupName", target.group());
            v3.put("namespaceId", publicNamespace ? "public" : target.namespace());
            return List.of(
                URI.create(target.baseUrl() + "/v1/cs/configs?" + query(v1)),
                URI.create(target.baseUrl() + "/v3/client/cs/config?" + query(v3)));
        }

        private static String query(Map<String, String> params) {
            return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        }
    }

}
