package com.readmanga.backend.comic.write;

import java.util.List;

/**
 * AI가 쓴 만화 대본 (아직 DB에 저장하기 전).
 * 사실 컷의 근거(evidence)는 AI가 아니라 설계도의 사실에서 코드가 옮겨 붙인 문장 번호다.
 */
public record WrittenComic(String title, String setting, String ending, List<Page> pages, long millis, List<String> servers) {

    public record Page(int pageNumber, String role, String beat, List<Panel> panels) {
    }

    public record Panel(String scene, boolean staging, String fact, List<LineRules.Line> lines, List<Integer> evidence,
                        List<String> problems) {
    }
}
