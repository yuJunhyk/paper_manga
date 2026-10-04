package com.readmanga.backend.ai;

/** AI 서버 하나의 상태. available이 false면 error에 이유가 들어간다. */
public record ServerStatus(OllamaServer server, String label, String url, boolean available, String version, String error) {
}
