package com.readmanga.backend.paper;

import org.springframework.context.annotation.Import;
import com.readmanga.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 작업 취소(협력적 중단)를 실제 DB로 확인한다. 끝나면 되돌린다(롤백). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)  // 실제 DB 대신 일회용 테스트 DB
@Transactional
class PaperCancelTest {

    @Autowired
    PaperJobRepository paperJobRepository;

    @Autowired
    PaperService paperService;

    @Autowired
    JobProgress jobProgress;

    private PaperJob processingJob() {
        PaperJob job = new PaperJob("processing.pdf");
        job.start();
        job.moveTo(JobStage.WRITING, 29);
        return paperJobRepository.save(job);
    }

    @Test
    void 처리_중인_작업을_취소하면_취소됨이_되고_그_뒤의_진행_기록은_멈춘다() {
        Long id = processingJob().getId();

        JobResponse response = paperService.cancel(id);

        assertThat(response.status()).isEqualTo(JobStatus.CANCELLED);
        assertThatThrownBy(() -> jobProgress.update(id, 3, 29)).as("뒤에서 돌던 처리는 다음 진행 기록에서 멈춘다")
                .isInstanceOf(JobCancelledException.class);
        assertThat(jobProgress.isCancelled(id)).isTrue();
    }

    @Test
    void 취소된_작업은_실패로_덮어쓰지_않는다() {
        Long id = processingJob().getId();
        paperService.cancel(id);

        jobProgress.fail(id, "AI 서버 응답 없음");

        assertThat(paperJobRepository.findById(id).orElseThrow().getStatus()).isEqualTo(JobStatus.CANCELLED);
    }

    @Test
    void 대기_중인_작업을_취소하면_처리를_시작하지_않는다() {
        Long id = paperJobRepository.save(new PaperJob("pending.pdf")).getId();
        paperService.cancel(id);

        assertThatThrownBy(() -> jobProgress.start(id)).isInstanceOf(JobCancelledException.class);
    }

    @Test
    void 끝난_작업은_취소할_수_없고_없는_작업은_찾을_수_없다() {
        PaperJob done = new PaperJob("done.pdf");
        done.start();
        done.complete();
        Long id = paperJobRepository.save(done).getId();

        assertThatThrownBy(() -> paperService.cancel(id)).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> paperService.cancel(-1L)).isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
