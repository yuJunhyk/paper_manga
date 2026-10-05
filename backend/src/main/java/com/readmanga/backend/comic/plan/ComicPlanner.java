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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    static final int MAX_FACTS_PER_PAGE = 6;             // 쪽당 6~8컷 = 사실 컷 + 여는·닫는 연출 컷 2개
    static final int MIN_SECTION_SENTENCES = 3;          // 이보다 짧은 섹션은 '다른 섹션에서도 고르기' 검사에서 뺀다
    static final int RESULT_SCORE = 2;                   // 이 점수 이상이면 결과 문장 (TextRules.resultScore)
    static final int MAX_TERMS = 8;                      // 처음에 번역을 정해 둘 핵심 용어 수
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

    public ComicPlan plan(String paperTitle, List<PaperSentence> sentences) {
        return plan(paperTitle, sentences, ProgressListener.NONE);
    }

    /** listener에는 AI 요청이 하나 끝날 때마다 (끝난 수, 전체 수)를 알린다. 전체 = 만화 제목 1 + 용어 1 + 조각 제목 n + 사실 n */
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
        log.info("만화 설계 시작: 본문 문장 {}개, {}쪽, 조각 {}개", bodySentences, roles.size(), chunks.size());

        Set<String> servers = new LinkedHashSet<>();
        int totalSteps = 2 + chunks.size() * 2;
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

        List<ComicPlan.Term> terms = pickTerms(paperTitle, abstractText(sections, byNumber), paperWords, servers);
        listener.update(++doneSteps, totalSteps);

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

        // ④ 핵심 사실 추출
        started = System.currentTimeMillis();
        Map<Integer, List<String>> usedEvidence = new HashMap<>();
        List<String> acceptedFacts = new ArrayList<>();
        List<ComicPlan.PagePlan> pages = new ArrayList<>();
        int dropped = 0;
        for (int page = 0; page < roles.size(); page++) {
            List<ComicPlan.ChunkPlan> chunkPlans = new ArrayList<>();
            List<ComicPlan.FactPlan> facts = new ArrayList<>();
            int firstIndex = page * CHUNKS_PER_PAGE;
            List<Chunk> pageChunks = chunks.subList(firstIndex, Math.min(chunks.size(), (page + 1) * CHUNKS_PER_PAGE));
            List<Integer> counts = factCounts(pageChunks, page == roles.size() - 1);
            for (int index = firstIndex; index < firstIndex + pageChunks.size(); index++) {
                Chunk chunk = chunks.get(index);
                TitleItem title = chunkTitles.get(index);
                int count = counts.get(index - firstIndex);
                boolean closing = count == CLOSING_SECTION_FACTS;

                Map<String, String> unsupported = new HashMap<>();  // 사실 확인에서 근거가 없다고 나온 사실 → 이유
                AiAsker.Answer<FactsAnswer> answer = pickFacts(chunk, title, count, closing, byNumber, paperWords, terms, unsupported);
                answer.attempts().forEach(attempt -> servers.add(attempt.server()));
                listener.update(++doneSteps, totalSteps);
                List<String> droppedHere = new ArrayList<>();
                for (FactItem item : answer.value().facts().stream().limit(count).toList()) {
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
            }
            PageRole role = roles.get(page);
            pages.add(new ComicPlan.PagePlan(page + 1, role.name(), role.meaning(), chunkPlans, facts));
        }
        long factMillis = System.currentTimeMillis() - started;
        log.info("만화 설계 끝: 조각 제목 {}ms, 핵심 사실 추출 {}ms, 버린 사실 {}개, 사용한 AI 서버 {}", titleMillis, factMillis, dropped, servers);

        return new ComicPlan(comicTitle.value().comicTitle(), pages,
                new ComicPlan.Stats(titleMillis, factMillis, List.copyOf(servers), dropped, titleProblems), terms);
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
     * 조각마다 고를 사실 수. 기본 2개, 논문 끝(마지막 쪽)의 한계·결론 조각 하나만 3개.
     * 쪽당 사실이 6개를 넘으면(연출 컷 2개와 합쳐 8컷 초과) 그 조각이 아닌 조각 중 가장 짧은 조각부터 1개씩 줄인다.
     * 두 번째 논문(176번)에서 결과 섹션 'Perceived Limitations'까지 한계 조각으로 보고 3개씩 뽑아 3쪽이 10컷이 됐다.
     */
    static List<Integer> factCounts(List<Chunk> pageChunks, boolean lastPage) {
        List<Integer> counts = new ArrayList<>(Collections.nCopies(pageChunks.size(), FACTS_PER_CHUNK));
        int closing = -1;
        for (int index = pageChunks.size() - 1; lastPage && index >= 0 && closing < 0; index--) {
            if (pageChunks.get(index).sections().stream().anyMatch(TextRules::isClosingSection)) {
                closing = index;
            }
        }
        if (closing >= 0) {
            counts.set(closing, CLOSING_SECTION_FACTS);
        }
        while (counts.stream().mapToInt(Integer::intValue).sum() > MAX_FACTS_PER_PAGE) {
            int target = -1;
            for (int index = 0; index < pageChunks.size(); index++) {
                if (index != closing && counts.get(index) > 1 && (target < 0 || size(pageChunks.get(index)) < size(pageChunks.get(target)))) {
                    target = index;
                }
            }
            if (target < 0) {
                break;
            }
            counts.set(target, counts.get(target) - 1);
        }
        return counts;
    }

    private static int size(Chunk chunk) {
        return chunk.end() - chunk.start() + 1;
    }

    private AiAsker.Answer<FactsAnswer> pickFacts(Chunk chunk, TitleItem title, int count, boolean closing,
                                                  Map<Integer, PaperSentence> byNumber, Set<String> paperWords,
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
                count, count));
        Function<FactsAnswer, List<String>> check = answer -> {
            List<String> problems = new ArrayList<>(checkFacts(answer, allowed, paperWords, terms));
            problems.addAll(spreadProblems(answer, allowed, bySection, byNumber));
            problems.addAll(verifyFacts(answer, allowed, byNumber, unsupported));
            return problems;
        };
        return aiAsker.ask(properties.factModel(), PlanPrompts.FACT_SYSTEM,
                PlanPrompts.FACTS.formatted(title.title(), title.summary(), String.join(", ", chunk.sections()), count,
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
     * 사실 확인: 고른 사실을 근거 문장과 다시 대조한다(AI 요청 1번). '틀림'으로 나온 사실은 다시 쓰게 하고,
     * unsupported에 적어 두어 다시 써도 남으면 버린다. 확인 자체가 깨지면(답이 모두 잘림 등) 작업을 멈추지 않고 건너뛴다.
     */
    private List<String> verifyFacts(FactsAnswer answer, Set<Integer> allowed, Map<Integer, PaperSentence> byNumber,
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
            List<Integer> evidence = fact.evidence().stream().filter(allowed::contains).toList();
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
            problems.add("%d번 사실은 근거 문장에 없는 내용을 담고 있다: %s. 근거 문장에 적힌 내용만 쓴다."
                    .formatted(index + 1, result.reason()));
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
