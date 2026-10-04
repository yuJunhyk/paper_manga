"""로컬 AI 모델(Ollama)로 논문 만화 대본을 만들어 보는 실험 스크립트.

사용법:
    python3 spikes/llm-spike/run_spike.py qwen3:8b
    python3 spikes/llm-spike/run_spike.py exaone3.5:7.8b
    python3 spikes/llm-spike/run_spike.py qwen3:8b --dry-run   # AI 호출 없이 입력 크기만 확인

입력: read_manga/results/2026-10-02_grobid-api.json (Spring 서버가 만든 제목·섹션·문장·페이지 JSON)
출력: spikes/llm-spike/results/<모델이름>.json
"""

import json
import sys
import time
import urllib.request
from pathlib import Path

OLLAMA_URL = "http://localhost:11434/api/chat"
CONTEXT_LENGTH = 32768
SCRIPT_DIR = Path(__file__).resolve().parent
PAPER_JSON = SCRIPT_DIR.parents[1] / "results" / "2026-10-02_grobid-api.json"
RESULT_DIR = SCRIPT_DIR / "results"

SYSTEM_PROMPT = (
    "너는 연구 논문을 처음 읽는 사람을 위해 교육용 만화 대본을 쓰는 작가다. "
    "논문에 실제로 적힌 내용만 사용하고, 논문에 없는 사실이나 숫자는 지어내지 않는다."
)

INSTRUCTIONS = """아래 논문을 4~8컷짜리 한국어 만화 대본으로 만들어라.

규칙:
1. 논문의 흐름(어떤 문제가 있었나 → 어떻게 해결했나 → 결과가 어땠나 → 왜 중요한가)이 드러나게 컷을 나눈다. 컷 수는 내용에 맞게 4~8개 사이에서 정한다.
2. 각 컷에는 장면 설명(scene), 등장인물의 대사(dialogue), 근거 문장 번호(evidence)를 넣는다.
3. 대사는 논문을 모르는 사람도 이해할 수 있게 쉬운 한국어로 1~2문장만 쓴다.
4. evidence에는 그 컷 내용의 근거가 되는 문장 번호를 1~3개 넣는다. 반드시 아래 목록에 있는 번호만 쓴다.

논문 제목: {title}

논문 본문 (형식: [문장 번호] (p.페이지) 문장):
{body}
"""

RESPONSE_SCHEMA = {
    "type": "object",
    "properties": {
        "title_ko": {"type": "string"},
        "cuts": {
            "type": "array",
            "minItems": 4,
            "maxItems": 8,
            "items": {
                "type": "object",
                "properties": {
                    "scene": {"type": "string"},
                    "dialogue": {"type": "string"},
                    "evidence": {
                        "type": "array",
                        "minItems": 1,
                        "maxItems": 3,
                        "items": {"type": "integer"},
                    },
                },
                "required": ["scene", "dialogue", "evidence"],
            },
        },
    },
    "required": ["title_ko", "cuts"],
}


def number_sentences(paper):
    """문장마다 1부터 번호를 붙이고, 프롬프트용 본문과 번호 → 문장 표를 만든다."""
    lines = []
    sentences = {}
    number = 0
    for section in paper["sections"]:
        lines.append(f"\n## {section['title'] or '(제목 없음)'}")
        for sentence in section["sentences"]:
            number += 1
            sentences[number] = sentence
            lines.append(f"[{number}] (p.{sentence['page']}) {sentence['text']}")
    return "\n".join(lines), sentences


def call_ollama(model, prompt):
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": prompt},
        ],
        "format": RESPONSE_SCHEMA,
        "stream": False,
        "options": {"num_ctx": CONTEXT_LENGTH, "temperature": 0.2},
    }
    if model.startswith("qwen3"):
        body["think"] = False

    request = urllib.request.Request(
        OLLAMA_URL,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=1800) as response:
        return json.loads(response.read())


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    model = sys.argv[1]
    dry_run = "--dry-run" in sys.argv

    paper = json.loads(PAPER_JSON.read_text(encoding="utf-8"))
    body, sentences = number_sentences(paper)
    prompt = INSTRUCTIONS.format(title=paper["title"], body=body)

    print(f"모델: {model}")
    print(f"논문: {paper['title']}")
    print(
        f"문장 수: {len(sentences)}, 프롬프트 글자 수: {len(prompt):,} (토큰은 대략 글자 수 ÷ 4)"
    )
    if dry_run:
        print(prompt[:1500])
        return

    print("AI가 대본을 만드는 중입니다. 몇 분 걸릴 수 있습니다...")
    started = time.time()
    response = call_ollama(model, prompt)
    elapsed = time.time() - started

    script = json.loads(response["message"]["content"])
    invalid = [
        n for cut in script["cuts"] for n in cut["evidence"] if n not in sentences
    ]

    print(f"\n걸린 시간: {elapsed:.0f}초")
    print(
        f"AI가 읽은 토큰: {response.get('prompt_eval_count')} / 최대 {CONTEXT_LENGTH}"
    )
    print(f"AI가 쓴 토큰: {response.get('eval_count')}")
    print(f"없는 근거 번호: {invalid if invalid else '없음'}")
    print(f"\n제목: {script['title_ko']}")
    for index, cut in enumerate(script["cuts"], start=1):
        print(f"\n[컷 {index}]")
        print(f"  장면: {cut['scene']}")
        print(f"  대사: {cut['dialogue']}")
        for n in cut["evidence"]:
            sentence = sentences.get(n)
            if sentence:
                print(f"  근거 [{n}] (p.{sentence['page']}) {sentence['text'][:120]}")
            else:
                print(f"  근거 [{n}] ← 목록에 없는 번호")

    RESULT_DIR.mkdir(exist_ok=True)
    output = RESULT_DIR / f"{model.replace(':', '_')}.json"
    output.write_text(
        json.dumps(
            {
                "model": model,
                "elapsed_seconds": round(elapsed),
                "prompt_tokens": response.get("prompt_eval_count"),
                "output_tokens": response.get("eval_count"),
                "invalid_evidence": invalid,
                "script": script,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"\n결과 저장: {output}")


if __name__ == "__main__":
    main()
