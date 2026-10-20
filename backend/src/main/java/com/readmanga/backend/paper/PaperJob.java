package com.readmanga.backend.paper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * 논문 한 편을 만화로 만드는 작업.
 * 상태(status): 대기 → 처리 중 → 완료/실패/취소됨. 처리 중일 때는 단계(stage)와 진행률(progressDone/progressTotal)을 함께 기록한다.
 * 취소된 작업은 더 진행할 수 없다(진행 기록·단계 이동·완료가 JobCancelledException을 던진다).
 */
@Entity
public class PaperJob {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;
    private static final int TITLE_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private JobStage stage;

    /** 이미 있는 작업 행에도 칸을 추가할 수 있게 기본값 0을 둔다 (빈 값 불가 칸은 기존 행 때문에 추가가 실패한다) */
    @ColumnDefault("0")
    private int progressDone;

    @ColumnDefault("0")
    private int progressTotal;

    /**
     * 낙관적 락(optimistic lock): 저장할 때마다 1씩 오른다. 읽은 뒤 다른 쪽이 먼저 바꿨으면 저장이 실패한다.
     * 뒤에서 도는 처리가 진행률을 쓰는 사이에 취소가 들어와도, 처리가 취소를 덮어쓰지 못하게 한다.
     */
    @Version
    @ColumnDefault("0")
    private long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(length = TITLE_MAX_LENGTH)
    private String title;

    @Column(length = ERROR_MESSAGE_MAX_LENGTH)
    private String errorMessage;

    /** 핵심 사실 추출 결과(만화 설계도, ComicPlan의 JSON). 대본 쓰기에서 멈춘 작업은 다시 시도할 때 이것으로 대본 쓰기부터 한다 */
    @Column(columnDefinition = "text")
    private String planJson;

    protected PaperJob() {
    }

    public PaperJob(String fileName) {
        this.fileName = fileName;
        this.status = JobStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void start() {
        this.status = JobStatus.PROCESSING;
        moveTo(JobStage.EXTRACTING, 0);
    }

    /** 다시 시도: 저장된 결과를 살려 stage 단계부터 다시 처리한다. 핵심 사실 추출부터 하면 예전 설계도는 버린다. */
    public void resume(JobStage stage) {
        ensureNotCancelled();
        this.status = JobStatus.PROCESSING;
        this.errorMessage = null;
        if (stage == JobStage.PLANNING) {
            this.planJson = null;
        }
        moveTo(stage, 0);
    }

    /** 핵심 사실 추출이 끝났다: 설계도를 저장하고 대본 쓰기로 넘어간다. */
    public void finishPlanning(String planJson) {
        ensureNotCancelled();
        this.planJson = planJson;
        moveTo(JobStage.WRITING, 0);
    }

    /** 다음 단계로 넘어간다. total은 그 단계의 전체 작업 수 (모르면 0). */
    public void moveTo(JobStage stage, int total) {
        ensureNotCancelled();
        this.stage = stage;
        this.progressDone = 0;
        this.progressTotal = total;
        touch();
    }

    public void progress(int done, int total) {
        ensureNotCancelled();
        this.progressDone = done;
        this.progressTotal = total;
        touch();
    }

    public void recordTitle(String title) {
        ensureNotCancelled();
        if (title != null && title.length() > TITLE_MAX_LENGTH) {
            title = title.substring(0, TITLE_MAX_LENGTH);
        }
        this.title = title;
        touch();
    }

    public void complete() {
        ensureNotCancelled();
        this.status = JobStatus.COMPLETED;
        this.stage = JobStage.DONE;
        this.progressDone = this.progressTotal;
        touch();
    }

    /** 실패. 어느 단계에서 실패했는지 알 수 있게 단계는 그대로 둔다. 이미 취소된 작업은 취소 상태로 둔다. */
    public void fail(String errorMessage) {
        if (isCancelled()) {
            return;
        }
        if (errorMessage != null && errorMessage.length() > ERROR_MESSAGE_MAX_LENGTH) {
            errorMessage = errorMessage.substring(0, ERROR_MESSAGE_MAX_LENGTH);
        }
        this.errorMessage = errorMessage;
        this.status = JobStatus.FAILED;
        touch();
    }

    public boolean isCancelled() {
        return status == JobStatus.CANCELLED;
    }

    private void ensureNotCancelled() {
        if (isCancelled()) {
            throw new JobCancelledException(id);
        }
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public JobStatus getStatus() {
        return status;
    }

    public JobStage getStage() {
        return stage;
    }

    public int getProgressDone() {
        return progressDone;
    }

    public int getProgressTotal() {
        return progressTotal;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getTitle() {
        return title;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getPlanJson() {
        return planJson;
    }

    public boolean hasPlan() {
        return planJson != null;
    }
}
