package com.readmanga.backend.paper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 서버가 시작될 때, 전에 돌던 서버가 끝내지 못한 작업(대기·처리 중)을 실패로 표시한다.
 * 작업은 서버 안의 스레드가 돌리므로 서버가 꺼지면 함께 사라지는데, DB에는 '처리 중'으로 영원히 남아 있었다(39·40번).
 * 테스트는 개발 서버와 같은 DB를 쓰므로, 테스트가 시작될 때는 돌리지 않는다(돌리면 실행 중인 서버의 작업을 실패로 바꾼다).
 */
@Component
public class StaleJobCleaner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaleJobCleaner.class);
    static final String MESSAGE = "서버가 재시작되어 처리가 중단되었습니다. 논문을 다시 올려 주세요.";

    private final PaperJobRepository paperJobRepository;
    private final boolean enabled;

    public StaleJobCleaner(PaperJobRepository paperJobRepository,
                           @Value("${readmanga.clean-stale-jobs-on-startup:true}") boolean enabled) {
        this.paperJobRepository = paperJobRepository;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (enabled) {
            cleanUp();
        }
    }

    /** 대기·처리 중인 작업을 모두 실패로 표시한다. */
    @Transactional
    public void cleanUp() {
        List<PaperJob> stale = paperJobRepository.findByStatusIn(List.of(JobStatus.PENDING, JobStatus.PROCESSING));
        stale.forEach(job -> job.fail(MESSAGE));
        if (!stale.isEmpty()) {
            log.warn("서버 재시작으로 중단된 작업 {}개를 실패로 표시했습니다: {}", stale.size(), stale.stream().map(PaperJob::getId).toList());
        }
    }
}
