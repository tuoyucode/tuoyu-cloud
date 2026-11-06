package org.tuoyu.common.selfcheck;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("P6 Nacos 鉴权的 HTTP 探测")
class NacosAuthProbeTest {

    private final List<String> requests = new CopyOnWriteArrayList<>();

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 启动一个本地服务，按路径返回指定状态码，未列出的路径返回 404
     */
    private NacosAuthProbe.Target serve(Map<String, Integer> statusByPath) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            exchange.sendResponseHeaders(statusByPath.getOrDefault(exchange.getRequestURI().getPath(), 404), -1);
            exchange.close();
        });
        server.start();
        return new NacosAuthProbe.Target("http://127.0.0.1:" + server.getAddress().getPort() + "/nacos",
            "tuoyu", "DEFAULT_GROUP", "application-common.yml");
    }

    private NacosAuthProbe.Result probe(NacosAuthProbe.Target target) {
        return new NacosAuthProbe.HttpProbe(Duration.ofSeconds(2)).probe(target);
    }

    @Test
    @DisplayName("2.x：v1 接口拒绝匿名请求时判定为已开启鉴权，不再请求 v3")
    void enabledOnV1() throws IOException {
        NacosAuthProbe.Result result = probe(serve(Map.of("/nacos/v1/cs/configs", 403)));

        assertEquals(NacosAuthProbe.State.ENABLED, result.state());
        assertEquals(List.of("/nacos/v1/cs/configs?dataId=application-common.yml&group=DEFAULT_GROUP&tenant=tuoyu"), requests);
    }

    @Test
    @DisplayName("匿名请求读取成功时判定为未开启鉴权")
    void disabledOnV1() throws IOException {
        assertEquals(NacosAuthProbe.State.DISABLED, probe(serve(Map.of("/nacos/v1/cs/configs", 200))).state());
    }

    @Test
    @DisplayName("3.x：v1 接口不存在时改用 v3 客户端接口")
    void fallsBackToV3() throws IOException {
        NacosAuthProbe.Result result = probe(serve(Map.of("/nacos/v3/client/cs/config", 401)));

        assertEquals(NacosAuthProbe.State.ENABLED, result.state());
        assertEquals("/nacos/v3/client/cs/config?dataId=application-common.yml&groupName=DEFAULT_GROUP&namespaceId=tuoyu",
            requests.get(1));
    }

    @Test
    @DisplayName("两个接口都无法判断时返回 UNKNOWN 并说明响应码")
    void unknownWhenInconclusive() throws IOException {
        NacosAuthProbe.Result result = probe(serve(Map.of()));

        assertEquals(NacosAuthProbe.State.UNKNOWN, result.state());
        assertTrue(result.detail().contains("返回 404"), result.detail());
    }

    @Test
    @DisplayName("连接失败时返回 UNKNOWN")
    void unknownWhenUnreachable() throws IOException {
        NacosAuthProbe.Target target = serve(Map.of());
        server.stop(0);
        server = null;

        assertEquals(NacosAuthProbe.State.UNKNOWN, probe(target).state());
    }

    @Test
    @DisplayName("public 命名空间：v1 不带 tenant，v3 使用 public")
    void publicNamespace() {
        List<String> uris = NacosAuthProbe.HttpProbe.candidates(
                new NacosAuthProbe.Target("http://h:8848/nacos", "", "DEFAULT_GROUP", "application-common.yml"))
            .stream().map(Object::toString).toList();

        assertEquals(List.of(
            "http://h:8848/nacos/v1/cs/configs?dataId=application-common.yml&group=DEFAULT_GROUP",
            "http://h:8848/nacos/v3/client/cs/config?dataId=application-common.yml&groupName=DEFAULT_GROUP&namespaceId=public"), uris);
    }

}
