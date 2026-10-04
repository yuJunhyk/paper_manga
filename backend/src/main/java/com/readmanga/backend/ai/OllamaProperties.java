package com.readmanga.backend.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * application.properties의 ollama.* 설정.
 *
 * @param primaryUrl     먼저 쓰는 주 AI 서버 (예: GPU가 있는 데스크탑)
 * @param fallbackUrl    주 서버가 응답하지 않을 때 쓰는 예비 AI 서버 (예: 개발하는 노트북)
 * @param healthTimeout  서버가 살아 있는지 확인할 때 기다리는 시간
 * @param requestTimeout 대본 생성 요청 하나를 기다리는 최대 시간
 * @param contextLength  AI가 한 번에 읽을 수 있는 길이(토큰)
 * @param factModel      조각 제목·사실 고르기에 쓰는 모델
 * @param dialogueModel  이야기·컷 쓰기에 쓰는 모델
 */
@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(
        String primaryUrl,
        String fallbackUrl,
        Duration healthTimeout,
        Duration requestTimeout,
        int contextLength,
        String factModel,
        String dialogueModel
) {
}
