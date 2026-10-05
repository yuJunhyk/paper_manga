package com.readmanga.backend.ai;

import java.time.Instant;

/**
 * AI 서버 하나의 상태. available이 false면 error에 이유가 들어간다.
 * pausedUntil이 있으면 최근 요청 실패로 그 시각까지 건너뛰는 중이다.
 */
public record ServerStatus(OllamaServer server, String label, String url, boolean available, String version, String error,
                           Instant pausedUntil) {
}
