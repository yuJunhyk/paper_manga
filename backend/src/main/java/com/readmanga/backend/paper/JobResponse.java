package com.readmanga.backend.paper;

import java.time.LocalDateTime;

/**
 * 작업 조회 응답. 처리 중이면 stage(논문 분석 / 사실 고르기 / 대본 쓰기)와 진행률(progressDone/progressTotal)을 함께 본다.
 */
public record JobResponse(
        Long id,
        String fileName,
        String title,
        JobStatus status,
        String stage,
        int progressDone,
        int progressTotal,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static JobResponse from(PaperJob job) {
        return new JobResponse(
                job.getId(),
                job.getFileName(),
                job.getTitle(),
                job.getStatus(),
                job.getStage() == null ? null : job.getStage().getLabel(),
                job.getProgressDone(),
                job.getProgressTotal(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
