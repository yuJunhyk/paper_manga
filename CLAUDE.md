# CLAUDE.md

## 프롬프트 처리 워크플로우

Before acting on any prompt/instruction the user gives in this project, first improve it, then execute:

1. Rewrite the original prompt into a structured prompt.
2. Apply modern prompt engineering techniques.
3. Only apply techniques with well-established, proven effectiveness (no speculative/untested techniques).
4. Review the result and repeat steps 1–3 until no further improvement is possible.
5. Present the improved prompt briefly, then proceed with the task based on it.

### 재작성을 생략하는 경우

짧고 뜻이 분명한 지시는 재작성 없이 바로 수행한다. 예:
- 승인·선택·짧은 답: "진행", "go", "남기고 푸쉬까지 진행", "A로 하자", "1, 2번 진행해줘"
- 한 가지 일만 시키는 명령: "업로드해줘", "결과 확인해줘", "서버 상태 봐줘"
- 바로 앞 답변에 대한 짧은 질문: "그게 무슨 뜻이야?", "왜 그래?"

여러 단계가 얽혀 있거나, 뜻이 모호하거나, 새로운 작업을 처음 시키는 지시는 위 워크플로우대로 재작성한다. 애매하면 재작성한다.

### 실행 규칙

- **1~4단계(프롬프트 재작성)는 Sonnet 모델에 위임한다.** Agent 도구를 `model: "sonnet"`, `run_in_background: false`로 호출해 개선된 프롬프트를 받아온 뒤, 5단계(제시)와 실제 작업 수행은 메인 모델이 담당한다. 결과를 기다려야 하므로 백그라운드 실행하지 않는다.
- 위임 시 서브에이전트에게 넘길 것: 사용자의 원본 지시 원문, 이 프로젝트의 맥락, 그리고 "개선된 프롬프트 본문만 반환하라"는 출력 형식 지시.
- 서브에이전트에게는 **프롬프트 재작성만** 시킨다. 파일 수정이나 조사 같은 실제 작업을 대신 수행하게 하지 않는다.

적용 시 참고 — 검증된 기법의 예: 역할 부여, 명시적 출력 형식 지정, 작업의 단계 분해, 제약 조건의 명시, 예시 제공(few-shot), 성공 기준 명시. 근거가 불확실한 실험적 기법(마법 문구, 보상 약속 등)은 쓰지 않는다.
