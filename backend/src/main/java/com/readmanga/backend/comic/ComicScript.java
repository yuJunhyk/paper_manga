package com.readmanga.backend.comic;

import com.readmanga.backend.comic.ComicResponse.EvidenceResponse;
import com.readmanga.backend.comic.ComicResponse.LineResponse;
import com.readmanga.backend.comic.ComicResponse.PageResponse;
import com.readmanga.backend.comic.ComicResponse.PanelResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 완성된 만화 대본을 Markdown 한 파일로 바꾼다. 사실 컷의 근거 원문은 &lt;details&gt;로 접어서, 누르면 펼쳐진다.
 *
 * 논문 원문과 AI가 쓴 글이 그대로 들어가므로, 글 안의 HTML이 실행되거나 문서 모양을 깨지 않게
 * &amp; &lt; &gt;를 바꾸고 줄바꿈은 공백 하나로 합친다(줄 앞에 '#'이나 '&lt;/details&gt;'가 와서 새 블록이 되는 것을 막음).
 */
public record ComicScript(String fileName, String markdown) {

    /** 파일 이름에 남길 글자: 영어·숫자·한글·하이픈. 나머지는 하이픈 하나로 바꾼다 */
    private static final Pattern FILE_NAME_UNSAFE = Pattern.compile("[^A-Za-z0-9가-힣-]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final int FILE_NAME_TITLE_LIMIT = 100;

    public static ComicScript of(ComicResponse comic, String paperTitle) {
        return new ComicScript(fileName(comic.jobId(), paperTitle), markdown(comic));
    }

    /** 결과 파일 이름 규칙: 작업번호_논문제목.md (제목이 없으면 작업번호_comic-script.md) */
    static String fileName(Long jobId, String paperTitle) {
        String title = paperTitle == null ? "" : FILE_NAME_UNSAFE.matcher(paperTitle).replaceAll("-").replaceAll("^-+|-+$", "");
        if (title.length() > FILE_NAME_TITLE_LIMIT) {
            title = title.substring(0, FILE_NAME_TITLE_LIMIT).replaceAll("-+$", "");
        }
        return jobId + "_" + (title.isEmpty() ? "comic-script" : title) + ".md";
    }

    static String markdown(ComicResponse comic) {
        List<String> lines = new ArrayList<>();
        lines.add("# " + text(comic.title()));
        lines.add("");
        lines.add("- 설정: " + text(comic.setting()));
        lines.add("- 결말: " + text(comic.ending()));
        for (PageResponse page : comic.pages()) {
            lines.add("");
            lines.add("## %d쪽 (%s) — %s".formatted(page.pageNumber(), text(page.role()), text(page.beat())));
            for (PanelResponse panel : page.panels()) {
                lines.add("");
                lines.add("### %d컷 (%s)".formatted(panel.order(), panel.staging() ? "연출" : "사실"));
                lines.add("");
                lines.add("장면: " + text(panel.scene()));
                lines.add("");
                for (LineResponse line : panel.lines()) {
                    lines.add("- **%s**: %s".formatted(text(line.speaker()), text(line.text())));
                }
                if (!panel.evidence().isEmpty()) {
                    lines.addAll(evidence(panel.evidence()));
                }
            }
        }
        return String.join("\n", lines) + "\n";
    }

    /** 근거 원문 접기. &lt;summary&gt; 다음 빈 줄이 있어야 안쪽 Markdown(인용문)이 그려진다 */
    private static List<String> evidence(List<EvidenceResponse> evidence) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("<details>");
        lines.add("<summary>근거 원문 (" + evidence.stream().map(ComicScript::where).collect(Collectors.joining(", ")) + ")</summary>");
        for (EvidenceResponse sentence : evidence) {
            lines.add("");
            String section = sentence.section() == null ? "" : " · " + text(sentence.section());
            lines.add("> **%s**%s<br>%s".formatted(where(sentence), section, text(sentence.text())));
        }
        lines.add("");
        lines.add("</details>");
        return lines;
    }

    private static String where(EvidenceResponse sentence) {
        return (sentence.page() == null ? "" : sentence.page() + "쪽 ") + sentence.number() + "번";
    }

    /** 글 하나를 한 줄 안전한 글로: HTML 특수 문자를 바꾸고 줄바꿈을 공백으로 */
    static String text(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return WHITESPACE.matcher(escaped).replaceAll(" ").strip();
    }
}
