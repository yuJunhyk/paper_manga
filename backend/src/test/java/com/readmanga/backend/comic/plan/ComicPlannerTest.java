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
    private static final Pattern LINK_SECTIONS = Pattern.compile("사실이 나온 섹션: ([^,\n]+)");
    static final String PROBLEM = "기존 확산 모델은 계산 비용이 매우 크다.";
    static final String PROBLEM_2 = "고해상도 그림은 다루기 어렵다.";
    static final String METHOD = "이 연구는 압축한 잠재 공간에서 모델을 학습한다.";
    static final String METHOD_2 = "글자 설명으로 그림을 조절하는 장치를 넣었다.";
    static final String RESULT = "이 연구는 적은 계산으로 높은 품질을 얻었다.";
    static final String RESULT_2 = "여러 그림 작업에서 좋은 점수를 냈다.";
    static final String LIMITATION = "이 연구의 모델은 샘플링이 느리다는 한계가 있다.";

    /** 시험 단계의 실수를 흉내 내는 가짜 AI. 받은 요청을 기록한다. */
    static class FakeAi implements AiChatClient {

        final List<List<ChatMessage>> requests = new ArrayList<>();
        boolean noResultLinks;   // 결과 부분의 사실을 어느 요약 문장에도 연결하지 않는다
        int factRequests;        // 기본 사실을 조각마다 다른 문장으로 만들려고 센다 (비슷한 문장은 반복 검사로 버려진다)

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
            } else if (properties.containsKey("problem")) {
                // 논문 요약: 첫 시도는 문제 칸 근거로 목록에 없는 본문 문장(100)을 쓰고 결과 칸이 1개뿐이다 → 다시 쓰기
                content = """
                        {"problem":[{"point":"%s","evidence":[%d]},{"point":"%s","evidence":[3]}],
                         "method":[{"point":"%s","evidence":[2]},{"point":"%s","evidence":[4]}],
                         "result":[{"point":"%s","evidence":[5]}%s],"limitation":[{"point":"%s","evidence":[239]}]}"""
                        .formatted(PROBLEM, retry ? 1 : 100, PROBLEM_2, METHOD, METHOD_2, RESULT,
                                retry ? ",{\"point\":\"" + RESULT_2 + "\",\"evidence\":[6]}" : "", LIMITATION);
            } else if (properties.containsKey("links")) {
                content = links(prompt);
            } else {
                content = facts(prompt, retry, factRequests++);
            }
            return new ChatResult(content, OllamaServer.PRIMARY, 100, 50, 10);
        }

        /**
         * 요약 연결: 사실이 나온 첫 섹션으로 정한다. 초록 → 2(둘째 문제), 서론~이미지 합성 → 1(첫 문제), 두 단계 합성~조건 → 3(첫 방법),
         * 실험~인페인팅 → 5(첫 결과), 한계·결론 → 7(한계). 'Democratizing…'으로 시작하는 조각은 어느 요약과도 잇지 않는다.
         */
        private String links(String prompt) {
            Matcher matcher = LINK_SECTIONS.matcher(prompt);
            String section = matcher.find() ? matcher.group(1).strip() : "?";
            int index = 0;
            while (index < PaperFixture.SECTIONS.length && !PaperFixture.SECTIONS[index][0].equals(section)) {
                index++;
            }
            int point = index == 0 ? 2 : index == 2 ? 0 : index <= 3 ? 1 : index <= 10 ? 3 : index <= 17 ? (noResultLinks ? 0 : 5) : 7;
            String facts = prompt.substring(prompt.indexOf("사실:\n"));
            long count = facts.lines().filter(line -> line.matches("^\\d+\\. .*")).count();
            List<String> items = new ArrayList<>();
            for (int fact = 1; fact <= count; fact++) {
                items.add("{\"fact\":%d,\"point\":%d}".formatted(fact, point));
            }
            return "{\"links\":[" + String.join(",", items) + "]}";
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

        private static String facts(String prompt, boolean retry, int request) {
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
                      {"fact":"%3$s","evidence":[%1$d]},
                      {"fact":"%4$s","evidence":[%2$d]}]}"""
                    .formatted(first, numbers.getLast(), DISTINCT[(2 * request) % DISTINCT.length], DISTINCT[(2 * request + 1) % DISTINCT.length]);
        }

        /** 서로 겹치는 말이 적은 기본 사실 문장 */
        private static final String[] DISTINCT = {
                "그림을 작은 조각으로 나눠 다룬다.", "색깔 정보를 따로 저장한다.", "학습은 여러 날 걸렸다.", "잡음을 조금씩 걷어 낸다.",
                "글자 설명을 조건으로 넣는다.", "메모리를 덜 쓴다.", "얼굴 사진으로 시험했다.", "풍경 사진에서 점수가 올랐다.",
                "빈 곳을 채우는 일에 쓴다.", "작은 그림을 크게 키운다.", "사람들이 결과를 비교했다.", "속도가 두 배 넘게 빨라졌다.",
                "압축률을 여러 가지로 바꿔 봤다.", "경계가 흐려지는 문제가 있다.", "물체 배치를 지도로 받는다.", "세밀한 무늬를 잘 살린다.",
                "기존 점수보다 낮은 값을 얻었다.", "큰 해상도에서도 돌아간다.", "모델 크기를 키우면 좋아진다.", "훈련 데이터가 많이 필요하다.",
                "가짜 사진을 만드는 데 악용될 수 있다.", "편향된 데이터를 따라 배운다.", "결과를 다시 고치는 단계가 있다.", "여러 방법과 함께 쓸 수 있다."};

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
    void 근거가_겹쳐도_내용이_다른_사실은_뽑아_두지만_만화에는_근거마다_하나만_쓰고_수식_사실은_버린다() {
        // 198번 4쪽: 240번 한 문장에서 나온 사실 두 개가 연달아 나왔다
        ComicPlan plan = planner(new FakeAi()).plan("Latent Diffusion", PaperFixture.sentences());
        ComicPlan.ChunkPlan closing = plan.pages().getLast().chunks().getLast();
        List<String> lastFacts = plan.pages().getLast().facts().stream().map(ComicPlan.FactPlan::fact).toList();

        assertThat(lastFacts).contains("잠재 확산 모델은 픽셀 기반 모델에 비해 계산 요구가 적다.")
                .doesNotContain("잠재 확산 모델은 GAN에 비해 샘플링 속도가 느리다.");
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

        assertThat(firstPage).extracting(ComicPlan.FactPlan::fact).contains("확산 모델은 이미지 생성에서 최고 수준의 결과를 낸다.");
        assertThat(firstPage).filteredOn(fact -> fact.fact().equals("기존 확산 모델은 계산 비용이 매우 크다."))
                .singleElement().extracting(ComicPlan.FactPlan::evidence).isEqualTo(List.of(3));
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
    void 한계_규칙은_마지막_쪽_조각_중_한계_결론_섹션이_든_가장_뒤_조각에만_붙인다() {
        List<Chunk> chunks = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            List<String> sections = index == 7 ? List.of("Perceived Limitations")       // 가운데 쪽의 결과 섹션 (176번)
                    : index == 10 ? List.of("Limitations and Future Work", "Conclusion") : List.of("Section " + index);
            chunks.add(new Chunk(index * 10 + 1, index * 10 + 10, sections));
        }

        assertThat(ComicPlanner.closingChunk(chunks, 4)).isEqualTo(10);
        assertThat(ComicPlanner.closingChunk(chunks.subList(0, 6), 2)).as("마지막 쪽에 한계 섹션이 없으면 붙이지 않는다").isEqualTo(-1);
    }

    @Test
    void 사실은_조각마다_개수를_정하지_않고_10개까지_받는다() {
        FakeAi ai = new FakeAi();
        planner(ai).plan("Latent Diffusion", PaperFixture.sentences());

        assertThat(ai.promptsOf("이 항목의 원래 섹션:")).allMatch(prompt -> prompt.contains("핵심 사실을 빠짐없이 뽑아라(1~10개"));
    }

    @Test
    void 요약은_초록과_결론_한계_문장만_보고_네_칸으로_쓰고_근거가_목록_밖이면_다시_쓰게_한다() {
        FakeAi ai = new FakeAi();
        ComicPlan plan = planner(ai).plan("Latent Diffusion", PaperFixture.sentences());

        assertThat(ai.promptsOf("네 칸으로 요약하라")).singleElement().asString()
                .contains("[1] (Abstract)", "[239] (Limitations & Societal Impact)", "[259] (Conclusion)")
                .doesNotContain("[100] (", "[250] (")   // 본문 문장과 표 조각은 보여 주지 않는다
                .contains("결론·한계 부분 문장에서 찾아 1개 이상 쓴다.");
        assertThat(ai.requests.stream().filter(messages -> messages.size() == 4).map(messages -> messages.get(3).content()))
                .anyMatch(content -> content.contains("problem 칸의 1번째 요약 문장의 근거 번호 [100]가 모두 목록에 없는 번호다.")
                        && content.contains("result 칸의 요약 문장이 1개다. 논문 문장에서 찾아 서로 다른 요약 문장을 2개 이상 쓴다."));
        assertThat(plan.summary()).extracting(ComicPlan.SummaryPoint::slot)
                .containsExactly("problem", "problem", "method", "method", "result", "result", "limitation");
        assertThat(plan.summary().getFirst().evidence()).containsExactly(1);
    }

    @Test
    void 사실은_요약_문장에_연결된_것만_쓰고_요약_문장마다_정한_수까지다() {
        FakeAi ai = new FakeAi();
        ComicPlan plan = planner(ai).plan("Latent Diffusion", PaperFixture.sentences());

        assertThat(ai.promptsOf("요약 문장 (형식: 번호. [칸] 문장):")).as("사실이 남은 조각마다 한 번").isNotEmpty()
                .allMatch(prompt -> prompt.contains("1. [문제] " + PROBLEM) && prompt.contains("7. [한계] " + LIMITATION));
        List<String> allFacts = plan.pages().stream().flatMap(page -> page.facts().stream()).map(ComicPlan.FactPlan::fact).toList();
        assertThat(allFacts).as("어느 요약과도 잇지 않은 사실").doesNotContain("이 연구는 훈련 비용을 줄이는 방법을 찾았다.");
        assertThat(plan.pages()).as("요약 문장마다 FACTS_PER_POINT개까지: 문제·방법·결과 쪽은 요약 2개, 결 쪽은 1개")
                .allSatisfy(page -> assertThat(page.facts()).hasSizeLessThanOrEqualTo(ComicPlanner.FACTS_PER_POINT * page.points().size()));
    }

    @Test
    void 쪽은_요약_칸으로_정하고_쪽마다_맡은_요약_문장을_담는다() {
        ComicPlan plan = planner(new FakeAi()).plan("Latent Diffusion", PaperFixture.sentences());

        assertThat(plan.pages()).extracting(ComicPlan.PagePlan::points)
                .containsExactly(List.of(PROBLEM, PROBLEM_2), List.of(METHOD, METHOD_2), List.of(RESULT, RESULT_2), List.of(LIMITATION));
        assertThat(plan.pages().get(2).facts()).as("전(결과) 쪽은 실험 부분 사실 (연결된 사실이 없는 요약 문장은 요약 자체)")
                .filteredOn(fact -> !fact.chunkTitle().equals("요약")).isNotEmpty()
                .allSatisfy(fact -> assertThat(fact.evidence()).allMatch(number -> number >= 120 && number <= 238));
    }

    @Test
    void 요약_문장마다_본문_근거와_결과_숫자가_있는_사실을_먼저_고른다() {
        // 196번: 논문 순서로 고르니 결과 쪽에 초록 사실만 남고 실험 숫자(FID 5.11)가 빠졌다
        PaperJob job = new PaperJob("ldm.pdf");
        Map<Integer, PaperSentence> byNumber = Map.of(
                5, new PaperSentence(job, 5, "Abstract", 1, "LDMs achieve new state-of-the-art scores."),
                140, new PaperSentence(job, 140, "Image Generation with Latent Diffusion", 6, "On CelebA-HQ, we report a new FID of 5.11."),
                141, new PaperSentence(job, 141, "Image Generation with Latent Diffusion", 6, "Samples look realistic."));
        ComicPlan.SummaryPoint result = new ComicPlan.SummaryPoint("result", "이 연구는 새로운 최고 점수를 냈다.", List.of(5));
        ComicPlan.FactPlan fromAbstract = new ComicPlan.FactPlan("이 연구는 새로운 최고 점수를 달성했다.", List.of(5), "초록");
        ComicPlan.FactPlan withNumber = new ComicPlan.FactPlan("이 연구는 얼굴 사진에서 FID 5.11을 얻었다.", List.of(140), "실험");
        ComicPlan.FactPlan plain = new ComicPlan.FactPlan("만든 그림이 진짜 같아 보인다.", List.of(141), "실험");

        assertThat(ComicPlanner.pickScore(withNumber, result, byNumber)).isGreaterThan(ComicPlanner.pickScore(plain, result, byNumber));
        assertThat(ComicPlanner.pickScore(plain, result, byNumber)).isGreaterThan(ComicPlanner.pickScore(fromAbstract, result, byNumber));
    }

    @Test
    void 연결된_사실이_없는_요약_문장은_요약_문장을_사실로_쓴다() {
        FakeAi ai = new FakeAi();
        ai.noResultLinks = true;
        ComicPlan plan = planner(ai).plan("Latent Diffusion", PaperFixture.sentences());

        assertThat(plan.pages().get(2).facts()).containsExactly(new ComicPlan.FactPlan(RESULT, List.of(5), "요약"),
                new ComicPlan.FactPlan(RESULT_2, List.of(6), "요약"));
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
                PlanPrompts.CHUNK_TITLE, PlanPrompts.FACT_SYSTEM, PlanPrompts.LIMITATION_RULE, PlanPrompts.FACTS,
                PlanPrompts.SUMMARY_SYSTEM, PlanPrompts.SUMMARY, PlanPrompts.LINK_SYSTEM, PlanPrompts.LINK))
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

    @Test
    void 같은_결과_숫자를_말하는_사실은_요약_섹션_쪽을_버리고_하나만_남긴다() {
        // 187번: 초록(5번)과 실험 절(154번)의 'BLEU 28.4'가 1쪽과 3쪽에 두 번 나왔다
        PaperJob job = new PaperJob("attention.pdf");
        Map<Integer, PaperSentence> byNumber = Map.of(
                5, new PaperSentence(job, 5, "Abstract", 1, "Our model achieves 28.4 BLEU."),
                140, new PaperSentence(job, 140, "Regularization", 7, "We apply dropout with a rate of 0.1."),
                141, new PaperSentence(job, 141, "Regularization", 7, "We employ label smoothing of value 0.1."),
                154, new PaperSentence(job, 154, "Machine Translation", 8, "The big model establishes a new BLEU score of 28.4."));
        ComicPlan.FactPlan fromAbstract = new ComicPlan.FactPlan(
                "이 연구의 모델은 기존 최고 성능의 결과보다 2 블루 점수 이상 향상된 28.4 블루 점수를 달성했다.", List.of(5), "초록");
        ComicPlan.FactPlan dropout = new ComicPlan.FactPlan("이 연구는 각 하위 계층 출력에 비율 0.1의 드롭아웃을 적용했다.", List.of(140), "규제");
        ComicPlan.FactPlan smoothing = new ComicPlan.FactPlan("학습 중 값이 0.1인 라벨 스무딩을 썼다.", List.of(141), "규제");
        ComicPlan.FactPlan fromResults = new ComicPlan.FactPlan(
                "이 큰 트랜스포머 모델은 WMT 2014 영어-독일 번역 과제에서 기존 최고 성능 모델들보다 2.0 블루 점수 이상 높게 나와 새로운 최고 수준의 블루 점수인 28.4를 달성했다.",
                List.of(154), "실험");
        List<ComicPlan.PagePlan> pages = List.of(
                new ComicPlan.PagePlan(1, "기", "", List.of(), List.of(fromAbstract)),
                new ComicPlan.PagePlan(3, "전", "", List.of(), List.of(dropout, smoothing, fromResults)));

        List<ComicPlan.PagePlan> result = ComicPlanner.dropRepeatedResults(pages, byNumber);

        assertThat(result.getFirst().facts()).as("초록 쪽 사실을 버린다").isEmpty();
        assertThat(result.getLast().facts()).as("숫자 0.1만 같은 다른 사실은 남긴다").containsExactly(dropout, smoothing, fromResults);
    }

    @Test
    void 문제_칸에는_논문_앞부분_사실만_방법_결과_칸에는_이_연구의_사실만_잇는다() {
        // 198번: 방법 섹션의 '압축 단계와 생성 학습 단계를 분리한다'가 1쪽(문제)에, '한 연구는 … 조건부 가역 네트워크를 사용한다'가 2쪽(방법)에 붙었다
        assertThat(ComicPlanner.linkMismatch("problem", ComicPlanner.CHUNKS_PER_PAGE, 12, "이 연구는 압축 단계와 생성 학습 단계를 분리한다.")).isNotNull();
        assertThat(ComicPlanner.linkMismatch("problem", ComicPlanner.CHUNKS_PER_PAGE - 1, 12, "기존 방법은 계산 비용이 크다.")).isNull();
        assertThat(ComicPlanner.linkMismatch("method", 3, 12, "더 일반적으로, 한 연구는 조건부 가역 네트워크를 사용한다.")).isNotNull();
        assertThat(ComicPlanner.linkMismatch("result", 8, 12, "앞선 연구들은 픽셀 공간에서 학습했다.")).isNotNull();
        assertThat(ComicPlanner.linkMismatch("method", 3, 12, "이 연구는 자동 인코더로 그림을 압축한다.")).isNull();
        assertThat(ComicPlanner.linkMismatch("limitation", 11, 12, "기존 방법은 더 빠르다.")).isNull();
        // 200번: 서론의 '5만 장에 5일'(1번째 조각)이 한계 쪽에, '몇 가지 2단계 접근 방식은 … ARM을 사용한다'가 방법 쪽에 붙었다
        assertThat(ComicPlanner.linkMismatch("limitation", 1, 12, "그림 5만 장을 만드는 데 약 5일이 걸린다.")).isNotNull();
        assertThat(ComicPlanner.linkMismatch("method", 2, 12, "더 높은 해상도로 확장하기 위해 몇 가지 2단계 접근 방식은 압축된 공간을 모델링한다.")).isNotNull();
        assertThat(ComicPlanner.linkMismatch("method", 3, 12, "연구들은 두 단계 접근 방식으로 장점을 결합했다.")).isNotNull();
        assertThat(ComicPlanner.linkMismatch("method", 3, 12, "이 연구는 몇 가지 실험으로 압축 정도를 정한다.")).isNull();
        assertThat(ComicPlanner.linkMismatch("method", 3, 12, "더 높은 해상도로 확장하기 위해, 여러 2단계 접근법은 ARM을 사용한다.")).isNotNull();
    }

    @Test
    void 한_쪽의_사실은_아직_없는_섹션에서_먼저_고른다() {
        // 200번: 결과 쪽 6개 중 5개가 '압축 정도 비교' 한 섹션에서 나와 FID 5.11 등이 빠졌다
        PaperJob job = new PaperJob("ldm.pdf");
        Map<Integer, PaperSentence> byNumber = Map.of(
                7, new PaperSentence(job, 7, "Abstract", 1, "LDMs achieve new state-of-the-art scores."),
                130, new PaperSentence(job, 130, "On Perceptual Compression Tradeoffs", 5, "Small factors result in slow training."),
                132, new PaperSentence(job, 132, "On Perceptual Compression Tradeoffs", 5, "A FID gap of 38."),
                134, new PaperSentence(job, 134, "On Perceptual Compression Tradeoffs", 5, "LDM-4 and -8 outperform."),
                140, new PaperSentence(job, 140, "Image Generation with Latent Diffusion", 5, "A new FID of 5.11."));
        ComicPlan.SummaryPoint result = new ComicPlan.SummaryPoint("result", "이 연구는 새로운 최고 점수를 냈다.", List.of(7));
        ComicPlan.FactPlan slow = new ComicPlan.FactPlan("압축 계수가 작으면 학습이 2.5배 느리다.", List.of(130), "실험");
        ComicPlan.FactPlan gap = new ComicPlan.FactPlan("픽셀 기반과 FID 차이가 38.0이다.", List.of(132), "실험");
        ComicPlan.FactPlan better = new ComicPlan.FactPlan("LDM-4와 LDM-8이 1.5배 낫다.", List.of(134), "실험");
        ComicPlan.FactPlan faces = new ComicPlan.FactPlan("얼굴 사진 생성에서 FID 5.11을 얻었다.", List.of(140), "실험");
        List<ComicPlanner.Linked> linked = List.of(new ComicPlanner.Linked(slow, 1), new ComicPlanner.Linked(gap, 1),
                new ComicPlanner.Linked(better, 1), new ComicPlanner.Linked(faces, 1));

        List<ComicPlan.PagePlan> pages = ComicPlanner.pagesBySummary(PageRole.forPages(4), List.of(), List.of(result), linked, byNumber);

        assertThat(pages.get(2).facts()).hasSize(ComicPlanner.FACTS_PER_POINT).contains(faces);
    }

    @Test
    void 만화에_쓸_사실은_근거_문장이나_글이_겹치지_않고_요약의_근거_사실을_먼저_쓴다() {
        // 198번: 1쪽 요약의 근거(3번 '수백 GPU 날')가 빠지고, 4쪽에 240번 사실이 두 번 나왔다
        PaperJob job = new PaperJob("ldm.pdf");
        Map<Integer, PaperSentence> byNumber = Map.of(
                3, new PaperSentence(job, 3, "Abstract", 1, "Training consumes hundreds of GPU days."),
                20, new PaperSentence(job, 20, "Introduction", 1, "Evaluations are computationally demanding."),
                21, new PaperSentence(job, 21, "Introduction", 1, "Producing samples takes 5 days."),
                240, new PaperSentence(job, 240, "Limitations", 9, "Reconstruction can become a bottleneck."),
                241, new PaperSentence(job, 241, "Limitations", 9, "Sampling is slower than GANs."),
                259, new PaperSentence(job, 259, "Conclusion", 9, "Sampling remains slower than GANs."));
        ComicPlan.SummaryPoint problem = new ComicPlan.SummaryPoint("problem", "확산 모델 학습에 수백 GPU 날이 든다.", List.of(3));
        ComicPlan.SummaryPoint limitation = new ComicPlan.SummaryPoint("limitation", "재구성 능력이 병목이 될 수 있다.", List.of(240));
        ComicPlan.FactPlan fromAbstract = new ComicPlan.FactPlan("기존 확산 모델은 학습에 수백 GPU 날이 걸린다.", List.of(3), "초록");
        ComicPlan.FactPlan demanding = new ComicPlan.FactPlan("확산 모델은 계산을 많이 한다.", List.of(20), "서론");
        ComicPlan.FactPlan days = new ComicPlan.FactPlan("확산 모델로 그림 5만 장을 만드는 데 5일이 걸린다.", List.of(21), "서론");
        ComicPlan.FactPlan precision = new ComicPlan.FactPlan("정밀도가 높아야 할 때는 이 모델이 맞지 않을 수 있다.", List.of(240), "한계");
        ComicPlan.FactPlan bottleneck = new ComicPlan.FactPlan("재구성 능력이 세밀한 작업에서 병목이 된다.", List.of(240), "한계");
        ComicPlan.FactPlan slow = new ComicPlan.FactPlan("이 모델은 GAN보다 샘플링이 느리다.", List.of(241), "한계");
        ComicPlan.FactPlan sameText = new ComicPlan.FactPlan("이 모델은 GAN보다 샘플링이 느리다.", List.of(259), "결론");
        List<ComicPlanner.Linked> linked = List.of(new ComicPlanner.Linked(demanding, 1), new ComicPlanner.Linked(days, 1),
                new ComicPlanner.Linked(fromAbstract, 1), new ComicPlanner.Linked(precision, 2), new ComicPlanner.Linked(bottleneck, 2),
                new ComicPlanner.Linked(slow, 2), new ComicPlanner.Linked(sameText, 2));

        List<ComicPlan.PagePlan> pages = ComicPlanner.pagesBySummary(PageRole.forPages(4), List.of(), List.of(problem, limitation), linked,
                byNumber);

        assertThat(pages.getFirst().facts()).as("요약과 근거가 같은 초록 사실이 먼저").first().isEqualTo(fromAbstract);
        assertThat(pages.getLast().facts()).as("240번은 한 번만, 글이 같은 사실도 한 번만").hasSize(2)
                .filteredOn(fact -> fact.evidence().contains(240)).hasSize(1);
        assertThat(pages.getLast().facts()).extracting(ComicPlan.FactPlan::fact).containsOnlyOnce("이 모델은 GAN보다 샘플링이 느리다.");
    }

    @Test
    void 요약_문장의_근거에서_나온_사실이_없을_때만_요약_문장을_먼저_쓴다() {
        PaperJob job = new PaperJob("ldm.pdf");
        Map<Integer, PaperSentence> byNumber = Map.of(
                3, new PaperSentence(job, 3, "Abstract", 1, "Training consumes hundreds of GPU days."),
                20, new PaperSentence(job, 20, "Introduction", 1, "Evaluations are computationally demanding."));
        ComicPlan.SummaryPoint problem = new ComicPlan.SummaryPoint("problem", "확산 모델 학습에 수백 GPU 날이 든다.", List.of(3));
        ComicPlan.FactPlan demanding = new ComicPlan.FactPlan("확산 모델은 계산을 많이 한다.", List.of(20), "서론");
        ComicPlan.FactPlan elsewhere = new ComicPlan.FactPlan("기존 확산 모델은 학습에 수백 GPU 날이 걸린다.", List.of(3), "초록");

        assertThat(ComicPlanner.candidates(problem, List.of(demanding), Set.of(20), byNumber)).as("3번에서 나온 사실이 없다")
                .containsExactly(new ComicPlan.FactPlan(problem.text(), List.of(3), "요약"), demanding);
        assertThat(ComicPlanner.candidates(problem, List.of(demanding), Set.of(3, 20), byNumber)).as("3번 사실이 다른 요약에 붙어 있다")
                .containsExactly(demanding);
        assertThat(ComicPlanner.candidates(problem, List.of(demanding, elsewhere), Set.of(3, 20), byNumber))
                .containsExactly(elsewhere, demanding);
    }

    @Test
    void 결과_숫자가_든_사실은_어느_실험의_결과인지_있어야_한다() {
        // 198번: 인페인팅 섹션의 228번 'Overall, … 2.7×'가 '전반적으로 … 최소 2.7배'가 되어 논문 전체의 결과처럼 읽혔다
        PaperJob job = new PaperJob("ldm.pdf");
        Map<Integer, PaperSentence> byNumber = Map.of(
                7, new PaperSentence(job, 7, "Abstract", 1, "LDMs achieve new state-of-the-art scores for image inpainting."),
                228, new PaperSentence(job, 228, "Inpainting with Latent Diffusion", 8, "Overall, we observe a speed-up of at least 2.7×."));
        Set<String> titleWords = ComicPlanner.sectionKeywords("High-Resolution Image Synthesis with Latent Diffusion Models", Set.of());
        Set<Integer> allowed = Set.of(7, 228);
        List<ComicPlan.Term> terms = List.of(new ComicPlan.Term("inpainting", "이미지 채우기", List.of()));

        assertThat(ComicPlanner.sectionKeywords("Inpainting with Latent Diffusion", titleWords)).containsExactly("inpainting");
        assertThat(ComicPlanner.contextProblems(new ComicPlanner.FactsAnswer(List.of(
                new ComicPlanner.FactItem("전반적으로 최소 2.7배의 속도 향상이 관찰되었다.", List.of(228)))), allowed, byNumber, titleWords, terms))
                .singleElement().asString().contains("1번 사실의 숫자가 어느 실험에서 나온 결과인지 빠졌다", "Inpainting with Latent Diffusion");
        assertThat(ComicPlanner.contextProblems(new ComicPlanner.FactsAnswer(List.of(
                new ComicPlanner.FactItem("인페인팅(inpainting)에서 최소 2.7배 빨라졌다.", List.of(228)),
                new ComicPlanner.FactItem("이미지 채우기에서 최소 2.7배 빨라졌다.", List.of(228)),
                new ComicPlanner.FactItem("이 연구는 7개 작업에서 2.7배 좋은 점수를 냈다.", List.of(7)))), allowed, byNumber, titleWords, terms))
                .as("영어 원문, 정한 번역, 초록 근거는 통과").isEmpty();
    }

    @Test
    void 요약에_붙지_않은_결과_숫자가_있는_실험_사실은_결과_칸_후보로_넣는다() {
        // 201번: 인페인팅의 '최소 2.7배 … 1.6배'(228번)를 AI가 문제 칸에 붙여 끊겼고 결과 쪽에 실험 숫자가 빠졌다
        PaperJob job = new PaperJob("ldm.pdf");
        Map<Integer, PaperSentence> byNumber = Map.of(
                7, new PaperSentence(job, 7, "Abstract", 1, "LDMs achieve new state-of-the-art scores."),
                140, new PaperSentence(job, 140, "Image Generation with Latent Diffusion", 5, "A new FID of 5.11."),
                228, new PaperSentence(job, 228, "Inpainting with Latent Diffusion", 8, "A speed-up of at least 2.7×."));
        ComicPlan.FactPlan inpainting = new ComicPlan.FactPlan("인페인팅에서 최소 2.7배 빨라졌다.", List.of(228), "실험");
        ComicPlan.FactPlan fromAbstract = new ComicPlan.FactPlan("이 연구는 7개 작업에서 1.5배 좋았다.", List.of(7), "초록");
        ComicPlan.FactPlan noNumber = new ComicPlan.FactPlan("얼굴 사진이 진짜 같다.", List.of(140), "실험");
        List<ComicPlan.SummaryPoint> summary = List.of(new ComicPlan.SummaryPoint("problem", "계산이 많다.", List.of(7)),
                new ComicPlan.SummaryPoint("result", "최고 점수를 냈다.", List.of(7)), new ComicPlan.SummaryPoint("result", "빨라졌다.", List.of(7)));
        List<ComicPlanner.Linked> linked = List.of(new ComicPlanner.Linked(noNumber, 2));

        assertThat(ComicPlanner.isExperimentResult(inpainting, byNumber)).isTrue();
        assertThat(ComicPlanner.isExperimentResult(fromAbstract, byNumber)).as("초록 사실").isFalse();
        assertThat(ComicPlanner.isExperimentResult(noNumber, byNumber)).as("숫자 없음").isFalse();
        assertThat(ComicPlanner.spareResults(summary, List.of(inpainting), linked)).as("후보가 적은 3번 요약(결과)에")
                .containsExactly(new ComicPlanner.Linked(inpainting, 3));
    }

    @Test
    void 다른_연구의_문장을_이_연구의_일로_쓴_사실은_다시_쓰게_하고_방법_결과_칸에_잇지_않는다() {
        // 204번 3쪽 2컷: 원문 "[66] extend this approach …"가 사실 "이 연구는 … 공동 분포를 학습하여 …"가 되었다
        PaperJob job = new PaperJob("ldm.pdf");
        Map<Integer, PaperSentence> byNumber = Map.of(
                66, new PaperSentence(job, 66, "Two-Stage Image Synthesis", 3, "[66] extend this approach to text-to-image generation."),
                70, new PaperSentence(job, 70, "Two-Stage Image Synthesis", 3, "Our proposed LDMs scale more gently."));
        Set<Integer> allowed = Set.of(66, 70);

        assertThat(ComicPlanner.attributionProblems(new ComicPlanner.FactsAnswer(List.of(
                new ComicPlanner.FactItem("이 연구는 이미지와 텍스트의 공동 분포를 학습한다.", List.of(66)),
                new ComicPlanner.FactItem("앞선 연구는 이 방법을 글로 그림 만들기로 넓혔다.", List.of(66)),
                new ComicPlanner.FactItem("이 연구의 모델은 더 부드럽게 확장된다.", List.of(70)))), allowed, byNumber))
                .singleElement().asString().contains("1번 사실", "다른 연구");
        assertThat(ComicPlanner.isOtherStudy(new ComicPlan.FactPlan("앞선 연구는 글로 그림 만들기로 넓혔다.", List.of(66), "관련 연구"), byNumber)).isTrue();
        assertThat(ComicPlanner.linkMismatch("method", 3, 12, "앞선 연구는 글로 그림 만들기로 넓혔다.", true)).isNotNull();
        assertThat(ComicPlanner.linkMismatch("problem", 1, 12, "앞선 연구는 글로 그림 만들기로 넓혔다.", true)).as("문제 칸(기존 방법)에는 된다").isNull();
    }
}
