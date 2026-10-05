package com.readmanga.backend.paper;

/** 취소된 작업을 더 진행하려 할 때. 파이프라인은 이 예외로 하던 일을 멈춘다. */
public class JobCancelledException extends RuntimeException {

    public JobCancelledException(Long jobId) {
        super("작업 " + jobId + "은(는) 취소되었습니다");
    }
}
