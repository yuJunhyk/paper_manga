package com.readmanga.backend.comic.plan;

import com.readmanga.backend.ai.OllamaProperties;
import com.readmanga.backend.comic.ProgressListener;
import com.readmanga.backend.paper.PaperSentence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 논문 문장으로 만화 설계도를 만든다.
 * ① 본문만 남기고(부록 제외) ② 쪽당 3조각으로 고르게 나누고(코드) ③ 조각마다 제목·요약을 붙이고(AI)
 * ④ 조각마다 사실과 근거 번호를 뽑아(AI) 코드가 검사·거른다.
 */
@Service
public class ComicPlanner {

    private static final Logger log = LoggerFactory.getLogger(ComicPlanner.class);

    static final int FOUR_PAGE_MIN_SENTENCES = 120;
    static final int CHUNKS_PER_PAGE = 3;
    static final int FACTS_PER_CHUNK = 2;
    static final int CLOSING_SECTION_FACTS = 3;          // 한계·결론 조각은 하나 더
    static final int MAX_RETRIES = 1;
    static final double SIMILAR_FACT = 0.55;             // 어떤 사실과든 이만큼 비슷하면 버린다
    static final double SIMILAR_FACT_SAME_EVIDENCE = 0.35;  // 근거 문장이 겹치는 사실과 이만큼 비슷하면 버린다

    private final AiAsker aiAsker;
    private final OllamaProperties properties;

    public ComicPlanner(AiAsker aiAsker, OllamaProperties properties) {
        this.aiAsker = aiAsker;
        this.properties = properties;
    }

    /** AI 응답 모양: 만화 제목 */
    record ComicTitleAnswer(String comicTitle) {
    }

    /** AI 응답 모양: 조각 하나의 제목 (조각마다 따로 묻는다) */
    record TitleItem(String title, String summary) {
    }

    /** AI 응답 모양: 사실 */
    record FactsAnswer(List<FactItem> facts) {
    }

    record FactItem(String fact, List<Integer> evidence) {
    }

    public ComicPlan plan(String paperTitle, List<PaperSentence> sentences) {
        return plan(paperTitle, sentences, ProgressListener.NONE);
    }

    /** listener에는 AI 요청이 하나 끝날 때마다 (끝난 수, 전체 수)를 알린다. 전체 = 만화 제목 1 + 조각 제목 n + 사실 n */
    public ComicPlan plan(String paperTitle, List<PaperSentence> sentences, ProgressListener listener) {
        List<ChunkSplitter.Section> sections = ChunkSplitter.bodySections(sentences);
        int bodySentences = sections.stream().mapToInt(ChunkSplitter.Section::size).sum();
        List<PageRole> roles = PageRole.forPages(bodySentences >= FOUR_PAGE_MIN_SENTENCES ? 4 : 3);
        List<Chunk> chunks = ChunkSplitter.split(sections, roles.size() * CHUNKS_PER_PAGE);
        Map<Integer, PaperSentence> byNumber = new HashMap<>();
        sentences.forEach(sentence -> byNumber.put(sentence.getNumber(), sentence));
        log.info("만화 설계 시작: 본문 문장 {}개, {}쪽, 조각 {}개", bodySentences, roles.size(), chunks.size());

        Set<String> servers = new LinkedHashSet<>();
        int totalSteps = 1 + chunks.size() * 2;
        int doneSteps = 0;
        listener.update(0, totalSteps);  // 시작하자마자 0/전체를 보여 준다 (0/0이면 멈춤과 대기를 구분할 수 없다)

        // ③ 만화 제목과 조각 제목 (조각 제목은 조각마다 따로 묻는다. 한 번에 쓰게 했더니 한 칸씩 밀렸다)
        long started = System.currentTimeMillis();
        List<String> titleProblems = new ArrayList<>();
        AiAsker.Answer<ComicTitleAnswer> comicTitle = aiAsker.ask(properties.factModel(), PlanPrompts.OUTLINE_SYSTEM,
                PlanPrompts.COMIC_TITLE.formatted(paperTitle, abstractText(sections, byNumber)),
                JsonSchemas.object("comicTitle", JsonSchemas.string()), ComicTitleAnswer.class,
                answer -> mixedScriptProblems("만화 제목", answer.comicTitle()), MAX_RETRIES);
        comicTitle.attempts().forEach(attempt -> servers.add(attempt.server()));
        titleProblems.addAll(comicTitle.problems());
        listener.update(++doneSteps, totalSteps);

        List<TitleItem> chunkTitles = new ArrayList<>();
        for (Chunk chunk : chunks) {
            AiAsker.Answer<TitleItem> title = aiAsker.ask(properties.factModel(), PlanPrompts.OUTLINE_SYSTEM,
                    chunkTitlePrompt(paperTitle, chunk, byNumber),
                    JsonSchemas.object("title", JsonSchemas.string(), "summary", JsonSchemas.string()), TitleItem.class,
                    answer -> mixedScriptProblems("조각 제목", answer.title() + " " + answer.summary()), MAX_RETRIES);
            title.attempts().forEach(attempt -> servers.add(attempt.server()));
            titleProblems.addAll(title.problems());
            chunkTitles.add(title.value());
            listener.update(++doneSteps, totalSteps);
        }
        long titleMillis = System.currentTimeMillis() - started;

        // ④ 핵심 사실 추출
        started = System.currentTimeMillis();
        Map<Integer, List<String>> usedEvidence = new HashMap<>();
        List<String> acceptedFacts = new ArrayList<>();
        List<ComicPlan.PagePlan> pages = new ArrayList<>();
        int dropped = 0;
        for (int page = 0; page < roles.size(); page++) {
            List<ComicPlan.ChunkPlan> chunkPlans = new ArrayList<>();
            List<ComicPlan.FactPlan> facts = new ArrayList<>();
            for (int index = page * CHUNKS_PER_PAGE; index < Math.min(chunks.size(), (page + 1) * CHUNKS_PER_PAGE); index++) {
                Chunk chunk = chunks.get(index);
                TitleItem title = chunkTitles.get(index);
                boolean closing = chunk.sections().stream().anyMatch(TextRules::isClosingSection);
                int count = closing ? CLOSING_SECTION_FACTS : FACTS_PER_CHUNK;

                AiAsker.Answer<FactsAnswer> answer = pickFacts(chunk, title, count, closing, byNumber);
                answer.attempts().forEach(attempt -> servers.add(attempt.server()));
                listener.update(++doneSteps, totalSteps);
                List<String> droppedHere = new ArrayList<>();
                for (FactItem item : answer.value().facts()) {
                    ComicPlan.FactPlan fact = filterFact(item, chunk, title, byNumber, usedEvidence, acceptedFacts);
                    if (fact == null) {
                        droppedHere.add(item.fact());
                    } else {
                        facts.add(fact);
                    }
                }
                dropped += droppedHere.size();
                chunkPlans.add(new ComicPlan.ChunkPlan(chunk.start(), chunk.end(), chunk.sections(), title.title(),
                        title.summary(), droppedHere, answer.problems()));
            }
            PageRole role = roles.get(page);
            pages.add(new ComicPlan.PagePlan(page + 1, role.name(), role.meaning(), chunkPlans, facts));
        }
        long factMillis = System.currentTimeMillis() - started;
        log.info("만화 설계 끝: 조각 제목 {}ms, 핵심 사실 추출 {}ms, 버린 사실 {}개, 사용한 AI 서버 {}", titleMillis, factMillis, dropped, servers);

        return new ComicPlan(comicTitle.value().comicTitle(), pages,
                new ComicPlan.Stats(titleMillis, factMillis, List.copyOf(servers), dropped, titleProblems));
    }

    // ---------- ③ 조각 제목 ----------

    private static String abstractText(List<ChunkSplitter.Section> sections, Map<Integer, PaperSentence> byNumber) {
        ChunkSplitter.Section first = sections.getFirst();
        StringBuilder text = new StringBuilder();
        for (int number = first.start(); number <= first.end(); number++) {
            text.append(byNumber.get(number).getText()).append(' ');
        }
        return text.toString().trim();
    }

    /** 조각 하나만 보여 준다: 섹션 이름과 앞부분 문장 3개(표 조각 제외). */
    private static String chunkTitlePrompt(String paperTitle, Chunk chunk, Map<Integer, PaperSentence> byNumber) {
        List<String> leading = new ArrayList<>();
        for (int number = chunk.start(); number <= chunk.end() && leading.size() < 3; number++) {
            String text = byNumber.get(number).getText();
            if (!TextRules.isTableFragment(text)) {
                leading.add("- " + (text.length() > 200 ? text.substring(0, 200) : text));
            }
        }
        return PlanPrompts.CHUNK_TITLE.formatted(paperTitle, String.join(", ", chunk.sections()),
                chunk.start() + "~" + chunk.end(), String.join("\n", leading));
    }

    // ---------- ④ 핵심 사실 추출 ----------

    private AiAsker.Answer<FactsAnswer> pickFacts(Chunk chunk, TitleItem title, int count, boolean closing,
                                                  Map<Integer, PaperSentence> byNumber) {
        List<Integer> candidates = new ArrayList<>();
        StringBuilder lines = new StringBuilder();
        for (int number = chunk.start(); number <= chunk.end(); number++) {
            PaperSentence sentence = byNumber.get(number);
            if (TextRules.isTableFragment(sentence.getText())) {
                continue;  // 표 조각 문장은 AI에게 보여 주지 않는다
            }
            candidates.add(number);
            lines.append("[%d] (p.%s) %s%n".formatted(number, sentence.getPage(), sentence.getText()));
        }
        Set<Integer> allowed = Set.copyOf(candidates);
        Map<String, Object> schema = JsonSchemas.object("facts", JsonSchemas.array(
                JsonSchemas.object("fact", JsonSchemas.string(), "evidence", JsonSchemas.array(JsonSchemas.integer(), 1, 3)),
                count, count));
        Function<FactsAnswer, List<String>> check = answer -> checkFacts(answer, allowed);
        return aiAsker.ask(properties.factModel(), PlanPrompts.FACT_SYSTEM,
                PlanPrompts.FACTS.formatted(title.title(), title.summary(), String.join(", ", chunk.sections()), count,
                        closing ? "\n" + PlanPrompts.LIMITATION_RULE : "", lines.toString().trim()),
                schema, FactsAnswer.class, check, MAX_RETRIES);
    }

    private static List<String> checkFacts(FactsAnswer answer, Set<Integer> allowed) {
        List<String> problems = new ArrayList<>();
        Map<Integer, Integer> seen = new HashMap<>();
        for (int index = 0; index < answer.facts().size(); index++) {
            FactItem fact = answer.facts().get(index);
            int factNumber = index + 1;
            List<Integer> valid = fact.evidence().stream().filter(allowed::contains).toList();
            if (valid.isEmpty()) {
                problems.add("%d번 사실의 근거 번호 %s가 모두 목록에 없는 번호다.".formatted(factNumber, fact.evidence()));
            }
            for (int number : valid) {
                Integer previous = seen.putIfAbsent(number, factNumber);
                if (previous != null && previous != factNumber) {
                    problems.add("근거 번호 %d을 %d번 사실과 %d번 사실에서 함께 썼다.".formatted(number, previous, factNumber));
                }
            }
            addMixedScriptProblem(problems, factNumber + "번 사실", fact.fact());
            if (TextRules.hasFormula(fact.fact())) {
                problems.add(factNumber + "번 사실에 수식 기호가 있다. 수식 대신 그 뜻을 쉬운 말로 써라.");
            }
            if (TextRules.hasCitation(fact.fact())) {
                problems.add("%d번 사실에 인용 번호가 들어 있다: \"%s\". 번호를 빼고 가리키는 대상을 말로 써라(예: '[88]의 모델' → '기존 인페인팅 모델')."
                        .formatted(factNumber, fact.fact()));
            }
        }
        return problems;
    }

    /**
     * 사실 하나를 거른다. 버려야 하면 null.
     * 근거 번호가 틀렸거나, 섞인 단어·수식이 남았거나, 앞 사실의 반복이면 버린다.
     * 근거 문장이 겹쳐도 내용이 다르면 남긴다 (한 문장에서 '계산이 적다'와 'GAN보다 느리다'가 함께 나올 수 있다).
     */
    private static ComicPlan.FactPlan filterFact(FactItem item, Chunk chunk, TitleItem title, Map<Integer, PaperSentence> byNumber,
                                                 Map<Integer, List<String>> usedEvidence, List<String> acceptedFacts) {
        String text = TextRules.removeCitations(item.fact());
        List<Integer> evidence = item.evidence().stream().distinct()
                .filter(number -> number >= chunk.start() && number <= chunk.end())
                .filter(number -> !TextRules.isTableFragment(byNumber.get(number).getText()))
                .toList();
        if (evidence.isEmpty() || TextRules.hasMixedScript(text) || TextRules.hasFormula(text)) {
            return null;
        }
        boolean repeatsSameEvidence = evidence.stream()
                .flatMap(number -> usedEvidence.getOrDefault(number, List.of()).stream())
                .anyMatch(other -> TextRules.similarity(text, other) >= SIMILAR_FACT_SAME_EVIDENCE);
        boolean repeatsAnyFact = acceptedFacts.stream().anyMatch(other -> TextRules.similarity(text, other) >= SIMILAR_FACT);
        if (repeatsSameEvidence || repeatsAnyFact) {
            return null;
        }
        evidence.forEach(number -> usedEvidence.computeIfAbsent(number, key -> new ArrayList<>()).add(text));
        acceptedFacts.add(text);
        return new ComicPlan.FactPlan(text, evidence, title.title());
    }

    private static List<String> mixedScriptProblems(String label, String text) {
        List<String> problems = new ArrayList<>();
        addMixedScriptProblem(problems, label, text);
        return problems;
    }

    private static void addMixedScriptProblem(List<String> problems, String label, String text) {
        String words = TextRules.mixedScriptWords(text == null ? "" : text);
        if (!words.isEmpty()) {
            problems.add("%s에 한글과 영어가 한 단어 안에 섞여 있다: %s. %s".formatted(label, words, PlanPrompts.TITLE_RULE));
        }
    }
}
