package com.readmanga.backend.comic.plan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * 시험 단계(spikes/llm-spike)에서 정한 글자 검사 규칙들.
 * 숫자 기준은 시험 결과로 정했다 (plan.md 참고).
 */
public final class TextRules {

    /** 'A. Changelog', 'B Mini-...', 'D.6. Super-...'처럼 알파벳 한 글자 번호 뒤에 대문자 단어가 오는 섹션은 부록. */
    private static final Pattern APPENDIX_HEADING = Pattern.compile("^[A-Z](\\.\\d+)*\\.?\\s+[A-Z][a-z]");

    /** '라tent', '마asking'처럼 한글 바로 뒤에 영어가 붙은 단어. 'LDM은', 'GAN보다'는 통과. */
    private static final Pattern MIXED_SCRIPT_WORD = Pattern.compile("\\S*[가-힣][A-Za-z]\\S*");

    /** p(z|y), p(x) 같은 수식. 'LDM(ours)'는 통과. */
    private static final Pattern FORMULA = Pattern.compile("\\b[A-Za-z]\\s*\\([^()]*[|,][^()]*\\)|\\b[A-Za-z]\\([A-Za-z_]\\w*\\)");

    /** 사실 문장에 남은 [11], [62, 63] 같은 번호. */
    private static final Pattern CITATION = Pattern.compile("\\s*\\[\\d+(\\s*,\\s*\\d+)*]");
    /** 사실 끝에 붙인 근거 번호: "(239)", "(239, 240)", "(239번)" (4차 실행) */
    private static final Pattern PAREN_NUMBER = Pattern.compile("\\s*\\(\\s*\\d+(\\s*,\\s*\\d+)*\\s*번?\\s*\\)");
    /**
     * 표·그림 제목: "Table 2. Evaluation of …", "Comparison of a class-conditional ImageNet LDM with …".
     * 제목에는 결과가 없는데, 사실 고르기가 제목만 보고 실험 결과를 지어냈다(1차·4차 실행 181번).
     * 'Tab. 8 shows …'처럼 표를 인용하는 본문 문장은 제목이 아니다 (실제 논문 383문장으로 오탐 0 확인).
     */
    private static final Pattern CAPTION = Pattern.compile(
            "^(Table|Figure)\\s+\\d+\\.\\s+\\S|^(Comparison|Evaluation|Samples|Overview|Illustration|Visualization|Qualitative|Quantitative|Ablation)\\s+(of|on|for|from)\\b");

    /** 표 조각에 많은 숫자·기호만으로 된 낱말. */
    private static final Pattern NUMERIC_TOKEN = Pattern.compile("^[\\d.,±×%()\\[\\]\\-–—↑↓/:;]+$");

    /** 한계·결론 섹션 (사실을 하나 더 뽑는다). */
    private static final Pattern CLOSING_SECTION = Pattern.compile("limitation|conclusion|discussion|future work",
            Pattern.CASE_INSENSITIVE);

    /** 결론 섹션: 'Conclusion', 'Conclusions', 'Concluding remarks'. 이 뒤에 한계·결론이 아닌 섹션이 오면 부록으로 본다. */
    private static final Pattern CONCLUSION_SECTION = Pattern.compile("conclu", Pattern.CASE_INSENSITIVE);

    /** 초록·서론·결론: 본문 결과를 미리 또는 다시 요약하는 섹션. */
    private static final Pattern SUMMARY_SECTION = Pattern.compile("abstract|introduction|conclu", Pattern.CASE_INSENSITIVE);

    /** 결과 숫자: 소수점이 있는 수(28.4, 0.750)와 백분율(12%). 연도·개수 같은 정수는 겹쳐도 같은 결과라고 볼 수 없어 뺀다. */
    private static final Pattern RESULT_VALUE = Pattern.compile("\\d+\\.\\d+|\\d+(\\.\\d+)?\\s*%");

    /** 한계 섹션 (대사가 단점을 뒤집지 않는지 더 살핀다). */
    private static final Pattern LIMITATION_SECTION = Pattern.compile("limitation", Pattern.CASE_INSENSITIVE);

    private static final Pattern ENGLISH_WORD = Pattern.compile("[A-Za-z]{3,}");

    /** 결과를 말하는 문장의 신호: 비교·변화 낱말과 숫자 결과(0.750, 28.1%, 2.7×, p < 0.05). 특정 분야의 낱말은 넣지 않는다. */
    private static final Pattern RESULT_WORD = Pattern.compile(
            "\\b(significant\\w*|outperform\\w*|higher|lower|better|worse|improv\\w*|faster|slower|increas\\w*|decreas\\w*|reduc\\w*|gains?|than)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RESULT_NUMBER = Pattern.compile("\\d+(\\.\\d+)?\\s*(%|×)|\\b\\d*\\.\\d+|\\bp\\s*[<=]");

    /**
     * 영어 원문 자리에 한국어만 든 괄호: "윤리적 문제(윤리적 문제)", "시각 자료 이해 능력(시각 자료 이해력)" (180번).
     * 숫자·기호가 섞인 괄호("(중앙값 0.750)", "(N=54)")는 설명이라 잡지 않는다.
     */
    private static final Pattern KOREAN_ONLY_PARENS = Pattern.compile("\\(\\s*[가-힣]+(\\s+[가-힣]+)*\\s*\\)");

    private static final Pattern NOT_WORD_CHAR = Pattern.compile("[^가-힣A-Za-z0-9]");

    private TextRules() {
    }

    public static boolean isAppendixHeading(String sectionTitle) {
        return sectionTitle != null && APPENDIX_HEADING.matcher(sectionTitle).find();
    }

    public static boolean isClosingSection(String sectionTitle) {
        return sectionTitle != null && CLOSING_SECTION.matcher(sectionTitle).find();
    }

    public static boolean isConclusionSection(String sectionTitle) {
        return sectionTitle != null && CONCLUSION_SECTION.matcher(sectionTitle).find();
    }

    public static boolean isSummarySection(String sectionTitle) {
        return sectionTitle != null && SUMMARY_SECTION.matcher(sectionTitle).find();
    }

    /** 글에 나온 결과 숫자들 (띄어쓰기는 지운다: '12 %' → '12%'). */
    public static Set<String> resultValues(String text) {
        Set<String> values = new HashSet<>();
        RESULT_VALUE.matcher(text == null ? "" : text).results().forEach(match -> values.add(match.group().replace(" ", "")));
        return values;
    }

    public static boolean isLimitationSection(String sectionTitle) {
        return sectionTitle != null && LIMITATION_SECTION.matcher(sectionTitle).find();
    }

    /** 'CelebA-HQ 256 × 256 Method FID ↓ Prec.'처럼 숫자·기호가 30%를 넘거나 낱말이 4개 미만인 문장. */
    public static boolean isTableFragment(String text) {
        String[] tokens = text.trim().split("\\s+");
        if (tokens.length < 4) {
            return true;
        }
        long numeric = Arrays.stream(tokens).filter(token -> NUMERIC_TOKEN.matcher(token).matches()).count();
        return (double) numeric / tokens.length > 0.3;
    }

    /** 한글·영어가 섞인 단어들. 없으면 빈 문자열. */
    public static String mixedScriptWords(String text) {
        return MIXED_SCRIPT_WORD.matcher(text).results().map(match -> match.group()).reduce((a, b) -> a + ", " + b).orElse("");
    }

    /**
     * text에 나온 영어 낱말(3글자 이상) 중 known(소문자)에 없는 것들. 논문에 없는 용어를 지어냈는지 보는 데 쓴다.
     * 복수형은 같은 낱말로 본다 (논문은 'GANs', 대본은 'GAN'으로 쓰는 일이 흔하다).
     */
    public static List<String> unknownEnglishWords(String text, Set<String> known) {
        return ENGLISH_WORD.matcher(text == null ? "" : text).results().map(MatchResult::group)
                .filter(word -> !isKnown(word.toLowerCase(Locale.ROOT), known)).distinct().toList();
    }

    private static boolean isKnown(String word, Set<String> known) {
        return known.contains(word) || known.contains(word + "s")
                || (word.endsWith("s") && known.contains(word.substring(0, word.length() - 1)));
    }

    /** 논문 문장들에 나온 영어 낱말(소문자) */
    public static Set<String> englishWords(Collection<String> texts) {
        Set<String> words = new HashSet<>();
        texts.forEach(text -> ENGLISH_WORD.matcher(text).results().forEach(match -> words.add(match.group().toLowerCase(Locale.ROOT))));
        return words;
    }

    public static boolean hasMixedScript(String text) {
        return !mixedScriptWords(text).isEmpty();
    }

    public static boolean hasFormula(String text) {
        return FORMULA.matcher(text).find();
    }

    public static String removeCitations(String text) {
        return PAREN_NUMBER.matcher(CITATION.matcher(text).replaceAll("")).replaceAll("").trim();
    }

    /** 결과 문장다운 정도: 서로 다른 비교·변화 낱말 수 + 숫자 결과가 있으면 1. 2 이상이면 결과 문장으로 본다. */
    public static int resultScore(String sentence) {
        if (sentence == null) {
            return 0;
        }
        long words = RESULT_WORD.matcher(sentence).results().map(match -> match.group().toLowerCase(Locale.ROOT)).distinct().count();
        return (int) words + (RESULT_NUMBER.matcher(sentence).find() ? 1 : 0);
    }

    /**
     * 정한 번역 대신 다른 번역(variants)을 쓴 곳: "'데이터 코믹스' → '데이터 만화'".
     * 정한 번역 안에 든 말은 빼고 찾는다 (variant '만화'가 '데이터 만화' 안에 있어도 문제가 아니다).
     */
    public static List<String> termVariants(String text, List<ComicPlan.Term> terms) {
        List<String> found = new ArrayList<>();
        for (ComicPlan.Term term : terms) {
            String rest = text == null ? "" : text.replace(term.korean(), " ");
            term.variants().stream().filter(rest::contains).findFirst()
                    .ifPresent(variant -> found.add("'%s' → '%s'".formatted(variant, term.korean())));
        }
        return found;
    }

    /** 다른 번역을 쓴 곳을 다시 쓰게 하는 문제 문장. 없으면 빈 목록. */
    public static List<String> termVariantProblems(String label, String text, List<ComicPlan.Term> terms) {
        List<String> found = termVariants(text, terms);
        return found.isEmpty() ? List.of()
                : List.of("%s에 정한 번역과 다른 용어가 있다: %s. 용어는 정한 번역으로 통일한다.".formatted(label, String.join(", ", found)));
    }

    /** 영어 원문 자리에 한국어를 넣은 괄호를 다시 쓰게 하는 문제 문장. 없으면 빈 목록. */
    public static List<String> koreanParenthesisProblems(String label, String text) {
        List<String> found = KOREAN_ONLY_PARENS.matcher(text == null ? "" : text).results().map(MatchResult::group).distinct().toList();
        return found.isEmpty() ? List.of() : List.of("%s의 괄호에 영어 원문 대신 한국어가 들어 있다: %s. 괄호에는 논문의 영어 원문만 쓰고, 한국어 설명은 괄호 없이 문장으로 쓴다."
                .formatted(label, String.join(", ", found)));
    }

    public static boolean isCaption(String sentence) {
        return sentence != null && CAPTION.matcher(sentence.strip()).find();
    }

    /** '[88]의 모델'처럼 인용 번호가 남아 있는지. 지우기만 하면 '모델이의 모델'처럼 문법이 깨져서(2차 실행), 먼저 다시 쓰게 한다. */
    public static boolean hasCitation(String text) {
        return text != null && (CITATION.matcher(text).find() || PAREN_NUMBER.matcher(text).find());
    }

    /** 글자 두 개씩 묶은 조각이 얼마나 겹치는지(0~1). 비슷한 사실을 거르는 데 쓴다. */
    public static double similarity(String a, String b) {
        Set<String> first = bigrams(a);
        Set<String> second = bigrams(b);
        if (first.isEmpty() || second.isEmpty()) {
            return 0.0;
        }
        Set<String> union = new HashSet<>(first);
        union.addAll(second);
        Set<String> common = new HashSet<>(first);
        common.retainAll(second);
        return (double) common.size() / union.size();
    }

    private static Set<String> bigrams(String text) {
        String cleaned = NOT_WORD_CHAR.matcher(text).replaceAll("");
        Set<String> result = new HashSet<>();
        for (int i = 0; i + 1 < cleaned.length(); i++) {
            result.add(cleaned.substring(i, i + 2));
        }
        return result;
    }
}
