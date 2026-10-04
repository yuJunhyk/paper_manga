package com.readmanga.backend.comic.plan;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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

    /** 표 조각에 많은 숫자·기호만으로 된 낱말. */
    private static final Pattern NUMERIC_TOKEN = Pattern.compile("^[\\d.,±×%()\\[\\]\\-–—↑↓/:;]+$");

    /** 한계·결론 섹션 (사실을 하나 더 뽑는다). */
    private static final Pattern CLOSING_SECTION = Pattern.compile("limitation|conclusion|discussion|future work",
            Pattern.CASE_INSENSITIVE);

    /** 한계 섹션 (대사가 단점을 뒤집지 않는지 더 살핀다). */
    private static final Pattern LIMITATION_SECTION = Pattern.compile("limitation", Pattern.CASE_INSENSITIVE);

    private static final Pattern NOT_WORD_CHAR = Pattern.compile("[^가-힣A-Za-z0-9]");

    private TextRules() {
    }

    public static boolean isAppendixHeading(String sectionTitle) {
        return sectionTitle != null && APPENDIX_HEADING.matcher(sectionTitle).find();
    }

    public static boolean isClosingSection(String sectionTitle) {
        return sectionTitle != null && CLOSING_SECTION.matcher(sectionTitle).find();
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

    public static boolean hasMixedScript(String text) {
        return !mixedScriptWords(text).isEmpty();
    }

    public static boolean hasFormula(String text) {
        return FORMULA.matcher(text).find();
    }

    public static String removeCitations(String text) {
        return CITATION.matcher(text).replaceAll("").trim();
    }

    /** '[88]의 모델'처럼 인용 번호가 남아 있는지. 지우기만 하면 '모델이의 모델'처럼 문법이 깨져서(2차 실행), 먼저 다시 쓰게 한다. */
    public static boolean hasCitation(String text) {
        return text != null && CITATION.matcher(text).find();
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
