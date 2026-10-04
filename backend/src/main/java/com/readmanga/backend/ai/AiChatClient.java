package com.readmanga.backend.ai;

import java.util.List;
import java.util.Map;

/**
 * "AI에게 대화를 보내고, 정해 둔 JSON 모양으로 답을 받는다"는 약속.
 * 만화를 만드는 코드는 이 약속만 알고, 실제로 어느 AI 서버를 쓰는지는 모른다.
 * 그래서 테스트에서는 진짜 AI 대신 가짜를 끼워 넣을 수 있다.
 */
public interface AiChatClient {

    ChatResult chat(String model, List<ChatMessage> messages, Map<String, Object> schema);
}
