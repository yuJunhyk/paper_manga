package com.readmanga.backend.paper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 뒤에서 도는 작업의 단계와 진행률을 DB에 기록한다.
 * 매번 DB에서 작업을 새로 읽어 바꾸므로, 오래 걸리는 처리 중에 들고 있던 옛 작업 객체로 최신 기록을 덮어쓰는 일이 없다.
 * 진행을 기록할 때마다 취소 여부도 함께 확인된다(취소됐으면 PaperJob이 JobCancelledException을 던진다) — 협력적 중단.
 */
@Service
public class JobProgress {

    private final PaperJobRepository paperJobRepository;

    public JobProgress(PaperJobRepository paperJobRepository) {
        this.paperJobRepository = paperJobRepository;
    }

    /** 처리를 시작한다. 그 사이 취소됐으면 JobCancelledException. */
    @Transactional
    public void start(Long jobId) {
        PaperJob job = find(jobId);
        if (job.isCancelled()) {
            throw new JobCancelledException(jobId);
        }
        job.start();
    }

    /** 다시 시도를 시작한다. 그 사이 취소됐으면 JobCancelledException. */
    @Transactional
    public void resume(Long jobId, JobStage stage) {
        find(jobId).resume(stage);
    }

    @Transactional(readOnly = true)
    public boolean isCancelled(Long jobId) {
        return find(jobId).isCancelled();
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
