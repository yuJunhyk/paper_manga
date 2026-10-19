package com.readmanga.backend.comic;

import java.util.Arrays;

/**
 * 대사의 화자. 상수 이름(NURI, DOCTOR)은 DB에 저장되는 값이라 그대로 두고, 보이는 이름(label)만 바꾼다.
 * 2026-10-06: 누리·박사님 → 콩이(흰 햄스터 학생)·냥박사(안경 쓴 고양이 박사). 예전 만화도 새 이름으로 보인다.
 */
public enum Speaker {
    NURI("콩이"),
    DOCTOR("냥박사"),
    NARRATION("해설");

    private final String label;

    Speaker(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Speaker fromLabel(String label) {
        return Arrays.stream(values())
                .filter(speaker -> speaker.label.equals(label))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 화자: " + label));
    }
}
