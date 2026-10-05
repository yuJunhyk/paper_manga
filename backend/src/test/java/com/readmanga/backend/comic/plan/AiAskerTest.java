package com.readmanga.backend.comic.plan;

import com.readmanga.backend.ai.AiChatClient;
import com.readmanga.backend.ai.ChatMessage;
import com.readmanga.backend.ai.ChatResult;
import com.readmanga.backend.ai.OllamaServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 잘리거나 깨진 답을 받았을 때 작업을 실패시키지 않고 다시 묻는지 확인한다. */
class AiAskerTest {

    record Title(String title) {
    }

    /** 정해 둔 답을 차례로 돌려주는 가짜 AI */
    static class ScriptedAi implements AiChatClient {

        final Deque<ChatResult> answers = new ArrayDeque<>();
        final List<List<ChatMessage>> requests = new ArrayList<>();

        ScriptedAi answer(String content, boolean truncated) {
            answers.add(new ChatResult(content, OllamaServer.PRIMARY, 10, 5, 1, truncated));
            return this;
        }

        @Override
        public ChatResult chat(String model, List<ChatMessage> messages, Map<String, Object> schema) {
            requests.add(messages);
            return answers.removeFirst();
        }
    }

    private static AiAsker.Answer<Title> ask(ScriptedAi ai) {
        return new AiAsker(ai, JsonMapper.builder().build())
                .ask("exaone3.5:7.8b", "system", "prompt", Map.of(), Title.class, title -> List.of(), 2);
    }

    @Test
    void 잘린_답은_버리고_다시_물어_정상_답을_쓴다() {
        ScriptedAi ai = new ScriptedAi()
                .answer("{\"title\":\"                                ", true)   // 반복하다 상한에 걸림
                .answer("{\"title\":\"잠재 공간 이야기\"}", false);

        AiAsker.Answer<Title> answer = ask(ai);

        assertThat(answer.value().title()).isEqualTo("잠재 공간 이야기");
        assertThat(answer.attempts()).extracting(AiAsker.Attempt::problems).containsExactly(-1, 0);
        List<ChatMessage> retry = ai.requests.get(1);
        assertThat(retry).as("깨진 답은 대화에 넣지 않고 다시 쓰라는 말만 덧붙인다").hasSize(3);
        assertThat(retry.getLast().content()).contains("잘렸거나");
    }

    @Test
    void JSON이_아닌_답도_다시_묻는다() {
        ScriptedAi ai = new ScriptedAi()
                .answer("죄송합니다, 다시 설명드리면", false)
                .answer("{\"title\":\"잠재 공간 이야기\"}", false);

        assertThat(ask(ai).value().title()).isEqualTo("잠재 공간 이야기");
    }

    @Test
    void 모든_시도가_깨지면_무엇이_잘못됐는지_알리고_실패한다() {
        ScriptedAi ai = new ScriptedAi()
                .answer("{\"title\":", true)
                .answer("{\"title\":", true)
                .answer("not json", false);

        assertThatThrownBy(() -> ask(ai)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3번 모두 잘렸거나 정해진 모양이 아니었습니다");
    }
}
