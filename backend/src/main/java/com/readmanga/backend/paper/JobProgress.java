package com.readmanga.backend.paper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 뒤에서 도는 작업의 단계와 진행률을 DB에 기록한다.
 * 매번 DB에서 작업을 새로 읽어 바꾸므로, 오래 걸리는 처리 중에 들고 있던 옛 작업 객체로 최신 기록을 덮어쓰는 일이 없다.
 */
@Service
public class JobProgress {

    private final PaperJobRepository paperJobRepository;

    public JobProgress(PaperJobRepository paperJobRepository) {
        this.paperJobRepository = paperJobRepository;
    }

    @Transactional
    public void moveTo(Long jobId, JobStage stage, int total) {
        find(jobId).moveTo(stage, total);
    }

    @Transactional
    public void update(Long jobId, int done, int total) {
        find(jobId).progress(done, total);
    }

    @Transactional
    public void fail(Long jobId, String errorMessage) {
        find(jobId).fail(errorMessage);
    }

    private PaperJob find(Long jobId) {
        return paperJobRepository.findById(jobId).orElseThrow();
    }
}
