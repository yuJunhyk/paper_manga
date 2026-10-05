package com.readmanga.backend.paper;

import java.time.LocalDateTime;

/**
 * 작업 조회 응답. 처리 중이면 stage(논문 분석 / 핵심 사실 추출 / 대본 쓰기)와 진행률(progressDone/progressTotal)을 함께 본다.
 * 대기 중이면 queuePosition에 지금 순번이 들어간다(1이면 다음 차례, 앞 작업이 끝날수록 줄어든다). 대기가 아니면 null.
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
        Integer queuePosition,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static JobResponse from(PaperJob job) {
        return from(job, null);
    }

    public static JobResponse from(PaperJob job, Integer queuePosition) {
        return new JobResponse(
                job.getId(),
                job.getFileName(),
                job.getTitle(),
                job.getStatus(),
                job.getStage() == null ? null : job.getStage().getLabel(),
                job.getProgressDone(),
                job.getProgressTotal(),
                job.getErrorMessage(),
                queuePosition,
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
