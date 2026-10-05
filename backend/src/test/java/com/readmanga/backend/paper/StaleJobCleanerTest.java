package com.readmanga.backend.paper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** 서버가 시작될 때 전 서버가 남긴 '대기·처리 중' 작업을 실패로 표시하는지 실제 DB로 확인한다. 끝나면 되돌린다(롤백). */
@SpringBootTest
@Transactional
class StaleJobCleanerTest {

    @Autowired
    PaperJobRepository paperJobRepository;

    @Autowired
    StaleJobCleaner staleJobCleaner;

    @Test
    void 대기_처리_중_작업만_재시작_실패로_표시하고_완료된_작업은_건드리지_않는다() {
        PaperJob pending = paperJobRepository.save(new PaperJob("pending.pdf"));
        PaperJob processing = new PaperJob("processing.pdf");
        processing.start();
        processing.moveTo(JobStage.WRITING, 29);
        paperJobRepository.save(processing);
        PaperJob completed = new PaperJob("done.pdf");
        completed.start();
        completed.complete();
        paperJobRepository.save(completed);

        staleJobCleaner.cleanUp();

        assertThat(paperJobRepository.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.FAILED);
        PaperJob failed = paperJobRepository.findById(processing.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.getStage()).as("어느 단계에서 끊겼는지는 남긴다").isEqualTo(JobStage.WRITING);
        assertThat(failed.getErrorMessage()).isEqualTo(StaleJobCleaner.MESSAGE);
        assertThat(paperJobRepository.findById(completed.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.COMPLETED);
    }
}
