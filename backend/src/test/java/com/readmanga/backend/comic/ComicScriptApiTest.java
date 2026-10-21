package com.readmanga.backend.comic;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/** 대본 내보내기 API의 응답 형식. Controller만 띄우고 조회는 가짜로 바꾼다 */
@WebMvcTest(ComicController.class)
class ComicScriptApiTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    ComicQuery comicQuery;

    @Test
    void 완성된_대본은_Markdown_파일로_내려준다() {
        given(comicQuery.script(206L)).willReturn(new ComicScript("206_High-Resolution-Image-Synthesis.md", "# 잠재 공간의 마법!\n"));

        assertThat(mvc.get().uri("/api/papers/206/comic/script")).hasStatusOk()
                .hasContentType("text/markdown;charset=UTF-8")
                .hasHeader("X-Content-Type-Options", "nosniff")
                .hasBodyTextEqualTo("# 잠재 공간의 마법!\n")
                .headers().satisfies(headers -> assertThat(headers.getFirst(HttpHeaders.CONTENT_DISPOSITION))
                        .startsWith("inline;").contains("206_High-Resolution-Image-Synthesis.md"));
    }

    @Test
    void 아직_만드는_중이면_409를_다른_API와_같은_오류_모양으로_준다() {
        given(comicQuery.script(5L)).willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "아직 만화를 만드는 중입니다: 대본 쓰기"));

        assertThat(mvc.get().uri("/api/papers/5/comic/script")).hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").isEqualTo("아직 만화를 만드는 중입니다: 대본 쓰기");
    }

    @Test
    void 없는_작업은_404다() {
        given(comicQuery.script(999L)).willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다: 999"));

        assertThat(mvc.get().uri("/api/papers/999/comic/script")).hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").isEqualTo("작업을 찾을 수 없습니다: 999");
    }

    @Test
    void Markdown만_받겠다고_해도_오류는_JSON으로_나간다() {
        given(comicQuery.script(5L)).willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "아직 만화를 만드는 중입니다: 대본 쓰기"));

        assertThat(mvc.get().uri("/api/papers/5/comic/script").accept(MediaType.TEXT_MARKDOWN)).hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").isEqualTo("아직 만화를 만드는 중입니다: 대본 쓰기");
    }
}
