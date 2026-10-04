package com.readmanga.backend.comic;

import java.util.List;

/** 만화 대본 조회 응답. 사실 컷에는 근거 문장(번호·페이지·원문)이 함께 들어간다. */
public record ComicResponse(Long jobId, String title, String setting, String ending, List<PageResponse> pages) {

    public record PageResponse(int pageNumber, String role, String beat, List<PanelResponse> panels) {
    }

    public record PanelResponse(int order, String scene, boolean staging, String fact, List<LineResponse> lines,
                                List<EvidenceResponse> evidence) {
    }

    public record LineResponse(String speaker, String text) {
    }

    public record EvidenceResponse(int number, Integer page, String section, String text) {
    }

    static ComicResponse from(Long jobId, Comic comic) {
        List<PageResponse> pages = comic.getPages().stream().map(page -> new PageResponse(page.getPageNumber(), page.getRole(),
                page.getBeat(), page.getPanels().stream().map(panel -> new PanelResponse(panel.getPanelOrder(), panel.getScene(),
                        panel.isStaging(), panel.getFact(),
                        panel.getLines().stream().map(line -> new LineResponse(line.getSpeaker().getLabel(), line.getText())).toList(),
                        panel.getEvidence().stream().map(sentence -> new EvidenceResponse(sentence.getNumber(), sentence.getPage(),
                                sentence.getSectionTitle(), sentence.getText())).toList())).toList())).toList();
        return new ComicResponse(jobId, comic.getTitle(), comic.getSetting(), comic.getEnding(), pages);
    }
}
