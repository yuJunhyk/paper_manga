package com.readmanga.backend.comic.plan;

import com.readmanga.backend.ai.AiChatClient;
import com.readmanga.backend.ai.ChatMessage;
import com.readmanga.backend.ai.ChatResult;
import com.readmanga.backend.ai.OllamaProperties;
import com.readmanga.backend.ai.OllamaServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가짜 AI로 만화 설계도 전체 흐름을 확인한다. 시험 단계와 실제 실행에서 나온 실수를 일부러 섞는다.
 */
class ComicPlannerTest {

    private static final Pattern SENTENCE_NUMBER = Pattern.compile("^\\[(\\d+)] \\(p\\.", Pattern.MULTILINE);
    private static final Pattern CHUNK_SECTIONS = Pattern.compile("이 부분의 섹션: (.+)");

    /** 시험 단계의 실수를 흉내 내는 가짜 AI. 받은 요청을 기록한다. */
    static class FakeAi implements AiChatClient {

        final List<List<ChatMessage>> requests = new ArrayList<>();

        @Override
        public ChatResult chat(String model, List<ChatMessage> messages, Map<String, Object> schema) {
            requests.add(messages);
            boolean retry = messages.size() > 2;
            String prompt = messages.get(1).content();
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            String content;
            if (properties.containsKey("comicTitle")) {
                // 첫 시도는 '라tent' 섞인 제목 → 다시 쓰기에서 고친다
                content = "{\"comicTitle\":\"%s\"}".formatted(retry ? "잠재 공간 이야기" : "라tent 이야기");
            } else if (properties.containsKey("title")) {
                // 조각 제목: 받은 섹션 이름을 그대로 제목으로 돌려준다 (어느 조각의 요청인지 확인용)
                Matcher matcher = CHUNK_SECTIONS.matcher(prompt);
                String sections = matcher.find() ? matcher.group(1) : "?";
                content = "{\"title\":\"%s\",\"summary\":\"요약\"}".formatted(sections.replace("\"", ""));
            } else {
                content = facts(prompt);
            }
            return new ChatResult(content, OllamaServer.PRIMARY, 100, 50, 10);
        }

        private static String facts(String prompt) {
            List<Integer> numbers = new ArrayList<>();
            Matcher matcher = SENTENCE_NUMBER.matcher(prompt);
            while (matcher.find()) {
                numbers.add(Integer.parseInt(matcher.group(1)));
            }
            int first = numbers.getFirst();
            if (numbers.contains(239)) {
                // 한계·결론 조각: 같은 근거(239)에서 다른 사실 두 개 + 수식 사실 하나
                return """
                        {"facts":[
                          {"fact":"잠재 확산 모델은 픽셀 기반 모델에 비해 계산 요구가 적다.","evidence":[239]},
                          {"fact":"잠재 확산 모델은 GAN에 비해 샘플링 속도가 느리다.","evidence":[239]},
                          {"fact":"이 연구는 조건부 분포 p(z|y)를 다룬다.","evidence":[240]}]}""";
            }
            if (first == 1) {
                // 첫 조각: [11] 번호가 붙은 사실과, 범위 밖 근거(999)를 섞은 사실
                return """
                        {"facts":[
                          {"fact":"확산 모델은 이미지 생성에서 최고 수준의 결과를 낸다 [11].","evidence":[1]},
                          {"fact":"기존 확산 모델은 계산 비용이 매우 크다.","evidence":[3, 999]}]}""";
            }
            if (first == 19) {
                // 둘째 조각: 첫 조각 사실과 거의 같은 문장 → 버려야 한다
                return """
                        {"facts":[
                          {"fact":"확산 모델은 이미지 생성에서 최고 수준의 결과를 냈다.","evidence":[19]},
                          {"fact":"이 연구는 훈련 비용을 줄이는 방법을 찾았다.","evidence":[30]}]}""";
            }
            return """
                    {"facts":[
                      {"fact":"문장 %1$d번은 이 조각의 첫 번째 발견을 설명한다.","evidence":[%1$d]},
                      {"fact":"마지막 문장 %2$d번에는 두 번째 결과가 나온다.","evidence":[%2$d]}]}"""
                    .formatted(first, numbers.getLast());
        }

        List<String> promptsOf(String marker) {
            return requests.stream().filter(messages -> messages.size() == 2)
                    .map(messages -> messages.get(1).content()).filter(prompt -> prompt.contains(marker)).toList();
        }
    }

    private static ComicPlanner planner(FakeAi ai) {
        OllamaProperties properties = new OllamaProperties("http://desktop", "http://macbook", Duration.ofSeconds(2),
                Duration.ofMinutes(10), Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofSeconds(10), Duration.ZERO, 16384, 8192, 1500, 1.1, "qwen3:8b", "exaone3.5:7.8b");
        return new ComicPlanner(new AiAsker(ai, JsonMapper.builder().build()), properties);
    }

    @Test
    void 설계도는_기승전결_4쪽과_조각_12개로_만들어진다() {
        ComicPlan plan = planner(new FakeAi()).plan("Latent Diffusion", PaperFixture.sentences());

        assertThat(plan.comicTitle()).isEqualTo("잠재 공간 이야기");
        assertThat(plan.pages()).extracting(ComicPlan.PagePlan::role).containsExactly("기", "승", "전", "결");
        assertThat(plan.pages()).allSatisfy(page -> assertThat(page.chunks()).hasSize(3));
        assertThat(plan.pages().getLast().chunks().getLast().start()).isEqualTo(239);
    }

    @Test
    void 조각_제목은_조각마다_따로_묻고_요청에는_그_조각의_섹션만_들어간다() {
        FakeAi ai = new FakeAi();
        ComicPlan plan = planner(ai).plan("Latent Diffusion", PaperFixture.sentences());

        List<String> titlePrompts = ai.promptsOf("이 부분의 섹션:");
        assertThat(titlePrompts).hasSize(12);
        assertThat(titlePrompts.get(9)).contains("Super-Resolution with Latent Diffusion")
                .doesNotContain("Inpainting with Latent Diffusion");
        // 가짜 AI는 받은 섹션 이름을 제목으로 돌려주므로, 제목이 자기 조각의 섹션과 맞으면 밀리지 않은 것이다
        List<ComicPlan.ChunkPlan> chunks = plan.pages().stream().flatMap(page -> page.chunks().stream()).toList();
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.title()).isEqualTo(String.join(", ", chunk.sections())));
    }

    @Test
    void 섞인_단어가_있는_만화_제목은_다시_쓰게_한다() {
        FakeAi ai = new FakeAi();
        ComicPlan plan = planner(ai).plan("Latent Diffusion", PaperFixture.sentences());

        assertThat(plan.comicTitle()).isEqualTo("잠재 공간 이야기");
        assertThat(plan.stats().titleProblems()).isEmpty();
        assertThat(ai.requests.get(0)).hasSize(2);   // 첫 요청: 역할 설명 + 요청
        assertThat(ai.requests.get(1)).hasSize(4);   // 다시 쓰기: + 이전 답 + 문제 목록
        assertThat(ai.requests.get(1).getLast().content()).contains("라tent");
    }

    @Test
    void 사실_요청에는_원래_섹션_이름이_들어가고_한계_조각에만_한계_규칙이_붙는다() {
        FakeAi ai = new FakeAi();
        planner(ai).plan("Latent Diffusion", PaperFixture.sentences());

        List<String> factPrompts = ai.promptsOf("이 항목의 원래 섹션:");
        assertThat(factPrompts).hasSize(12);
        assertThat(factPrompts.getLast()).contains("이 항목의 원래 섹션: Limitations & Societal Impact, Conclusion")
                .contains("'잠재'(O)).\n8. 이 부분에는 연구의 한계가 들어 있다.");   // 8번 규칙이 7번 다음 줄에 붙는다
        assertThat(factPrompts.subList(0, 11)).noneMatch(prompt -> prompt.contains("반드시 한계"))
                .allMatch(prompt -> prompt.contains("'잠재'(O)).\n\n논문 문장"));       // 다른 조각은 7번 다음에 바로 문장 목록
    }

    @Test
    void 근거가_겹쳐도_내용이_다른_한계_사실은_남기고_수식_사실은_버린다() {
        ComicPlan plan = planner(new FakeAi()).plan("Latent Diffusion", PaperFixture.sentences());
        ComicPlan.ChunkPlan closing = plan.pages().getLast().chunks().getLast();
        List<String> lastFacts = plan.pages().getLast().facts().stream().map(ComicPlan.FactPlan::fact).toList();

        assertThat(lastFacts).contains("잠재 확산 모델은 픽셀 기반 모델에 비해 계산 요구가 적다.",
                "잠재 확산 모델은 GAN에 비해 샘플링 속도가 느리다.");
        assertThat(closing.droppedFacts()).containsExactly("이 연구는 조건부 분포 p(z|y)를 다룬다.");
    }

    @Test
    void 인용_번호가_든_사실은_먼저_다시_쓰게_한다() {
        FakeAi ai = new FakeAi();
        planner(ai).plan("Latent Diffusion", PaperFixture.sentences());

        List<String> retries = ai.requests.stream().filter(messages -> messages.size() == 4)
                .map(messages -> messages.get(3).content()).filter(content -> content.contains("인용 번호")).toList();
        assertThat(retries).hasSize(1);
        assertThat(retries.getFirst()).contains("최고 수준의 결과를 낸다 [11].");
    }

    @Test
    void 번호를_지우고_범위_밖_근거를_빼고_반복된_사실은_버린다() {
        ComicPlan plan = planner(new FakeAi()).plan("Latent Diffusion", PaperFixture.sentences());
        List<ComicPlan.FactPlan> firstPage = plan.pages().getFirst().facts();

        assertThat(firstPage.get(0).fact()).isEqualTo("확산 모델은 이미지 생성에서 최고 수준의 결과를 낸다.");
        assertThat(firstPage.get(1).evidence()).containsExactly(3);
        assertThat(plan.pages().getFirst().chunks().get(1).droppedFacts())
                .containsExactly("확산 모델은 이미지 생성에서 최고 수준의 결과를 냈다.");
        assertThat(plan.stats().servers()).containsExactly("주 서버");
    }
}
