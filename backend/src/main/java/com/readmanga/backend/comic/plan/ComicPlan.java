package com.readmanga.backend.comic.plan;

import java.util.List;

/**
 * 만화 설계도: 쪽(기승전결) → 조각(제목·요약·문장 범위) → 사실(근거 문장 번호).
 * 5단계에서 이 설계도를 보고 이야기와 컷을 쓴다.
 */
public record ComicPlan(String comicTitle, List<PagePlan> pages, Stats stats) {

    public record PagePlan(int pageNumber, String role, String roleMeaning, List<ChunkPlan> chunks, List<FactPlan> facts) {
    }

    public record ChunkPlan(int start, int end, List<String> sections, String title, String summary,
                            List<String> droppedFacts, List<String> problems) {
    }

    public record FactPlan(String fact, List<Integer> evidence, String chunkTitle) {
    }

    /** 단계별로 걸린 시간(밀리초), 사용한 AI 서버, 버린 사실 수. */
    public record Stats(long titleMillis, long factMillis, List<String> servers, int droppedFacts, List<String> titleProblems) {
    }
}
