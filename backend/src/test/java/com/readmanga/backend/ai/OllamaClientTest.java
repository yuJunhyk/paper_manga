package com.readmanga.backend.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 가짜 Ollama 서버(JDK 내장 HTTP 서버)를 띄워서, 주 서버 우선·예비 서버 자동 전환이 맞게 동작하는지 확인한다.
 * 실제 AI 서버나 네트워크 없이 이 컴퓨터 안에서만 돈다.
 */
class OllamaClientTest {

    private static final String CLOSED_PORT_URL = "http://127.0.0.1:1";  // 아무도 기다리지 않는 포트 = 꺼진 서버
    private static final Map<String, Object> SCHEMA = Map.of("type", "object");

    private final List<HttpServer> servers = new ArrayList<>();
    private final List<String> receivedBodies = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    /** /api/version과 /api/chat에 답하는 가짜 Ollama 서버. answer에는 어느 서버인지 적어 돌려준다. */
    private String fakeOllama(String name) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/version", exchange -> respond(exchange, "{\"version\":\"fake\"}"));
        server.createContext("/api/chat", exchange -> {
            receivedBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, "{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"answer\\\":\\\"" + name + "\\\"}\"},"
                    + "\"prompt_eval_count\":12,\"eval_count\":5}");
        });
        server.start();
        servers.add(server);
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static OllamaClient client(String primaryUrl, String fallbackUrl) {
        return new OllamaClient(new OllamaProperties(primaryUrl, fallbackUrl, Duration.ofMillis(500), Duration.ofSeconds(5),
                16384, "qwen3:8b", "exaone3.5:7.8b"));
    }

    private static List<ChatMessage> hello() {
        return List.of(ChatMessage.user("안녕"));
    }

    @Test
    void 주_서버가_살아_있으면_주_서버를_쓴다() throws IOException {
        OllamaClient client = client(fakeOllama("desktop"), fakeOllama("macbook"));

        ChatResult result = client.chat("qwen3:8b", hello(), SCHEMA);

        assertThat(result.server()).isEqualTo(OllamaServer.PRIMARY);
        assertThat(result.content()).contains("desktop");
        assertThat(result.promptTokens()).isEqualTo(12);
        assertThat(result.outputTokens()).isEqualTo(5);
    }

    @Test
    void 주_서버가_응답하지_않으면_예비_서버로_전환한다() throws IOException {
        OllamaClient client = client(CLOSED_PORT_URL, fakeOllama("macbook"));

        ChatResult result = client.chat("exaone3.5:7.8b", hello(), SCHEMA);

        assertThat(client.status(OllamaServer.PRIMARY).available()).isFalse();
        assertThat(result.server()).isEqualTo(OllamaServer.FALLBACK);
        assertThat(result.content()).contains("macbook");
    }

    @Test
    void 둘_다_응답하지_않으면_AI를_쓸_수_없다는_예외를_던진다() {
        OllamaClient client = client(CLOSED_PORT_URL, CLOSED_PORT_URL);

        assertThatThrownBy(() -> client.chat("qwen3:8b", hello(), SCHEMA))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void qwen3에는_생각하기를_끄고_읽을_길이를_함께_보낸다() throws IOException {
        OllamaClient client = client(fakeOllama("desktop"), CLOSED_PORT_URL);

        client.chat("qwen3:8b", hello(), SCHEMA);
        client.chat("exaone3.5:7.8b", hello(), SCHEMA);

        assertThat(receivedBodies.get(0)).contains("\"think\":false").contains("\"num_ctx\":16384").contains("\"stream\":false");
        assertThat(receivedBodies.get(1)).doesNotContain("think");
    }
}
