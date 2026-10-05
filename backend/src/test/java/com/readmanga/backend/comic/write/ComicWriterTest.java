package com.readmanga.backend.comic.write;

import com.readmanga.backend.ai.AiChatClient;
import com.readmanga.backend.ai.ChatMessage;
import com.readmanga.backend.ai.ChatResult;
import com.readmanga.backend.ai.OllamaProperties;
import com.readmanga.backend.ai.OllamaServer;
import com.readmanga.backend.comic.plan.AiAsker;
import com.readmanga.backend.comic.plan.ComicPlan;
import com.readmanga.backend.paper.PaperJob;
import com.readmanga.backend.paper.PaperSentence;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가짜 AI로 대본 쓰기 흐름을 확인한다. 시험 단계에서 나온 실수(긴 대사, 박사님 존댓말, 해설 괄호)와
 * 실제 실행 1차에서 나온 실수(장면에 섞인 대사, 한계를 뒤집는 대사)를 섞는다.
 */
class ComicWriterTest {

    private static final Pattern FACT = Pattern.compile("이 컷이 전할 사실: (.+)");
    private static final String LIMITATION_RULE_START = "7. 이 사실은 논문의 한계";
    private static final String[] PLACES = {"연구실 칠판 앞", "학교 복도", "도서관 창가", "실험실 책상", "운동장 벤치", "컴퓨터실"};
    private static final String[] ACTIONS = {"박사님이 그림을 그린다", "누리가 모형을 집어 든다", "박사님이 창밖을 가리킨다", "누리가 노트를 펼친다"};
    private static final int LIMITATION_SENTENCE = 8;

    static class FakeAi implements AiChatClient {

        final List<List<ChatMessage>> requests = new ArrayList<>();

        @Override
        public ChatResult chat(String model, List<ChatMessage> messages, Map<String, Object> schema) {
            requests.add(messages);
            String prompt = messages.get(1).content();
            int attempt = (messages.size() - 2) / 2 + 1;
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            String content;
            if (properties.containsKey("setting")) {
                // 칸 이름으로 받으므로 각 쪽의 흐름이 자기 칸에 들어간다. 흐름 안에 따옴표 대사, '**이름표**:', '2쪽:'도 섞는다(2차 실행)
                content = """
                        {"setting":"누리의 풍경화 숙제가 몇 시간째 끝나지 않는다",
                         "기":"**문제 제기**: 누리가 좌절해 박사님을 찾아간다 '도와주세요!'","승":"2쪽: 박사님이 방법을 알려 준다",
                         "전":"실험 결과에 누리가 놀란다","결":"누리가 숙제를 끝내고 한계도 배운다",
                         "ending":"누리가 그림을 완성하고 과학의 힘을 깨닫는다"}""";
            } else if (properties.containsKey("opening")) {
                content = """
                        {"opening":{"scene":"누리의 방, 멈춘 컴퓨터 앞에서 누리가 한숨을 쉰다","lines":[{"speaker":"해설","text":"누리는 고민에 빠졌다."}]},
                         "closing":{"scene":"박사님 연구실, 누리가 노트를 덮으며 활짝 웃는다","lines":[{"speaker":"누리","text":"다음이 궁금해요!"}]}}""";
            } else {
                Matcher matcher = FACT.matcher(prompt);
                String fact = matcher.find() ? matcher.group(1) : "?";
                if (attempt == 1) {
                    // 첫 시도: 장면에 대사가 섞이고 박사님 존댓말 → 다시 쓰기
                    content = "{\"scene\":\"연구실 칠판 앞, 박사님이 그림을 그린다. **박사님**:\",\"lines\":[{\"speaker\":\"박사님\",\"text\":\"이건 정말 중요해요.\"}]}";
                } else if (attempt == 2 && prompt.contains(LIMITATION_RULE_START)) {
                    // 한계 컷의 두 번째 시도: 단점을 뒤집음 → 한 번 더 다시 쓰기
                    content = "{\"scene\":\"연구실 창가, 누리가 고개를 갸웃한다\",\"lines\":[{\"speaker\":\"박사님\",\"text\":\"처음엔 느리지만 결국엔 훨씬 빨라.\"}]}";
                } else {
                    // 컷마다 장소와 행동을 바꿔 앞 컷과 다른 장면이 되게 한다
                    int n = requests.size();
                    content = """
                            {"scene":"%2$s, %3$s","lines":[
                              {"speaker":"누리","text":"박사님, %1$s 왜 그래요?"},
                              {"speaker":"박사님","text":"레고 조립이랑 비슷해. (해설: 레고는 블록 장난감이다.)"}]}"""
                            .formatted(fact, PLACES[n % PLACES.length], ACTIONS[n % ACTIONS.length]);
                }
            }
            return new ChatResult(content, OllamaServer.PRIMARY, 100, 50, 10);
        }
    }

    private static ComicPlan plan() {
        List<ComicPlan.PagePlan> pages = new ArrayList<>();
        String[] roles = {"기", "승", "전", "결"};
        int number = 1;
        for (int page = 0; page < 4; page++) {
            List<ComicPlan.FactPlan> facts = new ArrayList<>();
            for (int i = 0; i < 2; i++, number++) {
                facts.add(new ComicPlan.FactPlan("사실 " + number + "번 내용이다.", List.of(number), "조각"));
            }
            pages.add(new ComicPlan.PagePlan(page + 1, roles[page], "역할 설명",
                    List.of(new ComicPlan.ChunkPlan(1, 2, List.of("Section"), "조각 제목", "요약", List.of(), List.of())), facts));
        }
        return new ComicPlan("잠재 공간 이야기", pages, new ComicPlan.Stats(0, 0, List.of(), 0, List.of()));
    }

    private static Map<Integer, PaperSentence> sentences() {
        PaperJob job = new PaperJob("fixture.pdf");
        Map<Integer, PaperSentence> sentences = new HashMap<>();
        for (int number = 1; number <= 8; number++) {
            String section = number == LIMITATION_SENTENCE ? "Limitations & Societal Impact" : "Section";
            sentences.put(number, new PaperSentence(job, number, section, 1, "Original sentence " + number + " of the paper."));
        }
        return sentences;
    }

    private static ComicWriter writer(FakeAi ai) {
        OllamaProperties properties = new OllamaProperties("http://desktop", "http://macbook", Duration.ofSeconds(2),
                Duration.ofMinutes(10), Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofSeconds(10), Duration.ZERO, 16384, 8192, 1500, 1.1, "qwen3:8b", "exaone3.5:7.8b");
        return new ComicWriter(new AiAsker(ai, JsonMapper.builder().build()), properties);
    }

    @Test
    void 쪽마다_여는_컷_사실_컷_닫는_컷_순서로_쓰고_사실_컷의_해설은_사실_문장이다() {
        WrittenComic comic = writer(new FakeAi()).write(plan(), sentences(), (done, total) -> {
        });

        assertThat(comic.pages()).hasSize(4);
        WrittenComic.Page first = comic.pages().getFirst();
        assertThat(first.panels()).extracting(WrittenComic.Panel::staging).containsExactly(true, false, false, true);
        WrittenComic.Panel factPanel = first.panels().get(1);
        assertThat(factPanel.lines().getLast()).isEqualTo(new LineRules.Line("해설", "사실 1번 내용이다."));
        assertThat(factPanel.evidence()).containsExactly(1);
        assertThat(factPanel.lines()).contains(new LineRules.Line("해설", "레고는 블록 장난감이다."));  // 괄호 해설은 따로 뺀다
        assertThat(factPanel.problems()).isEmpty();  // 존댓말과 장면에 섞인 대사는 다시 쓰기에서 고쳐졌다
        assertThat(factPanel.scene()).doesNotContain("*", "박사님:");
    }

    @Test
    void 이야기_흐름은_자기_쪽_칸에서_가져오고_따옴표_대사와_이름표와_기호는_지운다() {
        FakeAi ai = new FakeAi();
        WrittenComic comic = writer(ai).write(plan(), sentences(), (done, total) -> {
        });

        assertThat(comic.pages()).extracting(WrittenComic.Page::beat).containsExactly(
                "누리가 좌절해 박사님을 찾아간다 도와주세요!", "박사님이 방법을 알려 준다",
                "실험 결과에 누리가 놀란다", "누리가 숙제를 끝내고 한계도 배운다");
        assertThat(comic.setting()).isEqualTo("누리의 풍경화 숙제가 몇 시간째 끝나지 않는다");
        // 기호와 이름표는 먼저 다시 쓰게 하고(가짜 AI는 같은 답을 내서), 그래도 남으면 코드가 지운다
        assertThat(ai.requests.stream().filter(messages -> messages.size() == 4).map(messages -> messages.get(3).content()))
                .anyMatch(content -> content.contains("이야기 뼈대에 서식 기호"));
    }

    @Test
    void 사실_컷_요청에는_사실이_하나뿐이고_앞_컷은_대사_없이_장면만_넘긴다() {
        FakeAi ai = new FakeAi();
        writer(ai).write(plan(), sentences(), (done, total) -> {
        });

        List<String> factPrompts = ai.requests.stream().filter(messages -> messages.size() == 2)
                .map(messages -> messages.get(1).content()).filter(prompt -> prompt.contains("이 컷이 전할 사실:")).toList();
        assertThat(factPrompts).hasSize(8);
        assertThat(factPrompts.get(1)).contains("이 컷이 전할 사실: 사실 2번 내용이다.").doesNotContain("사실 1번 내용이다.")
                .contains("바로 앞 컷의 장면:").doesNotContain("레고 조립이랑")
                .contains("쓰지 않는다. 영어 용어는");   // 텍스트 블록이 줄 끝 공백을 지워도 규칙 사이가 띄어져 있어야 한다
    }

    @Test
    void 장면에_대사가_섞이면_다시_쓰기_요청에_그_문제를_알려_준다() {
        FakeAi ai = new FakeAi();
        writer(ai).write(plan(), sentences(), (done, total) -> {
        });

        String retryRequest = ai.requests.stream().filter(messages -> messages.size() == 4)
                .map(messages -> messages.get(3).content()).filter(content -> content.contains("장면 칸에")).findFirst().orElseThrow();
        assertThat(retryRequest).contains("장면 칸에 대사나 기호", "존댓말을 썼다");
    }

    @Test
    void 한계_섹션의_사실_컷에만_한계_규칙을_넣고_단점을_뒤집으면_다시_쓴다() {
        FakeAi ai = new FakeAi();
        WrittenComic comic = writer(ai).write(plan(), sentences(), (done, total) -> {
        });

        List<String> limitationPrompts = ai.requests.stream().filter(messages -> messages.size() == 2)
                .map(messages -> messages.get(1).content()).filter(prompt -> prompt.contains(LIMITATION_RULE_START)).toList();
        assertThat(limitationPrompts).hasSize(1).first().asString().contains("이 컷이 전할 사실: 사실 8번 내용이다.");

        // 세 번째 시도 요청에 '단점을 뒤집는다'는 문제가 들어가고, 최종 대사에는 뒤집는 말이 없다
        assertThat(ai.requests.stream().filter(messages -> messages.size() == 6)
                .map(messages -> messages.get(5).content())).anyMatch(content -> content.contains("단점을 뒤집는다"));
        WrittenComic.Panel limitationPanel = comic.pages().getLast().panels().get(2);
        assertThat(limitationPanel.evidence()).containsExactly(LIMITATION_SENTENCE);
        assertThat(limitationPanel.lines()).noneMatch(line -> line.text().contains("결국"));
        // 가짜 AI는 쪽마다 같은 비유 대사를 반복해서 '같은 대사' 문제는 남는다. 한계를 뒤집는 문제만 없으면 된다
        assertThat(limitationPanel.problems()).noneMatch(problem -> problem.contains("단점을 뒤집는다"));
    }

    @Test
    void 진행률은_시작_때_0을_알리고_이야기_1번_사실_컷_8번_연출_4번으로_모두_13단계다() {
        List<String> progress = new ArrayList<>();
        writer(new FakeAi()).write(plan(), sentences(), (done, total) -> progress.add(done + "/" + total));

        assertThat(progress).hasSize(14).first().isEqualTo("0/13");
        assertThat(progress).last().isEqualTo("13/13");
    }
}
