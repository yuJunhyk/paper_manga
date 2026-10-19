package com.readmanga.backend.comic.write;

import com.readmanga.backend.ai.OllamaProperties;
import com.readmanga.backend.comic.ProgressListener;
import com.readmanga.backend.comic.Speaker;
import com.readmanga.backend.comic.plan.AiAsker;
import com.readmanga.backend.comic.plan.ComicPlan;
import com.readmanga.backend.comic.plan.JsonSchemas;
import com.readmanga.backend.comic.plan.TextRules;
import com.readmanga.backend.paper.PaperSentence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 만화 설계도로 대본을 쓴다 (AI: 설정의 ollama.dialogue-model, 2026-10-06부터 gemma4).
 * ① 이야기 뼈대: 배경 사건, 쪽별 흐름(기·승·전·결 이름 칸), 결말
 * ② 쪽마다 콘티: 사실 컷의 순서와 컷마다의 줄거리를 정한다(사실 문장은 건드리지 않음).
 * ③ 콘티 순서대로 사실 컷을 하나씩 따로 쓴다. 요청마다 사실이 하나뿐이라 다른 사실과 섞일 수 없다(5차 시험의 한 칸 밀림 방지).
 *    컷마다 줄거리와 바로 앞 대사를 넘겨 앞 컷에 이어지게 한다(186번: 컷마다 따로 써서 질문 나열이 됨).
 *    사실 컷의 해설은 AI가 아니라 코드가 검증된 사실 문장으로 넣는다(6차 시험의 덧붙임 방지).
 * ④ 쪽마다 여는 연출 컷과 닫는 연출 컷을 쓴다.
 * 쪽이 바뀔 때는 앞 쪽 마지막 컷의 장면과 대사를 다음 쪽의 콘티·첫 사실 컷·여는 컷에 넘긴다(188번: 쪽마다 같은 불평으로 되돌아감).
 * 한계 섹션에서 나온 사실 컷은 단점을 뒤집는 대사가 없는지 더 살핀다(실제 실행 1차).
 */
@Service
public class ComicWriter {

    private static final Logger log = LoggerFactory.getLogger(ComicWriter.class);

    static final int STORY_RETRIES = 1;
    static final int PANEL_RETRIES = 2;  // 컷은 요청 하나가 작아서 다시 쓰기를 2번까지 한다

    /** 이야기 뼈대에 섞인 서식 기호와 '1쪽:' 이름표 (2차 실행에서 쪽별 흐름이 모두 '**문제 제기**:'로 시작했다) */
    private static final Pattern STORY_MARKUP = Pattern.compile("[*#`{}]|(^|\\s)\\d+\\s*쪽\\s*[:：]");
    /** 흐름 앞에 붙는 이름표: "1쪽: …", "문제 제기: …" (기호를 지운 뒤에 뗀다) */
    private static final Pattern BEAT_LABEL = Pattern.compile("^(\\d+\\s*쪽\\s*[:：]\\s*)?([가-힣A-Za-z· ]{1,8}[:：]\\s*)?");

    private static final List<String> FACT_SPEAKERS = List.of(Speaker.NURI.getLabel(), Speaker.DOCTOR.getLabel());  // 해설은 코드가 넣는다
    private static final List<String> STAGING_SPEAKERS = List.of(Speaker.NURI.getLabel(), Speaker.DOCTOR.getLabel(), Speaker.NARRATION.getLabel());

    private final AiAsker aiAsker;
    private final OllamaProperties properties;

    public ComicWriter(AiAsker aiAsker, OllamaProperties properties) {
        this.aiAsker = aiAsker;
        this.properties = properties;
    }

    /** AI 응답 모양 */
    record PanelAnswer(String scene, List<LineAnswer> lines) {
    }

    record LineAnswer(String speaker, String text) {
    }

    record StagingAnswer(PanelAnswer opening, PanelAnswer closing) {
    }

    record StoryboardAnswer(List<StoryboardItem> panels) {
    }

    record StoryboardItem(int fact, String plan) {
    }

    /** 콘티 한 칸: 이 컷이 전할 사실과 그 컷의 줄거리 */
    record Board(ComicPlan.FactPlan fact, String plan) {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public WrittenComic write(ComicPlan plan, Map<Integer, PaperSentence> sentencesByNumber, ProgressListener listener) {
        long started = System.currentTimeMillis();
        Set<String> servers = new LinkedHashSet<>();
        int totalSteps = 1 + plan.pages().stream().mapToInt(page -> page.facts().size() + 2).sum();  // 이야기 뼈대 + 쪽마다 (콘티 + 사실 컷들 + 연출 컷)
        int doneSteps = 0;
        listener.update(0, totalSteps);  // 시작하자마자 0/전체를 보여 준다 (0/0이면 멈춤과 대기를 구분할 수 없다)
        String model = properties.dialogueModel();
        // 이야기 뼈대·연출 컷은 사실 검사를 받지 않아 논문에 없는 용어를 지어내도 걸러지지 않았다(3차 실행 '커널 필터링').
        // 영어 낱말만은 논문 원문과 대조할 수 있다. 한국어로 음차된 용어는 코드로 대조하지 못한다
        Set<String> paperWords = TextRules.englishWords(sentencesByNumber.values().stream()
                .flatMap(sentence -> Stream.of(sentence.getText(), String.valueOf(sentence.getSectionTitle()))).toList());

        // ① 이야기 뼈대
        List<String> roles = plan.pages().stream().map(ComicPlan.PagePlan::role).toList();
        AiAsker.Answer<Map> story = aiAsker.ask(model, WritePrompts.STORY_SYSTEM + plan.termGuide(), storyPrompt(plan), storySchema(roles),
                Map.class, answer -> storyProblems((Map<String, Object>) answer, paperWords, plan.terms()), STORY_RETRIES);
        story.attempts().forEach(attempt -> servers.add(attempt.server()));
        listener.update(++doneSteps, totalSteps);
        Map<String, Object> storyValue = story.value();
        String setting = beat(storyValue.get("setting"));
        String ending = beat(storyValue.get("ending"));
        log.info("이야기 뼈대: {}", setting);

        // ②③④ 쪽마다 콘티, 사실 컷(콘티 순서), 연출 컷
        List<WrittenComic.Page> pages = new ArrayList<>();
        String previousScene = null;               // 앞 쪽 마지막 컷의 장면
        List<LineRules.Line> previousLines = List.of();  // 앞 쪽 마지막 컷의 인물 대사
        for (ComicPlan.PagePlan page : plan.pages()) {
            String beat = beat(storyValue.get(page.role()));
            String previousPage = previousPage(previousScene, previousLines);
            List<Board> board = storyboard(model, plan, page, setting, beat, previousPage, paperWords, servers);
            listener.update(++doneSteps, totalSteps);
            Set<String> seenLines = new HashSet<>();
            List<WrittenComic.Panel> factPanels = new ArrayList<>();
            for (Board item : board) {
                String sceneBefore = factPanels.isEmpty() ? previousScene : factPanels.getLast().scene();
                List<LineRules.Line> linesBefore = factPanels.isEmpty() ? previousLines : spoken(factPanels.getLast().lines());
                WrittenComic.Panel panel = writeFactPanel(model, plan, page, setting, beat, sceneBefore, item, linesBefore,
                        sentencesByNumber, seenLines, paperWords, servers);
                factPanels.add(panel);
                listener.update(++doneSteps, totalSteps);
            }

            String lastFactScene = factPanels.isEmpty() ? previousScene : factPanels.getLast().scene();
            String around = previousPage + firstFactPanel(factPanels);
            List<WrittenComic.Panel> staging = writeStagingPanels(model, plan, page, setting, beat, ending, previousScene, around,
                    factPanels.isEmpty() ? null : factPanels.getFirst(), lastFactScene, seenLines, paperWords, servers);
            listener.update(++doneSteps, totalSteps);

            List<WrittenComic.Panel> panels = new ArrayList<>();
            panels.add(staging.getFirst());
            panels.addAll(factPanels);
            panels.add(staging.getLast());
            pages.add(new WrittenComic.Page(page.pageNumber(), page.role(), beat, panels));
            previousScene = staging.getLast().scene();
            List<LineRules.Line> closingLines = spoken(staging.getLast().lines());
            previousLines = closingLines.isEmpty() && !factPanels.isEmpty() ? spoken(factPanels.getLast().lines()) : closingLines;
            log.info("{}쪽({}) 대본 완료: 컷 {}개", page.pageNumber(), page.role(), panels.size());
        }

        return new WrittenComic(plan.comicTitle(), setting, ending, pages, System.currentTimeMillis() - started, List.copyOf(servers));
    }

    // ---------- ② 쪽 콘티 ----------

    /**
     * 쪽 콘티: 사실 컷의 순서와 컷마다의 줄거리를 먼저 정한다. 사실 번호가 빠지거나 겹치면 다시 묻고,
     * 끝까지 틀리면(또는 답이 모두 깨지면) 작업을 멈추지 않고 원래 순서·줄거리 없이 쓴다.
     */
    private List<Board> storyboard(String model, ComicPlan plan, ComicPlan.PagePlan page, String setting, String beat,
                                   String previousPage, Set<String> paperWords, Set<String> servers) {
        List<ComicPlan.FactPlan> facts = page.facts();
        List<Board> original = facts.stream().map(fact -> new Board(fact, "")).toList();
        if (facts.size() < 2) {
            return original;
        }
        StringBuilder list = new StringBuilder();
        for (int index = 0; index < facts.size(); index++) {
            list.append("%d. %s%n".formatted(index + 1, facts.get(index).fact()));
        }
        String prompt = WritePrompts.STORYBOARD.formatted(page.pageNumber(), plan.pages().size(), page.role(), setting, beat,
                previousPage, facts.size(), list.toString().trim());
        Map<String, Object> schema = JsonSchemas.object("panels", JsonSchemas.array(
                JsonSchemas.object("fact", JsonSchemas.integer(), "plan", JsonSchemas.string()), facts.size(), facts.size()));
        AiAsker.Answer<StoryboardAnswer> answer;
        try {
            answer = aiAsker.ask(model, WritePrompts.STORY_SYSTEM + plan.termGuide(), prompt, schema, StoryboardAnswer.class,
                    value -> storyboardProblems(value, facts.size(), paperWords, plan.terms()), PANEL_RETRIES);
        } catch (IllegalStateException e) {
            log.warn("{}쪽 콘티를 건너뜁니다(사실 순서 그대로): {}", page.pageNumber(), e.getMessage());
            return original;
        }
        answer.attempts().forEach(attempt -> servers.add(attempt.server()));
        if (!isPermutation(answer.value(), facts.size())) {
            log.warn("{}쪽 콘티의 사실 번호가 끝까지 맞지 않아 사실 순서 그대로 씁니다", page.pageNumber());
            return original;
        }
        return answer.value().panels().stream()
                .map(item -> new Board(facts.get(item.fact() - 1), oneLine(item.plan())))
                .toList();
    }

    static List<String> storyboardProblems(StoryboardAnswer answer, int factCount, Set<String> paperWords, List<ComicPlan.Term> terms) {
        List<String> problems = new ArrayList<>();
        if (answer.panels() == null) {
            return List.of("panels가 없다. 사실마다 컷 하나씩 panels에 넣는다.");
        }
        List<Integer> numbers = answer.panels().stream().map(StoryboardItem::fact).toList();
        for (int number = 1; number <= factCount; number++) {
            int times = java.util.Collections.frequency(numbers, number);
            if (times == 0) {
                problems.add("%d번 사실이 콘티에 없다. 모든 사실을 한 번씩 넣는다.".formatted(number));
            } else if (times > 1) {
                problems.add("%d번 사실을 %d번 넣었다. 사실마다 한 번씩만 넣는다.".formatted(number, times));
            }
        }
        numbers.stream().filter(number -> number < 1 || number > factCount).distinct()
                .forEach(number -> problems.add("%d번 사실은 없다. 1~%d번만 쓴다.".formatted(number, factCount)));
        String plans = answer.panels().stream().map(StoryboardItem::plan).map(String::valueOf).collect(Collectors.joining(" "));
        if (answer.panels().stream().anyMatch(item -> item.plan() == null || item.plan().isBlank())) {
            problems.add("plan이 비어 있는 컷이 있다. 컷마다 일어나는 일을 한 문장으로 쓴다.");
        }
        problems.addAll(mixedScriptProblems("콘티", plans));
        problems.addAll(unknownTermProblems("콘티", plans, paperWords));
        problems.addAll(TextRules.termVariantProblems("콘티", plans, terms));
        return problems;
    }

    private static boolean isPermutation(StoryboardAnswer answer, int factCount) {
        return answer.panels() != null && answer.panels().size() == factCount && answer.panels().stream().map(StoryboardItem::fact).distinct()
                .filter(number -> number >= 1 && number <= factCount).count() == factCount;
    }

    /** 앞 쪽 마지막 컷의 장면과 마지막 대사. 첫 쪽이면 빈 글자. */
    private static String previousPage(String previousScene, List<LineRules.Line> previousLines) {
        if (previousScene == null) {
            return "";
        }
        String text = "앞 쪽의 마지막 장면: " + previousScene + "\n";
        return previousLines.isEmpty() ? text : text + "앞 쪽의 마지막 대사: " + say(previousLines.getLast()) + "\n";
    }

    /** 이번 쪽 첫 사실 컷의 장면과 첫 대사 (여는 컷이 그 앞을 잇는다) */
    private static String firstFactPanel(List<WrittenComic.Panel> factPanels) {
        if (factPanels.isEmpty()) {
            return "";
        }
        WrittenComic.Panel first = factPanels.getFirst();
        List<LineRules.Line> lines = spoken(first.lines());
        return "이번 쪽 첫 사실 컷의 장면: " + first.scene() + "\n"
                + (lines.isEmpty() ? "" : "이번 쪽 첫 사실 컷의 첫 대사: " + say(lines.getFirst()) + "\n");
    }

    private static String say(LineRules.Line line) {
        return line.speaker() + ": " + line.text();
    }

    /** 해설을 뺀 인물 대사 (다음 컷이 이어서 말할 앞 대사) */
    private static List<LineRules.Line> spoken(List<LineRules.Line> lines) {
        return lines.stream().filter(line -> !Speaker.NARRATION.getLabel().equals(line.speaker())).toList();
    }

    // ---------- ③ 사실 컷 ----------

    private WrittenComic.Panel writeFactPanel(String model, ComicPlan plan, ComicPlan.PagePlan page, String setting, String beat,
                                              String sceneBefore, Board item, List<LineRules.Line> linesBefore,
                                              Map<Integer, PaperSentence> sentencesByNumber, Set<String> seenLines,
                                              Set<String> paperWords, Set<String> servers) {
        ComicPlan.FactPlan fact = item.fact();
        String quotes = fact.evidence().stream()
                .map(number -> "[%d] %s".formatted(number, shorten(sentencesByNumber.get(number).getText(), 200)))
                .collect(Collectors.joining(" / "));
        boolean limitation = fact.evidence().stream()
                .anyMatch(number -> TextRules.isLimitationSection(sentencesByNumber.get(number).getSectionTitle()));
        // 앞 컷의 장면, 이 컷의 줄거리(콘티), 바로 앞 대사(마지막 한 줄)를 넘겨 컷끼리 이어지게 한다
        StringBuilder context = new StringBuilder();
        if (sceneBefore != null) {
            context.append("바로 앞 컷의 장면: ").append(sceneBefore).append('\n');
        }
        if (!item.plan().isBlank()) {
            context.append("이 컷의 줄거리: ").append(item.plan()).append('\n');
        }
        if (!linesBefore.isEmpty()) {
            LineRules.Line last = linesBefore.getLast();
            context.append("바로 앞 대사: ").append(say(last)).append('\n');
        }
        boolean previousWasQuestion = LineRules.isAddressedQuestion(linesBefore);
        String prompt = WritePrompts.FACT_PANEL.formatted(page.pageNumber(), plan.pages().size(), page.role(), setting, beat,
                context.toString(), fact.fact(), quotes, limitation ? WritePrompts.LIMITATION_RULE : "");
        AiAsker.Answer<PanelAnswer> answer = aiAsker.ask(model, WritePrompts.PANEL_SYSTEM + plan.termGuide(), prompt, panelSchema(FACT_SPEAKERS, 2),
                PanelAnswer.class, value -> {
                    List<String> problems = new ArrayList<>(LineRules.problems(value.scene(), lines(value), seenLines, sceneBefore));
                    if (limitation) {
                        problems.addAll(LineRules.limitationProblems(lines(value)));
                    }
                    String text = value.scene() + " " + value.lines().stream().map(LineAnswer::text).collect(Collectors.joining(" "));
                    problems.addAll(unknownTermProblems("사실 컷", text, paperWords));
                    problems.addAll(TextRules.termVariantProblems("사실 컷", text, plan.terms()));
                    problems.addAll(LineRules.repeatedQuestionProblems(lines(value), previousWasQuestion));
                    problems.addAll(LineRules.previousLineProblems(spoken(lines(value)), linesBefore));
                    return problems;
                }, PANEL_RETRIES);
        answer.attempts().forEach(attempt -> servers.add(attempt.server()));

        List<LineRules.Line> lines = new ArrayList<>(LineRules.tidy(lines(answer.value())));
        lines.add(new LineRules.Line("해설", fact.fact()));  // 해설은 검증된 사실 그대로
        lines.forEach(line -> seenLines.add(line.text()));
        return new WrittenComic.Panel(LineRules.tidyScene(answer.value().scene()), false, fact.fact(), lines, fact.evidence(),
                answer.problems());
    }

    private List<WrittenComic.Panel> writeStagingPanels(String model, ComicPlan plan, ComicPlan.PagePlan page, String setting,
                                                        String beat, String ending, String previousScene, String around,
                                                        WrittenComic.Panel firstFact, String lastFactScene, Set<String> seenLines,
                                                        Set<String> paperWords, Set<String> servers) {
        boolean firstPage = page.pageNumber() == 1;
        boolean lastPage = page.pageNumber() == plan.pages().size();
        String prompt = WritePrompts.STAGING.formatted(page.pageNumber(), plan.pages().size(), page.role(), setting, beat,
                firstPage ? "첫 쪽이므로 opening에서 이야기 배경의 사건을 처음 보여 준다." : "",
                around,
                lastPage ? "이야기를 끝맺는다: " + ending : "이번 쪽 내용에 대한 %s의 감탄이나 다음 쪽에 대한 궁금증으로 마무리한다.".formatted(Speaker.NURI.getLabel()));
        Map<String, Object> panel = panelSchema(STAGING_SPEAKERS, 3);
        AiAsker.Answer<StagingAnswer> answer = aiAsker.ask(model, WritePrompts.PANEL_SYSTEM + plan.termGuide(), prompt,
                JsonSchemas.object("opening", panel, "closing", panel), StagingAnswer.class,
                value -> {
                    List<String> problems = new ArrayList<>(
                            LineRules.problems(value.opening().scene(), lines(value.opening()), seenLines, previousScene));
                    if (firstFact != null) {
                        problems.addAll(LineRules.nextPanelProblems(value.opening().scene(), spoken(lines(value.opening())),
                                firstFact.scene(), spoken(firstFact.lines())));
                    }
                    problems.addAll(LineRules.problems(value.closing().scene(), lines(value.closing()), seenLines, lastFactScene));
                    String text = Stream.of(value.opening(), value.closing())
                            .flatMap(answerPanel -> Stream.concat(Stream.of(answerPanel.scene()), answerPanel.lines().stream().map(LineAnswer::text)))
                            .collect(Collectors.joining(" "));
                    problems.addAll(unknownTermProblems("연출 컷", text, paperWords));
                    problems.addAll(TextRules.termVariantProblems("연출 컷", text, plan.terms()));
                    return problems;
                }, PANEL_RETRIES);
        answer.attempts().forEach(attempt -> servers.add(attempt.server()));

        WrittenComic.Panel opening = new WrittenComic.Panel(LineRules.tidyScene(answer.value().opening().scene()), true, null,
                LineRules.tidy(lines(answer.value().opening())), List.of(), answer.problems());
        WrittenComic.Panel closing = new WrittenComic.Panel(LineRules.tidyScene(answer.value().closing().scene()), true, null,
                LineRules.tidy(lines(answer.value().closing())), List.of(), List.of());
        return List.of(opening, closing);
    }

    // ---------- 도우미 ----------

    private static String storyPrompt(ComicPlan plan) {
        String pages = plan.pages().stream()
                .map(page -> "%d쪽 (%s: %s) — %s".formatted(page.pageNumber(), page.role(), page.roleMeaning(),
                        page.chunks().stream().map(ComicPlan.ChunkPlan::title).collect(Collectors.joining(", "))))
                .collect(Collectors.joining("\n"));
        String roleKeys = plan.pages().stream().map(page -> "'" + page.role() + "'").collect(Collectors.joining(", "));
        return WritePrompts.STORY.formatted(plan.pages().size(), roleKeys, plan.comicTitle(), pages, WritePrompts.TERM_RULE);
    }

    /** 쪽별 흐름을 순서 있는 목록 대신 '기', '승', '전', '결' 이름 칸으로 받는다 (7차 시험에서 한 칸씩 밀려서). */
    private static Map<String, Object> storySchema(List<String> roles) {
        List<Object> fields = new ArrayList<>(List.of("setting", JsonSchemas.string()));
        roles.forEach(role -> fields.addAll(List.of(role, JsonSchemas.string())));
        fields.addAll(List.of("ending", JsonSchemas.string()));
        return JsonSchemas.object(fields.toArray());
    }

    private static Map<String, Object> panelSchema(List<String> speakers, int maxLines) {
        return JsonSchemas.object(
                "scene", JsonSchemas.string(),
                "lines", JsonSchemas.array(JsonSchemas.object("speaker", JsonSchemas.stringEnum(speakers), "text", JsonSchemas.string()),
                        1, maxLines));
    }

    private static List<LineRules.Line> lines(PanelAnswer answer) {
        return answer.lines().stream().map(line -> new LineRules.Line(line.speaker(), line.text())).toList();
    }

    /**
     * 이야기 흐름 정리: 따옴표·서식 기호를 지우고, 앞의 이름표("1쪽:", "**문제 제기**:")를 떼고 한 줄로.
     * 인용 표시 '>'와 수식 표시 '$'도 지운다 (177·178번 설정이 '>누리는 …'으로 시작, 흐름에 '$f$', 당시 주인공 이름은 누리).
     */
    static String beat(Object value) {
        if (value == null) {
            return "";
        }
        String text = oneLine(String.valueOf(value).replaceAll("[*#`{}\"'“”‘’<>$]", ""));
        return oneLine(BEAT_LABEL.matcher(text).replaceFirst(""));
    }

    /** 이야기 뼈대 검사: 섞인 단어, 서식 기호·'1쪽:' 이름표, 논문에 없는 영어 용어, 정한 번역과 다른 용어, 한국어만 든 괄호 */
    private static List<String> storyProblems(Map<String, Object> story, Set<String> paperWords, List<ComicPlan.Term> terms) {
        List<String> values = story.values().stream().map(String::valueOf).toList();
        List<String> problems = new ArrayList<>(mixedScriptProblems("이야기 뼈대", String.join(" ", values)));
        problems.addAll(unknownTermProblems("이야기 뼈대", String.join(" ", values), paperWords));
        problems.addAll(TextRules.termVariantProblems("이야기 뼈대", String.join(" ", values), terms));
        problems.addAll(TextRules.koreanParenthesisProblems("이야기 뼈대", String.join(" ", values)));
        if (values.stream().anyMatch(value -> STORY_MARKUP.matcher(value).find())) {
            problems.add("이야기 뼈대에 서식 기호(*, #, {)나 '1쪽:' 같은 이름표가 있다. 기호와 이름표 없이 문장만 쓴다.");
        }
        return problems;
    }

    private static List<String> unknownTermProblems(String label, String text, Set<String> paperWords) {
        List<String> unknown = TextRules.unknownEnglishWords(text, paperWords);
        return unknown.isEmpty() ? List.of() : List.of("%s에 논문에 없는 영어 용어가 있다: %s. 기술 이름은 다룰 내용에 나온 것만 쓰고 지어내지 않는다."
                .formatted(label, String.join(", ", unknown)));
    }

    private static String oneLine(String text) {
        return text == null ? "" : String.join(" ", text.trim().split("\\s+"));
    }

    private static String shorten(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static List<String> mixedScriptProblems(String label, String text) {
        String words = TextRules.mixedScriptWords(text);
        return words.isEmpty() ? List.of() : List.of("%s에 한글과 영어가 한 단어 안에 섞여 있다: %s. %s".formatted(label, words, WritePrompts.TITLE_RULE));
    }
}
