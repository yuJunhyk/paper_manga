package com.readmanga.backend.comic.plan;

import com.readmanga.backend.ai.OllamaProperties;
import com.readmanga.backend.comic.ProgressListener;
import com.readmanga.backend.paper.PaperSentence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Function;

/**
 * 논문 문장으로 만화 설계도를 만든다.
 * ① 본문만 남기고(부록 제외) ② 쪽당 3조각으로 고르게 나누고(코드) ③ 초록·결론·한계로 논문 요약(문제·방법·결과·한계)을 쓰고(AI)
 * ④ 조각마다 제목·요약을 붙이고(AI) ⑤ 조각마다 사실과 근거 번호를 개수 제한 없이 뽑아(AI) 코드가 검사·거른다
 * ⑥ 사실을 요약 문장에 연결하고(AI) ⑦ 요약 문장마다 사실을 몇 개씩 골라 요약 칸으로 쪽을 정한다(코드).
 * 예전에는 조각마다 사실을 2개씩 정해 두고 위치로 쪽을 정해서, 중요도와 상관없이 같은 말이 되풀이됐다
 * (195번 1쪽: '계산 비용이 크다'가 세 번).
 */
@Service
public class ComicPlanner {

    private static final Logger log = LoggerFactory.getLogger(ComicPlanner.class);

    static final int FOUR_PAGE_MIN_SENTENCES = 120;
    static final int CHUNKS_PER_PAGE = 3;
    static final int MAX_FACTS_PER_CHUNK = 10;           // 개수는 정하지 않고, 답 길이 상한(1500토큰)에 들어가는 만큼만 받는다
    static final int MAX_POINTS_PER_SLOT = 3;            // 요약 칸마다 요약 문장 수
    static final int MIN_POINTS_PER_SLOT = 2;            // 문제·방법·결과 칸은 2개 이상 (196번: 문제 칸 1개라 1쪽이 사실 2개, 4컷)
    static final int FACTS_PER_POINT = 3;                // 요약 문장마다 만화에 쓸 사실 수 (연출 컷을 줄인 자리를 사실 컷으로 채운다)
    static final int MAX_FACTS_PER_PAGE = 6;             // 쪽당 6~7컷 = 사실 컷 + 첫 쪽 여는 컷·끝 쪽 닫는 컷
    static final int MAX_SUMMARY_SENTENCES = 80;         // 요약에 보여 줄 초록·결론·한계 문장 수 (한 번에 읽을 수 있는 양)
    /** 요약 칸: 문제, 방법, 결과, 한계·남은 과제 */
    static final List<String> SLOTS = List.of("problem", "method", "result", "limitation");
    private static final Map<String, String> SLOT_NAMES = Map.of("problem", "문제", "method", "방법", "result", "결과", "limitation", "한계");
    static final int MIN_SECTION_SENTENCES = 3;          // 이보다 짧은 섹션은 '다른 섹션에서도 고르기' 검사에서 뺀다
    static final int RESULT_SCORE = 2;                   // 이 점수 이상이면 결과 문장 (TextRules.resultScore)
    static final int MAX_TERMS = 8;                      // 처음에 번역을 정해 둘 핵심 용어 수
    static final int MAX_RETRIES = 1;
    static final double SIMILAR_FACT = 0.55;             // 어떤 사실과든 이만큼 비슷하면 버린다
    static final double SIMILAR_FACT_SAME_EVIDENCE = 0.35;  // 근거 문장이 겹치는 사실과 이만큼 비슷하면 버린다
    static final double SIMILAR_RESULT = 0.2;            // 같은 결과 숫자를 말하는 사실과 이만큼 비슷하면 같은 결과로 본다
    /** 이 연구가 주어인 말: "이 연구는", "본 논문은", "제안된 방법은", "우리는" */
    private static final java.util.regex.Pattern THIS_STUDY = java.util.regex.Pattern.compile("(이|본)\\s*(연구|논문)|제안(된|한)|우리(는|가)");
    /** 섹션 이름에 흔한 낱말 (실험 이름이 아니다) */
    private static final Set<String> COMMON_SECTION_WORDS = Set.of("with", "and", "for", "the", "from", "via", "using", "beyond",
            "toward", "into", "result", "experiment", "evaluation", "analysi", "comparison", "study", "detail", "method", "approach");
    /**
     * 다른 연구가 주어인 사실: "한 연구는", "앞선 연구들은", "기존 방법은"(문장 첫머리 또는 쉼표 뒤),
     * "몇 가지 2단계 접근 방식은", "연구들은"(200번 2쪽: '… 몇 가지 2단계 접근 방식은 … ARM을 사용한다')
     */
    private static final java.util.regex.Pattern OTHER_STUDY = java.util.regex.Pattern.compile(
            "(^|[,，]\\s*)(한|앞선|다른|기존|이전)\\s*(연구|방법|모델)(들)?(은|는|이|가)"
                    + "|(몇몇|몇 가지|일부|여러)\\s*([가-힣0-9]+\\s+){0,2}(연구|방법|모델|접근 방식|접근법)(들)?(은|는|이|가)\\s"
                    + "|(^|[,，]\\s*)(연구|접근 방식|접근법)들(은|이)\\s");

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

    record VerifyAnswer(List<VerifyItem> results) {
    }

    record VerifyItem(int fact, String verdict, String reason) {
    }

    record FactItem(String fact, List<Integer> evidence) {
    }

    record TermsAnswer(List<TermItem> terms) {
    }

    record TermItem(String english, String korean, List<String> variants) {
    }

    /** AI 응답 모양: 논문 요약 (칸마다 요약 문장과 근거 번호) */
    record SummaryAnswer(List<PointItem> problem, List<PointItem> method, List<PointItem> result, List<PointItem> limitation) {

        List<PointItem> slot(String name) {
            List<PointItem> items = switch (name) {
                case "problem" -> problem;
                case "method" -> method;
                case "result" -> result;
                default -> limitation;
            };
            return items == null ? List.of() : items;
        }
    }

    record PointItem(String point, List<Integer> evidence) {
    }

    /** AI 응답 모양: 사실마다 연결한 요약 문장 번호 (0은 연결 없음) */
    record LinksAnswer(List<LinkItem> links) {
    }

    record LinkItem(int fact, int point) {
    }

    /** 요약 문장에 연결한 사실 */
    record Linked(ComicPlan.FactPlan fact, int point) {
    }

    public ComicPlan plan(String paperTitle, List<PaperSentence> sentences) {
        return plan(paperTitle, sentences, ProgressListener.NONE);
    }

    /** listener에는 AI 요청이 하나 끝날 때마다 (끝난 수, 전체 수)를 알린다. 전체 = 만화 제목 1 + 용어 1 + 요약 1 + 조각마다 (제목 + 사실 + 연결) */
    public ComicPlan plan(String paperTitle, List<PaperSentence> sentences, ProgressListener listener) {
        List<ChunkSplitter.Section> sections = ChunkSplitter.bodySections(sentences);
        int bodySentences = sections.stream().mapToInt(ChunkSplitter.Section::size).sum();
        List<PageRole> roles = PageRole.forPages(bodySentences >= FOUR_PAGE_MIN_SENTENCES ? 4 : 3);
        List<Chunk> chunks = ChunkSplitter.split(sections, roles.size() * CHUNKS_PER_PAGE);
        Map<Integer, PaperSentence> byNumber = new HashMap<>();
        sentences.forEach(sentence -> byNumber.put(sentence.getNumber(), sentence));
        // 논문에 나온 영어 낱말: 문장, 섹션 이름, 논문 제목
        List<String> paperTexts = new ArrayList<>(List.of(paperTitle == null ? "" : paperTitle));
        sentences.forEach(sentence -> paperTexts.addAll(List.of(sentence.getText(), String.valueOf(sentence.getSectionTitle()))));
        Set<String> paperWords = TextRules.englishWords(paperTexts);
        Set<String> titleWords = sectionKeywords(paperTitle == null ? "" : paperTitle, Set.of());
        log.info("만화 설계 시작: 본문 문장 {}개, {}쪽, 조각 {}개", bodySentences, roles.size(), chunks.size());

        Set<String> servers = new LinkedHashSet<>();
        int totalSteps = 3 + chunks.size() * 3;
        int doneSteps = 0;
        listener.update(0, totalSteps);  // 시작하자마자 0/전체를 보여 준다 (0/0이면 멈춤과 대기를 구분할 수 없다)

        // ③ 만화 제목, 용어, 논문 요약
        long started = System.currentTimeMillis();
        List<String> titleProblems = new ArrayList<>();
        AiAsker.Answer<ComicTitleAnswer> comicTitle = aiAsker.ask(properties.factModel(), PlanPrompts.OUTLINE_SYSTEM,
                PlanPrompts.COMIC_TITLE.formatted(paperTitle, abstractText(sections, byNumber)),
                JsonSchemas.object("comicTitle", JsonSchemas.string()), ComicTitleAnswer.class,
                answer -> mixedScriptProblems("만화 제목", answer.comicTitle()), MAX_RETRIES);
        comicTitle.attempts().forEach(attempt -> servers.add(attempt.server()));
        titleProblems.addAll(comicTitle.problems());
        listener.update(++doneSteps, totalSteps);

        List<ComicPlan.Term> terms = pickTerms(paperTitle, abstractText(sections, byNumber), paperWords, servers);
        listener.update(++doneSteps, totalSteps);

        List<ComicPlan.SummaryPoint> summary = summarize(paperTitle, sections, byNumber, paperWords, terms, servers);
        listener.update(++doneSteps, totalSteps);

        // ④ 조각 제목 (조각마다 따로 묻는다. 한 번에 쓰게 했더니 한 칸씩 밀렸다)
        List<TitleItem> chunkTitles = new ArrayList<>();
        for (Chunk chunk : chunks) {
            AiAsker.Answer<TitleItem> title = aiAsker.ask(properties.factModel(), PlanPrompts.OUTLINE_SYSTEM,
                    chunkTitlePrompt(paperTitle, chunk, byNumber),
                    JsonSchemas.object("title", JsonSchemas.string(), "summary", JsonSchemas.string()), TitleItem.class,
                    answer -> {
                        List<String> problems = mixedScriptProblems("조각 제목", answer.title() + " " + answer.summary());
                        problems.addAll(unknownTermProblems("조각 제목", answer.title() + " " + answer.summary(), paperWords));
                        return problems;
                    }, MAX_RETRIES);
            title.attempts().forEach(attempt -> servers.add(attempt.server()));
            titleProblems.addAll(title.problems());
            chunkTitles.add(title.value());
            listener.update(++doneSteps, totalSteps);
        }
        long titleMillis = System.currentTimeMillis() - started;
        log.info("조각 제목: {}", chunkTitles.stream().map(TitleItem::title).toList());

        // ⑤ 핵심 사실 추출(개수 제한 없음) ⑥ 요약 문장에 연결
        started = System.currentTimeMillis();
        int closingIndex = closingChunk(chunks, roles.size());
        Map<Integer, List<String>> usedEvidence = new HashMap<>();
        List<String> acceptedFacts = new ArrayList<>();
        List<ComicPlan.ChunkPlan> chunkPlans = new ArrayList<>();
        List<List<ComicPlan.FactPlan>> chunkFacts = new ArrayList<>();
        List<Linked> linked = new ArrayList<>();
        int dropped = 0;
        int unlinked = 0;
        List<ComicPlan.FactPlan> spare = new ArrayList<>();  // 어느 요약에도 붙지 않은, 결과 숫자가 있는 실험 사실
        for (int index = 0; index < chunks.size(); index++) {
            Chunk chunk = chunks.get(index);
            TitleItem title = chunkTitles.get(index);
            Map<String, String> unsupported = new HashMap<>();  // 사실 확인에서 근거가 없다고 나온 사실 → 이유
            AiAsker.Answer<FactsAnswer> answer = pickFacts(chunk, title, index == closingIndex, byNumber, paperWords, titleWords, terms,
                    unsupported);
            answer.attempts().forEach(attempt -> servers.add(attempt.server()));
            listener.update(++doneSteps, totalSteps);
            List<String> droppedHere = new ArrayList<>();
            List<ComicPlan.FactPlan> facts = new ArrayList<>();
            for (FactItem item : answer.value().facts().stream().limit(MAX_FACTS_PER_CHUNK).toList()) {
                ComicPlan.FactPlan fact = unsupported.containsKey(item.fact()) ? null
                        : filterFact(item, chunk, title, byNumber, usedEvidence, acceptedFacts, paperWords);
                if (unsupported.containsKey(item.fact())) {
                    log.info("근거 없는 사실을 버렸습니다: {} ({})", item.fact(), unsupported.get(item.fact()));
                }
                if (fact == null) {
                    droppedHere.add(item.fact());
                } else {
                    facts.add(fact);
                }
            }
            dropped += droppedHere.size();
            chunkPlans.add(new ComicPlan.ChunkPlan(chunk.start(), chunk.end(), chunk.sections(), title.title(),
                    title.summary(), droppedHere, answer.problems()));
            chunkFacts.add(facts);

            List<Integer> points = link(summary, chunk, facts, servers);
            listener.update(++doneSteps, totalSteps);
            for (int i = 0; i < facts.size(); i++) {
                String mismatch = points.get(i) == 0 ? null
                        : linkMismatch(summary.get(points.get(i) - 1).slot(), index, chunks.size(), facts.get(i).fact(),
                                isOtherStudy(facts.get(i), byNumber));
                if (mismatch != null) {
                    log.info("요약 칸과 맞지 않아 연결을 끊은 사실({}): {}", mismatch, facts.get(i).fact());
                    points.set(i, 0);
                }
                if (points.get(i) == 0 && isExperimentResult(facts.get(i), byNumber)) {
                    spare.add(facts.get(i));
                } else if (points.get(i) == 0) {
                    unlinked++;
                    log.info("요약과 관계가 약해 쓰지 않는 사실: {}", facts.get(i).fact());
                } else {
                    linked.add(new Linked(facts.get(i), points.get(i)));
                }
            }
        }

        linked.addAll(spareResults(summary, spare, linked));
        unlinked += spare.stream().filter(fact -> linked.stream().noneMatch(link -> link.fact() == fact)).count();

        // ⑦ 요약 문장마다 사실을 고르고 요약 칸으로 쪽을 정한다 (요약이 없으면 예전처럼 위치로)
        List<ComicPlan.PagePlan> pages = summary.isEmpty()
                ? pagesByPosition(roles, chunkPlans, chunkFacts)
                : pagesBySummary(roles, chunkPlans, summary, linked, byNumber);
        int extracted = chunkFacts.stream().mapToInt(List::size).sum();
        int beforeRepeats = pages.stream().mapToInt(page -> page.facts().size()).sum();
        pages = dropRepeatedResults(pages, byNumber);
        int used = pages.stream().mapToInt(page -> page.facts().size()).sum();
        dropped += beforeRepeats - used;
        long factMillis = System.currentTimeMillis() - started;
        log.info("만화 설계 끝: 뽑은 사실 {}개 중 요약에 연결 {}개(연결 없음 {}개), 만화에 쓴 사실 {}개, 쪽마다 {}, 버린 사실 {}개, "
                        + "조각 제목 {}ms, 핵심 사실 추출 {}ms, 사용한 AI 서버 {}",
                extracted, linked.size(), unlinked, used, pages.stream().map(page -> page.facts().size()).toList(), dropped,
                titleMillis, factMillis, servers);

        return new ComicPlan(comicTitle.value().comicTitle(), pages,
                new ComicPlan.Stats(titleMillis, factMillis, List.copyOf(servers), dropped, titleProblems), terms, summary);
    }

    // ---------- ③-3 논문 요약 ----------

    /**
     * 초록·결론·한계 문장으로 논문 요약(문제·방법·결과·한계 칸)을 쓴다. 요약 문장에도 근거 번호를 받아 검사하고 사실 확인을 한다.
     * 요약이 깨지면(답이 모두 잘림 등) 작업을 멈추지 않고 빈 요약을 돌려준다(그때는 예전처럼 위치로 쪽을 정한다).
     */
    private List<ComicPlan.SummaryPoint> summarize(String paperTitle, List<ChunkSplitter.Section> sections,
                                                   Map<Integer, PaperSentence> byNumber, Set<String> paperWords,
                                                   List<ComicPlan.Term> terms, Set<String> servers) {
        List<Integer> shown = summarySentences(sections, byNumber);
        boolean hasClosing = shown.stream().anyMatch(number -> TextRules.isClosingSection(byNumber.get(number).getSectionTitle()));
        StringBuilder lines = new StringBuilder();
        shown.forEach(number -> lines.append("[%d] (%s) %s%n".formatted(number, byNumber.get(number).getSectionTitle(),
                byNumber.get(number).getText())));
        Set<Integer> allowed = Set.copyOf(shown);
        Map<String, Object> point = JsonSchemas.object("point", JsonSchemas.string(), "evidence", JsonSchemas.array(JsonSchemas.integer(), 1, 3));
        Map<String, Object> schema = JsonSchemas.object(
                "problem", JsonSchemas.array(point, MIN_POINTS_PER_SLOT, MAX_POINTS_PER_SLOT),
                "method", JsonSchemas.array(point, MIN_POINTS_PER_SLOT, MAX_POINTS_PER_SLOT),
                "result", JsonSchemas.array(point, MIN_POINTS_PER_SLOT, MAX_POINTS_PER_SLOT),
                "limitation", JsonSchemas.array(point, hasClosing ? 1 : 0, MAX_POINTS_PER_SLOT));
        Map<String, String> unsupported = new HashMap<>();
        AiAsker.Answer<SummaryAnswer> answer;
        try {
            answer = aiAsker.ask(properties.factModel(), PlanPrompts.SUMMARY_SYSTEM,
                    PlanPrompts.SUMMARY.formatted(paperTitle,
                            hasClosing ? "결론·한계 부분 문장에서 찾아 1개 이상 쓴다." : "문장에 한계가 없으면 비워 둔다.",
                            ComicPlan.termGuide(terms), lines.toString().trim(), MAX_POINTS_PER_SLOT, MIN_POINTS_PER_SLOT),
                    schema, SummaryAnswer.class, value -> {
                        List<String> problems = new ArrayList<>(summaryProblems(value, allowed, hasClosing, paperWords, terms));
                        List<FactItem> items = new ArrayList<>();
                        List<String> labels = new ArrayList<>();
                        for (String slot : SLOTS) {
                            List<PointItem> slotItems = value.slot(slot);
                            for (int i = 0; i < slotItems.size(); i++) {
                                items.add(new FactItem(slotItems.get(i).point(), slotItems.get(i).evidence()));
                                labels.add("%s 칸의 %d번째 요약 문장".formatted(slot, i + 1));
                            }
                        }
                        problems.addAll(verifyFacts(new FactsAnswer(items), labels, allowed, byNumber, unsupported));
                        return problems;
                    }, MAX_RETRIES);
        } catch (IllegalStateException e) {
            log.warn("논문 요약을 건너뜁니다(위치로 쪽을 정함): {}", e.getMessage());
            return List.of();
        }
        servers.addAll(answer.attempts().stream().map(AiAsker.Attempt::server).toList());

        List<ComicPlan.SummaryPoint> points = new ArrayList<>();
        for (String slot : SLOTS) {
            for (PointItem item : answer.value().slot(slot).stream().limit(MAX_POINTS_PER_SLOT).toList()) {
                String text = item.point() == null ? "" : TextRules.removeCitations(item.point()).strip();
                List<Integer> evidence = item.evidence() == null ? List.of() : item.evidence().stream().distinct().filter(allowed::contains).toList();
                boolean repeats = points.stream().anyMatch(other -> TextRules.similarity(text, other.text()) >= SIMILAR_FACT);
                if (text.isEmpty() || evidence.isEmpty() || unsupported.containsKey(item.point()) || repeats
                        || TextRules.hasMixedScript(text) || TextRules.hasFormula(text) || !TextRules.unknownEnglishWords(text, paperWords).isEmpty()) {
                    log.info("요약 문장을 버렸습니다: [{}] {}", SLOT_NAMES.get(slot), item.point());
                    continue;
                }
                points.add(new ComicPlan.SummaryPoint(slot, text, evidence));
            }
        }
        log.info("논문 요약: {}", points.stream().map(p -> "[" + SLOT_NAMES.get(p.slot()) + "] " + p.text()).toList());
        return points;
    }

    /** 요약에 보여 줄 문장: 초록(본문 첫 섹션)과 결론·한계·논의 섹션. 표 조각과 표·그림 제목은 뺀다. */
    static List<Integer> summarySentences(List<ChunkSplitter.Section> sections, Map<Integer, PaperSentence> byNumber) {
        List<Integer> numbers = new ArrayList<>();
        for (int index = 0; index < sections.size(); index++) {
            ChunkSplitter.Section section = sections.get(index);
            if (index != 0 && !TextRules.isClosingSection(byNumber.get(section.start()).getSectionTitle())) {
                continue;
            }
            for (int number = section.start(); number <= section.end(); number++) {
                String text = byNumber.get(number).getText();
                if (!TextRules.isTableFragment(text) && !TextRules.isCaption(text)) {
                    numbers.add(number);
                }
            }
        }
        return numbers.stream().limit(MAX_SUMMARY_SENTENCES).toList();
    }

    static List<String> summaryProblems(SummaryAnswer answer, Set<Integer> allowed, boolean hasClosing, Set<String> paperWords,
                                        List<ComicPlan.Term> terms) {
        List<String> problems = new ArrayList<>();
        for (String slot : SLOTS) {
            List<PointItem> items = answer.slot(slot);
            int least = "limitation".equals(slot) ? (hasClosing ? 1 : 0) : MIN_POINTS_PER_SLOT;
            if (items.size() < least) {
                problems.add("%s 칸의 요약 문장이 %d개다. 논문 문장에서 찾아 서로 다른 요약 문장을 %d개 이상 쓴다.".formatted(slot, items.size(), least));
            }
            for (int i = 0; i < items.size(); i++) {
                PointItem item = items.get(i);
                String label = "%s 칸의 %d번째 요약 문장".formatted(slot, i + 1);
                String text = item.point() == null ? "" : item.point();
                List<Integer> evidence = item.evidence() == null ? List.of() : item.evidence();
                if (evidence.stream().noneMatch(allowed::contains)) {
                    problems.add("%s의 근거 번호 %s가 모두 목록에 없는 번호다.".formatted(label, evidence));
                }
                addMixedScriptProblem(problems, label, text);
                if (TextRules.hasFormula(text)) {
                    problems.add(label + "에 수식 기호가 있다. 수식 대신 그 뜻을 쉬운 말로 써라.");
                }
                if (TextRules.hasCitation(text)) {
                    problems.add("%s에 인용 번호가 들어 있다: \"%s\". 번호를 빼고 가리키는 대상을 말로 써라.".formatted(label, text));
                }
                problems.addAll(unknownTermProblems(label, text, paperWords));
                problems.addAll(TextRules.termVariantProblems(label, text, terms));
                problems.addAll(TextRules.koreanParenthesisProblems(label, text));
            }
        }
        return problems;
    }

    // ---------- ⑥ 요약 연결 ----------

    /**
     * 조각의 사실마다 연결할 요약 문장 번호(1부터, 0은 연결 없음)를 묻는다. 요약이나 사실이 없으면 묻지 않는다.
     * 연결이 깨지면(답이 모두 잘림 등) 작업을 멈추지 않고 이 조각의 사실을 모두 0으로 둔다.
     */
    private List<Integer> link(List<ComicPlan.SummaryPoint> summary, Chunk chunk, List<ComicPlan.FactPlan> facts, Set<String> servers) {
        List<Integer> none = new ArrayList<>(Collections.nCopies(facts.size(), 0));
        if (summary.isEmpty() || facts.isEmpty()) {
            return none;
        }
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < summary.size(); i++) {
            points.append("%d. [%s] %s%n".formatted(i + 1, SLOT_NAMES.get(summary.get(i).slot()), summary.get(i).text()));
        }
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < facts.size(); i++) {
            list.append("%d. %s%n".formatted(i + 1, facts.get(i).fact()));
        }
        Map<String, Object> schema = JsonSchemas.object("links", JsonSchemas.array(
                JsonSchemas.object("fact", JsonSchemas.integer(), "point", JsonSchemas.integer()), facts.size(), facts.size()));
        AiAsker.Answer<LinksAnswer> answer;
        try {
            answer = aiAsker.ask(properties.factModel(), PlanPrompts.LINK_SYSTEM,
                    PlanPrompts.LINK.formatted(points.toString().trim(), String.join(", ", chunk.sections()), list.toString().trim()),
                    schema, LinksAnswer.class, value -> linkProblems(value, facts.size(), summary.size()), MAX_RETRIES);
        } catch (IllegalStateException e) {
            log.warn("요약 연결을 건너뜁니다(문장 {}~{}): {}", chunk.start(), chunk.end(), e.getMessage());
            return none;
        }
        answer.attempts().forEach(attempt -> servers.add(attempt.server()));
        List<LinkItem> links = answer.value().links() == null ? List.of() : answer.value().links();
        for (int i = 0; i < Math.min(facts.size(), links.size()); i++) {
            int point = links.get(i).point();
            none.set(i, point >= 1 && point <= summary.size() ? point : 0);
        }
        return none;
    }

    /**
     * AI가 단어만 보고 엉뚱한 요약 칸에 붙인 연결을 코드로 끊는다. 맞지 않으면 이유, 맞으면 null.
     * 지시문에 '다른 연구 소개는 0'을 넣었어도 198번에서 '한 연구는 … 조건부 가역 네트워크를 사용한다'(67번)가 방법 칸에,
     * 방법 섹션의 '압축 단계와 생성 학습 단계를 분리한다'(75번)가 '계산' 낱말 때문에 문제 칸에 붙었다.
     * - 문제 칸: 논문 앞부분(첫 쪽 몫의 조각: 초록·서론·관련 연구)에서 나온 사실만
     * - 한계 칸: 논문 뒷부분(끝 쪽 몫의 조각: 응용·한계·결론)에서 나온 사실만. 200번에서 서론의 '5만 장에 5일'(21번)과
     *   관련 연구의 69·70번이 '느리다', '비용' 낱말 때문에 한계 쪽에 붙었다.
     * - 방법·결과 칸: 이 연구의 이야기만 (주어가 '한 연구', '앞선 연구'처럼 다른 연구인 사실은 뺀다. 사실 뽑기 2번 규칙이 주어를 이렇게 쓰게 한다)
     */
    static String linkMismatch(String slot, int chunkIndex, int chunkCount, String fact) {
        return linkMismatch(slot, chunkIndex, chunkCount, fact, false);
    }

    /** otherStudyEvidence: 근거 문장이 모두 다른 연구가 주어인 문장(TextRules.isOtherStudySentence)이면 방법·결과 칸에 잇지 않는다 */
    static String linkMismatch(String slot, int chunkIndex, int chunkCount, String fact, boolean otherStudyEvidence) {
        if (("method".equals(slot) || "result".equals(slot)) && otherStudyEvidence) {
            return "근거 문장이 다른 연구의 이야기";
        }
        if ("problem".equals(slot) && chunkIndex >= CHUNKS_PER_PAGE) {
            return "문제 칸인데 논문 뒷부분의 사실";
        }
        if ("limitation".equals(slot) && chunkIndex < chunkCount - CHUNKS_PER_PAGE) {
            return "한계 칸인데 논문 앞부분의 사실";
        }
        if (("method".equals(slot) || "result".equals(slot)) && OTHER_STUDY.matcher(fact).find()) {
            return "다른 연구의 이야기";
        }
        return null;
    }

    /** 실험 섹션(초록·서론·결론·한계가 아닌 곳)에서 나온, 결과 숫자가 있는 사실 */
    static boolean isExperimentResult(ComicPlan.FactPlan fact, Map<Integer, PaperSentence> byNumber) {
        return !TextRules.resultValues(fact.fact()).isEmpty() && !fact.evidence().isEmpty() && fact.evidence().stream()
                .map(number -> byNumber.get(number).getSectionTitle())
                .noneMatch(title -> TextRules.isSummarySection(title) || TextRules.isClosingSection(title));
    }

    /**
     * 연결되지 않았거나 엉뚱한 칸이라 끊긴, 결과 숫자가 있는 실험 사실을 결과 칸 후보로 넣는다(후보가 가장 적은 결과 요약 문장에).
     * 201번: 인페인팅의 '최소 2.7배 … 1.6배'(228번)를 AI가 '추론 비용이 크다'(문제)에 붙여 끊겼고, 결과 쪽에 실험 숫자가 빠졌다.
     */
    static List<Linked> spareResults(List<ComicPlan.SummaryPoint> summary, List<ComicPlan.FactPlan> spare, List<Linked> linked) {
        List<Integer> resultPoints = new ArrayList<>();
        for (int i = 0; i < summary.size(); i++) {
            if ("result".equals(summary.get(i).slot())) {
                resultPoints.add(i + 1);
            }
        }
        if (resultPoints.isEmpty()) {
            return List.of();
        }
        Map<Integer, Integer> counts = new HashMap<>();
        resultPoints.forEach(point -> counts.put(point, (int) linked.stream().filter(link -> link.point() == point).count()));
        List<Linked> added = new ArrayList<>();
        for (ComicPlan.FactPlan fact : spare) {
            int point = resultPoints.stream().min(Comparator.comparingInt(counts::get)).orElseThrow();
            counts.merge(point, 1, Integer::sum);
            added.add(new Linked(fact, point));
            log.info("결과 숫자가 있는 실험 사실을 결과 칸 후보로 넣었습니다: {}", fact.fact());
        }
        return added;
    }

    static List<String> linkProblems(LinksAnswer answer, int factCount, int pointCount) {
        List<LinkItem> links = answer.links() == null ? List.of() : answer.links();
        List<String> problems = new ArrayList<>();
        if (links.size() != factCount) {
            problems.add("links에 사실 %d개를 모두 순서대로 넣는다.".formatted(factCount));
        }
        links.stream().filter(link -> link.point() < 0 || link.point() > pointCount).map(LinkItem::point).distinct()
                .forEach(point -> problems.add("%d번 요약 문장은 없다. 1~%d번이나 0을 쓴다.".formatted(point, pointCount)));
        return problems;
    }

    // ---------- ⑦ 쪽 정하기 ----------

    /** 쪽 역할이 맡는 요약 칸: 기 = 문제, 승 = 방법, 전 = 결과, 결 = 한계 (3쪽이면 승·전이 방법과 결과) */
    static List<String> slotsOf(PageRole role) {
        return switch (role.name()) {
            case "기" -> List.of("problem");
            case "승" -> List.of("method");
            case "전" -> List.of("result");
            case "승·전" -> List.of("method", "result");
            default -> List.of("limitation");
        };
    }

    /**
     * 요약 문장마다 연결된 사실을 FACTS_PER_POINT개까지 고른다. 쪽은 요약 칸으로 정하고, 사실이 MAX_FACTS_PER_PAGE개를 넘으면
     * 요약 문장마다 돌아가며 고른다. 쪽의 chunks에는 그 위치의 조각 기록(제목, 버린 사실)을 둔다.
     * - 근거 문장이 이미 쓴 사실과 겹치는 사실은 고르지 않는다(198번 4쪽: 240번 한 문장에서 나온 사실 두 개가 연달아 나옴).
     * - 결과가 아닌 칸은 요약 문장과 근거가 같은 사실을 먼저 쓰고, 그 근거에서 나온 사실이 하나도 없으면 요약 문장 자체를 사실로 먼저 쓴다.
     *   198번 1쪽은 본문 사실만 점수가 높아 요약의 근거 '수백 GPU 날'(3번)이 빠졌다. 결과 칸은 숫자 있는 본문 사실이 먼저다.
     * - 같은 쪽에 아직 없는 섹션의 사실을 먼저 쓴다(200번 결과 쪽 6개 중 5개가 '압축 정도 비교' 한 섹션에서 나와 FID 5.11 등이 빠졌다).
     */
    static List<ComicPlan.PagePlan> pagesBySummary(List<PageRole> roles, List<ComicPlan.ChunkPlan> chunkPlans,
                                                   List<ComicPlan.SummaryPoint> summary, List<Linked> linked,
                                                   Map<Integer, PaperSentence> byNumber) {
        Set<Integer> factEvidence = linked.stream().flatMap(link -> link.fact().evidence().stream()).collect(Collectors.toSet());
        List<List<ComicPlan.FactPlan>> byPoint = new ArrayList<>();
        for (int i = 0; i < summary.size(); i++) {
            int point = i + 1;
            byPoint.add(new ArrayList<>(candidates(summary.get(i),
                    linked.stream().filter(link -> link.point() == point).map(Linked::fact).toList(), factEvidence, byNumber)));
            log.info("요약 문장 {} [{}] 후보 사실 {}개: {}", point, SLOT_NAMES.get(summary.get(i).slot()), byPoint.getLast().size(),
                    byPoint.getLast().stream().map(fact -> fact.evidence().toString()).toList());
        }
        Set<Integer> usedEvidence = new HashSet<>();
        List<String> usedTexts = new ArrayList<>();
        List<ComicPlan.PagePlan> pages = new ArrayList<>();
        for (int page = 0; page < roles.size(); page++) {
            PageRole role = roles.get(page);
            List<Integer> pagePoints = new ArrayList<>();
            for (int i = 0; i < summary.size(); i++) {
                if (slotsOf(role).contains(summary.get(i).slot())) {
                    pagePoints.add(i);
                }
            }
            Map<Integer, List<ComicPlan.FactPlan>> chosen = new HashMap<>();
            Set<String> usedSections = new HashSet<>();
            Map<Integer, Integer> next = new HashMap<>();  // 요약 문장마다 다음에 볼 후보 위치
            int count = 0;
            for (int rank = 0; rank < FACTS_PER_POINT; rank++) {
                for (int point : pagePoints) {
                    List<ComicPlan.FactPlan> pointFacts = byPoint.get(point);
                    int at = next.getOrDefault(point, 0);
                    while (at < pointFacts.size() && isUsed(pointFacts.get(at), usedEvidence, usedTexts)) {
                        at++;
                    }
                    // 이 쪽에 아직 없는 섹션의 사실이 뒤에 있으면 그것을 먼저 쓴다
                    int pick = at;
                    for (int k = at; k < pointFacts.size(); k++) {
                        if (!isUsed(pointFacts.get(k), usedEvidence, usedTexts) && !usedSections.contains(sectionOf(pointFacts.get(k), byNumber))) {
                            pick = k;
                            break;
                        }
                    }
                    if (pick < pointFacts.size() && count < MAX_FACTS_PER_PAGE) {
                        ComicPlan.FactPlan fact = pointFacts.remove(pick);
                        pointFacts.add(at, fact);
                        chosen.computeIfAbsent(point, key -> new ArrayList<>()).add(fact);
                        usedEvidence.addAll(fact.evidence());
                        usedTexts.add(fact.fact());
                        usedSections.add(sectionOf(fact, byNumber));
                        count++;
                        at++;
                    }
                    next.put(point, at);
                }
            }
            List<ComicPlan.FactPlan> facts = pagePoints.stream().flatMap(point -> chosen.getOrDefault(point, List.of()).stream()).toList();
            if (facts.isEmpty()) {
                log.warn("{}쪽({})에 쓸 요약 문장이 없습니다", page + 1, role.name());
            }
            pages.add(new ComicPlan.PagePlan(page + 1, role.name(), role.meaning(), positional(chunkPlans, page), facts,
                    pagePoints.stream().map(point -> summary.get(point).text()).toList()));
        }
        return pages;
    }

    /** 사실의 첫 근거 문장이 있는 섹션 */
    private static String sectionOf(ComicPlan.FactPlan fact, Map<Integer, PaperSentence> byNumber) {
        PaperSentence sentence = fact.evidence().isEmpty() ? null : byNumber.get(fact.evidence().getFirst());
        return sentence == null ? "" : String.valueOf(sentence.getSectionTitle());
    }

    /** 이미 고른 사실과 근거 문장이 겹치거나 글이 거의 같은 사실 */
    private static boolean isUsed(ComicPlan.FactPlan fact, Set<Integer> usedEvidence, List<String> usedTexts) {
        return fact.evidence().stream().anyMatch(usedEvidence::contains)
                || usedTexts.stream().anyMatch(text -> TextRules.factSimilarity(text, fact.fact()) >= SIMILAR_FACT);
    }

    /**
     * 요약 문장 하나에 쓸 사실 후보를 고를 순서대로 (pagesBySummary 설명 참고).
     * 요약 문장 자체는 그 근거 문장에서 나온 사실이 하나도 없을 때만 쓴다(있으면 그 사실이 같은 내용을 더 자세히 말한다).
     */
    static List<ComicPlan.FactPlan> candidates(ComicPlan.SummaryPoint point, List<ComicPlan.FactPlan> linkedFacts,
                                               Set<Integer> factEvidence, Map<Integer, PaperSentence> byNumber) {
        List<ComicPlan.FactPlan> facts = linkedFacts.stream()
                .sorted(Comparator.comparingInt((ComicPlan.FactPlan fact) -> -pickScore(fact, point, byNumber)))
                .collect(Collectors.toCollection(ArrayList::new));
        ComicPlan.FactPlan own = new ComicPlan.FactPlan(point.text(), point.evidence(), "요약");
        if (facts.isEmpty()) {
            return List.of(own);
        }
        if (!"result".equals(point.slot())) {
            facts.stream().filter(fact -> fact.evidence().stream().anyMatch(point.evidence()::contains)).findFirst()
                    .ifPresentOrElse(same -> {
                        facts.remove(same);
                        facts.addFirst(same);
                    }, () -> {
                        if (point.evidence().stream().noneMatch(factEvidence::contains)) {
                            facts.addFirst(own);
                        }
                    });
        }
        return facts;
    }

    /**
     * 요약 문장에 연결된 사실 중 먼저 고를 순서(점수가 같으면 논문 순서). 논문 순서로만 고르면 초록·서론 사실이 늘 먼저 뽑혀,
     * 196번 결과 쪽에 실험 숫자(FID 5.11 등)가 빠지고 한계 요약 '재구성 병목'(240번) 대신 앞쪽 131번이 뽑혔다.
     * 본문(초록·서론·결론이 아닌) 근거 +2, 요약 문장과 근거가 같으면 +1, 결과 칸에서 결과 숫자가 있으면 +2.
     */
    static int pickScore(ComicPlan.FactPlan fact, ComicPlan.SummaryPoint point, Map<Integer, PaperSentence> byNumber) {
        int score = isSummary(fact, byNumber) ? 0 : 2;
        if (fact.evidence().stream().anyMatch(point.evidence()::contains)) {
            score += 1;
        }
        if ("result".equals(point.slot()) && !TextRules.resultValues(fact.fact()).isEmpty()) {
            score += 2;
        }
        return score;
    }

    /** 요약이 없을 때: 예전처럼 위치로 쪽을 정하고, 쪽마다 앞에서부터 MAX_FACTS_PER_PAGE개 */
    private static List<ComicPlan.PagePlan> pagesByPosition(List<PageRole> roles, List<ComicPlan.ChunkPlan> chunkPlans,
                                                            List<List<ComicPlan.FactPlan>> chunkFacts) {
        List<ComicPlan.PagePlan> pages = new ArrayList<>();
        for (int page = 0; page < roles.size(); page++) {
            int from = page * CHUNKS_PER_PAGE;
            List<ComicPlan.FactPlan> facts = chunkFacts.subList(from, Math.min(chunkFacts.size(), from + CHUNKS_PER_PAGE)).stream()
                    .flatMap(List::stream).limit(MAX_FACTS_PER_PAGE).toList();
            PageRole role = roles.get(page);
            pages.add(new ComicPlan.PagePlan(page + 1, role.name(), role.meaning(), positional(chunkPlans, page), facts));
        }
        return pages;
    }

    private static List<ComicPlan.ChunkPlan> positional(List<ComicPlan.ChunkPlan> chunkPlans, int page) {
        int from = page * CHUNKS_PER_PAGE;
        return List.copyOf(chunkPlans.subList(Math.min(from, chunkPlans.size()), Math.min(chunkPlans.size(), from + CHUNKS_PER_PAGE)));
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

    // ---------- ③-2 용어 정하기 ----------

    /**
     * 핵심 용어의 번역을 처음에 하나로 정한다. 영어 원문이 논문에 없거나 한국어가 비어 있는 용어는 뺀다.
     * 정하기가 깨지면(답이 모두 잘림 등) 작업을 멈추지 않고 용어 없이 간다.
     */
    private List<ComicPlan.Term> pickTerms(String paperTitle, String abstractText, Set<String> paperWords, Set<String> servers) {
        Map<String, Object> schema = JsonSchemas.object("terms", JsonSchemas.array(JsonSchemas.object(
                "english", JsonSchemas.string(), "korean", JsonSchemas.string(),
                "variants", JsonSchemas.array(JsonSchemas.string(), 0, 3)), 1, MAX_TERMS));
        AiAsker.Answer<TermsAnswer> answer;
        try {
            answer = aiAsker.ask(properties.factModel(), PlanPrompts.GLOSSARY_SYSTEM,
                    PlanPrompts.GLOSSARY.formatted(paperTitle, abstractText, MAX_TERMS), schema, TermsAnswer.class,
                    value -> value.terms().stream().filter(term -> !isUsableTerm(term, paperWords))
                            .map(term -> "용어 '%s'(%s)는 영어 원문이 제목·초록에 없거나 한국어 번역이 아니다. 영어는 원문 그대로, 번역은 한국어로 쓴다."
                                    .formatted(term.english(), term.korean())).toList(),
                    MAX_RETRIES);
        } catch (IllegalStateException e) {
            log.warn("용어 정하기를 건너뜁니다: {}", e.getMessage());
            return List.of();
        }
        answer.attempts().forEach(attempt -> servers.add(attempt.server()));
        List<ComicPlan.Term> terms = answer.value().terms().stream().filter(term -> isUsableTerm(term, paperWords))
                .map(term -> new ComicPlan.Term(term.english().strip(), term.korean().strip(), cleanVariants(term)))
                .toList();
        log.info("용어 번역: {}", terms.stream().map(term -> term.english() + " → " + term.korean() + " " + term.variants()).toList());
        return terms;
    }

    private static boolean isUsableTerm(TermItem term, Set<String> paperWords) {
        return term.english() != null && !term.english().isBlank() && term.korean() != null
                && term.korean().matches(".*[가-힣].*") && !TextRules.hasMixedScript(term.korean())
                && TextRules.unknownEnglishWords(term.english(), paperWords).isEmpty();
    }

    /** 다른 번역 중 쓸 만한 것만: 한국어이고, 두 글자 이상이고, 정한 번역에 들어 있지 않은 말 */
    private static List<String> cleanVariants(TermItem term) {
        String korean = term.korean().strip();
        return (term.variants() == null ? List.<String>of() : term.variants()).stream().map(String::strip)
                .filter(variant -> variant.length() >= 2 && variant.matches(".*[가-힣].*") && !korean.contains(variant))
                .distinct().toList();
    }

    // ---------- ④ 핵심 사실 추출 ----------

    /**
     * 한계 규칙을 붙일 조각: 마지막 쪽 조각 중 한계·결론 섹션이 든 가장 뒤의 조각(없으면 -1).
     * 두 번째 논문(176번)에서 결과 섹션 'Perceived Limitations'까지 한계 조각으로 봐서, 마지막 쪽에서만 찾는다.
     */
    static int closingChunk(List<Chunk> chunks, int pages) {
        int lastPageStart = (pages - 1) * CHUNKS_PER_PAGE;
        for (int index = chunks.size() - 1; index >= lastPageStart && index >= 0; index--) {
            if (chunks.get(index).sections().stream().anyMatch(TextRules::isClosingSection)) {
                return index;
            }
        }
        return -1;
    }

    private AiAsker.Answer<FactsAnswer> pickFacts(Chunk chunk, TitleItem title, boolean closing,
                                                  Map<Integer, PaperSentence> byNumber, Set<String> paperWords, Set<String> titleWords,
                                                  List<ComicPlan.Term> terms, Map<String, String> unsupported) {
        List<Integer> candidates = new ArrayList<>();
        Map<String, List<Integer>> bySection = new LinkedHashMap<>();
        StringBuilder lines = new StringBuilder();
        for (int number = chunk.start(); number <= chunk.end(); number++) {
            PaperSentence sentence = byNumber.get(number);
            if (TextRules.isTableFragment(sentence.getText()) || TextRules.isCaption(sentence.getText())) {
                continue;  // 표 조각과 표·그림 제목은 AI에게 보여 주지 않는다 (제목만 보고 결과를 지어냈다)
            }
            candidates.add(number);
            bySection.computeIfAbsent(String.valueOf(sentence.getSectionTitle()), key -> new ArrayList<>()).add(number);
            lines.append("[%d] (p.%s) %s%n".formatted(number, sentence.getPage(), sentence.getText()));
        }
        Set<Integer> allowed = Set.copyOf(candidates);
        Map<String, Object> schema = JsonSchemas.object("facts", JsonSchemas.array(
                JsonSchemas.object("fact", JsonSchemas.string(), "evidence", JsonSchemas.array(JsonSchemas.integer(), 1, 3)),
                1, MAX_FACTS_PER_CHUNK));
        Function<FactsAnswer, List<String>> check = answer -> {
            List<String> problems = new ArrayList<>(checkFacts(answer, allowed, paperWords, terms));
            problems.addAll(spreadProblems(answer, allowed, bySection, byNumber));
            problems.addAll(contextProblems(answer, allowed, byNumber, titleWords, terms));
            problems.addAll(attributionProblems(answer, allowed, byNumber));
            List<String> labels = java.util.stream.IntStream.rangeClosed(1, answer.facts().size()).mapToObj(n -> n + "번 사실").toList();
            problems.addAll(verifyFacts(answer, labels, allowed, byNumber, unsupported));
            return problems;
        };
        return aiAsker.ask(properties.factModel(), PlanPrompts.FACT_SYSTEM,
                PlanPrompts.FACTS.formatted(title.title(), title.summary(), String.join(", ", chunk.sections()), MAX_FACTS_PER_CHUNK,
                        (closing ? "\n" + PlanPrompts.LIMITATION_RULE : "") + ComicPlan.termGuide(terms),
                        lines.toString().trim()),
                schema, FactsAnswer.class, check, MAX_RETRIES);
    }

    /**
     * 사실이 조각의 한 섹션에서만 나왔으면 다른 섹션에서도 고르게 한다. AI가 조각 앞부분 문장만 고르는 경향 때문에
     * 두 번째 논문(176번)에서 같은 조각 뒤쪽 섹션의 핵심 결과(정답률 0.750 대 0.333)가 빠졌다.
     * 다른 섹션의 결과 문장(비교·변화 낱말과 숫자가 있는 문장) 번호를 함께 알려 준다.
     */
    static List<String> spreadProblems(FactsAnswer answer, Set<Integer> allowed, Map<String, List<Integer>> bySection,
                                       Map<Integer, PaperSentence> byNumber) {
        List<String> sections = bySection.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= MIN_SECTION_SENTENCES).map(Map.Entry::getKey).toList();
        if (answer.facts().size() < 2 || sections.size() < 2) {
            return List.of();
        }
        Set<String> used = answer.facts().stream().flatMap(fact -> fact.evidence().stream()).filter(allowed::contains)
                .map(number -> String.valueOf(byNumber.get(number).getSectionTitle())).collect(Collectors.toSet());
        if (used.size() != 1) {
            return List.of();
        }
        String only = used.iterator().next();
        List<String> others = sections.stream().filter(section -> !section.equals(only)).toList();
        List<Integer> results = others.stream().flatMap(section -> bySection.get(section).stream())
                .filter(number -> TextRules.resultScore(byNumber.get(number).getText()) >= RESULT_SCORE)
                .sorted(Comparator.comparingInt((Integer number) -> -TextRules.resultScore(byNumber.get(number).getText()))
                        .thenComparing(Comparator.naturalOrder()))
                .limit(3).toList();
        String hint = results.isEmpty() ? "" : " 특히 결과 문장 %s를 살펴본다."
                .formatted(results.stream().map(number -> "[" + number + "]").collect(Collectors.joining(", ")));
        return List.of("사실이 모두 '%s' 섹션에서만 나왔다. 이 부분의 다른 섹션(%s)에서도 하나 이상 고른다.%s"
                .formatted(only, String.join(", ", others), hint));
    }

    /**
     * 결과 숫자가 든 사실에 어느 실험에서 나온 숫자인지가 있는지. 근거 문장이 있는 섹션 이름의 핵심 낱말(논문 제목에도 있는 낱말과
     * 흔한 낱말은 뺀다)이 사실에 영어로든 정한 한국어 번역으로든 하나도 없으면 다시 쓰게 한다. 초록·서론·결론·한계 섹션은 보지 않는다.
     * 198번: 인페인팅 섹션의 228번 'Overall, … 2.7×'가 '전반적으로 … 최소 2.7배'가 되어 논문 전체의 결과처럼 읽혔다.
     */
    static List<String> contextProblems(FactsAnswer answer, Set<Integer> allowed, Map<Integer, PaperSentence> byNumber,
                                        Set<String> titleWords, List<ComicPlan.Term> terms) {
        List<String> problems = new ArrayList<>();
        for (int index = 0; index < answer.facts().size(); index++) {
            FactItem fact = answer.facts().get(index);
            List<Integer> evidence = fact.evidence() == null ? List.of() : fact.evidence().stream().filter(allowed::contains).toList();
            if (fact.fact() == null || TextRules.resultValues(fact.fact()).isEmpty() || evidence.isEmpty()) {
                continue;
            }
            String section = String.valueOf(byNumber.get(evidence.getFirst()).getSectionTitle());
            if (evidence.stream().map(number -> byNumber.get(number).getSectionTitle())
                    .anyMatch(title -> TextRules.isSummarySection(title) || TextRules.isClosingSection(title))) {
                continue;
            }
            Set<String> keywords = sectionKeywords(section, titleWords);
            String text = fact.fact().toLowerCase(java.util.Locale.ROOT);
            boolean named = keywords.stream().anyMatch(text::contains) || terms.stream()
                    .filter(term -> term.english() != null && keywords.stream().anyMatch(term.english().toLowerCase(java.util.Locale.ROOT)::contains))
                    .anyMatch(term -> term.korean() != null && !term.korean().isBlank() && fact.fact().contains(term.korean()));
            if (!keywords.isEmpty() && !named) {
                problems.add("%d번 사실의 숫자가 어느 실험에서 나온 결과인지 빠졌다. 근거 문장은 '%s' 섹션에 있다. 사실에 그 실험이 무엇인지 함께 쓴다."
                        .formatted(index + 1, section));
            }
        }
        return problems;
    }

    /**
     * 다른 연구의 문장을 이 연구의 일로 쓴 사실. 204번: 원문 "[66] extend this approach to text-to-image generation …"을
     * 사실 "이 연구는 … 공동 분포를 학습하여 …"로 써서, 앞선 연구가 이 논문의 방법처럼 나왔다.
     */
    static List<String> attributionProblems(FactsAnswer answer, Set<Integer> allowed, Map<Integer, PaperSentence> byNumber) {
        List<String> problems = new ArrayList<>();
        for (int index = 0; index < answer.facts().size(); index++) {
            FactItem fact = answer.facts().get(index);
            List<Integer> evidence = fact.evidence() == null ? List.of() : fact.evidence().stream().filter(allowed::contains).toList();
            if (fact.fact() != null && !evidence.isEmpty() && THIS_STUDY.matcher(fact.fact()).find()
                    && evidence.stream().allMatch(number -> TextRules.isOtherStudySentence(byNumber.get(number).getText()))) {
                problems.add("%d번 사실의 근거 문장 %s는 다른 연구(인용 번호가 주어)의 이야기인데 '이 연구'의 일로 썼다. 주어를 '앞선 연구는' 또는 그 방법 이름으로 쓴다."
                        .formatted(index + 1, evidence));
            }
        }
        return problems;
    }

    /** 근거 문장이 모두 다른 연구가 주어인 문장인 사실 */
    static boolean isOtherStudy(ComicPlan.FactPlan fact, Map<Integer, PaperSentence> byNumber) {
        return !fact.evidence().isEmpty() && fact.evidence().stream().allMatch(number -> TextRules.isOtherStudySentence(byNumber.get(number).getText()));
    }

    /** 섹션 이름의 핵심 낱말(소문자, 끝의 복수 s 뺌): 세 글자 이상 영어 낱말에서 흔한 낱말과 except를 뺀다 */
    static Set<String> sectionKeywords(String title, Set<String> except) {
        return java.util.regex.Pattern.compile("[A-Za-z]{3,}").matcher(title).results()
                .map(match -> match.group().toLowerCase(java.util.Locale.ROOT))
                .map(word -> word.length() > 4 && word.endsWith("s") ? word.substring(0, word.length() - 1) : word)
                .filter(word -> !COMMON_SECTION_WORDS.contains(word) && !except.contains(word))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /**
     * 사실 확인: 고른 사실(또는 요약 문장)을 근거 문장과 다시 대조한다(AI 요청 1번). '틀림'으로 나온 것은 다시 쓰게 하고,
     * unsupported에 적어 두어 다시 써도 남으면 버린다. 확인 자체가 깨지면(답이 모두 잘림 등) 작업을 멈추지 않고 건너뛴다.
     * labels는 다시 쓰기 요청에서 부를 이름("1번 사실", "result 칸의 1번째 요약 문장")이다.
     */
    private List<String> verifyFacts(FactsAnswer answer, List<String> labels, Set<Integer> allowed, Map<Integer, PaperSentence> byNumber,
                                     Map<String, String> unsupported) {
        List<FactItem> facts = answer.facts();
        if (facts.isEmpty()) {
            return List.of();
        }
        StringBuilder sentences = new StringBuilder();
        StringBuilder claims = new StringBuilder();
        Set<Integer> shown = new java.util.TreeSet<>();
        for (int index = 0; index < facts.size(); index++) {
            FactItem fact = facts.get(index);
            List<Integer> evidence = fact.evidence() == null ? List.of() : fact.evidence().stream().filter(allowed::contains).toList();
            shown.addAll(evidence);
            claims.append("[사실 %d] %s (근거: %s)%n".formatted(index + 1, fact.fact(),
                    evidence.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "))));
        }
        shown.forEach(number -> sentences.append("[%d] (%s) %s%n".formatted(number, byNumber.get(number).getSectionTitle(),
                byNumber.get(number).getText())));
        Map<String, Object> schema = JsonSchemas.object("results", JsonSchemas.array(
                JsonSchemas.object("fact", JsonSchemas.integer(), "verdict", JsonSchemas.stringEnum(PlanPrompts.VERDICTS),
                        "reason", JsonSchemas.string()),
                facts.size(), facts.size()));
        AiAsker.Answer<VerifyAnswer> verdict;
        try {
            verdict = aiAsker.ask(properties.factModel(), PlanPrompts.VERIFY_SYSTEM,
                    PlanPrompts.VERIFY.formatted(sentences.toString().trim(), claims.toString().trim()), schema, VerifyAnswer.class,
                    value -> value.results().size() == facts.size() ? List.of()
                            : List.of("results에 사실 %d개를 모두 순서대로 넣는다.".formatted(facts.size())), 0);
        } catch (IllegalStateException e) {
            log.warn("사실 확인을 건너뜁니다: {}", e.getMessage());
            return List.of();
        }
        List<String> problems = new ArrayList<>();
        List<VerifyItem> results = verdict.value().results();
        for (int index = 0; index < Math.min(facts.size(), results.size()); index++) {
            String fact = facts.get(index).fact();
            VerifyItem result = results.get(index);
            if (!"틀림".equals(result.verdict())) {  // 맞음·과장은 그대로 둔다 (과장까지 막으면 맞는 사실이 너무 많이 버려진다)
                unsupported.remove(fact);
                continue;
            }
            unsupported.put(fact, result.reason());
            problems.add("%s은 근거 문장에 없는 내용을 담고 있다: %s. 근거 문장에 적힌 내용만 쓴다."
                    .formatted(labels.get(index), result.reason()));
        }
        return problems;
    }

    private static List<String> checkFacts(FactsAnswer answer, Set<Integer> allowed, Set<String> paperWords, List<ComicPlan.Term> terms) {
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
            problems.addAll(unknownTermProblems(factNumber + "번 사실", fact.fact(), paperWords));
            problems.addAll(TextRules.termVariantProblems(factNumber + "번 사실", fact.fact(), terms));
            problems.addAll(TextRules.koreanParenthesisProblems(factNumber + "번 사실", fact.fact()));
            if (TextRules.hasCitation(fact.fact())) {
                problems.add("%d번 사실에 인용 번호가 들어 있다: \"%s\". 번호를 빼고 가리키는 대상을 말로 써라(예: '[12]의 방법' → '기존 방법')."
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
                                                 Map<Integer, List<String>> usedEvidence, List<String> acceptedFacts, Set<String> paperWords) {
        String text = TextRules.removeCitations(item.fact());
        List<Integer> evidence = item.evidence().stream().distinct()
                .filter(number -> number >= chunk.start() && number <= chunk.end())
                .filter(number -> !TextRules.isTableFragment(byNumber.get(number).getText()))
                .filter(number -> !TextRules.isCaption(byNumber.get(number).getText()))
                .toList();
        if (evidence.isEmpty() || TextRules.hasMixedScript(text) || TextRules.hasFormula(text)
                || !TextRules.unknownEnglishWords(text, paperWords).isEmpty()) {
            return null;  // 다시 써도 논문에 없는 영어 용어가 남은 사실은 틀린 용어를 가르치게 되므로 버린다
        }
        if (THIS_STUDY.matcher(text).find() && evidence.stream().allMatch(number -> TextRules.isOtherStudySentence(byNumber.get(number).getText()))) {
            log.info("다른 연구의 문장을 이 연구의 일로 쓴 사실을 버렸습니다: {}", text);
            return null;  // 다시 써도 다른 연구를 이 연구로 말하면 틀린 내용을 가르치게 된다
        }
        boolean repeatsSameEvidence = evidence.stream()
                .flatMap(number -> usedEvidence.getOrDefault(number, List.of()).stream())
                .anyMatch(other -> TextRules.factSimilarity(text, other) >= SIMILAR_FACT_SAME_EVIDENCE);
        boolean repeatsAnyFact = acceptedFacts.stream().anyMatch(other -> TextRules.factSimilarity(text, other) >= SIMILAR_FACT);
        if (repeatsSameEvidence || repeatsAnyFact) {
            return null;
        }
        evidence.forEach(number -> usedEvidence.computeIfAbsent(number, key -> new ArrayList<>()).add(text));
        acceptedFacts.add(text);
        return new ComicPlan.FactPlan(text, evidence, title.title());
    }

    /**
     * 같은 결과 숫자(28.4, 0.750, 12%)를 말하는 비슷한 사실이 여러 번 나오면 하나만 남긴다. 187번에서 초록과 실험 절이
     * 같은 'BLEU 28.4'를 말해서 1쪽과 3쪽에 두 번 나왔다(조각이 달라서 앞의 반복 검사에 걸리지 않음).
     * 초록·서론·결론은 본문 결과의 요약이므로 한쪽만 요약 섹션이면 그쪽을 버리고, 아니면 뒤에 나온 쪽을 버린다.
     * 숫자만 같고 글이 거의 안 겹치면(드롭아웃 0.1과 라벨 스무딩 0.1처럼) 다른 사실로 본다.
     */
    static List<ComicPlan.PagePlan> dropRepeatedResults(List<ComicPlan.PagePlan> pages, Map<Integer, PaperSentence> byNumber) {
        List<ComicPlan.FactPlan> facts = pages.stream().flatMap(page -> page.facts().stream()).toList();
        Set<ComicPlan.FactPlan> repeated = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int i = 0; i < facts.size(); i++) {
            for (int j = i + 1; j < facts.size(); j++) {
                ComicPlan.FactPlan first = facts.get(i);
                ComicPlan.FactPlan second = facts.get(j);
                if (repeated.contains(first) || repeated.contains(second) || !isSameResult(first.fact(), second.fact())) {
                    continue;
                }
                boolean dropFirst = isSummary(first, byNumber) && !isSummary(second, byNumber);
                ComicPlan.FactPlan drop = dropFirst ? first : second;
                log.info("같은 결과를 말하는 사실을 버렸습니다: {} (남긴 사실: {})", drop.fact(), (dropFirst ? second : first).fact());
                repeated.add(drop);
            }
        }
        return pages.stream().map(page -> new ComicPlan.PagePlan(page.pageNumber(), page.role(), page.roleMeaning(), page.chunks(),
                page.facts().stream().filter(fact -> !repeated.contains(fact)).toList(), page.points())).toList();
    }

    private static boolean isSameResult(String first, String second) {
        Set<String> shared = new java.util.HashSet<>(TextRules.resultValues(first));
        shared.retainAll(TextRules.resultValues(second));
        return !shared.isEmpty() && TextRules.similarity(first, second) >= SIMILAR_RESULT;
    }

    /** 근거 문장이 모두 초록·서론·결론에 있는 사실 */
    private static boolean isSummary(ComicPlan.FactPlan fact, Map<Integer, PaperSentence> byNumber) {
        return fact.evidence().stream().allMatch(number -> TextRules.isSummarySection(byNumber.get(number).getSectionTitle()));
    }

    private static List<String> unknownTermProblems(String label, String text, Set<String> paperWords) {
        List<String> unknown = TextRules.unknownEnglishWords(text, paperWords);
        return unknown.isEmpty() ? List.of() : List.of("%s에 논문에 없는 영어 용어가 있다: %s. 영어 원문은 논문 문장에 나온 그대로 쓴다."
                .formatted(label, String.join(", ", unknown)));
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
