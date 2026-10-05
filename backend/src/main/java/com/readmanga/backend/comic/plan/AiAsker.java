package com.readmanga.backend.comic.plan;

import com.readmanga.backend.ai.AiChatClient;
import com.readmanga.backend.ai.ChatMessage;
import com.readmanga.backend.ai.ChatResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * AI에게 묻고, 답을 검사해서 문제가 있으면 문제를 알려 주며 다시 묻는다.
 * 문제가 가장 적은 답을 돌려주고, 문제 수가 같으면 지적을 받고 고친 나중 답을 고른다.
 * 답이 잘렸거나 JSON이 깨졌으면 작업을 실패시키지 않고 다시 묻는다. 모든 시도가 깨졌을 때만 실패한다.
 * (시험 단계의 ask_with_retry를 옮긴 것)
 */
@Component
public class AiAsker {

    private static final Logger log = LoggerFactory.getLogger(AiAsker.class);
    private static final String RETRY_INSTRUCTIONS = """


            방금 쓴 결과에 아래 문제가 있었다. 문제를 모두 고쳐서 처음부터 다시 써라.
            %s
            """;
    private static final String BROKEN_ANSWER = "방금 답은 너무 길어 잘렸거나 정해진 JSON 모양이 아니었다. "
            + "같은 말을 반복하지 말고, 정해진 모양대로 짧게 처음부터 다시 써라.";

    private final AiChatClient aiChatClient;
    private final JsonMapper jsonMapper;

    public AiAsker(AiChatClient aiChatClient, JsonMapper jsonMapper) {
        this.aiChatClient = aiChatClient;
        this.jsonMapper = jsonMapper;
    }

    /** 결과와 남은 문제, 시도마다 걸린 시간과 문제 수. */
    public record Answer<T>(T value, List<String> problems, List<Attempt> attempts) {
    }

    public record Attempt(String server, long millis, int problems) {
    }

    public <T> Answer<T> ask(String model, String system, String prompt, Map<String, Object> schema, Class<T> type,
                             Function<T, List<String>> check, int maxRetries) {
        List<ChatMessage> messages = new ArrayList<>(List.of(ChatMessage.system(system), ChatMessage.user(prompt)));
        List<Attempt> attempts = new ArrayList<>();
        T best = null;
        List<String> bestProblems = null;

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            // 아래에서 messages에 다시 쓰기 요청을 덧붙이므로, 넘길 때는 바뀌지 않는 복사본을 넘긴다
            ChatResult result = aiChatClient.chat(model, List.copyOf(messages), schema);
            T value = parse(result, type);
            if (value == null) {
                // 깨진 답은 대화에 넣지 않는다 (반복된 글자가 수천 토큰일 수 있어 다음 요청의 기억 공간을 차지한다)
                attempts.add(new Attempt(result.server().getLabel(), result.millis(), -1));
                log.warn("{} 시도 {}: 답이 {} 다시 묻습니다", model, attempt, result.truncated() ? "잘려서" : "JSON 모양이 아니라서");
                messages.add(ChatMessage.user(BROKEN_ANSWER));
                continue;
            }
            List<String> problems = check.apply(value);
            attempts.add(new Attempt(result.server().getLabel(), result.millis(), problems.size()));
            log.debug("{} 시도 {}: {}ms, 문제 {}개", model, attempt, result.millis(), problems.size());

            if (bestProblems == null || problems.size() <= bestProblems.size()) {
                best = value;
                bestProblems = problems;
            }
            if (problems.isEmpty()) {
                break;
            }
            messages.add(ChatMessage.assistant(result.content()));
            messages.add(ChatMessage.user(RETRY_INSTRUCTIONS.formatted(String.join("\n", problems.stream().map(p -> "- " + p).toList()))));
        }
        if (best == null) {
            throw new IllegalStateException("AI의 답이 %d번 모두 잘렸거나 정해진 모양이 아니었습니다 (%s)".formatted(attempts.size(), model));
        }
        return new Answer<>(best, bestProblems, attempts);
    }

    /** 답을 정해진 모양으로 읽는다. 잘렸거나 JSON이 아니면 null. */
    private <T> T parse(ChatResult result, Class<T> type) {
        if (result.truncated()) {
            return null;
        }
        try {
            return jsonMapper.readValue(result.content(), type);
        } catch (JacksonException e) {
            return null;
        }
    }
}
