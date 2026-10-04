package com.readmanga.backend.comic.plan;

import java.util.List;

/** 코드가 나눈 본문 조각 하나. 문장 번호 start~end를 다루고, 그 안에 들어 있는 섹션 이름들을 함께 기억한다. */
public record Chunk(int start, int end, List<String> sections) {

    public int size() {
        return end - start + 1;
    }
}
