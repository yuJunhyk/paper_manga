"""로컬 AI 모델(Ollama)로 Why책 같은 학습 만화 대본(3~4쪽, 쪽당 6~8컷)을 만들어 보는 실험 스크립트.

두 단계로 만든다.
  1단계 콘티: 코드가 섹션 경계를 따라 본문을 쪽 수만큼 나누고, AI는 쪽마다 주제만 정한다.
  2단계 쪽 생성: 쪽마다 그 범위의 문장만 넣어서 6~8컷을 만든다.
             결과를 코드가 검사하고, 규칙을 어기면 문제를 알려 주고 다시 쓰게 한다(최대 MAX_RETRIES번).

2차 시험(2026-10-02) 뒤 고친 점:
  - 쪽별 문장 범위를 AI가 아니라 코드가 정한다. (exaone이 앞쪽 30%만 다룬 문제)
  - 쪽당 비유·연출 컷을 최대 MAX_ANALOGY_PANELS개로 제한한다. (exaone이 모든 컷을 비유로 표시한 문제)
  - 한 쪽 안에서 같은 근거 번호·같은 대사를 다시 쓰지 못하게 한다. (qwen3의 반복 문제)
  - 대사 속 [23], (p.3) 같은 표시를 금지하고, 남아 있으면 코드로 지운다.
  - 말투를 고정한다: 박사님은 누리에게 반말, 누리는 박사님에게 존댓말.

사용법:
    python3 spikes/llm-spike/run_comic_spike.py qwen3:8b
    python3 spikes/llm-spike/run_comic_spike.py exaone3.5:7.8b
    python3 spikes/llm-spike/run_comic_spike.py qwen3:8b --dry-run   # AI 호출 없이 쪽 나누기와 콘티 입력만 확인
    OLLAMA_URL=http://192.168.0.10:11434 python3 spikes/llm-spike/run_comic_spike.py qwen3:8b   # 다른 컴퓨터의 Ollama 사용

입력: read_manga/results/2026-10-02_grobid-api.json
출력: spikes/llm-spike/results/comic_<모델이름>.json, comic_<모델이름>.md
"""

import json
import os
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

# 다른 컴퓨터의 Ollama를 쓰려면: OLLAMA_URL=http://192.168.0.10:11434 python3 ...
OLLAMA_URL = (
    os.environ.get("OLLAMA_URL", "http://localhost:11434").rstrip("/") + "/api/chat"
)
# macOS의 '로컬 네트워크' 권한이 없는 환경(Orca 터미널 등)에서는 Anaconda의 python이 집 안의 다른 컴퓨터에
# 접속하지 못한다("No route to host"). 애플 기본 /usr/bin/curl은 접속되므로, 원격 주소일 때는 그걸 거쳐 보낸다.
USE_SYSTEM_CURL = urllib.parse.urlparse(OLLAMA_URL).hostname not in (
    "localhost",
    "127.0.0.1",
)
CONTEXT_LENGTH = 16384
MAX_SENTENCES_PER_PAGE = 200
MAX_ANALOGY_PANELS = 2
MAX_RETRIES = 2
MIN_DUPLICATE_LINE_LENGTH = 10  # "와!" 같은 짧은 감탄사는 반복해도 문제로 보지 않는다
FOUR_PAGE_MIN_SENTENCES = 120
SCRIPT_DIR = Path(__file__).resolve().parent
PAPER_JSON = SCRIPT_DIR.parents[1] / "results" / "2026-10-02_grobid-api.json"
RESULT_DIR = SCRIPT_DIR / "results"

# "A. Changelog", "B Mini-...", "D.6. Super-..." 처럼 알파벳 한 글자 번호 뒤에 대문자 단어가 오는 섹션부터는 부록으로 본다.
APPENDIX_HEADING = re.compile(r"^[A-Z](\.\d+)*\.?\s+[A-Z][a-z]")

# 대사에 남은 "[23]", "[47], [75]", "(p.3)" 같은 표시
CITATION_MARK = re.compile(r"\s*(\[\d+(\s*,\s*\d+)*\](\s*,\s*\[\d+\])*|\(p\.\s*\d+\))")

CHARACTERS = (
    "등장인물은 두 명으로 고정한다.\n"
    "- 누리: 과학을 좋아하는 호기심 많은 중학생. 모르는 것을 솔직하게 묻고 놀라거나 감탄한다. 박사님에게 존댓말을 쓴다.\n"
    "- 박사님: 어려운 논문을 쉬운 말과 비유로 풀어 주는 친절한 과학자. 누리에게 반말을 쓴다.\n"
    "필요하면 '해설'(내레이션)로 상황이나 용어를 짧게 설명할 수 있다."
)

STORYBOARD_SYSTEM = "너는 연구 논문을 Why책 같은 학습 만화로 만드는 편집자다."

STORYBOARD_INSTRUCTIONS = """아래 논문을 학습 만화 {total}쪽으로 만든다. 쪽마다 다룰 섹션은 이미 정해져 있다.
쪽마다 독자가 한눈에 알 수 있는 짧은 한국어 주제(topic)를 정하고, 만화 전체의 한국어 제목(title_ko)을 정하라.
주제는 그 쪽에 배정된 섹션 내용에 맞아야 하고, 쪽이 넘어갈수록 문제 → 해결 방법 → 실험 결과 → 의미와 한계 흐름이 드러나게 한다.

논문 제목: {title}

초록:
{abstract}

쪽별 배정 섹션 (형식: 섹션 제목 — 섹션 첫 문장):
{pages}
"""

PAGE_SYSTEM = (
    "너는 연구 논문을 Why책 같은 학습 만화 대본으로 쓰는 작가다. "
    "논문에 적힌 사실만 사실로 말하고, 논문에 없는 숫자나 주장은 지어내지 않는다.\n"
    + CHARACTERS
)

PAGE_INSTRUCTIONS = """학습 만화의 {page}쪽(전체 {total}쪽) 대본을 써라.

전체 구성:
{plan}

이번 쪽 주제: {topic}
{previous}
규칙:
1. 컷은 6~8개로 한다. 내용이 충분하면 7~8컷으로 한다.
2. scene에는 그림 작가가 그릴 수 있게 장소, 인물의 행동과 표정을 구체적으로 쓴다. 컷마다 장면이 달라야 한다.
3. lines에는 대사를 1~3개 넣는다. speaker는 누리, 박사님, 해설 중 하나다. 대사는 중학생도 이해할 수 있는 쉬운 한국어로 짧게 쓴다.
4. 대사 안에 [23] 같은 문장 번호나 (p.3) 같은 페이지 표시를 쓰지 않는다. 근거 번호는 evidence에만 넣는다.
5. 논문의 사실이나 숫자를 말하는 컷은 analogy를 false로 하고, evidence에 근거 문장 번호를 1~3개 넣는다. 번호는 반드시 아래 참고 문장에 있는 번호만 쓴다.
6. 비유, 감탄, 장면 전환처럼 논문에 없는 연출 컷만 analogy를 true로 하고 evidence는 비워 둔다. 이런 컷은 한 쪽에 {max_analogy}개까지만 쓴다.
7. 같은 근거 번호를 두 컷 이상에서 쓰지 않는다. 같은 대사를 반복하지 않는다.
8. 참고 문장의 앞부분만 쓰지 말고, 범위 전체에서 중요한 내용을 고르게 골라 쓴다.

참고 문장 (형식: [문장 번호] (p.페이지) 문장):
{sentences}
"""

RETRY_INSTRUCTIONS = """

방금 쓴 대본에 아래 문제가 있었다. 문제를 모두 고쳐서 이 쪽의 대본을 처음부터 다시 써라.
{problems}
"""


def storyboard_schema(total_pages):
    return {
        "type": "object",
        "properties": {
            "title_ko": {"type": "string"},
            "topics": {
                "type": "array",
                "minItems": total_pages,
                "maxItems": total_pages,
                "items": {"type": "string"},
            },
        },
        "required": ["title_ko", "topics"],
    }


PAGE_SCHEMA = {
    "type": "object",
    "properties": {
        "panels": {
            "type": "array",
            "minItems": 6,
            "maxItems": 8,
            "items": {
                "type": "object",
                "properties": {
                    "scene": {"type": "string"},
                    "lines": {
                        "type": "array",
                        "minItems": 1,
                        "maxItems": 3,
                        "items": {
                            "type": "object",
                            "properties": {
                                "speaker": {
                                    "type": "string",
                                    "enum": ["누리", "박사님", "해설"],
                                },
                                "text": {"type": "string"},
                            },
                            "required": ["speaker", "text"],
                        },
                    },
                    "analogy": {"type": "boolean"},
                    "evidence": {
                        "type": "array",
                        "maxItems": 3,
                        "items": {"type": "integer"},
                    },
                },
                "required": ["scene", "lines", "analogy", "evidence"],
            },
        },
    },
    "required": ["panels"],
}


def number_sentences(paper):
    """본문 섹션의 문장에 1부터 번호를 붙인다. 부록 섹션부터는 뺀다."""
    sections = []
    sentences = {}
    number = 0
    for section in paper["sections"]:
        if APPENDIX_HEADING.match(section["title"]):
            break
        start = number + 1
        for sentence in section["sentences"]:
            number += 1
            sentences[number] = sentence
        if number >= start:
            sections.append({"title": section["title"], "start": start, "end": number})
    return sections, sentences


def split_pages(sections, total_sentences):
    """섹션 경계를 따라 본문을 3~4쪽으로 나눈다. 쪽마다 문장 수가 비슷해지는 경계를 고른다."""
    total_pages = 4 if total_sentences >= FOUR_PAGE_MIN_SENTENCES else 3
    boundaries = [section["end"] for section in sections[:-1]]
    cuts = []
    for k in range(1, total_pages):
        target = total_sentences * k / total_pages
        candidates = [b for b in boundaries if (not cuts or b > cuts[-1])]
        if not candidates:
            break
        cuts.append(min(candidates, key=lambda b: abs(b - target)))

    pages = []
    start = 1
    for end in cuts + [total_sentences]:
        page_sections = [s for s in sections if s["start"] >= start and s["end"] <= end]
        pages.append({"start": start, "end": end, "sections": page_sections})
        start = end + 1
    return pages


def post_json(url, body):
    data = json.dumps(body).encode("utf-8")
    if USE_SYSTEM_CURL:
        completed = subprocess.run(
            [
                "/usr/bin/curl",
                "-sS",
                "--fail-with-body",
                "-m",
                "1800",
                "-H",
                "Content-Type: application/json",
                "--data-binary",
                "@-",
                url,
            ],
            input=data,
            capture_output=True,
        )
        if completed.returncode != 0:
            raise RuntimeError(
                f"curl 요청 실패(exit {completed.returncode}): "
                f"{completed.stderr.decode()} {completed.stdout.decode()[:500]}"
            )
        return json.loads(completed.stdout)

    request = urllib.request.Request(
        url, data=data, headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(request, timeout=1800) as response:
        return json.loads(response.read())


def call_ollama(model, system, messages, schema):
    body = {
        "model": model,
        "messages": [{"role": "system", "content": system}] + messages,
        "format": schema,
        "stream": False,
        "options": {"num_ctx": CONTEXT_LENGTH, "temperature": 0.4},
    }
    if model.startswith("qwen3"):
        body["think"] = False

    started = time.time()
    result = post_json(OLLAMA_URL, body)
    stats = {
        "seconds": round(time.time() - started),
        "prompt_tokens": result.get("prompt_eval_count"),
        "output_tokens": result.get("eval_count"),
    }
    return result["message"]["content"], stats


def build_storyboard_prompt(paper, pages, sentences):
    abstract = " ".join(s["text"] for s in paper["sections"][0]["sentences"])
    page_lines = []
    for index, page in enumerate(pages, start=1):
        page_lines.append(f"\n{index}쪽 (문장 {page['start']}~{page['end']}):")
        for section in page["sections"]:
            page_lines.append(
                f"- {section['title']} — {sentences[section['start']]['text'][:150]}"
            )
    return STORYBOARD_INSTRUCTIONS.format(
        total=len(pages),
        title=paper["title"],
        abstract=abstract,
        pages="\n".join(page_lines),
    )


def clean_lines(panels):
    """대사에 남은 문장 번호·페이지 표시를 지운다."""
    for panel in panels:
        for line in panel["lines"]:
            line["text"] = CITATION_MARK.sub("", line["text"]).strip()


def find_problems(panels, allowed):
    problems = []
    analogy_count = sum(1 for panel in panels if panel["analogy"])
    if analogy_count > MAX_ANALOGY_PANELS:
        problems.append(
            f"비유·연출 컷(analogy가 true)이 {analogy_count}개다. {MAX_ANALOGY_PANELS}개 이하로 줄이고, "
            "논문 내용을 말하는 컷은 analogy를 false로 하고 근거 번호를 넣어라."
        )

    seen_evidence = {}
    seen_lines = set()
    for index, panel in enumerate(panels, start=1):
        if not panel["analogy"] and not panel["evidence"]:
            problems.append(f"{index}번 컷은 논문 내용을 말하는데 근거 번호가 없다.")
        for n in panel["evidence"]:
            if n not in allowed:
                problems.append(
                    f"{index}번 컷의 근거 번호 {n}은 참고 문장에 없는 번호다."
                )
            elif n in seen_evidence:
                problems.append(
                    f"근거 번호 {n}을 {seen_evidence[n]}번 컷과 {index}번 컷에서 함께 썼다."
                )
            else:
                seen_evidence[n] = index
        for line in panel["lines"]:
            if CITATION_MARK.search(line["text"]):
                problems.append(
                    f"{index}번 컷의 대사에 문장 번호나 페이지 표시가 들어 있다. 대사에서 빼고 evidence에만 넣어라."
                )
            if (
                len(line["text"]) >= MIN_DUPLICATE_LINE_LENGTH
                and line["text"] in seen_lines
            ):
                problems.append(
                    f"{index}번 컷의 대사 \"{line['text']}\"가 앞 컷과 똑같다."
                )
            seen_lines.add(line["text"])
    return problems


def write_page(model, prompt, allowed):
    """쪽 하나를 쓰고, 문제가 있으면 문제를 알려 주며 다시 쓰게 한다. 문제가 가장 적은 결과를 돌려준다."""
    messages = [{"role": "user", "content": prompt}]
    attempts = []
    best = None
    for attempt in range(1, MAX_RETRIES + 2):
        content, stats = call_ollama(model, PAGE_SYSTEM, messages, PAGE_SCHEMA)
        result = json.loads(content)
        problems = find_problems(result["panels"], allowed)
        clean_lines(result["panels"])
        attempts.append({**stats, "problems": problems})
        print(
            f"  시도 {attempt}: {stats['seconds']}초, 컷 {len(result['panels'])}개, 문제 {len(problems)}개"
        )

        if best is None or len(problems) < len(best[1]):
            best = (result, problems)
        if not problems:
            break
        messages = messages + [
            {"role": "assistant", "content": content},
            {
                "role": "user",
                "content": RETRY_INSTRUCTIONS.format(
                    problems="\n".join(f"- {p}" for p in problems)
                ),
            },
        ]
    return best[0], best[1], attempts


def render_markdown(model, title_ko, pages, results, sentences):
    out = [f"# {title_ko}", f"모델: `{model}`", ""]
    for index, (page, result) in enumerate(zip(pages, results), start=1):
        out.append(f"## {index}쪽 — {page['topic']}")
        out.append(f"참고 범위: [{page['start']}~{page['end']}]")
        if result["problems"]:
            out.append("남은 문제: " + " / ".join(result["problems"]))
        out.append("")
        for p_index, panel in enumerate(result["panels"], start=1):
            tag = " (비유·연출)" if panel["analogy"] else ""
            out.append(f"**컷 {p_index}{tag}** — {panel['scene']}")
            for line in panel["lines"]:
                out.append(f"- **{line['speaker']}**: {line['text']}")
            for n in panel["evidence"]:
                sentence = sentences.get(n)
                text = (
                    f"(p.{sentence['page']}) {sentence['text']}"
                    if sentence
                    else "← 없는 번호"
                )
                out.append(f"  - 근거 [{n}] {text}")
            out.append("")
    return "\n".join(out)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    model = sys.argv[1]
    dry_run = "--dry-run" in sys.argv

    paper = json.loads(PAPER_JSON.read_text(encoding="utf-8"))
    sections, sentences = number_sentences(paper)
    pages = split_pages(sections, len(sentences))
    storyboard_prompt = build_storyboard_prompt(paper, pages, sentences)

    print(f"모델: {model}")
    print(f"본문 문장 수: {len(sentences)} (부록 제외), 쪽 수: {len(pages)}")
    for index, page in enumerate(pages, start=1):
        titles = ", ".join(s["title"] for s in page["sections"])
        print(
            f"  {index}쪽 [{page['start']}~{page['end']}] {page['end'] - page['start'] + 1}문장: {titles}"
        )
    if dry_run:
        print(f"\n콘티 프롬프트 글자 수: {len(storyboard_prompt):,}")
        print(storyboard_prompt)
        return

    total_started = time.time()
    print("\n[1단계] 쪽별 주제를 정하는 중...")
    content, storyboard_stats = call_ollama(
        model,
        STORYBOARD_SYSTEM,
        [{"role": "user", "content": storyboard_prompt}],
        storyboard_schema(len(pages)),
    )
    storyboard = json.loads(content)
    print(f"  {storyboard_stats['seconds']}초. 제목: {storyboard['title_ko']}")
    for page, topic in zip(pages, storyboard["topics"]):
        page["topic"] = topic
        print(f"  [{page['start']}~{page['end']}] {topic}")

    plan = "\n".join(
        f"{index}쪽: {page['topic']}" for index, page in enumerate(pages, start=1)
    )
    results = []
    previous = ""
    for index, page in enumerate(pages, start=1):
        numbers = list(range(page["start"], page["end"] + 1))[:MAX_SENTENCES_PER_PAGE]
        sentence_text = "\n".join(
            f"[{n}] (p.{sentences[n]['page']}) {sentences[n]['text']}" for n in numbers
        )
        prompt = PAGE_INSTRUCTIONS.format(
            page=index,
            total=len(pages),
            plan=plan,
            topic=page["topic"],
            previous=previous,
            max_analogy=MAX_ANALOGY_PANELS,
            sentences=sentence_text,
        )
        print(
            f"\n[2단계] {index}/{len(pages)}쪽을 쓰는 중... (참고 문장 {len(numbers)}개)"
        )
        panels_result, problems, attempts = write_page(model, prompt, set(numbers))
        results.append(
            {
                "panels": panels_result["panels"],
                "problems": problems,
                "attempts": attempts,
            }
        )

        last_panel = panels_result["panels"][-1]
        previous = (
            f"이전 쪽은 이렇게 끝났다: {last_panel['scene']}\n"
            "이번 쪽은 이 장면에서 자연스럽게 이어지되, 이전 쪽의 대사를 반복하지 않는다.\n"
        )

    panels = [panel for result in results for panel in result["panels"]]
    summary = {
        "model": model,
        "total_seconds": round(time.time() - total_started),
        "pages": len(results),
        "panels": len(panels),
        "analogy_panels": sum(1 for panel in panels if panel["analogy"]),
        "retries": sum(len(result["attempts"]) - 1 for result in results),
        "remaining_problems": sum(len(result["problems"]) for result in results),
        "storyboard_stats": storyboard_stats,
    }

    print("\n===== 요약 =====")
    for key in [
        "total_seconds",
        "pages",
        "panels",
        "analogy_panels",
        "retries",
        "remaining_problems",
    ]:
        print(f"{key}: {summary[key]}")

    RESULT_DIR.mkdir(exist_ok=True)
    name = model.replace(":", "_")
    for page in pages:
        page.pop("sections")
    (RESULT_DIR / f"comic_{name}.json").write_text(
        json.dumps(
            {
                "summary": summary,
                "title_ko": storyboard["title_ko"],
                "pages": pages,
                "results": results,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    (RESULT_DIR / f"comic_{name}.md").write_text(
        render_markdown(model, storyboard["title_ko"], pages, results, sentences),
        encoding="utf-8",
    )
    print(f"\n결과 저장: {RESULT_DIR / f'comic_{name}.md'}")


if __name__ == "__main__":
    main()
