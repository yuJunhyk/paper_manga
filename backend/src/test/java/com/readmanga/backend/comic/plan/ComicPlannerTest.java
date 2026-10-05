package com.readmanga.backend.comic.plan;

import com.readmanga.backend.ai.AiChatClient;
import com.readmanga.backend.ai.ChatMessage;
import com.readmanga.backend.ai.ChatResult;
import com.readmanga.backend.ai.OllamaProperties;
import com.readmanga.backend.ai.OllamaServer;
import com.readmanga.backend.paper.PaperJob;
import com.readmanga.backend.paper.PaperSentence;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
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
            } else if (properties.containsKey("terms")) {
                // 용어 정하기: 논문에 없는 영어('kernel filtering')는 빼고, 정한 번역과 같거나 그 안에 든 다른 번역도 뺀다
                content = """
                        {"terms":[
                          {"english":"Latent Diffusion Models","korean":"잠재 확산 모델","variants":["레이턴트 디퓨전 모델","잠재 확산 모델","확산"]},
                          {"english":"kernel filtering","korean":"커널 필터링","variants":[]}]}""";
            } else if (properties.containsKey("results")) {
                content = verify(prompt);
            } else {
                content = facts(prompt, retry);
            }
            return new ChatResult(content, OllamaServer.PRIMARY, 100, 50, 10);
        }

        /** 사실 확인: '선호도가 높았다'(원문에 없는 결과)가 든 사실만 근거 없음으로 판정한다 */
        private static String verify(String prompt) {
            List<String> items = new ArrayList<>();
            Matcher matcher = Pattern.compile("\\[사실 (\\d+)] (.+)").matcher(prompt);
            while (matcher.find()) {
                boolean fabricated = matcher.group(2).contains("선호도가 높았다");
                items.add("{\"fact\":%s,\"verdict\":\"%s\",\"reason\":\"%s\"}".formatted(matcher.group(1), fabricated ? "틀림" : "맞음",
                        fabricated ? "원문은 사용자 조사를 했다는 것까지만 말한다" : ""));
            }
            return "{\"results\":[" + String.join(",", items) + "]}";
        }

        private static String facts(String prompt, boolean retry) {
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
            if ((first == 64 && !retry) || first == 92) {
                // 64 조각은 첫 시도에만, 92 조각은 다시 써도 원문에 없는 결과를 지어낸다 (4차 실행 207번)
                return """
                        {"facts":[
                          {"fact":"사용자 조사에서 이 연구의 결과가 선호도가 높았다.","evidence":[%1$d]},
                          {"fact":"문장 %1$d번은 이 조각의 첫 번째 발견을 설명한다.","evidence":[%2$d]}]}""".formatted(first, numbers.getLast());
            }
            if (first == 49 && !retry) {
                // 셋째 조각 첫 시도: 논문에 없는 영어 용어를 지어냄 (3차 실행의 '커널 필터링')
                return """
                        {"facts":[
                          {"fact":"이 연구는 커널 필터링(kernel filtering)으로 샘플링을 빠르게 했다.","evidence":[%1$d]},
                          {"fact":"마지막 문장 %2$d번에는 두 번째 결과가 나온다.","evidence":[%2$d]}]}""".formatted(first, numbers.getLast());
            }
            if (numbers.contains(130) && !retry) {
                // 정한 번역 대신 소리 나는 대로 옮긴 말을 씀 → 다시 쓰기
                return """
                        {"facts":[
                          {"fact":"레이턴트 디퓨전 모델은 문장 %1$d번의 발견을 보여 준다.","evidence":[%1$d]},
                          {"fact":"마지막 문장 %2$d번에는 두 번째 결과가 나온다.","evidence":[%2$d]}]}""".formatted(first, numbers.getLast());
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
                .contains("쓰지 않는다).\n8. 이 부분에는 연구의 한계가 들어 있다.");   // 8번 규칙이 7번 다음 줄에 붙는다
        assertThat(factPrompts.subList(0, 11)).noneMatch(prompt -> prompt.contains("반드시 한계"))
                .allMatch(prompt -> prompt.contains("쓰지 않는다).\n용어 번역 (만화 전체에서"));       // 다른 조각은 7번 다음에 바로 문장 목록
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

    @Test
    void 논문에_없는_영어_용어로_쓴_사실은_다시_쓰게_하고_지시문에는_용어_규칙이_들어간다() {
        FakeAi ai = new FakeAi();
        ComicPlan plan = planner(ai).plan("Latent Diffusion", PaperFixture.sentences());

        assertThat(ai.requests.stream().filter(messages -> messages.size() == 4).map(messages -> messages.get(3).content()))
                .anyMatch(content -> content.contains("1번 사실에 논문에 없는 영어 용어가 있다: kernel, filtering"));
        assertThat(plan.pages().getFirst().facts()).extracting(ComicPlan.FactPlan::fact).noneMatch(fact -> fact.contains("kernel"));
        assertThat(ai.promptsOf("이 부분에 맞는 소제목")).allMatch(prompt -> prompt.contains("괄호에 논문의 영어 원문"));
        assertThat(ai.promptsOf("핵심 사실")).allMatch(prompt -> prompt.contains("괄호에 논문의 영어 원문"));
    }

    @Test
    void 근거_문장에_없는_결과를_지어낸_사실은_다시_쓰게_하고_끝까지_남으면_버린다() {
        FakeAi ai = new FakeAi();
        ComicPlan plan = planner(ai).plan("Latent Diffusion", PaperFixture.sentences());

        assertThat(ai.requests.stream().filter(messages -> messages.size() == 4).map(messages -> messages.get(3).content()))
                .anyMatch(content -> content.contains("1번 사실은 근거 문장에 없는 내용을 담고 있다: 원문은 사용자 조사를 했다는 것까지만 말한다"));
        List<String> allFacts = plan.pages().stream().flatMap(page -> page.facts().stream()).map(ComicPlan.FactPlan::fact).toList();
        assertThat(allFacts).as("다시 써서 고쳐졌거나(64 조각) 끝까지 남아 버려졌다(92 조각)").noneMatch(fact -> fact.contains("선호도"));
        assertThat(plan.pages().stream().flatMap(page -> page.chunks().stream()).flatMap(chunk -> chunk.droppedFacts().stream()))
                .contains("사용자 조사에서 이 연구의 결과가 선호도가 높았다.");
    }

    @Test
    void 표_그림_제목은_근거_후보로_보여_주지_않는다() {
        FakeAi ai = new FakeAi();
        planner(ai).plan("Latent Diffusion", PaperFixture.sentences());

        assertThat(ai.promptsOf("핵심 사실")).filteredOn(prompt -> prompt.contains("[180]"))
                .singleElement().asString().doesNotContain("[181]");
    }

    @Test
    void 쪽마다_사실은_6개를_넘지_않고_한계_조각_보너스는_마지막_쪽에만_준다() {
        Chunk results = new Chunk(195, 232, List.of("Preliminary Exploration", "Effectiveness of Data Comics"));
        Chunk benefits = new Chunk(233, 256, List.of("Perceived Benefits and Limitations"));
        Chunk perceived = new Chunk(257, 283, List.of("Perceived Limitations"));
        assertThat(ComicPlanner.factCounts(List.of(results, benefits, perceived), false))
                .as("가운데 쪽의 'Perceived Limitations'는 논문의 한계 섹션이 아니다 (176번 3쪽 10컷)").containsExactly(2, 2, 2);

        Chunk discussion = new Chunk(320, 338, List.of("Summary of the Results and Discussion"));
        Chunk implications = new Chunk(339, 352, List.of("Implications for HCI"));
        Chunk limitations = new Chunk(353, 393, List.of("Limitations and Future Work", "Conclusion"));
        assertThat(ComicPlanner.factCounts(List.of(discussion, implications, limitations), true))
                .as("마지막 한계 조각만 3개, 가장 짧은 다른 조각을 줄여 6개").containsExactly(2, 1, 3);
    }

    @Test
    void 설계도의_모든_쪽은_사실이_6개_이하다() {
        ComicPlan plan = planner(new FakeAi()).plan("Latent Diffusion", PaperFixture.sentences());

        assertThat(plan.pages()).allSatisfy(page -> assertThat(page.facts().size()).isLessThanOrEqualTo(ComicPlanner.MAX_FACTS_PER_PAGE));
    }

    @Test
    void 사실이_한_섹션에서만_나오면_다른_섹션의_결과_문장을_알려_주며_다시_고르게_한다() {
        PaperJob job = new PaperJob("comics.pdf");
        Map<Integer, PaperSentence> byNumber = new LinkedHashMap<>();
        String[][] sentences = {
                {"195", "Preliminary Exploration", "Our exploration revealed that participants' overall accuracy was moderate (median: 0.500, IQR: 0.250)."},
                {"196", "Preliminary Exploration", "Participants performed better on retrieval questions (median = 0.667) than on comprehension questions."},
                {"197", "Preliminary Exploration", "This difference was significant."},
                {"205", "Effectiveness of Data Comics", "To address RQ1, we compared the effectiveness of data comics and conventional visualisations."},
                {"206", "Effectiveness of Data Comics", "Participants achieved a significantly higher CorrectRate with DC (median = 0.750) than with CV (median = 0.333)."},
                {"207", "Effectiveness of Data Comics", "We report effect sizes for each comparison."},
        };
        Map<String, List<Integer>> bySection = new LinkedHashMap<>();
        for (String[] row : sentences) {
            int number = Integer.parseInt(row[0]);
            byNumber.put(number, new PaperSentence(job, number, row[1], 10, row[2]));
            bySection.computeIfAbsent(row[1], key -> new ArrayList<>()).add(number);
        }
        Set<Integer> allowed = byNumber.keySet();

        ComicPlanner.FactsAnswer front = new ComicPlanner.FactsAnswer(List.of(
                new ComicPlanner.FactItem("참가자들의 정확도는 보통이었다.", List.of(195)),
                new ComicPlanner.FactItem("정보 찾기 문제를 더 잘 풀었다.", List.of(196))));
        assertThat(ComicPlanner.spreadProblems(front, allowed, bySection, byNumber)).singleElement().asString()
                .contains("'Preliminary Exploration' 섹션에서만").contains("Effectiveness of Data Comics").contains("[206]")
                .doesNotContain("[207]");

        ComicPlanner.FactsAnswer spread = new ComicPlanner.FactsAnswer(List.of(
                new ComicPlanner.FactItem("참가자들의 정확도는 보통이었다.", List.of(195)),
                new ComicPlanner.FactItem("데이터 만화의 정답률이 더 높았다.", List.of(206))));
        assertThat(ComicPlanner.spreadProblems(spread, allowed, bySection, byNumber)).isEmpty();
    }

    @Test
    void 사실_지시문에는_첫_논문의_소재가_예시로_남아_있지_않다() {
        // 사실 확인(VERIFY)의 예시는 판정 연습용이라 AI가 글로 옮겨 쓰지 않으므로 뺀다
        assertThat(Stream.of(PlanPrompts.TITLE_RULE, PlanPrompts.TERM_RULE, PlanPrompts.OUTLINE_SYSTEM, PlanPrompts.COMIC_TITLE,
                PlanPrompts.CHUNK_TITLE, PlanPrompts.FACT_SYSTEM, PlanPrompts.LIMITATION_RULE, PlanPrompts.FACTS))
                .noneMatch(prompt -> prompt.contains("잠재") || prompt.contains("확산") || prompt.contains("GAN") || prompt.contains("고화질"));
        assertThat(Stream.of(PlanPrompts.TITLE_RULE, PlanPrompts.TERM_RULE, PlanPrompts.GLOSSARY))
                .as("예시 낱말은 무엇이든 새어 나온다 (178번 '표본 크기(sample size)')").noneMatch(prompt -> prompt.contains("예:"));
    }

    @Test
    void 용어_번역은_처음에_한_번_정해_사실_요청에_붙이고_다른_번역을_쓴_사실은_다시_쓰게_한다() {
        FakeAi ai = new FakeAi();
        ComicPlan plan = planner(ai).plan("Latent Diffusion", PaperFixture.sentences());

        assertThat(plan.terms()).containsExactly(new ComicPlan.Term("Latent Diffusion Models", "잠재 확산 모델", List.of("레이턴트 디퓨전 모델")));
        assertThat(ai.promptsOf("이 항목의 원래 섹션:"))
                .allMatch(prompt -> prompt.contains("- Latent Diffusion Models → 잠재 확산 모델 (쓰지 않을 말: 레이턴트 디퓨전 모델)"));
        assertThat(ai.requests.stream().filter(messages -> messages.size() == 4).map(messages -> messages.get(3).content()))
                .anyMatch(content -> content.contains("1번 사실에 정한 번역과 다른 용어가 있다: '레이턴트 디퓨전 모델' → '잠재 확산 모델'"));
        assertThat(plan.pages().stream().flatMap(page -> page.facts().stream()).map(ComicPlan.FactPlan::fact))
                .noneMatch(fact -> fact.contains("레이턴트"));
    }
}
