package com.readmanga.backend.paper;

/** 작업 상태: 대기 → 처리 중 → 완료 / 실패 / 취소됨 */
public enum JobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}
