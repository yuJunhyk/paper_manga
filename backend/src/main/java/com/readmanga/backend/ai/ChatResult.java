package com.readmanga.backend.ai;

/**
 * AI의 답.
 *
 * @param content      AI가 쓴 내용 (정해 둔 JSON 모양의 글자)
 * @param server       답한 서버 (주 서버 또는 예비 서버)
 * @param promptTokens AI가 읽은 토큰 수
 * @param outputTokens AI가 쓴 토큰 수
 * @param millis       걸린 시간(밀리초)
 */
public record ChatResult(String content, OllamaServer server, Integer promptTokens, Integer outputTokens, long millis) {
}
