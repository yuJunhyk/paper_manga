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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가짜 AI로 대본 쓰기 흐름을 확인한다. 시험 단계에서 나온 실수(긴 대사, 냥박사님 존댓말, 해설 괄호)와
 * 실제 실행 1차에서 나온 실수(장면에 섞인 대사, 한계를 뒤집는 대사)를 섞는다.
 */
class ComicWriterTest {

    private static final Pattern FACT = Pattern.compile("이 컷이 전할 사실: (.+)");
    private static final Pattern VERIFY_LINE = Pattern.compile("^\\d+\\. (콩이|냥박사): .+$", Pattern.MULTILINE);
    private static final String LIMITATION_RULE_START = "7. 이 사실은 논문의 한계";
    private static final String[] PLACES = {"연구실 칠판 앞", "학교 복도", "도서관 창가", "실험실 책상", "운동장 벤치", "컴퓨터실"};
    private static final String[] ACTIONS = {"냥박사님이 그림을 그린다", "콩이가 모형을 집어 든다", "냥박사님이 창밖을 가리킨다", "콩이가 노트를 펼친다"};
    private static final int LIMITATION_SENTENCE = 8;
    /** 사실 2번부터 쓰는 대사. 컷마다 같은 대사를 내면 '바로 앞 대사 되풀이' 검사에 걸린다 */
    private static final String[] REACTIONS = {"그럼 제가 직접 해 볼게요!", "아하, 그래서 그렇게 되는군요!", "이번엔 제가 틀리게 짐작했네요.",
            "우와, 생각보다 간단하네요!", "그럼 다음은 어떻게 되나요?", "제가 그려 보니까 알겠어요!", "정말 놀라운 결과예요!"};
    private static final String[] ANALOGIES = {"요리 순서랑 비슷해.", "지도 읽기랑 비슷해.", "택배 분류랑 비슷해.", "도서관 정리랑 비슷해.",
            "줄넘기 연습이랑 비슷해.", "퍼즐 맞추기랑 비슷해.", "자전거 타기랑 비슷해."};
    private static final Pattern FACT_NUMBER = Pattern.compile("사실 (\\d+)번");

    static class FakeAi implements AiChatClient {

        final List<List<ChatMessage>> requests = new ArrayList<>();
        boolean reverseBoard;   // 콘티가 사실 순서를 거꾸로 바꾼다
        boolean brokenBoard;    // 콘티가 같은 사실 번호를 두 번 쓴다 (끝까지 고치지 않음)
        boolean alwaysAsk;      // 콩이가 컷마다 "냥박사님, …?" 질문으로 시작한다
        boolean wrongDoctor;    // 대사 확인이 냥박사 대사를 늘 '어긋남'으로 판정한다 (다시 써도 고치지 않음)
        boolean copyOpening;    // 여는 컷 첫 시도가 앞 쪽 마지막 대사("다음이 궁금해요!")를 그대로 옮긴다

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
                        {"setting":"콩이의 풍경화 숙제가 몇 시간째 끝나지 않는다",
                         "기":"**문제 제기**: 콩이가 좌절해 냥박사님을 찾아간다 '도와주세요!'","승":"2쪽: 냥박사님이 %s 방법을 알려 준다",
                         "전":"실험 결과에 콩이가 놀란다","결":"콩이가 숙제를 끝내고 한계도 배운다",
                         "ending":"콩이가 그림을 완성하고 과학의 힘을 깨닫는다"}""".formatted(attempt == 1 ? "Kernel Filtering 데이터 코믹스 흥미(학습 몰입도)" : "")
                        .replace("  ", " ");
            } else if (properties.containsKey("panels")) {
                String factList = prompt.substring(prompt.indexOf("전할 사실:"), prompt.indexOf("규칙:"));
                int count = (int) factList.lines().filter(line -> line.matches("^\\d+\\. .*")).count();
                List<String> items = new ArrayList<>();
                for (int k = 1; k <= count; k++) {
                    int fact = brokenBoard ? 1 : reverseBoard ? count - k + 1 : k;
                    items.add("{\"fact\":%d,\"plan\":\"앞 컷에 이어 콩이가 %d번째로 직접 해 본다\"}".formatted(fact, k));
                }
                content = "{\"panels\":[" + String.join(",", items) + "]}";
            } else if (properties.containsKey("results")) {
                List<String> verdicts = new ArrayList<>();
                Matcher line = VERIFY_LINE.matcher(prompt);
                for (int number = 1; line.find(); number++) {
                    boolean wrong = wrongDoctor && line.group(1).equals("냥박사");
                    verdicts.add("{\"line\":%d,\"verdict\":\"%s\",\"reason\":\"%s\"}"
                            .formatted(number, wrong ? "어긋남" : "맞음", wrong ? "비유가 사실과 반대다" : "풀어 썼다"));
                }
                content = "{\"results\":[" + String.join(",", verdicts) + "]}";
            } else if (properties.containsKey("opening")) {
                String opening = copyOpening && attempt == 1 && prompt.contains("앞 쪽의 마지막 대사")
                        ? "{\"speaker\":\"콩이\",\"text\":\"다음이 궁금해요!\"}" : "{\"speaker\":\"해설\",\"text\":\"콩이는 고민에 빠졌다.\"}";
                content = """
                        {"opening":{"scene":"콩이의 방, 멈춘 컴퓨터 앞에서 콩이가 한숨을 쉰다","lines":[%s]},
                         "closing":{"scene":"냥박사님 연구실, 콩이가 노트를 덮으며 활짝 웃는다","lines":[{"speaker":"콩이","text":"다음이 궁금해요!"}]}}""".formatted(opening);
            } else {
                Matcher matcher = FACT.matcher(prompt);
                String fact = matcher.find() ? matcher.group(1) : "?";
                if (attempt == 1) {
                    // 첫 시도: 장면에 대사가 섞이고 냥박사님 존댓말 → 다시 쓰기
                    content = "{\"scene\":\"연구실 칠판 앞, 냥박사님이 그림을 그린다. **냥박사님**:\",\"lines\":[{\"speaker\":\"냥박사\",\"text\":\"이건 정말 중요해요.\"}]}";
                } else if (attempt == 2 && prompt.contains(LIMITATION_RULE_START)) {
                    // 한계 컷의 두 번째 시도: 단점을 뒤집음 → 한 번 더 다시 쓰기
                    content = "{\"scene\":\"연구실 창가, 콩이가 고개를 갸웃한다\",\"lines\":[{\"speaker\":\"냥박사\",\"text\":\"처음엔 느리지만 결국엔 훨씬 빨라.\"}]}";
                } else {
                    // 컷마다 장소와 행동, 대사를 바꿔 앞 컷과 다른 장면이 되게 한다
                    int n = requests.size();
                    Matcher number = FACT_NUMBER.matcher(fact);
                    int k = number.find() ? Integer.parseInt(number.group(1)) : 1;
                    content = """
                            {"scene":"%2$s, %3$s","lines":[
                              {"speaker":"콩이","text":"%4$s"},
                              {"speaker":"냥박사","text":"%5$s"}]}"""
                            .formatted(fact, PLACES[n % PLACES.length], ACTIONS[n % ACTIONS.length],
                                    alwaysAsk ? "냥박사님, " + fact + " 왜 그래요?" : k == 1 ? "와, " + fact + " 신기해요!" : REACTIONS[(k - 2) % REACTIONS.length],
                                    k == 1 ? "레고 조립이랑 비슷해. (해설: 레고는 블록 장난감이다.)" : ANALOGIES[(k - 2) % ANALOGIES.length]);
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
        assertThat(factPanel.scene()).doesNotContain("*", "냥박사님:");
    }

    @Test
    void 이야기_흐름은_자기_쪽_칸에서_가져오고_따옴표_대사와_이름표와_기호는_지운다() {
        FakeAi ai = new FakeAi();
        WrittenComic comic = writer(ai).write(plan(), sentences(), (done, total) -> {
        });

        assertThat(comic.pages()).extracting(WrittenComic.Page::beat).containsExactly(
                "콩이가 좌절해 냥박사님을 찾아간다 도와주세요!", "냥박사님이 방법을 알려 준다",
                "실험 결과에 콩이가 놀란다", "콩이가 숙제를 끝내고 한계도 배운다");
        assertThat(comic.setting()).isEqualTo("콩이의 풍경화 숙제가 몇 시간째 끝나지 않는다");
        // 기호와 이름표는 먼저 다시 쓰게 하고(가짜 AI는 같은 답을 내서), 그래도 남으면 코드가 지운다
        assertThat(ai.requests.stream().filter(messages -> messages.size() == 4).map(messages -> messages.get(3).content()))
                .anyMatch(content -> content.contains("이야기 뼈대에 서식 기호"));
    }

    @Test
    void 이야기_뼈대에_논문에_없는_영어_용어가_있으면_다시_쓰게_한다() {
        FakeAi ai = new FakeAi();
        WrittenComic comic = writer(ai).write(plan(), sentences(), (done, total) -> {
        });

        assertThat(ai.requests.stream().filter(messages -> messages.size() == 4).map(messages -> messages.get(3).content()))
                .anyMatch(content -> content.contains("논문에 없는 영어 용어가 있다: Kernel, Filtering"));
        assertThat(comic.pages().get(1).beat()).doesNotContain("Kernel");
        assertThat(ai.requests.getFirst().get(1).content()).as("이야기 뼈대 지시문에 용어 규칙").contains("괄호에 논문의 영어 원문");
    }

    @Test
    void 사실_컷_요청에는_사실이_하나뿐이고_앞_컷의_장면과_줄거리와_바로_앞_대사를_넘긴다() {
        FakeAi ai = new FakeAi();
        writer(ai).write(plan(), sentences(), (done, total) -> {
        });

        List<String> factPrompts = ai.requests.stream().filter(messages -> messages.size() == 2)
                .map(messages -> messages.get(1).content()).filter(prompt -> prompt.contains("이 컷이 전할 사실:")).toList();
        assertThat(factPrompts).hasSize(8);
        assertThat(factPrompts.get(1)).contains("이 컷이 전할 사실: 사실 2번 내용이다.").doesNotContain("사실 1번 내용이다.")
                .contains("바로 앞 컷의 장면:").contains("이 컷의 줄거리: 앞 컷에 이어 콩이가 2번째로 직접 해 본다")
                .contains("바로 앞 대사: 냥박사: 레고 조립이랑 비슷해.")
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
        // 한계를 뒤집는 문제만 없으면 된다
        assertThat(limitationPanel.problems()).noneMatch(problem -> problem.contains("단점을 뒤집는다"));
    }

    @Test
    void 진행률은_시작_때_0을_알리고_이야기_1번_콘티_4번_사실_컷_8번_연출_4번으로_모두_17단계다() {
        List<String> progress = new ArrayList<>();
        writer(new FakeAi()).write(plan(), sentences(), (done, total) -> progress.add(done + "/" + total));

        assertThat(progress).hasSize(18).first().isEqualTo("0/17");
        assertThat(progress).last().isEqualTo("17/17");
    }

    @Test
    void 사실_컷은_콘티가_정한_순서대로_놓이고_콘티_요청에는_그_쪽의_사실이_번호와_함께_들어간다() {
        FakeAi ai = new FakeAi();
        ai.reverseBoard = true;
        WrittenComic comic = writer(ai).write(plan(), sentences(), (done, total) -> { });

        List<WrittenComic.Panel> firstPage = comic.pages().getFirst().panels();
        assertThat(firstPage.get(1).fact()).isEqualTo("사실 2번 내용이다.");
        assertThat(firstPage.get(1).evidence()).as("근거는 사실을 따라간다").containsExactly(2);
        assertThat(firstPage.get(2).fact()).isEqualTo("사실 1번 내용이다.");
        assertThat(ai.requests.stream().map(messages -> messages.get(1).content()).filter(prompt -> prompt.contains("콘티를 짜라")).findFirst())
                .get().asString().contains("전할 사실:\n1. 사실 1번 내용이다.\n2. 사실 2번 내용이다.");
    }

    @Test
    void 콘티가_사실을_빠뜨리거나_겹치면_다시_묻고_끝까지_틀리면_원래_순서로_쓴다() {
        FakeAi ai = new FakeAi();
        ai.brokenBoard = true;
        WrittenComic comic = writer(ai).write(plan(), sentences(), (done, total) -> { });

        assertThat(ai.requests.stream().filter(messages -> messages.size() == 4).map(messages -> messages.get(3).content()))
                .anyMatch(content -> content.contains("2번 사실이 콘티에 없다") && content.contains("1번 사실을 2번 넣었다"));
        assertThat(comic.pages().getFirst().panels()).extracting(WrittenComic.Panel::fact)
                .containsExactly(null, "사실 1번 내용이다.", "사실 2번 내용이다.", null);
    }

    @Test
    void 앞_컷과_같은_모양의_질문으로_시작하면_다시_쓰게_한다() {
        FakeAi ai = new FakeAi();
        ai.alwaysAsk = true;
        writer(ai).write(plan(), sentences(), (done, total) -> { });

        assertThat(ai.requests.stream().filter(messages -> messages.size() >= 4).map(messages -> messages.getLast().content()))
                .anyMatch(content -> content.contains("앞 컷도 '냥박사님, …?' 질문으로 시작했다"));
    }

    @Test
    void 대본_지시문에는_첫_논문의_소재가_예시로_남아_있지_않다() {
        // 두 번째 논문(176번)의 이야기에 예시 '숙제로 고화질 그림을…'이 '고화질 데이터 시각화 만화'로 새어 나왔다
        assertThat(Stream.of(WritePrompts.TITLE_RULE, WritePrompts.TERM_RULE, WritePrompts.CHARACTERS, WritePrompts.STYLE_EXAMPLE,
                WritePrompts.STORY, WritePrompts.STORYBOARD, WritePrompts.LIMITATION_RULE, WritePrompts.FACT_PANEL, WritePrompts.STAGING,
                WritePrompts.LINE_VERIFY_SYSTEM, WritePrompts.LINE_VERIFY))
                .noneMatch(prompt -> prompt.contains("잠재") || prompt.contains("확산") || prompt.contains("GAN") || prompt.contains("고화질"));
        assertThat(Stream.of(WritePrompts.TITLE_RULE, WritePrompts.TERM_RULE))
                .as("예시 낱말은 무엇이든 새어 나온다 (178번 '표본 크기(sample size)')").noneMatch(prompt -> prompt.contains("예:"));
    }

    @Test
    void 정한_용어_번역을_지시문에_붙이고_다른_번역을_쓴_이야기는_다시_쓰게_한다() {
        FakeAi ai = new FakeAi();
        ComicPlan base = plan();
        ComicPlan plan = new ComicPlan(base.comicTitle(), base.pages(), base.stats(),
                List.of(new ComicPlan.Term("data comics", "데이터 만화", List.of("데이터 코믹스"))));

        writer(ai).write(plan, sentences(), (done, total) -> { });

        assertThat(ai.requests).allMatch(messages -> messages.getFirst().content().contains("- data comics → 데이터 만화 (쓰지 않을 말: 데이터 코믹스)"));
        assertThat(ai.requests.get(1).getLast().content()).contains("이야기 뼈대에 정한 번역과 다른 용어가 있다: '데이터 코믹스' → '데이터 만화'")
                .contains("이야기 뼈대의 괄호에 영어 원문 대신 한국어가 들어 있다: (학습 몰입도)");
    }

    @Test
    void 이야기_흐름에서_인용_표시와_수식_기호를_지운다() {
        assertThat(ComicWriter.beat(">콩이는 다운샘플링 계수 $f$를 바꿔 본다")).isEqualTo("콩이는 다운샘플링 계수 f를 바꿔 본다");
    }

    @Test
    void 쪽이_바뀌면_앞_쪽_마지막_장면과_대사를_콘티와_첫_사실_컷과_여는_컷에_넘긴다() {
        // 188번: 쪽마다 첫 컷이 "긴 문장은 왜 자꾸 틀리게 번역되나요?"로 되돌아갔다
        FakeAi ai = new FakeAi();
        writer(ai).write(plan(), sentences(), (done, total) -> { });

        List<String> prompts = ai.requests.stream().filter(messages -> messages.size() == 2).map(messages -> messages.get(1).content()).toList();
        List<String> boards = prompts.stream().filter(prompt -> prompt.contains("콘티를 짜라")).toList();
        assertThat(boards.getFirst()).doesNotContain("앞 쪽의 마지막 장면:");
        assertThat(boards.get(1)).contains("앞 쪽의 마지막 장면: 냥박사님 연구실, 콩이가 노트를 덮으며 활짝 웃는다")
                .contains("앞 쪽의 마지막 대사: 콩이: 다음이 궁금해요!");
        assertThat(prompts.stream().filter(prompt -> prompt.contains("이 컷이 전할 사실: 사실 3번 내용이다.")).findFirst()).get().asString()
                .as("2쪽 첫 사실 컷").contains("바로 앞 대사: 콩이: 다음이 궁금해요!");
        List<String> staging = prompts.stream().filter(prompt -> prompt.contains("연출 컷 두 개를 써라")).toList();
        assertThat(staging.getFirst()).contains("이번 쪽 첫 사실 컷의 첫 대사: 콩이: 와, 사실 1번 내용이다. 신기해요!")
                .doesNotContain("앞 쪽의 마지막 장면:");
        assertThat(staging.get(1)).contains("앞 쪽의 마지막 대사: 콩이: 다음이 궁금해요!", "이번 쪽 첫 사실 컷의 장면:");
    }

    @Test
    void 여는_컷이_앞_쪽_마지막_대사를_되풀이하면_다시_쓰게_한다() {
        // 195번: 2쪽 여는 컷이 1쪽 7컷 "그럼 고화질로 만들려면 그만큼 시간이 오래 걸린다는 뜻인가요?"를 그대로 옮겼다
        FakeAi ai = new FakeAi();
        ai.copyOpening = true;
        WrittenComic comic = writer(ai).write(plan(), sentences(), (done, total) -> { });

        assertThat(ai.requests.stream().filter(messages -> messages.size() >= 4).map(messages -> messages.getLast().content()))
                .anyMatch(content -> content.contains("바로 앞 대사 \"다음이 궁금해요!\"와 거의 같은 말이다"));
        assertThat(comic.pages().get(1).panels().getFirst().lines()).extracting(LineRules.Line::text).doesNotContain("다음이 궁금해요!");
    }

    @Test
    void 사실_컷의_인물_대사는_사실과_근거_원문에_대조하고_다시_써도_어긋나면_뺀다() {
        // 188번: 학습률을 '처음에 늘리고 나중에 줄인다'는 사실에 냥박사가 '점점 속도를 올리는 것'이라는 비유를 했다
        FakeAi ai = new FakeAi();
        ai.wrongDoctor = true;
        WrittenComic comic = writer(ai).write(plan(), sentences(), (done, total) -> { });

        String verifyPrompt = ai.requests.stream().map(messages -> messages.get(1).content())
                .filter(prompt -> prompt.contains("대사마다 판정하라") && prompt.contains("이 컷의 사실: 사실 1번 내용이다."))
                .filter(prompt -> prompt.contains("레고")).findFirst().orElseThrow();
        assertThat(verifyPrompt).contains("근거 원문: [1] Original sentence 1 of the paper.", "2. 냥박사: 레고 조립이랑 비슷해.");
        assertThat(ai.requests.stream().filter(messages -> messages.size() >= 4).map(messages -> messages.getLast().content()))
                .anyMatch(content -> content.contains("2번 대사 \"레고 조립이랑 비슷해.\"는 사실과 어긋난다: 비유가 사실과 반대다"));
        WrittenComic.Panel factPanel = comic.pages().getFirst().panels().get(1);
        assertThat(factPanel.lines()).extracting(LineRules.Line::speaker).doesNotContain("냥박사");
        assertThat(factPanel.lines()).contains(new LineRules.Line("콩이", "와, 사실 1번 내용이다. 신기해요!"),
                new LineRules.Line("해설", "사실 1번 내용이다."));
    }
}
