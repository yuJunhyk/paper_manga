package com.readmanga.backend.paper;

/** 처리 중인 작업이 지금 어느 단계에 있는지. 상태(JobStatus)가 '처리 중'일 때 의미가 있다. */
public enum JobStage {
    EXTRACTING("논문 분석"),
    PLANNING("핵심 사실 추출"),
    WRITING("대본 쓰기"),
    DONE("완료");

    private final String label;

    JobStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
