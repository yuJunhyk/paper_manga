package com.readmanga.backend.paper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 실패·취소된 작업 다시 시도를 실제 DB로 확인한다. 끝나면 되돌린다(롤백).
 * 실제 AI를 부르지 않도록 비동기 처리기는 가짜(mock)로 바꾼다.
 */
@SpringBootTest
@Transactional
class PaperRetryTest {

    @Autowired
    PaperJobRepository paperJobRepository;

    @Autowired
    PaperSentenceRepository paperSentenceRepository;

    @Autowired
    PaperService paperService;

    @Autowired
    JobProgress jobProgress;

    @MockitoBean
    PaperJobProcessor paperJobProcessor;

    /** 논문 문장은 저장된 채로 대본 쓰기 중에 실패한 작업 */
    private Long failedJobWithSentences() {
        PaperJob job = new PaperJob("failed.pdf");
        job.start();
        job.moveTo(JobStage.WRITING, 30);
        job.fail("주 서버와 예비 서버가 모두 응답하지 않습니다");
        paperJobRepository.save(job);
        paperSentenceRepository.save(new PaperSentence(job, 1, "Introduction", 1, "Diffusion models are slow."));
        return job.getId();
    }

    @Test
    void 실패한_작업을_다시_시도하면_대기로_바뀌고_처리를_다시_시작한다() {
        Long id = failedJobWithSentences();

        JobResponse response = paperService.retry(id);

        assertThat(response.status()).isEqualTo(JobStatus.PENDING);
        assertThat(response.errorMessage()).isNull();
        assertThat(response.stage()).as("접수 순간부터 다시 시작할 단계와 0으로 보인다").isEqualTo(JobStage.PLANNING.getLabel());
        assertThat(response.progressDone()).isZero();
        verify(paperJobProcessor).retry(id);
    }

    @Test
    void 다시_시작하면_핵심_사실_추출_단계부터_처리_중이_된다() {
        Long id = failedJobWithSentences();
        paperService.retry(id);

        jobProgress.resume(id, JobStage.PLANNING);

        PaperJob job = paperJobRepository.findById(id).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);
        assertThat(job.getStage()).isEqualTo(JobStage.PLANNING);
        assertThat(job.getProgressDone()).isZero();
    }

    @Test
    void 취소된_작업도_다시_시도할_수_있다() {
        PaperJob job = new PaperJob("cancelled.pdf");
        job.start();
        paperJobRepository.save(job);
        paperSentenceRepository.save(new PaperSentence(job, 1, "Introduction", 1, "Text."));
        paperService.cancel(job.getId());

        assertThat(paperService.retry(job.getId()).status()).isEqualTo(JobStatus.PENDING);
    }

    @Test
    void 처리_중이거나_문장이_없는_작업은_다시_시도할_수_없다() {
        PaperJob processing = new PaperJob("processing.pdf");
        processing.start();
        paperJobRepository.save(processing);
        paperSentenceRepository.save(new PaperSentence(processing, 1, "Introduction", 1, "Text."));
        PaperJob noSentences = new PaperJob("broken.pdf");
        noSentences.start();
        noSentences.fail("GROBID 오류");
        paperJobRepository.save(noSentences);

        assertThatThrownBy(() -> paperService.retry(processing.getId())).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> paperService.retry(noSentences.getId())).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(paperJobProcessor, never()).retry(anyLong());
    }
}
