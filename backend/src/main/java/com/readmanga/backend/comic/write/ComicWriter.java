package com.readmanga.backend.comic.write;

import com.readmanga.backend.ai.OllamaProperties;
import com.readmanga.backend.comic.ProgressListener;
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

/**
 * 만화 설계도로 대본을 쓴다 (AI: exaone).
 * ① 이야기 뼈대: 배경 사건, 쪽별 흐름(기·승·전·결 이름 칸), 결말
 * ② 쪽마다 사실 컷을 하나씩 따로 쓴다. 요청마다 사실이 하나뿐이라 다른 사실과 섞일 수 없다(5차 시험의 한 칸 밀림 방지).
 *    사실 컷의 해설은 AI가 아니라 코드가 검증된 사실 문장으로 넣는다(6차 시험의 덧붙임 방지).
 * ③ 쪽마다 여는 연출 컷과 닫는 연출 컷을 쓴다.
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

    private static final List<String> FACT_SPEAKERS = List.of("누리", "박사님");          // 해설은 코드가 넣는다
    private static final List<String> STAGING_SPEAKERS = List.of("누리", "박사님", "해설");

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

    @SuppressWarnings({"unchecked", "rawtypes"})
    public WrittenComic write(ComicPlan plan, Map<Integer, PaperSentence> sentencesByNumber, ProgressListener listener) {
        long started = System.currentTimeMillis();
        Set<String> servers = new LinkedHashSet<>();
        int totalSteps = 1 + plan.pages().stream().mapToInt(page -> page.facts().size() + 1).sum();
        int doneSteps = 0;
        String model = properties.dialogueModel();

        // ① 이야기 뼈대
        List<String> roles = plan.pages().stream().map(ComicPlan.PagePlan::role).toList();
        AiAsker.Answer<Map> story = aiAsker.ask(model, WritePrompts.STORY_SYSTEM, storyPrompt(plan), storySchema(roles),
                Map.class, answer -> storyProblems((Map<String, Object>) answer), STORY_RETRIES);
        story.attempts().forEach(attempt -> servers.add(attempt.server()));
        listener.update(++doneSteps, totalSteps);
        Map<String, Object> storyValue = story.value();
        String setting = beat(storyValue.get("setting"));
        String ending = beat(storyValue.get("ending"));
        log.info("이야기 뼈대: {}", setting);

        // ②③ 쪽마다 사실 컷, 연출 컷
        List<WrittenComic.Page> pages = new ArrayList<>();
        String previousScene = null;  // 앞 컷의 장면만 넘긴다 (대사를 넘기면 앞 컷 내용을 끌고 와서)
        for (ComicPlan.PagePlan page : plan.pages()) {
            String beat = beat(storyValue.get(page.role()));
            Set<String> seenLines = new HashSet<>();
            List<WrittenComic.Panel> factPanels = new ArrayList<>();
            for (ComicPlan.FactPlan fact : page.facts()) {
                String sceneBefore = factPanels.isEmpty() ? previousScene : factPanels.getLast().scene();
                WrittenComic.Panel panel = writeFactPanel(model, plan, page, setting, beat, sceneBefore, fact, sentencesByNumber,
                        seenLines, servers);
                factPanels.add(panel);
                listener.update(++doneSteps, totalSteps);
            }

            String lastFactScene = factPanels.isEmpty() ? previousScene : factPanels.getLast().scene();
            List<WrittenComic.Panel> staging = writeStagingPanels(model, plan, page, setting, beat, ending, previousScene,
                    lastFactScene, seenLines, servers);
            listener.update(++doneSteps, totalSteps);

            List<WrittenComic.Panel> panels = new ArrayList<>();
            panels.add(staging.getFirst());
            panels.addAll(factPanels);
            panels.add(staging.getLast());
            pages.add(new WrittenComic.Page(page.pageNumber(), page.role(), beat, panels));
            previousScene = staging.getLast().scene();
            log.info("{}쪽({}) 대본 완료: 컷 {}개", page.pageNumber(), page.role(), panels.size());
        }

        return new WrittenComic(plan.comicTitle(), setting, ending, pages, System.currentTimeMillis() - started, List.copyOf(servers));
    }

    private WrittenComic.Panel writeFactPanel(String model, ComicPlan plan, ComicPlan.PagePlan page, String setting, String beat,
                                              String sceneBefore, ComicPlan.FactPlan fact,
                                              Map<Integer, PaperSentence> sentencesByNumber, Set<String> seenLines,
                                              Set<String> servers) {
        String quotes = fact.evidence().stream()
                .map(number -> "[%d] %s".formatted(number, shorten(sentencesByNumber.get(number).getText(), 200)))
                .collect(Collectors.joining(" / "));
        boolean limitation = fact.evidence().stream()
                .anyMatch(number -> TextRules.isLimitationSection(sentencesByNumber.get(number).getSectionTitle()));
        String prompt = WritePrompts.FACT_PANEL.formatted(page.pageNumber(), plan.pages().size(), page.role(), setting, beat,
                sceneBefore == null ? "" : "바로 앞 컷의 장면: " + sceneBefore + "\n", fact.fact(), quotes,
                limitation ? WritePrompts.LIMITATION_RULE : "");
        AiAsker.Answer<PanelAnswer> answer = aiAsker.ask(model, WritePrompts.PANEL_SYSTEM, prompt, panelSchema(FACT_SPEAKERS, 2),
                PanelAnswer.class, value -> {
                    List<String> problems = new ArrayList<>(LineRules.problems(value.scene(), lines(value), seenLines, sceneBefore));
                    if (limitation) {
                        problems.addAll(LineRules.limitationProblems(lines(value)));
                    }
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
                                                        String beat, String ending, String previousScene, String lastFactScene,
                                                        Set<String> seenLines, Set<String> servers) {
        boolean firstPage = page.pageNumber() == 1;
        boolean lastPage = page.pageNumber() == plan.pages().size();
        String prompt = WritePrompts.STAGING.formatted(page.pageNumber(), plan.pages().size(), page.role(), setting, beat,
                firstPage ? "첫 쪽이므로 opening에서 이야기 배경의 사건을 처음 보여 준다." : "",
                previousScene == null ? "" : "앞 쪽의 마지막 장면: " + previousScene + "\n",
                lastPage ? "이야기를 끝맺는다: " + ending : "이번 쪽 내용에 대한 누리의 감탄이나 다음 쪽에 대한 궁금증으로 마무리한다.");
        Map<String, Object> panel = panelSchema(STAGING_SPEAKERS, 3);
        AiAsker.Answer<StagingAnswer> answer = aiAsker.ask(model, WritePrompts.PANEL_SYSTEM, prompt,
                JsonSchemas.object("opening", panel, "closing", panel), StagingAnswer.class,
                value -> {
                    List<String> problems = new ArrayList<>(
                            LineRules.problems(value.opening().scene(), lines(value.opening()), seenLines, previousScene));
                    problems.addAll(LineRules.problems(value.closing().scene(), lines(value.closing()), seenLines, lastFactScene));
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
        return WritePrompts.STORY.formatted(plan.pages().size(), roleKeys, plan.comicTitle(), pages);
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

    /** 이야기 흐름 정리: 따옴표·서식 기호를 지우고, 앞의 이름표("1쪽:", "**문제 제기**:")를 떼고 한 줄로. */
    private static String beat(Object value) {
        if (value == null) {
            return "";
        }
        String text = oneLine(String.valueOf(value).replaceAll("[*#`{}\"'“”‘’]", ""));
        return oneLine(BEAT_LABEL.matcher(text).replaceFirst(""));
    }

    /** 이야기 뼈대 검사: 섞인 단어, 서식 기호·'1쪽:' 이름표 */
    private static List<String> storyProblems(Map<String, Object> story) {
        List<String> values = story.values().stream().map(String::valueOf).toList();
        List<String> problems = new ArrayList<>(mixedScriptProblems("이야기 뼈대", String.join(" ", values)));
        if (values.stream().anyMatch(value -> STORY_MARKUP.matcher(value).find())) {
            problems.add("이야기 뼈대에 서식 기호(*, #, {)나 '1쪽:' 같은 이름표가 있다. 기호와 이름표 없이 문장만 쓴다.");
        }
        return problems;
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
