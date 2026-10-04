"""구조도 → 사실 고르기 → 대사 쓰기 순서로 Why책 같은 학습 만화 대본을 만드는 실험 스크립트 (4차 시험용).

3차 시험에서 AI가 사실 컷을 '비유'로 표시해 근거 검사를 피해 가는 문제가 있었다.
그래서 근거를 AI의 자기 신고에 맡기지 않고, 단계를 나눠 구조로 보장한다.

  ① 구조도: 논문 전체를 보고 만화 목차 항목 6~10개를 만든다(한국어 소제목, 한 줄 요약, 시작 섹션).
           코드가 목차가 본문 전체를 빈틈없이 덮게 정리하고, 목차를 3~4쪽으로 묶는다.
  ② 사실 고르기: 목차 항목마다 해당 문장만 보고 핵심 사실과 근거 문장 번호를 뽑는다.
           코드가 근거 번호를 검사하고, 근거가 없는 사실은 버린다.
  ③ 대사 쓰기: 쪽마다 사실 하나당 컷 하나를 순서대로 쓴다. 연출 컷(감탄·비유·장면 전환)은 최대 2개.
           근거는 코드가 ②에서 가져와 붙이므로 사실 컷에 근거가 빠질 수 없다.

사용법:
    python3 spikes/llm-spike/run_outline_spike.py exaone3.5:7.8b --dry-run   # AI 호출 없이 구조도 입력만 확인
    OLLAMA_URL=http://<주 서버 IP>:11434 python3 spikes/llm-spike/run_outline_spike.py exaone3.5:7.8b
    OLLAMA_URL=http://<주 서버 IP>:11434 python3 spikes/llm-spike/run_outline_spike.py qwen3:8b

입력: read_manga/results/2026-10-02_grobid-api.json
출력: spikes/llm-spike/results/outline_<모델이름>.json, outline_<모델이름>.md
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
FOUR_PAGE_MIN_SENTENCES = 120
FACTS_PER_PAGE = 6
MAX_STAGING_PANELS = 2
MAX_RETRIES = 1
MIN_DUPLICATE_LINE_LENGTH = 10  # "와!" 같은 짧은 감탄사는 반복해도 문제로 보지 않는다
SCRIPT_DIR = Path(__file__).resolve().parent
PAPER_JSON = SCRIPT_DIR.parents[1] / "results" / "2026-10-02_grobid-api.json"
RESULT_DIR = SCRIPT_DIR / "results"

# "A. Changelog", "B Mini-...", "D.6. Super-..." 처럼 알파벳 한 글자 번호 뒤에 대문자 단어가 오는 섹션부터는 부록으로 본다.
APPENDIX_HEADING = re.compile(r"^[A-Z](\.\d+)*\.?\s+[A-Z][a-z]")
# 대사에 남으면 안 되는 표시: "[23]", "[47], [75]", "(p.3)", "(p. 3, 122)", "F1"
MARK_IN_LINE = re.compile(r"\[\d+(\s*,\s*\d+)*\]|\(p\.\s*\d+[^)]*\)|\bF\d+\b")
# 박사님 대사가 존댓말로 끝나는지 대략 확인한다
POLITE_ENDING = re.compile(r"(요|니다|세요)[.!?~…\s]*$")

CHARACTERS = (
    "등장인물은 두 명으로 고정한다.\n"
    "- 누리: 과학을 좋아하는 호기심 많은 중학생. 모르는 것을 솔직하게 묻고 놀라거나 감탄한다. 박사님에게 존댓말을 쓴다.\n"
    "- 박사님: 어려운 논문을 쉬운 말과 비유로 풀어 주는 친절한 과학자. 누리에게 반말을 쓴다.\n"
    "필요하면 '해설'(내레이션)로 상황이나 용어를 짧게 설명할 수 있다."
)

OUTLINE_SYSTEM = "너는 연구 논문을 Why책 같은 학습 만화로 만드는 편집자다."

OUTLINE_INSTRUCTIONS = """아래 논문의 전체 구조도를 만들어라. 이 구조도가 학습 만화의 목차가 된다.

목차 항목은 6~10개로 하고, 항목마다 다음을 정한다.
- title_ko: 독자가 한눈에 알 수 있는 짧은 한국어 소제목
- summary: 이 항목에서 독자가 알게 될 내용을 한국어 한 문장으로
- first_section: 이 항목이 시작되는 섹션 번호 (아래 섹션 목록의 번호)

규칙:
1. 첫 항목의 first_section은 0이다. 뒤 항목일수록 first_section이 커져야 한다. 한 항목은 다음 항목의 first_section 바로 앞 섹션까지를 다룬다.
2. 목차 흐름은 연구가 왜 필요했는지(문제) → 어떻게 해결했는지(방법) → 실험해 보니 어땠는지(결과) → 왜 중요하고 한계는 무엇인지 순서로 한다.
3. 만화 전체의 한국어 제목(title_ko)도 정한다.

논문 제목: {title}

초록:
{abstract}

섹션 목록 (형식: [섹션 번호] 섹션 제목 (문장 수) — 첫 문장):
{sections}
"""

FACT_SYSTEM = "너는 논문에서 핵심 사실을 정확하게 뽑는 연구 조교다. 문장에 적힌 내용만 쓰고, 적히지 않은 내용은 지어내지 않는다."

FACT_INSTRUCTIONS = """학습 만화의 목차 항목: {title} — {summary}

아래 논문 문장에서 이 항목에 맞는 핵심 사실 {count}개를 뽑아라.

규칙:
1. fact는 중학생도 이해할 수 있는 쉬운 한국어 한 문장으로 쓴다. 문장에 적힌 내용만 쓰고, 숫자는 문장에 있는 그대로 쓴다.
2. evidence에는 그 사실이 적힌 문장 번호를 1~3개 넣는다. 반드시 아래 목록에 있는 번호만 쓴다.
3. 사실끼리 내용이 겹치지 않게 한다. 표 조각이나 참고문헌 번호만 있는 문장은 고르지 않는다.

논문 문장 (형식: [문장 번호] (p.페이지) 문장):
{sentences}
"""

DIALOGUE_SYSTEM = (
    "너는 연구 논문을 Why책 같은 학습 만화 대본으로 쓰는 작가다. "
    "주어진 사실만 사실로 말하고, 사실에 없는 숫자나 주장은 지어내지 않는다.\n"
    + CHARACTERS
)

DIALOGUE_INSTRUCTIONS = """학습 만화의 {page}쪽(전체 {total}쪽) 대본을 써라.

전체 목차:
{toc}

이번 쪽에서 다루는 목차: {page_items}
{previous}
이번 쪽에서 전할 사실 (순서대로, 괄호 안은 근거가 된 원문):
{facts}

규칙:
1. 사실 컷: 위의 사실마다 컷을 정확히 하나씩, F1부터 순서대로 만든다. 그 컷의 fact에는 사실 번호(F1이면 1)를 넣는다.
2. 연출 컷: 감탄, 비유, 장면 전환 같은 컷은 fact를 0으로 하고, 한 쪽에 {max_staging}개까지만 넣는다.
3. 사실 컷의 대사는 배정된 사실을 쉽게 전한다. 쉬운 비유를 곁들여도 되지만, 사실에 없는 숫자나 주장을 덧붙이지 않는다.
4. scene에는 그림 작가가 그릴 수 있게 장소, 인물의 행동과 표정을 구체적으로 쓴다. 컷마다 장면이 달라야 한다.
5. lines에는 대사를 1~3개 넣는다. speaker는 누리, 박사님, 해설 중 하나다. 박사님은 누리에게 반말을, 누리는 박사님에게 존댓말을 쓴다.
6. 대사 안에 [23], (p.3), F1 같은 번호 표시를 쓰지 않는다. 같은 대사를 반복하지 않는다.
"""

RETRY_INSTRUCTIONS = """

방금 쓴 결과에 아래 문제가 있었다. 문제를 모두 고쳐서 처음부터 다시 써라.
{problems}
"""


def outline_schema():
    return {
        "type": "object",
        "properties": {
            "title_ko": {"type": "string"},
            "items": {
                "type": "array",
                "minItems": 6,
                "maxItems": 10,
                "items": {
                    "type": "object",
                    "properties": {
                        "title_ko": {"type": "string"},
                        "summary": {"type": "string"},
                        "first_section": {"type": "integer"},
                    },
                    "required": ["title_ko", "summary", "first_section"],
                },
            },
        },
        "required": ["title_ko", "items"],
    }


def fact_schema(count):
    return {
        "type": "object",
        "properties": {
            "facts": {
                "type": "array",
                "minItems": count,
                "maxItems": count,
                "items": {
                    "type": "object",
                    "properties": {
                        "fact": {"type": "string"},
                        "evidence": {
                            "type": "array",
                            "minItems": 1,
                            "maxItems": 3,
                            "items": {"type": "integer"},
                        },
                    },
                    "required": ["fact", "evidence"],
                },
            },
        },
        "required": ["facts"],
    }


def dialogue_schema(fact_count):
    return {
        "type": "object",
        "properties": {
            "panels": {
                "type": "array",
                "minItems": fact_count,
                "maxItems": fact_count + MAX_STAGING_PANELS,
                "items": {
                    "type": "object",
                    "properties": {
                        "fact": {
                            "type": "integer",
                            "enum": list(range(fact_count + 1)),
                        },
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
                    },
                    "required": ["fact", "scene", "lines"],
                },
            },
        },
        "required": ["panels"],
    }


# ---------- Ollama 호출 ----------


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


def ask_with_retry(model, system, prompt, schema, check, max_retries=None):
    """요청하고 check로 문제를 찾는다. 문제가 있으면 알려 주고 다시 시킨다. 문제가 가장 적은 결과를 돌려준다."""
    max_retries = MAX_RETRIES if max_retries is None else max_retries
    messages = [{"role": "user", "content": prompt}]
    attempts = []
    best = None
    for attempt in range(1, max_retries + 2):
        content, stats = call_ollama(model, system, messages, schema)
        result = json.loads(content)
        problems = check(result)
        attempts.append({**stats, "problems": problems})
        print(f"    시도 {attempt}: {stats['seconds']}초, 문제 {len(problems)}개")
        if best is None or len(problems) <= len(best[1]):  # 문제 수가 같으면 지적을 받고 고친 나중 결과를 고른다
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


# ---------- 논문 준비 ----------


def number_sentences(paper):
    """본문 섹션의 문장에 1부터 번호를 붙인다. 부록 섹션부터는 뺀다. 문장이 없는 섹션도 뺀다."""
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


# ---------- ① 구조도 ----------


def build_outline_prompt(paper, sections, sentences):
    abstract = " ".join(s["text"] for s in paper["sections"][0]["sentences"])
    lines = [
        f"[{index}] {sec['title']} ({sec['end'] - sec['start'] + 1}) — {sentences[sec['start']]['text'][:150]}"
        for index, sec in enumerate(sections)
    ]
    return OUTLINE_INSTRUCTIONS.format(
        title=paper["title"], abstract=abstract, sections="\n".join(lines)
    )


def normalize_outline(items, sections):
    """AI가 정한 시작 섹션을 정리해서, 목차가 본문 전체를 순서대로 빈틈없이 덮게 만든다."""
    last = len(sections) - 1
    cleaned = []
    for item in sorted(items, key=lambda i: i["first_section"]):
        first = max(0, min(item["first_section"], last))
        if cleaned and first <= cleaned[-1]["first_section"]:
            continue  # 같은 섹션에서 시작하는 항목이 겹치면 앞 항목만 남긴다
        cleaned.append({**item, "first_section": first})
    cleaned[0]["first_section"] = 0

    for index, item in enumerate(cleaned):
        last_section = (
            cleaned[index + 1]["first_section"] - 1
            if index + 1 < len(cleaned)
            else last
        )
        item["sections"] = list(range(item["first_section"], last_section + 1))
        item["start"] = sections[item["first_section"]]["start"]
        item["end"] = sections[last_section]["end"]
    return cleaned


def group_pages(items, total_sentences):
    """목차 항목을 3~4쪽으로 묶는다. 쪽마다 문장 수가 비슷해지는 항목 경계를 고른다."""
    total_pages = 4 if total_sentences >= FOUR_PAGE_MIN_SENTENCES else 3
    total_pages = min(total_pages, len(items))
    boundaries = [
        index for index in range(1, len(items))
    ]  # index 앞에서 쪽을 나눌 수 있음
    cuts = []
    for k in range(1, total_pages):
        target = total_sentences * k / total_pages
        candidates = [
            b
            for b in boundaries
            if (not cuts or b > cuts[-1]) and b <= len(items) - (total_pages - k)
        ]
        cuts.append(min(candidates, key=lambda b: abs(items[b]["start"] - 1 - target)))
    pages = []
    start = 0
    for end in cuts + [len(items)]:
        pages.append(items[start:end])
        start = end
    return pages


def split_fact_counts(page_items):
    """쪽당 사실 FACTS_PER_PAGE개를 목차 항목에 문장 수 비율로 나눈다. 항목마다 최소 1개."""
    sizes = [item["end"] - item["start"] + 1 for item in page_items]
    counts = [1] * len(page_items)
    for _ in range(max(0, FACTS_PER_PAGE - len(page_items))):
        best = max(range(len(page_items)), key=lambda i: sizes[i] / counts[i])
        counts[best] += 1
    return counts


# ---------- ② 사실 고르기 ----------


def check_facts(result, allowed):
    problems = []
    seen = {}
    for index, fact in enumerate(result["facts"], start=1):
        valid = [n for n in fact["evidence"] if n in allowed]
        if not valid:
            problems.append(
                f"{index}번 사실의 근거 번호 {fact['evidence']}가 모두 목록에 없는 번호다."
            )
        for n in valid:
            if n in seen:
                problems.append(
                    f"근거 번호 {n}을 {seen[n]}번 사실과 {index}번 사실에서 함께 썼다."
                )
            seen.setdefault(n, index)
    return problems


def pick_facts(model, item, count, sentences):
    numbers = list(range(item["start"], item["end"] + 1))
    allowed = set(numbers)
    prompt = FACT_INSTRUCTIONS.format(
        title=item["title_ko"],
        summary=item["summary"],
        count=count,
        sentences="\n".join(
            f"[{n}] (p.{sentences[n]['page']}) {sentences[n]['text']}" for n in numbers
        ),
    )
    result, problems, attempts = ask_with_retry(
        model,
        FACT_SYSTEM,
        prompt,
        fact_schema(count),
        lambda r: check_facts(r, allowed),
    )
    facts = []
    for fact in result["facts"]:
        evidence = [n for n in dict.fromkeys(fact["evidence"]) if n in allowed]
        if evidence:  # 근거가 하나도 맞지 않는 사실은 버린다
            facts.append(
                {
                    "fact": fact["fact"].strip(),
                    "evidence": evidence,
                    "item": item["title_ko"],
                }
            )
    return facts, problems, attempts


# ---------- ③ 대사 쓰기 ----------


def check_dialogue(result, fact_count):
    problems = []
    order = [panel["fact"] for panel in result["panels"] if panel["fact"] != 0]
    if order != list(range(1, fact_count + 1)):
        problems.append(
            f"사실 컷의 순서가 {['F' + str(n) for n in order]}이다. F1부터 F{fact_count}까지 하나씩 순서대로 있어야 한다."
        )
    staging = sum(1 for panel in result["panels"] if panel["fact"] == 0)
    if staging > MAX_STAGING_PANELS:
        problems.append(
            f"연출 컷(fact가 0)이 {staging}개다. {MAX_STAGING_PANELS}개 이하로 줄여라."
        )

    seen_lines = set()
    for index, panel in enumerate(result["panels"], start=1):
        for line in panel["lines"]:
            text = line["text"]
            if MARK_IN_LINE.search(text):
                problems.append(
                    f'{index}번 컷의 대사에 번호 표시가 들어 있다: "{text}"'
                )
            if line["speaker"] == "박사님" and POLITE_ENDING.search(text):
                problems.append(
                    f'{index}번 컷에서 박사님이 존댓말을 썼다: "{text}". 박사님은 누리에게 반말을 쓴다.'
                )
            if len(text) >= MIN_DUPLICATE_LINE_LENGTH and text in seen_lines:
                problems.append(f'{index}번 컷의 대사 "{text}"가 앞 컷과 똑같다.')
            seen_lines.add(text)
    return problems


def write_page(
    model, page_number, total_pages, toc, page_items, facts, previous, sentences
):
    fact_lines = []
    for index, fact in enumerate(facts, start=1):
        quotes = " / ".join(
            f"[{n}] {sentences[n]['text'][:200]}" for n in fact["evidence"]
        )
        fact_lines.append(f"F{index}. {fact['fact']} (원문: {quotes})")
    prompt = DIALOGUE_INSTRUCTIONS.format(
        page=page_number,
        total=total_pages,
        toc=toc,
        page_items=", ".join(item["title_ko"] for item in page_items),
        previous=previous,
        facts="\n".join(fact_lines),
        max_staging=MAX_STAGING_PANELS,
    )
    result, problems, attempts = ask_with_retry(
        model,
        DIALOGUE_SYSTEM,
        prompt,
        dialogue_schema(len(facts)),
        lambda r: check_dialogue(r, len(facts)),
    )

    panels = []
    for panel in result["panels"]:
        fact = facts[panel["fact"] - 1] if 1 <= panel["fact"] <= len(facts) else None
        lines = [
            {**line, "text": MARK_IN_LINE.sub("", line["text"]).strip()}
            for line in panel["lines"]
        ]
        panels.append(
            {
                "scene": panel["scene"],
                "lines": lines,
                "staging": fact is None,
                "fact": fact["fact"] if fact else None,
                "evidence": (
                    fact["evidence"] if fact else []
                ),  # 근거는 AI가 아니라 코드가 붙인다
            }
        )
    return panels, problems, attempts


# ---------- 결과 정리 ----------


def render_markdown(model, title_ko, pages, sentences):
    out = [f"# {title_ko}", f"모델: `{model}`", "", "## 구조도", ""]
    for page_number, page in enumerate(pages, start=1):
        for item in page["items"]:
            out.append(
                f"- {page_number}쪽 · **{item['title_ko']}** [{item['start']}~{item['end']}] — {item['summary']}"
            )
    out.append("")
    for page_number, page in enumerate(pages, start=1):
        out.append(
            f"## {page_number}쪽 — {', '.join(item['title_ko'] for item in page['items'])}"
        )
        if page["problems"]:
            out.append("남은 문제: " + " / ".join(page["problems"]))
        out.append("")
        for index, panel in enumerate(page["panels"], start=1):
            tag = " (연출)" if panel["staging"] else ""
            out.append(f"**컷 {index}{tag}** — {panel['scene']}")
            for line in panel["lines"]:
                out.append(f"- **{line['speaker']}**: {line['text']}")
            if panel["fact"]:
                out.append(f"  - 사실: {panel['fact']}")
            for n in panel["evidence"]:
                out.append(
                    f"  - 근거 [{n}] (p.{sentences[n]['page']}) {sentences[n]['text']}"
                )
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
    outline_prompt = build_outline_prompt(paper, sections, sentences)

    print(f"모델: {model} / Ollama 주소: {OLLAMA_URL}")
    print(f"본문 문장 수: {len(sentences)} (부록 제외), 섹션 수: {len(sections)}")
    if dry_run:
        print(f"\n구조도 프롬프트 글자 수: {len(outline_prompt):,}")
        print(outline_prompt)
        return

    total_started = time.time()

    print("\n[① 구조도] 목차를 만드는 중...")
    content, outline_stats = call_ollama(
        model,
        OUTLINE_SYSTEM,
        [{"role": "user", "content": outline_prompt}],
        outline_schema(),
    )
    outline = json.loads(content)
    items = normalize_outline(outline["items"], sections)
    page_groups = group_pages(items, len(sentences))
    print(f"  {outline_stats['seconds']}초. 제목: {outline['title_ko']}")
    for page_number, page_items in enumerate(page_groups, start=1):
        for item in page_items:
            print(
                f"  {page_number}쪽 · {item['title_ko']} [{item['start']}~{item['end']}]"
            )
    toc = "\n".join(
        f"{page_number}쪽: " + ", ".join(item["title_ko"] for item in page_items)
        for page_number, page_items in enumerate(page_groups, start=1)
    )

    pages = []
    previous = ""
    for page_number, page_items in enumerate(page_groups, start=1):
        print(f"\n[② 사실 고르기] {page_number}/{len(page_groups)}쪽")
        facts = []
        fact_log = []
        for item, count in zip(page_items, split_fact_counts(page_items)):
            print(f"  · {item['title_ko']} — 사실 {count}개")
            item_facts, problems, attempts = pick_facts(model, item, count, sentences)
            facts.extend(item_facts)
            fact_log.append(
                {"item": item["title_ko"], "problems": problems, "attempts": attempts}
            )

        print(f"[③ 대사 쓰기] {page_number}/{len(page_groups)}쪽 (사실 {len(facts)}개)")
        panels, problems, attempts = write_page(
            model,
            page_number,
            len(page_groups),
            toc,
            page_items,
            facts,
            previous,
            sentences,
        )
        print(f"  컷 {len(panels)}개, 남은 문제 {len(problems)}개")
        pages.append(
            {
                "items": page_items,
                "facts": facts,
                "panels": panels,
                "problems": problems,
                "fact_log": fact_log,
                "dialogue_attempts": attempts,
            }
        )
        previous = (
            f"이전 쪽은 이렇게 끝났다: {panels[-1]['scene']}\n"
            "이번 쪽은 이 장면에서 자연스럽게 이어지되, 이전 쪽의 대사를 반복하지 않는다.\n"
        )

    all_panels = [panel for page in pages for panel in page["panels"]]
    summary = {
        "model": model,
        "total_seconds": round(time.time() - total_started),
        "outline_items": len(items),
        "pages": len(pages),
        "panels": len(all_panels),
        "fact_panels": sum(1 for panel in all_panels if not panel["staging"]),
        "staging_panels": sum(1 for panel in all_panels if panel["staging"]),
        "remaining_problems": sum(len(page["problems"]) for page in pages),
    }
    print("\n===== 요약 =====")
    for key, value in summary.items():
        print(f"{key}: {value}")

    RESULT_DIR.mkdir(exist_ok=True)
    name = model.replace(":", "_")
    (RESULT_DIR / f"outline_{name}.json").write_text(
        json.dumps(
            {
                "summary": summary,
                "title_ko": outline["title_ko"],
                "outline_stats": outline_stats,
                "pages": pages,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    (RESULT_DIR / f"outline_{name}.md").write_text(
        render_markdown(model, outline["title_ko"], pages, sentences), encoding="utf-8"
    )
    print(f"\n결과 저장: {RESULT_DIR / f'outline_{name}.md'}")


if __name__ == "__main__":
    main()
