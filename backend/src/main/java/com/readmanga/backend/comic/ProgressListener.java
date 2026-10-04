package com.readmanga.backend.comic;

/** 오래 걸리는 작업이 "전체 total개 중 done개 끝났다"고 알리는 약속. */
@FunctionalInterface
public interface ProgressListener {

    ProgressListener NONE = (done, total) -> {
    };

    void update(int done, int total);
}
