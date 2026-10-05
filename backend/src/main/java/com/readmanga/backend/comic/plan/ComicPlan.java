package com.readmanga.backend.comic.plan;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 만화 설계도: 쪽(기승전결) → 조각(제목·요약·문장 범위) → 사실(근거 문장 번호).
 * 5단계에서 이 설계도를 보고 이야기와 컷을 쓴다.
 */
public record ComicPlan(String comicTitle, List<PagePlan> pages, Stats stats, List<Term> terms) {

    public ComicPlan(String comicTitle, List<PagePlan> pages, Stats stats) {
        this(comicTitle, pages, stats, List.of());
    }

    public record PagePlan(int pageNumber, String role, String roleMeaning, List<ChunkPlan> chunks, List<FactPlan> facts) {
    }

    public record ChunkPlan(int start, int end, List<String> sections, String title, String summary,
                            List<String> droppedFacts, List<String> problems) {
    }

    public record FactPlan(String fact, List<Integer> evidence, String chunkTitle) {
    }

    /**
     * 만화 전체에서 하나로 통일할 용어 번역. variants는 쓰지 않을 다른 번역
     * (176·178번에서 data comics를 '데이터 만화'와 '데이터 코믹스'로 섞어 씀).
     */
    public record Term(String english, String korean, List<String> variants) {
    }

    public String termGuide() {
        return termGuide(terms);
    }

    /** 지시문에 붙일 용어 번역 목록. 정한 용어가 없으면 빈 글자. */
    public static String termGuide(List<Term> terms) {
        if (terms.isEmpty()) {
            return "";
        }
        return "\n용어 번역 (만화 전체에서 이 번역으로 통일한다):\n" + terms.stream()
                .map(term -> "- %s → %s%s".formatted(term.english(), term.korean(),
                        term.variants().isEmpty() ? "" : " (쓰지 않을 말: " + String.join(", ", term.variants()) + ")"))
                .collect(Collectors.joining("\n"));
    }

    /** 단계별로 걸린 시간(밀리초), 사용한 AI 서버, 버린 사실 수. */
    public record Stats(long titleMillis, long factMillis, List<String> servers, int droppedFacts, List<String> titleProblems) {
    }
}
