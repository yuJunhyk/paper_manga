package com.readmanga.backend.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** AI 서버 연결을 확인하는 개발용 API. (Spring이 127.0.0.1에서만 접속을 받으므로 서버를 돌리는 컴퓨터 안에서만 쓸 수 있다) */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Map<String, Object> ANSWER_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("answer", Map.of("type", "string")),
            "required", List.of("answer"));

    private final OllamaClient ollamaClient;
    private final OllamaProperties properties;

    public AiController(OllamaClient ollamaClient, OllamaProperties properties) {
        this.ollamaClient = ollamaClient;
        this.properties = properties;
    }

    /** 주 서버·예비 서버의 상태와, 지금 요청하면 어느 서버가 쓰일지. */
    @GetMapping("/status")
    public StatusResponse status() {
        List<ServerStatus> servers = Arrays.stream(OllamaServer.values()).map(ollamaClient::status).toList();
        String active = servers.stream().filter(ServerStatus::available).map(ServerStatus::label).findFirst().orElse("없음");
        return new StatusResponse(active, servers);
    }

    /** 재연결: 실패해서 건너뛰던 서버를 바로 다시 쓰게 한다. 멎은 서버를 고친 뒤 건너뛰는 시간이 끝나길 기다리지 않아도 된다. */
    @PostMapping("/reset")
    public StatusResponse reset() {
        List<ServerStatus> servers = ollamaClient.reset();
        String active = servers.stream().filter(ServerStatus::available).map(ServerStatus::label).findFirst().orElse("없음");
        return new StatusResponse(active, servers);
    }

    /** AI에게 짧은 인사를 시켜 실제로 답이 오는지 확인한다. */
    @GetMapping("/hello")
    public HelloResponse hello(@RequestParam(name = "model", required = false) String model) {
        String target = model == null ? properties.factModel() : model;
        ChatResult result = ollamaClient.chat(target, List.of(
                ChatMessage.system("너는 학습 만화의 친절한 고양이 박사 냥박사다. 콩이에게 반말로 말한다."),
                ChatMessage.user("콩이에게 한 문장으로 인사해 줘. answer 필드에 넣어.")), ANSWER_SCHEMA);
        return new HelloResponse(target, result.server().getLabel(), result.content(), result.millis(), result.outputTokens());
    }

    public record StatusResponse(String active, List<ServerStatus> servers) {
    }

    public record HelloResponse(String model, String server, String answer, long millis, Integer outputTokens) {
    }
}
