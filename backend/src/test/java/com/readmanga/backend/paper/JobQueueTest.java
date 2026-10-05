package com.readmanga.backend.paper;

import com.readmanga.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

/**
 * 대기 줄: 대기 중인 작업의 순번과, 줄이 꽉 찼을 때의 거절(429).
 * 실제 처리는 가짜로 바꿔서(@MockitoBean) 올린 작업이 대기 상태에 머물게 한다. 대기 줄 길이는 3으로 둔다.
 */
@SpringBootTest(properties = "readmanga.jobs.queue-capacity=3")
@Import(TestcontainersConfiguration.class)  // 실제 DB 대신 일회용 테스트 DB
@Transactional
class JobQueueTest {

    @Autowired
    PaperService paperService;

    @Autowired
    PaperJobRepository paperJobRepository;

    @Autowired
    PaperSentenceRepository paperSentenceRepository;

    @MockitoBean
    PaperJobProcessor paperJobProcessor;

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void 대기_중인_작업은_줄_선_순서대로_순번을_받고_앞_작업이_빠지면_당겨진다() {
        JobResponse first = paperService.upload(pdf("a.pdf"));
        JobResponse second = paperService.upload(pdf("b.pdf"));
        JobResponse third = paperService.upload(pdf("c.pdf"));
        assertThat(List.of(first, second, third)).extracting(JobResponse::queuePosition).containsExactly(1, 2, 3);

        paperService.cancel(first.id());

        assertThat(paperService.getJob(first.id()).queuePosition()).as("취소된 작업은 줄에서 빠진다").isNull();
        assertThat(paperService.getJob(second.id()).queuePosition()).isEqualTo(1);
        assertThat(paperService.getJob(third.id()).queuePosition()).isEqualTo(2);
    }

    @Test
    void 다시_시도한_작업은_번호가_작아도_줄_맨_뒤에_선다() {
        PaperJob old = new PaperJob("old.pdf");
        old.fail("테스트용 실패");
        paperJobRepository.save(old);
        paperSentenceRepository.save(new PaperSentence(old, 1, "Abstract", 1, "A sentence."));  // 다시 시도하려면 저장된 문장이 있어야 한다
        JobResponse fresh = paperService.upload(pdf("new.pdf"));

        JobResponse retried = paperService.retry(old.getId());

        assertThat(retried.id()).isLessThan(fresh.id());
        assertThat(paperService.getJob(fresh.id()).queuePosition()).isEqualTo(1);
        assertThat(retried.queuePosition()).isEqualTo(2);
    }

    @Test
    void 대기_줄이_꽉_차면_작업을_만들지_않고_429로_돌려보낸다() {
        for (int i = 0; i < 3; i++) {
            paperService.upload(pdf("paper" + i + ".pdf"));
        }
        long jobs = paperJobRepository.count();

        assertThatThrownBy(() -> paperService.upload(pdf("one-more.pdf")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getReason()).contains("최대 3개");
                });
        assertThat(paperJobRepository.count()).as("거절한 업로드는 작업 기록을 남기지 않는다").isEqualTo(jobs);
    }

    @Test
    void 실행기가_줄_세우기를_거절하면_만든_작업은_이유를_적어_실패로_남기고_429() {
        willThrow(new TaskRejectedException("대기 줄 가득 참")).given(paperJobProcessor).process(any(), any(), any());

        assertThatThrownBy(() -> paperService.upload(pdf("x.pdf")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        PaperJob job = paperJobRepository.findAll().stream().max(Comparator.comparing(PaperJob::getId)).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("대기 줄이 꽉 차서");
    }
}
