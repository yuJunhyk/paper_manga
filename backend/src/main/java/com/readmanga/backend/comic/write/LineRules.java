package com.readmanga.backend.comic.write;

import com.readmanga.backend.comic.Speaker;
import com.readmanga.backend.comic.plan.TextRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 대사 정리와 검사 규칙. 시험 단계에서 실제로 나온 실수들을 잡는다. */
public final class LineRules {

    static final int MAX_LINE_LENGTH = 80;
    static final int MIN_SCENE_LENGTH = 8;
    static final int MIN_DUPLICATE_LINE_LENGTH = 10;  // '와!' 같은 짧은 감탄사는 반복해도 문제로 보지 않는다

    /** 대사 안에 괄호로 들어간 해설: "(해설: 잠재 공간은 …)" */
    private static final Pattern NARRATION_IN_LINE = Pattern.compile("\\(\\s*해설\\s*[:：]\\s*(.+?)\\s*\\)");
    /** 괄호째 지울 번호 표시: (F5), (p.3), (p. 3, 122), (23) */
    private static final Pattern MARK_IN_PARENS = Pattern.compile("\\(\\s*(F\\d+|p\\.\\s*\\d+[^)]*|\\d+(\\s*,\\s*\\d+)*)\\s*\\)");
    /** 그냥 지울 번호 표시: [23], [47, 75], F1 */
    private static final Pattern MARK = Pattern.compile("\\[\\d+(\\s*,\\s*\\d+)*]|\\(p\\.\\s*\\d+[^)]*\\)|\\bF\\d+\\b");
    private static final Pattern EMPTY_PARENS = Pattern.compile("\\(\\s*\\)");
    private static final Pattern SENTENCE = Pattern.compile("[^.!?…]+[.!?…]*");
    private static final Pattern POLITE_ENDING = Pattern.compile("(요|니다|세요)[.!?~…\\s]*$");
    /** 해설이 인물 말투나 존댓말로 끝남 (해설은 '~이다', '~했다'로 끝나야 한다). 2차 실행에서 '~됩니다'가 통과해 '니다'를 추가 */
    private static final Pattern SPEECH_ENDING = Pattern.compile("(야|지|단다|요|어|거든|네|니다|세요)[.!?~…]*$");
    private static final String QUOTE_MARKS = "\"“”'‘’";
    /**
     * 장면 칸에 섞여 들어온 대사·서식 (실제 실행 1차 7컷, 2차 2컷, 3차 3컷). 특정 문구를 막으면 옆으로 새서, 넓게 잡는다:
     * 서식·구조 기호(*, {, #, [ 등), 큰따옴표(대사 인용), 쌍점("누리:", "질문한다:"), 웹 문서 줄바꿈 찌꺼기("br/"),
     * 참고 설명, 응답 칸 이름("lines에는 대사만 포함한다.")
     */
    private static final Pattern SCENE_INTRUSION = Pattern.compile(
            "[*{}\\[\\]<>#`|\\\\_\"“”:：]|(?i:\\bbr\\s*/)|\\(\\s*참고|\\b(lines|scene|speaker|text|JSON)\\b");
    /** 대사 속 괄호 행동 설명: "(눈을 반짝이며) 와, 그렇군요!" (3차 실행) */
    private static final Pattern ACTION_IN_PARENS = Pattern.compile("\\(\\s*[가-힣 ,]{2,30}(며|고|서|채로?|듯이?|면서)\\s*\\)");
    /** 대사 앞 목록 기호와 끝에 남은 쉼표: "- 확산 모델이…", "-- 맞아! …거야.," (3차 실행) */
    private static final Pattern LEADING_BULLET = Pattern.compile("^[-–—•·]+\\s*");
    private static final Pattern TRAILING_COMMA = Pattern.compile("\\s*[,，]+$");
    private static final double SIMILAR_SCENE = 0.5;  // 바로 앞 컷 장면과 이만큼 비슷하면 다시 쓴다 (2차 실행 1쪽 2~7컷이 거의 같았다)
    private static final double SIMILAR_LINE = 0.4;   // 바로 뒤 컷 대사와 이만큼 비슷하면 다시 쓴다 (187~189번 정상 대사끼리는 0.34 이하)
    /** 대사 안의 행동 설명: "… 거야. *박사님은 미소 지으며 설명한다.*" */
    private static final Pattern STAGE_DIRECTION = Pattern.compile("\\*+[^*]*\\*+");
    /** 한계를 뒤집는 말: "처음엔 느릴 수 있지만, 결국엔 훨씬 빠르게 결과를 낼 수 있어" */
    private static final Pattern REVERSAL = Pattern.compile("결국|오히려|그래도");

    private LineRules() {
    }

    private static final String STUDENT = Speaker.NURI.getLabel();
    private static final String DOCTOR = Speaker.DOCTOR.getLabel();

    public record Line(String speaker, String text) {
    }

    /** 대사 정리: (해설: …)는 해설 줄로 꺼내고, *행동 설명*·(괄호 행동 설명)·번호 표시·빈 괄호·따옴표·앞 기호를 지우고, 줄바꿈을 띄어쓰기로 바꾼다. */
    public static List<Line> tidy(List<Line> lines) {
        List<Line> result = new ArrayList<>();
        for (Line line : lines) {
            List<String> narrations = new ArrayList<>();
            Matcher matcher = NARRATION_IN_LINE.matcher(line.text());
            while (matcher.find()) {
                narrations.add(clean(matcher.group(1)));
            }
            String text = NARRATION_IN_LINE.matcher(line.text()).replaceAll("");
            text = STAGE_DIRECTION.matcher(text).replaceAll("").replace("*", "");
            text = ACTION_IN_PARENS.matcher(text).replaceAll("");
            text = MARK_IN_PARENS.matcher(text).replaceAll("");
            text = MARK.matcher(text).replaceAll("");
            text = clean(EMPTY_PARENS.matcher(text).replaceAll(""));
            text = clean(TRAILING_COMMA.matcher(LEADING_BULLET.matcher(text).replaceFirst("")).replaceFirst(""));
            if (!text.isEmpty()) {
                result.add(new Line(line.speaker(), text));
            }
            narrations.stream().filter(narration -> !narration.isEmpty()).forEach(narration -> result.add(new Line("해설", narration)));
        }
        return result;
    }

    private static String clean(String text) {
        String collapsed = String.join(" ", text.trim().split("\\s+"));
        int start = 0;
        int end = collapsed.length();
        while (start < end && QUOTE_MARKS.indexOf(collapsed.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && QUOTE_MARKS.indexOf(collapsed.charAt(end - 1)) >= 0) {
            end--;
        }
        return collapsed.substring(start, end).trim();
    }

    /** 컷 하나의 문제 목록. seenLines는 같은 쪽에서 이미 쓴 대사들, previousScene은 바로 앞 컷의 장면(없으면 null). */
    public static List<String> problems(String scene, List<Line> lines, Set<String> seenLines, String previousScene) {
        List<String> problems = new ArrayList<>(problems(scene, lines, seenLines));
        if (scene != null && previousScene != null && TextRules.similarity(scene, previousScene) >= SIMILAR_SCENE) {
            problems.add("바로 앞 컷과 장면이 거의 같다: \"%s\". 장소나 인물의 행동·표정 중 하나 이상을 바꿔 다른 장면으로 써라."
                    .formatted(shorten(scene)));
        }
        return problems;
    }

    /**
     * 여는 컷이 바로 뒤 첫 사실 컷을 베꼈는지. 여는 컷은 사실 컷을 다 쓴 뒤에 쓰고 첫 사실 컷을 보고 이어 쓰게 했더니,
     * 189번에서 장면을 그대로 옮겨 썼다(장면 유사도 0.81·1.0·0.95, 1쪽 대사 "너무 시간이 오래 걸려요"/"너무 느려요" 0.48).
     */
    public static List<String> nextPanelProblems(String scene, List<Line> lines, String nextScene, List<Line> nextLines) {
        List<String> problems = new ArrayList<>();
        if (scene != null && nextScene != null && TextRules.similarity(scene, nextScene) >= SIMILAR_SCENE) {
            problems.add("바로 뒤 첫 사실 컷과 장면이 거의 같다: \"%s\". 이 컷은 그 앞 장면이므로 장소나 인물의 행동·표정을 다르게 쓴다."
                    .formatted(shorten(scene)));
        }
        problems.addAll(similarLineProblems(lines, nextLines, "바로 뒤 첫 사실 컷의 대사", "그 대사로 이어질 다른 말을 쓴다"));
        return problems;
    }

    /**
     * 바로 앞 대사를 되풀이했는지. 앞 대사를 넘겨 이어 쓰게 했더니, 190번 2쪽 첫 사실 컷이 앞 쪽 마지막 대사
     * "다른 방법으로는 이 문장을 어떻게 이해할 수 있을까요?"를 그대로 옮겨 썼다(쪽마다 따로 세는 '같은 대사' 검사에 걸리지 않음).
     */
    public static List<String> previousLineProblems(List<Line> lines, List<Line> linesBefore) {
        return similarLineProblems(lines, linesBefore, "바로 앞 대사", "그 말에 대한 반응이나 다음 이야기를 쓴다");
    }

    private static List<String> similarLineProblems(List<Line> lines, List<Line> others, String label, String instead) {
        List<String> problems = new ArrayList<>();
        for (Line line : lines) {
            others.stream().filter(other -> TextRules.similarity(line.text(), other.text()) >= SIMILAR_LINE).findFirst()
                    .ifPresent(other -> problems.add("%s \"%s\"와 거의 같은 말이다: \"%s\". 같은 말을 되풀이하지 말고 %s."
                            .formatted(label, shorten(other.text()), shorten(line.text()), instead)));
        }
        return problems;
    }

    /** 컷 하나의 문제 목록. seenLines는 같은 쪽에서 이미 쓴 대사들. */
    public static List<String> problems(String scene, List<Line> lines, Set<String> seenLines) {
        List<String> problems = new ArrayList<>();
        if (scene == null || scene.strip().length() < MIN_SCENE_LENGTH) {
            problems.add("장면 설명이 너무 짧다: \"%s\". 장소, 행동, 표정을 구체적으로 써라.".formatted(scene));
        } else {
            Matcher intrusion = SCENE_INTRUSION.matcher(scene);
            if (intrusion.find()) {
                // 응답 칸 이름(scene, lines)을 쓰면 AI가 그 말을 장면에 따라 적는다(2차 실행). 그래서 '장면 칸'이라고만 부른다
                problems.add("장면 칸에 대사나 기호가 섞여 있다: \"%s\". 장면 칸에는 장소, 인물의 행동과 표정만 한국어 문장으로 쓴다. 따옴표로 인용한 대사, '질문한다:' 같은 쌍점, 기호(*, {, #), 참고 설명, 영어 칸 이름은 쓰지 않는다."
                        .formatted(shorten(scene.substring(intrusion.start()))));
            }
        }
        if (lines.stream().allMatch(line -> clean(line.text()).isEmpty())) {
            // 대사를 장면 칸에 넣어 버리면 대사 칸이 빈다(3차 실행: 사실 컷 1개, 연출 컷 2개)
            problems.add("대사가 하나도 없다. 인물이 하는 말은 장면 칸이 아니라 대사 칸에 1개 이상 쓴다.");
        }
        for (Line line : lines) {
            if (ACTION_IN_PARENS.matcher(line.text()).find()) {
                problems.add("대사에 괄호로 행동 설명이 들어 있다: \"%s\". 인물의 행동은 장면 칸에 쓰고, 대사에는 말만 쓴다.".formatted(shorten(line.text())));
            }
            if (line.text().contains("*")) {
                problems.add("대사에 '*' 꾸밈 기호가 들어 있다: \"%s\". 인물의 행동은 장면 칸에 쓰고, 대사에는 말만 쓴다.".formatted(shorten(line.text())));
            }
            String text = clean(NARRATION_IN_LINE.matcher(line.text()).replaceAll(""));
            if (MARK.matcher(text).find() || MARK_IN_PARENS.matcher(text).find()) {
                problems.add("대사에 번호 표시가 들어 있다: \"%s\"".formatted(shorten(text)));
            }
            if (text.contains("학습 만화")) {
                problems.add("대사에 '학습 만화'라는 말이 들어 있다.");
            }
            if (text.length() > MAX_LINE_LENGTH) {
                problems.add("대사가 너무 길다(%d자): \"%s\". 60자 안팎으로 줄여라.".formatted(text.length(), shorten(text)));
            }
            if (DOCTOR.equals(line.speaker()) && SENTENCE.matcher(text).results()
                    .anyMatch(sentence -> POLITE_ENDING.matcher(sentence.group().strip()).find())) {
                problems.add("%s가 존댓말을 썼다: \"%s\". %s는 %s에게 반말을 쓴다.".formatted(DOCTOR, shorten(text), DOCTOR, STUDENT));
            }
            if ("해설".equals(line.speaker()) && text.contains(STUDENT + "야")) {
                problems.add("해설이 %s에게 말을 걸었다: \"%s\". 해설은 설명문으로 쓴다.".formatted(STUDENT, shorten(text)));
            }
            if ("해설".equals(line.speaker()) && SPEECH_ENDING.matcher(text).find()) {
                problems.add("해설이 인물 말투나 존댓말로 끝난다: \"%s\". 해설은 '~이다', '~했다'로 끝나는 설명문으로 써라.".formatted(shorten(text)));
            }
            if (text.length() >= MIN_DUPLICATE_LINE_LENGTH && seenLines.contains(text)) {
                problems.add("앞 컷과 같은 대사다: \"%s\"".formatted(shorten(text)));
            }
            String mixed = TextRules.mixedScriptWords(text);
            if (!mixed.isEmpty()) {
                problems.add("대사에 한글과 영어가 한 단어 안에 섞여 있다: %s. %s".formatted(mixed, WritePrompts.TITLE_RULE));
            }
        }
        return problems;
    }

    /**
     * 한계 사실 컷의 추가 검사: 단점을 '결국엔 더 빨라' 같은 말로 뒤집지 않았는지.
     * 낱말로만 보므로 정상 대사가 걸릴 수 있지만, 그때는 다시 쓰기를 한 번 더 할 뿐이다.
     */
    /**
     * 학생이 박사를 부르며 묻는 대사로 시작하는 컷인지: "냥박사님, …인가요?".
     * 컷마다 이 모양만 되풀이되면 묻고 답하기의 나열이 되어 이야기가 이어지지 않는다(186번: 사실 컷 22개 중 20개).
     */
    public static boolean isAddressedQuestion(List<Line> lines) {
        return lines.stream().filter(line -> STUDENT.equals(line.speaker())).findFirst()
                .map(line -> line.text().strip())
                .map(text -> text.startsWith(DOCTOR) && text.endsWith("?"))
                .orElse(false);
    }

    /** 앞 컷도 같은 모양의 질문으로 시작했으면 다시 쓰게 한다. */
    public static List<String> repeatedQuestionProblems(List<Line> lines, boolean previousWasQuestion) {
        return previousWasQuestion && isAddressedQuestion(lines)
                ? List.of("앞 컷도 '%s님, …?' 질문으로 시작했다. 바로 앞 대사에 이어서, 감탄·시도·발견처럼 다른 방식으로 시작한다.".formatted(DOCTOR))
                : List.of();
    }

    public static List<String> limitationProblems(List<Line> lines) {
        return lines.stream().filter(line -> REVERSAL.matcher(line.text()).find())
                .map(line -> "이 컷의 사실은 연구의 한계인데 대사가 단점을 뒤집는다: \"%s\". 단점을 부정하거나 장점으로 바꾸지 말고 그대로 인정한다."
                        .formatted(shorten(line.text())))
                .toList();
    }

    /**
     * 장면 정리: 다시 써도 대사나 기호가 남아 있으면 그 앞까지만 장면으로 쓴다.
     * 앞부분이 너무 짧게 남으면 기호와 칸 이름만 지운다.
     */
    public static String tidyScene(String scene) {
        if (scene == null) {
            return "";
        }
        Matcher intrusion = SCENE_INTRUSION.matcher(scene);
        if (!intrusion.find()) {
            return clean(scene);
        }
        String before = clean(scene.substring(0, intrusion.start()));
        int lastStop = before.lastIndexOf('.');
        if (lastStop + 1 >= MIN_SCENE_LENGTH) {
            before = before.substring(0, lastStop + 1);  // "…기다리고 있다. 누리는 눈빛을 집중시키며 질문한다" → 끝난 문장까지만
        }
        return before.length() >= MIN_SCENE_LENGTH ? before : clean(SCENE_INTRUSION.matcher(scene).replaceAll(" "));
    }

    private static String shorten(String text) {
        return text.length() > 40 ? text.substring(0, 40) + "…" : text;
    }
}
