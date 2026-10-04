package com.readmanga.backend.paper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * 논문 한 편을 만화로 만드는 작업.
 * 상태(status): 대기 → 처리 중 → 완료/실패. 처리 중일 때는 단계(stage)와 진행률(progressDone/progressTotal)을 함께 기록한다.
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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(length = TITLE_MAX_LENGTH)
    private String title;

    @Column(length = ERROR_MESSAGE_MAX_LENGTH)
    private String errorMessage;

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

    /** 다음 단계로 넘어간다. total은 그 단계의 전체 작업 수 (모르면 0). */
    public void moveTo(JobStage stage, int total) {
        this.stage = stage;
        this.progressDone = 0;
        this.progressTotal = total;
        touch();
    }

    public void progress(int done, int total) {
        this.progressDone = done;
        this.progressTotal = total;
        touch();
    }

    public void recordTitle(String title) {
        if (title != null && title.length() > TITLE_MAX_LENGTH) {
            title = title.substring(0, TITLE_MAX_LENGTH);
        }
        this.title = title;
        touch();
    }

    public void complete() {
        this.status = JobStatus.COMPLETED;
        this.stage = JobStage.DONE;
        this.progressDone = this.progressTotal;
        touch();
    }

    /** 실패. 어느 단계에서 실패했는지 알 수 있게 단계는 그대로 둔다. */
    public void fail(String errorMessage) {
        if (errorMessage != null && errorMessage.length() > ERROR_MESSAGE_MAX_LENGTH) {
            errorMessage = errorMessage.substring(0, ERROR_MESSAGE_MAX_LENGTH);
        }
        this.errorMessage = errorMessage;
        this.status = JobStatus.FAILED;
        touch();
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
}
