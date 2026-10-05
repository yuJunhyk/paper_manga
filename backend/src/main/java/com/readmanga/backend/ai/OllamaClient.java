package com.readmanga.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 서버(Ollama)에 요청을 보낸다.
 * 주 서버(PRIMARY)가 살아 있으면 주 서버를, 응답이 없으면 예비 서버(FALLBACK)를 쓴다.
 * 주 서버에 보낸 요청이 도중에 끊겨도(연결 오류·시간 초과) 예비 서버로 한 번 더 보낸다.
 * 예: GPU가 있는 데스크탑을 주 서버로, 개발하는 노트북을 예비 서버로 두면 데스크탑이 꺼져 있어도 느리게나마 동작한다.
 *
 * 요청이 실패한 서버는 한동안 건너뛴다(서킷 브레이커). 실제 실행에서 데스크탑이 "살아 있니?"에는 답하면서
 * 생성은 멎은 상태(gray failure)가 되어, 요청마다 제한 시간까지 기다리는 일이 반복됐기 때문이다.
 * 건너뛰는 시간은 실패 종류마다 다르다: 응답이 멎은 서버(시간 초과)는 길게, 연결이 끊긴 서버(재시작 중)는 짧게.
 * 쓸 수 있는 서버가 하나도 없으면 바로 실패하지 않고 reconnectWait 동안 다시 연결되기를 기다린다(재연결 대기).
 * 실제 실행에서 예비 서버가 재시작되는 몇 초 사이에 요청이 끊겨 작업 전체가 실패한 일이 있었다(49번).
 */
@Component
public class OllamaClient implements AiChatClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Duration MAX_RECONNECT_POLL = Duration.ofSeconds(5);

    private final OllamaProperties properties;
    private final Clock clock;
    private final Map<OllamaServer, RestClient> healthClients = new EnumMap<>(OllamaServer.class);
    private final Map<OllamaServer, RestClient> chatClients = new EnumMap<>(OllamaServer.class);
    private final Map<OllamaServer, Instant> pausedUntil = new ConcurrentHashMap<>();
    private volatile OllamaServer lastUsed;

    @Autowired  // 생성자가 둘이라 Spring이 쓸 것을 표시한다
    public OllamaClient(OllamaProperties properties) {
        this(properties, Clock.systemDefaultZone());
    }

    /** 테스트에서 시간을 움직일 수 있게 시계를 받는다. */
    OllamaClient(OllamaProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        for (OllamaServer server : OllamaServer.values()) {
            // 살아 있는지 확인할 때는 짧게, 대본을 쓸 때는 길게 기다린다
            healthClients.put(server, restClient(urlOf(server), properties.healthTimeout(), properties.healthTimeout()));
            Duration requestTimeout = server == OllamaServer.PRIMARY ? properties.requestTimeout() : properties.fallbackRequestTimeout();
            chatClients.put(server, restClient(urlOf(server), properties.healthTimeout(), requestTimeout));
        }
    }

    private static RestClient restClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    private String urlOf(OllamaServer server) {
        return server == OllamaServer.PRIMARY ? properties.primaryUrl() : properties.fallbackUrl();
    }

    /** 서버 상태. 건너뛰는 중이면 살아 있어도 쓸 수 없다고 본다. 아니면 버전 정보를 물어 살아 있는지 확인한다. */
    public ServerStatus status(OllamaServer server) {
        Instant paused = pausedUntil.get(server);
        if (paused != null && clock.instant().isBefore(paused)) {
            return new ServerStatus(server, server.getLabel(), urlOf(server), false, null,
                    "최근 요청이 실패해 %s까지 건너뜁니다".formatted(TIME.format(paused)), paused);
        }
        try {
            Map<String, Object> body = healthClients.get(server).get().uri("/api/version").retrieve().body(JSON_OBJECT);
            String version = body == null ? null : String.valueOf(body.get("version"));
            return new ServerStatus(server, server.getLabel(), urlOf(server), true, version, null, null);
        } catch (RestClientException e) {
            return new ServerStatus(server, server.getLabel(), urlOf(server), false, null, e.getMessage(), null);
        }
    }

    /**
     * AI에게 대화를 보내고, schema 모양의 JSON 글자로 답을 받는다.
     * 주 서버 → 예비 서버 순서로, 건너뛰는 중이 아니고 살아 있는 서버에 보낸다.
     * 쓸 수 있는 서버가 없으면 reconnectWait 동안 몇 초 간격으로 다시 확인하고, 그래도 없으면 AiUnavailableException.
     */
    @Override
    public ChatResult chat(String model, List<ChatMessage> messages, Map<String, Object> schema) {
        long deadline = System.nanoTime() + properties.reconnectWait().toNanos();
        Duration poll = pollInterval();
        RestClientException lastError = null;
        while (true) {
            for (OllamaServer server : OllamaServer.values()) {
                ServerStatus status = status(server);
                if (!status.available()) {
                    log.debug("{} AI 서버를 쓸 수 없음: {}", server.getLabel(), status.error());
                    continue;
                }
                try {
                    ChatResult result = send(server, model, messages, schema);
                    pausedUntil.remove(server);
                    noteServer(server);
                    return result;
                } catch (RestClientException e) {
                    // 연결이 끊기거나 시간이 초과된 경우만 다음 서버로 넘긴다 (모델 이름이 틀린 것 같은 오류는 바로 알린다)
                    if (!isConnectionFailure(e)) {
                        throw e;
                    }
                    pause(server, e);
                    lastError = e;
                }
            }
            if (poll.isZero() || System.nanoTime() + poll.toNanos() > deadline) {
                break;
            }
            log.warn("쓸 수 있는 AI 서버가 없어 {}초 뒤 다시 확인합니다 (재연결 대기)", poll.toSeconds() == 0 ? "1" : poll.toSeconds());
            sleep(poll);
        }
        throw new AiUnavailableException("주 서버와 예비 서버가 모두 응답하지 않습니다", lastError);
    }

    /** 건너뛰기를 모두 풀고 두 서버의 상태를 다시 확인한다 (예: 멎은 서버를 고친 뒤 기다리지 않고 바로 다시 쓰고 싶을 때). */
    public List<ServerStatus> reset() {
        pausedUntil.clear();
        log.info("AI 서버 건너뛰기를 모두 풀었습니다 (재연결)");
        return List.of(OllamaServer.values()).stream().map(this::status).toList();
    }

    /** 재연결 대기 중 다시 확인하는 간격: 기다리는 시간의 1/5, 최대 5초 */
    private Duration pollInterval() {
        Duration fifth = properties.reconnectWait().dividedBy(5);
        return fifth.compareTo(MAX_RECONNECT_POLL) > 0 ? MAX_RECONNECT_POLL : fifth;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("재연결을 기다리다 중단되었습니다", null);
        }
    }

    /**
     * 연결 오류·시간 초과인지. 연결 자체가 안 되면 ResourceAccessException이지만, 답을 기다리다 시간이 초과되면
     * 일반 RestClientException 안에 SocketTimeoutException이 들어온다(테스트로 확인). 둘 다 서버 탓이라 다음 서버로 넘긴다.
     * HTTP 오류 응답(4xx·5xx)은 요청 내용 문제일 수 있어 넘기지 않는다.
     */
    private static boolean isConnectionFailure(RestClientException e) {
        if (e instanceof ResourceAccessException) {
            return true;
        }
        if (e instanceof RestClientResponseException) {
            return false;
        }
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    /** 실패한 서버를 한동안 건너뛰게 표시한다. 시간 초과(멎음)는 길게, 연결 끊김(재시작 중)은 짧게. */
    private void pause(OllamaServer server, RestClientException cause) {
        boolean timedOut = hasCause(cause, SocketTimeoutException.class);
        Instant until = clock.instant().plus(timedOut ? properties.failureCooldown() : properties.reconnectCooldown());
        pausedUntil.put(server, until);
        log.warn("{} AI 서버에 보낸 요청이 {} 실패해 {}까지 건너뜁니다: {}", server.getLabel(), timedOut ? "시간 초과로" : "연결 끊김으로",
                TIME.format(until), cause.getMessage());
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private ChatResult send(OllamaServer server, String model, List<ChatMessage> messages, Map<String, Object> schema) {
        // 대본 쓰기 모델은 따로 정한 길이를 쓴다 (GPU 메모리에 맞추기 위해 모델마다 다르게 둘 수 있다)
        int contextLength = model.equals(properties.dialogueModel()) ? properties.dialogueContextLength() : properties.contextLength();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("format", schema);
        body.put("stream", false);
        // 출력 상한: 데스크탑 Ollama는 기억 공간이 차도 생성을 이어 가는 설정(--context-shift)으로 돌아서,
        // 모델이 같은 말을 반복하는 고리에 빠지면 답이 끝나지 않고 생성 창구(1개)를 계속 차지했다
        body.put("options", Map.of("num_ctx", contextLength, "num_predict", properties.maxOutputTokens(), "temperature", 0.4,
                "repeat_penalty", properties.repeatPenalty()));
        if (model.startsWith("qwen3") || model.startsWith("gemma4")) {
            // '생각하기'를 끄고 바로 답하게 한다 (시험 단계와 같은 설정). gemma4도 그냥 두면 생각하기가 켜져 출력이 10배 가까이 길어진다
            body.put("think", false);
        }

        long started = System.nanoTime();
        Map<String, Object> response;
        try {
            response = chatClients.get(server).post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JSON_OBJECT);
        } catch (HttpServerErrorException e) {
            // Ollama가 반복 고리를 스스로 알아채고 생성을 멈춘 경우(500 "token repeat limit reached").
            // 서버 고장이 아니라 답이 깨진 것이므로, 잘린 답으로 돌려 다시 묻게 한다
            if (!e.getResponseBodyAsString().contains("repeat limit")) {
                throw e;
            }
            long millis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            log.warn("{} AI 서버가 같은 말 반복을 알아채고 생성을 멈췄습니다 ({}, {}ms)", server.getLabel(), model, millis);
            return new ChatResult("", server, null, null, millis, true);
        }
        long millis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        if (response == null || !(response.get("message") instanceof Map<?, ?> message)) {
            throw new IllegalStateException(server.getLabel() + " AI 서버의 답에 message가 없습니다: " + response);
        }
        boolean truncated = "length".equals(response.get("done_reason"));
        String content = String.valueOf(message.get("content"));
        if (truncated) {
            log.warn("{} AI 서버의 답이 출력 상한({}토큰)에 걸려 잘렸습니다 ({}, {}ms). 같은 말을 반복하는 고리에 빠졌을 수 있습니다. 끝부분: {}",
                    server.getLabel(), properties.maxOutputTokens(), model, millis, tail(content));
        }
        return new ChatResult(content, server,
                toInteger(response.get("prompt_eval_count")), toInteger(response.get("eval_count")), millis, truncated);
    }

    /** 잘린 답의 끝부분 (무엇을 반복했는지 보려고). 줄바꿈은 보이게 바꾼다. */
    private static String tail(String content) {
        String end = content.length() > 120 ? content.substring(content.length() - 120) : content;
        return "\"" + end.replace("\n", "\\n") + "\"";
    }

    private static Integer toInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    /** 쓰는 서버가 바뀔 때만 로그를 남긴다. 예비 서버로 바뀌면 느려질 수 있다는 것을 경고한다. */
    private void noteServer(OllamaServer server) {
        if (server == lastUsed) {
            return;
        }
        if (server == OllamaServer.FALLBACK) {
            log.warn("주 서버가 응답하지 않아 예비 서버로 전환했습니다. 예비 서버 성능에 따라 처리 시간이 길어질 수 있습니다.");
        } else {
            log.info("{} AI 서버({})를 사용합니다.", server.getLabel(), urlOf(server));
        }
        lastUsed = server;
    }
}
