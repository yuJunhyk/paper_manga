package com.readmanga.backend.comic;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
public class ComicController {

    private static final MediaType MARKDOWN = new MediaType(MediaType.TEXT_MARKDOWN, StandardCharsets.UTF_8);

    private final ComicQuery comicQuery;

    public ComicController(ComicQuery comicQuery) {
        this.comicQuery = comicQuery;
    }

    /** 완성된 만화 대본. 컷마다 대사와 근거 원문이 들어 있다. */
    @GetMapping("/api/papers/{id}/comic")
    public ComicResponse comic(@PathVariable("id") Long id) {
        return comicQuery.find(id);
    }

    /**
     * 완성된 만화 대본을 Markdown 한 파일로. 근거 원문은 접혀 있다.
     * 오류(없음 404, 만드는 중 409)는 다른 API와 같은 Problem Details(JSON)로 나가도록 produces를 정하지 않고 응답에서 형식을 정한다.
     * nosniff: 브라우저가 내용을 보고 HTML로 짐작해 실행하지 않게 한다.
     */
    @GetMapping("/api/papers/{id}/comic/script")
    public ResponseEntity<String> script(@PathVariable("id") Long id) {
        ComicScript script = comicQuery.script(id);
        return ResponseEntity.ok()
                .contentType(MARKDOWN)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(script.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(script.markdown());
    }
}
