package com.readmanga.backend.comic.plan;

import com.readmanga.backend.ai.AiChatClient;
import com.readmanga.backend.ai.ChatMessage;
import com.readmanga.backend.ai.ChatResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * AI에게 묻고, 답을 검사해서 문제가 있으면 문제를 알려 주며 다시 묻는다.
 * 문제가 가장 적은 답을 돌려주고, 문제 수가 같으면 지적을 받고 고친 나중 답을 고른다.
 * (시험 단계의 ask_with_retry를 옮긴 것)
 */
@Component
public class AiAsker {

    private static final Logger log = LoggerFactory.getLogger(AiAsker.class);
    private static final String RETRY_INSTRUCTIONS = """


            방금 쓴 결과에 아래 문제가 있었다. 문제를 모두 고쳐서 처음부터 다시 써라.
            %s
            """;

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
            T value = jsonMapper.readValue(result.content(), type);
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
        return new Answer<>(best, bestProblems, attempts);
    }
}
