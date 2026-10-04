package com.readmanga.backend.comic;

import java.util.Arrays;

public enum Speaker {
    NURI("누리"),
    DOCTOR("박사님"),
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
