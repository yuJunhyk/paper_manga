package com.readmanga.backend.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final TestClock clock = new TestClock();

    /** 테스트에서 마음대로 돌릴 수 있는 시계 */
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-05T04:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    /** /api/version과 /api/chat에 답하는 가짜 Ollama 서버. answer에는 어느 서버인지 적어 돌려준다. */
    private String fakeOllama(String name) throws IOException {
        return fakeOllama(name, Duration.ZERO);
    }

    /** delay만큼 답을 미루는 가짜 서버. 요청 제한 시간보다 길게 두면 '살아 있지만 일을 못 하는' 서버가 된다. */
    private String fakeOllama(String name, Duration delay) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/version", exchange -> respond(exchange, "{\"version\":\"fake\"}"));
        server.createContext("/api/chat", exchange -> {
            receivedBodies.add(name + ": " + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, "{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"answer\\\":\\\"" + name + "\\\"}\"},"
                    + "\"prompt_eval_count\":12,\"eval_count\":5}");
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
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

    private OllamaClient client(String primaryUrl, String fallbackUrl) {
        return client(primaryUrl, fallbackUrl, Duration.ofSeconds(5));
    }

    private OllamaClient client(String primaryUrl, String fallbackUrl, Duration requestTimeout) {
        return client(primaryUrl, fallbackUrl, requestTimeout, Duration.ofMillis(200));
    }

    /** 테스트에서는 연결이 끊긴 서버를 건너뛰는 시간을 0으로(시계가 멈춰 있어서), 재연결 대기는 짧게 둔다. */
    private OllamaClient client(String primaryUrl, String fallbackUrl, Duration requestTimeout, Duration reconnectWait) {
        return new OllamaClient(new OllamaProperties(primaryUrl, fallbackUrl, Duration.ofMillis(500), requestTimeout, requestTimeout,
                Duration.ofMinutes(5), Duration.ZERO, reconnectWait, 16384, 8192, 1500, 1.1, "qwen3:8b", "exaone3.5:7.8b"), clock);
    }

    /** 처음 failures번은 답하지 않고 연결을 끊는 가짜 서버 (재시작 중인 서버 흉내). 그 뒤로는 정상으로 답한다. */
    private String flakyOllama(String name, int failures) throws IOException {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/version", exchange -> respond(exchange, "{\"version\":\"fake\"}"));
        server.createContext("/api/chat", exchange -> {
            receivedBodies.add(name + ": " + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (calls.incrementAndGet() <= failures) {
                exchange.close();  // 답 없이 연결을 끊는다
                return;
            }
            respond(exchange, "{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"answer\\\":\\\"" + name + "\\\"}\"}}");
        });
        server.start();
        servers.add(server);
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private long chatRequestsTo(String name) {
        return receivedBodies.stream().filter(body -> body.startsWith(name + ": ")).count();
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
    void qwen3과_gemma4에는_생각하기를_끄고_모델마다_정한_읽을_길이를_보낸다() throws IOException {
        OllamaClient client = client(fakeOllama("desktop"), CLOSED_PORT_URL);

        client.chat("qwen3:8b", hello(), SCHEMA);
        client.chat("exaone3.5:7.8b", hello(), SCHEMA);
        client.chat("gemma4:e4b", hello(), SCHEMA);

        assertThat(receivedBodies.get(0)).contains("\"think\":false").contains("\"num_ctx\":16384").contains("\"stream\":false")
                .contains("\"num_predict\":1500").contains("\"repeat_penalty\":1.1");
        assertThat(receivedBodies.get(1)).doesNotContain("think").contains("\"num_ctx\":8192").contains("\"num_predict\":1500");
        assertThat(receivedBodies.get(2)).contains("\"think\":false");
    }

    @Test
    void Ollama가_반복을_알아채고_멈추면_서버_고장이_아니라_잘린_답으로_돌려준다() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/version", exchange -> respond(exchange, "{\"version\":\"fake\"}"));
        server.createContext("/api/chat", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = "{\"error\":\"prediction aborted, token repeat limit reached\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        servers.add(server);
        OllamaClient client = client("http://127.0.0.1:" + server.getAddress().getPort(), CLOSED_PORT_URL);

        ChatResult result = client.chat("exaone3.5:7.8b", hello(), SCHEMA);

        assertThat(result.truncated()).isTrue();
        assertThat(client.status(OllamaServer.PRIMARY).pausedUntil()).as("서버는 멀쩡하므로 건너뛰지 않는다").isNull();
    }

    @Test
    void 출력_상한에_걸려_잘린_답은_잘렸다고_표시한다() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/version", exchange -> respond(exchange, "{\"version\":\"fake\"}"));
        server.createContext("/api/chat", exchange -> {
            exchange.getRequestBody().readAllBytes();
            // 같은 글자를 반복하다 상한에 걸린 답 (JSON이 닫히지 않음)
            respond(exchange, "{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"scene\\\":\\\"연구실        \"},"
                    + "\"done_reason\":\"length\",\"eval_count\":1500}");
        });
        server.start();
        servers.add(server);
        OllamaClient client = client("http://127.0.0.1:" + server.getAddress().getPort(), CLOSED_PORT_URL);

        ChatResult result = client.chat("exaone3.5:7.8b", hello(), SCHEMA);

        assertThat(result.truncated()).isTrue();
        assertThat(result.outputTokens()).isEqualTo(1500);
    }

    @Test
    void 시간_초과된_서버는_건너뛰고_정해진_시간이_지나면_다시_써_본다() throws IOException {
        // 주 서버는 살아 있다고 답하지만 생성은 3초 걸림 (제한 시간 300ms) → 실제 실행에서 데스크탑이 멎었던 상황
        OllamaClient client = client(fakeOllama("desktop", Duration.ofSeconds(3)), fakeOllama("macbook"), Duration.ofMillis(300));

        ChatResult first = client.chat("exaone3.5:7.8b", hello(), SCHEMA);
        ChatResult second = client.chat("exaone3.5:7.8b", hello(), SCHEMA);

        assertThat(first.server()).isEqualTo(OllamaServer.FALLBACK);
        assertThat(second.server()).isEqualTo(OllamaServer.FALLBACK);
        assertThat(chatRequestsTo("desktop")).as("두 번째 요청은 주 서버를 건너뛰어야 한다").isEqualTo(1);
        ServerStatus status = client.status(OllamaServer.PRIMARY);
        assertThat(status.available()).isFalse();
        assertThat(status.error()).contains("건너뜁니다");
        assertThat(status.pausedUntil()).isEqualTo(clock.now.plus(Duration.ofMinutes(5)));

        clock.advance(Duration.ofMinutes(6));
        client.chat("exaone3.5:7.8b", hello(), SCHEMA);

        assertThat(chatRequestsTo("desktop")).as("건너뛰는 시간이 지나면 주 서버를 다시 써 본다").isEqualTo(2);
    }

    @Test
    void 연결이_끊긴_서버는_잠깐만_건너뛰고_재연결_대기_중에_다시_써서_이어간다() throws IOException {
        // 주 서버는 꺼져 있고, 예비 서버는 재시작 중이라 처음 두 번은 연결이 끊긴다 → 49번 작업이 실패했던 상황
        OllamaClient client = client(CLOSED_PORT_URL, flakyOllama("macbook", 2), Duration.ofSeconds(5), Duration.ofSeconds(3));

        ChatResult result = client.chat("qwen3:8b", hello(), SCHEMA);

        assertThat(result.server()).isEqualTo(OllamaServer.FALLBACK);
        assertThat(result.content()).contains("macbook");
        assertThat(chatRequestsTo("macbook")).as("끊긴 뒤 다시 연결해 이어간다").isGreaterThanOrEqualTo(3);
        assertThat(client.status(OllamaServer.FALLBACK).pausedUntil()).as("성공하면 건너뛰기가 풀린다").isNull();
    }

    @Test
    void 재연결_대기_시간이_지나도_쓸_서버가_없으면_실패한다() {
        OllamaClient client = client(CLOSED_PORT_URL, CLOSED_PORT_URL, Duration.ofSeconds(5), Duration.ofMillis(500));
        long started = System.nanoTime();

        assertThatThrownBy(() -> client.chat("qwen3:8b", hello(), SCHEMA)).isInstanceOf(AiUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).as("바로 포기하지 않고 기다린다").isGreaterThanOrEqualTo(Duration.ofMillis(300));
    }

    @Test
    void 재연결_요청을_하면_건너뛰던_서버를_바로_다시_쓴다() throws IOException {
        OllamaClient client = client(fakeOllama("desktop", Duration.ofSeconds(3)), fakeOllama("macbook"), Duration.ofMillis(300));
        client.chat("exaone3.5:7.8b", hello(), SCHEMA);  // 주 서버 시간 초과 → 5분 건너뛰기
        assertThat(client.status(OllamaServer.PRIMARY).available()).isFalse();

        List<ServerStatus> after = client.reset();

        assertThat(after.getFirst().available()).as("5분을 기다리지 않아도 바로 다시 쓸 수 있다").isTrue();
        assertThat(after.getFirst().pausedUntil()).isNull();
    }

    @Test
    void 건너뛰던_서버가_다시_답하면_건너뛰기를_푼다() throws IOException {
        OllamaClient client = client(fakeOllama("desktop", Duration.ofSeconds(3)), fakeOllama("macbook"), Duration.ofMillis(300));
        client.chat("exaone3.5:7.8b", hello(), SCHEMA);  // 주 서버 시간 초과 → 건너뛰기 시작
        assertThat(client.status(OllamaServer.PRIMARY).pausedUntil()).isNotNull();

        // 주 서버가 정상으로 돌아왔다고 가정: 같은 주소에서 바로 답하는 서버로 바꿀 수 없으니, 시계를 돌려 다시 써 보게 한다
        clock.advance(Duration.ofMinutes(6));
        ServerStatus afterCooldown = client.status(OllamaServer.PRIMARY);

        assertThat(afterCooldown.available()).isTrue();
        assertThat(afterCooldown.pausedUntil()).isNull();
    }
}
