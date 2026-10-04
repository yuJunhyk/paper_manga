package com.readmanga.backend.ai;

/** AI에게 보내는 대화 한 줄. role은 system(역할 설명), user(요청), assistant(AI의 이전 답) 중 하나. */
public record ChatMessage(String role, String content) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
