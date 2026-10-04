package com.readmanga.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 서버(Ollama)에 요청을 보낸다.
 * 주 서버(PRIMARY)가 살아 있으면 주 서버를, 응답이 없으면 예비 서버(FALLBACK)를 쓴다.
 * 주 서버에 보낸 요청이 도중에 끊겨도(연결 오류·시간 초과) 예비 서버로 한 번 더 보낸다.
 * 예: GPU가 있는 데스크탑을 주 서버로, 개발하는 노트북을 예비 서버로 두면 데스크탑이 꺼져 있어도 느리게나마 동작한다.
 */
@Component
public class OllamaClient implements AiChatClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
    };

    private final OllamaProperties properties;
    private final Map<OllamaServer, RestClient> healthClients = new EnumMap<>(OllamaServer.class);
    private final Map<OllamaServer, RestClient> chatClients = new EnumMap<>(OllamaServer.class);
    private volatile OllamaServer lastUsed;

    public OllamaClient(OllamaProperties properties) {
        this.properties = properties;
        for (OllamaServer server : OllamaServer.values()) {
            // 살아 있는지 확인할 때는 짧게, 대본을 쓸 때는 길게 기다린다
            healthClients.put(server, restClient(urlOf(server), properties.healthTimeout(), properties.healthTimeout()));
            chatClients.put(server, restClient(urlOf(server), properties.healthTimeout(), properties.requestTimeout()));
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

    /** 서버가 살아 있는지 버전 정보를 물어 확인한다. */
    public ServerStatus status(OllamaServer server) {
        try {
            Map<String, Object> body = healthClients.get(server).get().uri("/api/version").retrieve().body(JSON_OBJECT);
            String version = body == null ? null : String.valueOf(body.get("version"));
            return new ServerStatus(server, server.getLabel(), urlOf(server), true, version, null);
        } catch (RestClientException e) {
            return new ServerStatus(server, server.getLabel(), urlOf(server), false, null, e.getMessage());
        }
    }

    /**
     * AI에게 대화를 보내고, schema 모양의 JSON 글자로 답을 받는다.
     * 주 서버 → 예비 서버 순서로 살아 있는 서버에 보낸다.
     */
    @Override
    public ChatResult chat(String model, List<ChatMessage> messages, Map<String, Object> schema) {
        RestClientException lastError = null;
        for (OllamaServer server : OllamaServer.values()) {
            ServerStatus status = status(server);
            if (!status.available()) {
                log.debug("{} AI 서버 응답 없음: {}", server.getLabel(), status.error());
                continue;
            }
            try {
                ChatResult result = send(server, model, messages, schema);
                noteServer(server);
                return result;
            } catch (ResourceAccessException e) {
                // 연결이 끊기거나 시간이 초과된 경우만 다음 서버로 넘긴다 (모델 이름이 틀린 것 같은 오류는 바로 알린다)
                log.warn("{} AI 서버에 보낸 요청이 실패해 다음 서버로 넘깁니다: {}", server.getLabel(), e.getMessage());
                lastError = e;
            }
        }
        throw new AiUnavailableException("주 서버와 예비 서버가 모두 응답하지 않습니다", lastError);
    }

    private ChatResult send(OllamaServer server, String model, List<ChatMessage> messages, Map<String, Object> schema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("format", schema);
        body.put("stream", false);
        body.put("options", Map.of("num_ctx", properties.contextLength(), "temperature", 0.4));
        if (model.startsWith("qwen3")) {
            body.put("think", false);  // qwen3의 '생각하기'를 끄고 바로 답하게 한다 (시험 단계와 같은 설정)
        }

        long started = System.nanoTime();
        Map<String, Object> response = chatClients.get(server).post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JSON_OBJECT);
        long millis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        if (response == null || !(response.get("message") instanceof Map<?, ?> message)) {
            throw new IllegalStateException(server.getLabel() + " AI 서버의 답에 message가 없습니다: " + response);
        }
        return new ChatResult(String.valueOf(message.get("content")), server,
                toInteger(response.get("prompt_eval_count")), toInteger(response.get("eval_count")), millis);
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
