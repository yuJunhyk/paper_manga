package com.readmanga.backend.ai;

/**
 * AI의 답.
 *
 * @param content      AI가 쓴 내용 (정해 둔 JSON 모양의 글자)
 * @param server       답한 서버 (주 서버 또는 예비 서버)
 * @param promptTokens AI가 읽은 토큰 수
 * @param outputTokens AI가 쓴 토큰 수
 * @param millis       걸린 시간(밀리초)
 * @param truncated    출력 길이 상한에 걸려 답이 중간에 잘렸는지 (같은 말을 반복하는 고리에 빠졌을 때 주로 생긴다)
 */
public record ChatResult(String content, OllamaServer server, Integer promptTokens, Integer outputTokens, long millis,
                         boolean truncated) {

    public ChatResult(String content, OllamaServer server, Integer promptTokens, Integer outputTokens, long millis) {
        this(content, server, promptTokens, outputTokens, millis, false);
    }
}
