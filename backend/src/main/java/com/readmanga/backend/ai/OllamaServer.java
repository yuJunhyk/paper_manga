package com.readmanga.backend.ai;

public enum OllamaServer {
    PRIMARY("주 서버"),
    FALLBACK("예비 서버");

    private final String label;

    OllamaServer(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
