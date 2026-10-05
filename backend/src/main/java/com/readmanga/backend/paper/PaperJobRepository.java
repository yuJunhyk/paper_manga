package com.readmanga.backend.paper;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PaperJobRepository extends JpaRepository<PaperJob, Long> {

    List<PaperJob> findByStatusIn(Collection<JobStatus> statuses);

    long countByStatus(JobStatus status);

    /**
     * 나보다 먼저 줄 선 대기 작업 수. 줄 선 시각은 updatedAt이다: 대기 중에는 아무도 작업을 고치지 않고,
     * 새로 올릴 때(생성)와 다시 시도할 때(resetForRetry) updatedAt이 그 순간으로 정해진다. 같은 시각이면 번호가 작은 쪽이 먼저.
     * 자기 자신은 빼고 센다 (Java 시각과 DB에 저장된 시각의 정밀도가 달라 자기가 앞에 있는 것처럼 보일 수 있다).
     */
    @Query("""
            select count(j) from PaperJob j
            where j.status = :pending and j.id <> :id
              and (j.updatedAt < :queuedAt or (j.updatedAt = :queuedAt and j.id < :id))""")
    long countAhead(@Param("pending") JobStatus pending, @Param("queuedAt") LocalDateTime queuedAt, @Param("id") Long id);

    /**
     * 대기·처리 중인 작업만 취소로 바꾼다. 확인과 변경을 UPDATE 한 번으로 해서(원자적) 끝난 작업을 취소하는 일이 없다.
     * 버전도 올려, 그 사이 작업을 읽어 둔 처리 쪽의 저장은 낙관적 락으로 실패하게 한다. 바뀐 행 수(0 또는 1)를 돌려준다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PaperJob j set j.status = :cancelled, j.version = j.version + 1, j.updatedAt = :now
            where j.id = :id and j.status in :active""")
    int cancelIfActive(@Param("id") Long id, @Param("cancelled") JobStatus cancelled,
                       @Param("active") Collection<JobStatus> active, @Param("now") LocalDateTime now);

    /**
     * 실패·취소된 작업만 대기로 되돌린다(다시 시도). 확인과 변경이 UPDATE 한 번이라 두 번 눌러도 한 번만 시작된다.
     * 이 메서드 안에서 바로 커밋해야, 뒤이어 시작하는 비동기 처리가 바뀐 상태를 읽는다.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PaperJob j set j.status = :pending, j.stage = :stage, j.progressDone = 0, j.progressTotal = 0,
                j.errorMessage = null, j.version = j.version + 1, j.updatedAt = :now
            where j.id = :id and j.status in :retryable""")
    int resetForRetry(@Param("id") Long id, @Param("pending") JobStatus pending, @Param("stage") JobStage stage,
                      @Param("retryable") Collection<JobStatus> retryable, @Param("now") LocalDateTime now);
}
