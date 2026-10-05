package com.readmanga.backend.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * application.properties의 ollama.* 설정.
 *
 * @param primaryUrl            먼저 쓰는 주 AI 서버 (예: GPU가 있는 데스크탑)
 * @param fallbackUrl           주 서버가 응답하지 않을 때 쓰는 예비 AI 서버 (예: 개발하는 노트북)
 * @param healthTimeout         서버가 살아 있는지 확인할 때 기다리는 시간
 * @param requestTimeout        주 서버에서 요청 하나를 기다리는 최대 시간. 관측된 최장 요청(1분 미만)보다 넉넉하되, 멎은 서버에 오래 묶이지 않게 짧게
 * @param fallbackRequestTimeout 예비 서버에서 요청 하나를 기다리는 최대 시간. 예비 서버(노트북)는 몇 배 느려서 주 서버와 같은 기준이면 멀쩡한데도 실패로 본다
 * @param failureCooldown       요청이 시간 초과로 실패한 서버를 건너뛰는 시간 (살아 있다고는 답하지만 생성은 멎은 서버 대비)
 * @param reconnectCooldown     연결이 끊겨 실패한 서버를 건너뛰는 시간 (재시작 중인 서버는 금방 돌아오므로 짧게)
 * @param reconnectWait         쓸 수 있는 서버가 하나도 없을 때 바로 실패하지 않고 다시 연결되기를 기다리는 최대 시간
 * @param contextLength         핵심 사실 추출 모델이 한 번에 읽을 수 있는 길이(토큰)
 * @param dialogueContextLength 대본 쓰기 모델이 한 번에 읽을 수 있는 길이(토큰). 길수록 GPU 메모리를 더 쓴다(8GB 카드에서 16384는 넘쳤다)
 * @param maxOutputTokens       답 하나의 최대 길이(토큰). 작은 모델이 같은 말을 반복하는 고리에 빠지면 생성이 끝나지 않아서 상한을 둔다
 * @param repeatPenalty         최근에 쓴 글자를 다시 쓰기 어렵게 하는 정도(1.0이면 끔). 지금 Ollama는 기본이 1.0이라 반복 고리를 막지 못했다
 * @param factModel             조각 제목·핵심 사실 추출에 쓰는 모델
 * @param dialogueModel         이야기·컷 쓰기에 쓰는 모델
 */
@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(
        String primaryUrl,
        String fallbackUrl,
        Duration healthTimeout,
        Duration requestTimeout,
        Duration fallbackRequestTimeout,
        Duration failureCooldown,
        Duration reconnectCooldown,
        Duration reconnectWait,
        int contextLength,
        int dialogueContextLength,
        int maxOutputTokens,
        double repeatPenalty,
        String factModel,
        String dialogueModel
) {
}
