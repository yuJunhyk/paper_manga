package com.readmanga.backend.common;

import com.readmanga.backend.comic.ComicController;
import com.readmanga.backend.comic.ComicQuery;
import com.readmanga.backend.paper.JobResponse;
import com.readmanga.backend.paper.JobStatus;
import com.readmanga.backend.paper.PaperController;
import com.readmanga.backend.paper.PaperService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * API 오류 응답이 한 가지 모양(RFC 9457 Problem Details)으로 나가는지 확인한다.
 * Controller만 띄우고(@WebMvcTest) 서비스는 가짜(@MockitoBean)로 바꿔서, DB·AI 없이 빠르게 돈다.
 *
 * 오류 응답 모양 (Content-Type: application/problem+json):
 * {"type":"about:blank","title":"Not Found","status":404,"detail":"작업을 찾을 수 없습니다: 999","instance":"/api/papers/999"}
 */
@WebMvcTest({PaperController.class, ComicController.class})
class ApiErrorTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    PaperService paperService;

    @MockitoBean
    ComicQuery comicQuery;

    @Test
    void 서비스가_정한_오류는_상태_코드와_한국어_설명을_그대로_돌려준다() {
        given(paperService.getJob(999L)).willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다: 999"));

        assertThat(mvc.get().uri("/api/papers/999")).hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().satisfies(json -> {
                    json.assertThat().extractingPath("$.status").isEqualTo(404);
                    json.assertThat().extractingPath("$.detail").isEqualTo("작업을 찾을 수 없습니다: 999");
                    json.assertThat().extractingPath("$.instance").isEqualTo("/api/papers/999");
                });
    }

    @Test
    void 아직_만드는_중인_만화는_409와_지금_단계를_알려_준다() {
        given(comicQuery.find(5L)).willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "아직 만화를 만드는 중입니다: 대본 쓰기"));

        assertThat(mvc.get().uri("/api/papers/5/comic")).hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").isEqualTo("아직 만화를 만드는 중입니다: 대본 쓰기");
    }

    @Test
    void 숫자_자리에_글자를_넣으면_400과_어느_값이_틀렸는지_알려_준다() {
        assertThat(mvc.get().uri("/api/papers/abc")).hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").isEqualTo("'id' 값 'abc'의 형식이 올바르지 않습니다.");
    }

    @Test
    void 파일_없이_업로드하면_서버_고장_500이_아니라_400이다() {
        assertThat(mvc.post().uri("/api/papers")).hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").isEqualTo("PDF 파일을 'file'이라는 이름으로 올려 주세요.");
    }

    @Test
    void PDF가_아니거나_빈_파일은_작업을_만들지_않고_400이다() {
        MockMultipartFile text = new MockMultipartFile("file", "paper.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile empty = new MockMultipartFile("file", "paper.pdf", "application/pdf", new byte[0]);

        assertThat(mvc.post().uri("/api/papers").multipart().file(text)).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").isEqualTo("PDF 파일만 올릴 수 있습니다.");
        assertThat(mvc.post().uri("/api/papers").multipart().file(empty)).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").isEqualTo("빈 파일입니다. PDF 파일을 올려 주세요.");
    }

    @Test
    void PDF는_받아서_작업을_만들고_202를_돌려준다() {
        given(paperService.upload(any())).willReturn(new JobResponse(7L, "paper.pdf", null, JobStatus.PENDING, null, 0, 0, null, 1, null, null));
        MockMultipartFile pdf = new MockMultipartFile("file", "paper.pdf", "application/pdf", "%PDF-1.7 ...".getBytes(StandardCharsets.US_ASCII));

        assertThat(mvc.post().uri("/api/papers").multipart().file(pdf)).hasStatus(HttpStatus.ACCEPTED)
                .bodyJson().extractingPath("$.id").isEqualTo(7);
    }

    @Test
    void 예상하지_못한_오류는_500과_일반_안내만_주고_내부_정보는_숨긴다() {
        given(paperService.getJob(1L)).willThrow(new IllegalStateException("db-host=10.0.0.5 password=secret 연결 실패"));

        assertThat(mvc.get().uri("/api/papers/1")).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyText().contains("서버 내부 오류가 발생했습니다. 잠시 뒤 다시 시도해 주세요.")
                .doesNotContain("password").doesNotContain("10.0.0.5");
    }
}
