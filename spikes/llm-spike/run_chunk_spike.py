"""코드가 본문을 고르게 나누고, AI는 조각마다 제목·사실·컷을 쓰는 학습 만화 대본 생성 실험 스크립트 (6차 시험용).

5차 시험(run_hybrid_spike.py)에서 남은 문제와 고친 방법:
  - 목차가 앞부분에 몰려 한계·결론이 빠짐
      → 목차 나누기를 AI에게 맡기지 않는다. 코드가 섹션 경계를 따라 본문을 쪽당 3조각씩 고르게 나누고,
        AI(qwen3)는 조각마다 한국어 제목과 요약만 쓴다.
  - 사실이 범위 앞부분에만 몰림
      → 조각이 작아졌으므로 조각당 사실 2개만 뽑는다. 글자 유사도 0.55 이상으로 비슷한 사실은 버린다.
  - 대사와 사실이 한 칸씩 어긋남 (한 쪽의 사실 6개를 한 번에 쓰게 해서 생김)
      → 사실 컷을 하나씩 따로 요청한다. 요청마다 사실이 하나뿐이라 다른 사실을 쓸 수 없다.
        (글자 유사도로 어긋남을 찾는 방법도 시험했지만, 맞는 컷까지 어긋남으로 잡혀서 쓰지 않았다.)
        연출 컷은 쪽마다 여는 컷 1개, 닫는 컷 1개를 따로 받는다.
  - 박사님 존댓말 → 지시문에 말투 예시를 넣는다. 해설이 인물에게 말을 거는 것도 문제로 잡는다.
  - '(F5)'를 지운 자리의 '()', 대사 속 줄바꿈, 너무 짧은 장면 설명 → 코드로 정리하거나 문제로 잡는다.

모델 사용 순서: 조각 제목 → 모든 조각의 사실 (qwen3) → 모든 컷 (exaone). 모델 교체는 한 번뿐이다.

사용법:
    python3 spikes/llm-spike/run_chunk_spike.py --dry-run     # AI 호출 없이 조각 나누기 확인
    OLLAMA_URL=http://<주 서버 IP>:11434 python3 spikes/llm-spike/run_chunk_spike.py

입력: read_manga/results/2026-10-02_grobid-api.json
출력: spikes/llm-spike/results/chunk_<사실 모델>__<대사 모델>.json, .md
"""

import argparse
import json
import math
import re
import time

import run_hybrid_spike as hybrid  # 표 조각 거르기, 사실 고르기
import run_outline_spike as base  # Ollama 호출, 문장 번호 붙이기, 다시 쓰기

CHUNKS_PER_PAGE = 3
FACTS_PER_CHUNK = 2
SIMILAR_FACT_THRESHOLD = 0.55
MIN_SCENE_LENGTH = 8
RESULT_DIR = base.RESULT_DIR

MARK_IN_PARENS = re.compile(r"\(\s*(F\d+|p\.\s*\d+[^)]*|\d+(\s*,\s*\d+)*)\s*\)")
EMPTY_PARENS = re.compile(r"\(\s*\)")
SENTENCE_END = re.compile(r"[^.!?…]+[.!?…]*")

OUTLINE_INSTRUCTIONS = """아래 논문을 학습 만화로 만든다. 본문은 이미 {count}개 조각으로 순서대로 나뉘어 있다.
조각마다 다음을 정하라.
- title_ko: 그 조각 내용에 맞는 짧은 한국어 소제목
- summary: 그 조각에서 독자가 알게 될 내용을 한국어 한 문장으로
만화 전체의 한국어 제목(title_ko)도 정한다.

논문 제목: {title}

초록:
{abstract}

조각 목록 (형식: [조각 번호] 문장 범위 / 포함된 섹션 / 앞부분 문장):
{chunks}
"""

STYLE_EXAMPLE = """말투 예시 (이 말투를 그대로 따른다):
누리: 박사님, 잠재 공간이 뭐예요?
박사님: 좋은 질문이야, 누리야. 커다란 그림을 메모 한 장으로 줄여 두는 곳이라고 생각하면 돼.
해설: 잠재 공간은 이미지의 핵심 특징만 담은 작은 공간이다.
- 박사님은 누리에게 '~야', '~해', '~거든'처럼 반말로 말한다.
- 누리는 박사님에게 '~요', '~나요?'처럼 존댓말로 말한다.
- 해설은 인물에게 말을 걸지 않고 '~이다', '~한다'로 끝나는 설명문으로 쓴다."""

PANEL_SYSTEM = base.DIALOGUE_SYSTEM + "\n\n" + STYLE_EXAMPLE

PANEL_INSTRUCTIONS = """학습 만화 {page}쪽(전체 {total}쪽)의 컷 하나를 써라.

이번 쪽에서 다루는 내용: {page_topics}
{previous}
이 컷이 전할 사실: {fact}
(근거 원문: {quotes})

규칙:
1. 이 컷은 위의 사실 하나만 전한다. 사실에 없는 숫자나 주장을 덧붙이지 않는다.
2. 누리가 궁금한 점을 묻거나 반응하고, 박사님이 생활 속 비유를 곁들여 쉽게 설명한다.
3. scene에는 그림 작가가 그릴 수 있게 장소, 인물의 행동과 표정을 구체적으로 쓴다. 바로 앞 컷과 다른 장면으로 한다.
4. lines에는 대사를 1~3개 넣는다. speaker는 누리, 박사님, 해설 중 하나다.
5. 대사 안에 [23], (p.3), F1 같은 번호 표시와 '학습 만화'라는 말을 쓰지 않는다. 앞 컷의 대사를 반복하지 않는다.
"""

STAGING_INSTRUCTIONS = """학습 만화 {page}쪽(전체 {total}쪽)의 연출 컷 두 개를 써라.

이번 쪽에서 다루는 내용: {page_topics}
{previous}
이번 쪽의 사실 컷은 이렇게 시작하고 끝난다.
- 첫 사실 컷: {first_panel}
- 마지막 사실 컷: {last_panel}

- opening: 이번 쪽을 여는 컷. 앞 쪽에서 자연스럽게 이어지는 장면 전환이나 누리의 궁금증으로 시작한다.
- closing: 이번 쪽을 닫는 컷. 배운 내용에 대한 누리의 감탄이나 다음 내용에 대한 궁금증으로 마무리한다.

규칙:
1. 연출 컷이므로 논문의 숫자나 새로운 주장을 말하지 않는다.
2. scene에는 장소, 인물의 행동과 표정을 구체적으로 쓴다.
3. lines에는 대사를 1~3개 넣는다. speaker는 누리, 박사님, 해설 중 하나다.
4. 대사 안에 번호 표시와 '학습 만화'라는 말을 쓰지 않는다.
"""


def chunk_titles_schema(count):
    return {
        "type": "object",
        "properties": {
            "title_ko": {"type": "string"},
            "chunks": {
                "type": "array",
                "minItems": count,
                "maxItems": count,
                "items": {
                    "type": "object",
                    "properties": {
                        "title_ko": {"type": "string"},
                        "summary": {"type": "string"},
                    },
                    "required": ["title_ko", "summary"],
                },
            },
        },
        "required": ["title_ko", "chunks"],
    }


def staging_schema():
    return {
        "type": "object",
        "properties": {
            "opening": hybrid.panel_schema(),
            "closing": hybrid.panel_schema(),
        },
        "required": ["opening", "closing"],
    }


# ---------- 조각 나누기 (코드) ----------


def build_chunks(sections, total_sentences, chunk_count):
    """섹션 경계를 따라 본문을 chunk_count개 조각으로 고르게 나눈다. 아주 큰 섹션은 먼저 문장 단위로 나눈다."""
    target = total_sentences / chunk_count
    units = []
    for section in sections:
        size = section["end"] - section["start"] + 1
        parts = math.ceil(size / target) if size > 1.5 * target else 1
        for part in range(parts):
            start = section["start"] + round(size * part / parts)
            end = section["start"] + round(size * (part + 1) / parts) - 1
            units.append({"title": section["title"], "start": start, "end": end})

    chunk_count = min(chunk_count, len(units))
    boundaries = [
        unit["end"] for unit in units[:-1]
    ]  # 이 문장 번호 뒤에서 나눌 수 있음
    cuts = []
    for k in range(1, chunk_count):
        remaining = chunk_count - k
        candidates = [
            b
            for i, b in enumerate(boundaries)
            if (not cuts or b > cuts[-1]) and len(boundaries) - i >= remaining
        ]
        cuts.append(
            min(candidates, key=lambda b: abs(b - total_sentences * k / chunk_count))
        )

    chunks = []
    start = 1
    for end in cuts + [total_sentences]:
        titles = list(
            dict.fromkeys(
                u["title"] for u in units if u["start"] >= start and u["end"] <= end
            )
        )
        chunks.append({"start": start, "end": end, "sections": titles})
        start = end + 1
    return chunks


def build_outline_prompt(paper, chunks, sentences):
    abstract = " ".join(s["text"] for s in paper["sections"][0]["sentences"])
    lines = []
    for index, chunk in enumerate(chunks):
        leading = [
            sentences[n]["text"]
            for n in range(chunk["start"], chunk["end"] + 1)
            if not hybrid.is_table_fragment(sentences[n]["text"])
        ][:2]
        lines.append(
            f"[{index}] 문장 {chunk['start']}~{chunk['end']} / 섹션: {', '.join(chunk['sections'])} / "
            + " ".join(text[:200] for text in leading)
        )
    return OUTLINE_INSTRUCTIONS.format(
        count=len(chunks),
        title=paper["title"],
        abstract=abstract,
        chunks="\n".join(lines),
    )


# ---------- 사실 고르기 ----------


def text_similarity(a, b):
    """글자 두 개씩 묶은 조각이 얼마나 겹치는지(0~1)."""

    def grams(text):
        text = re.sub(r"[^가-힣A-Za-z0-9]", "", text)
        return {text[i : i + 2] for i in range(len(text) - 1)}

    first, second = grams(a), grams(b)
    return len(first & second) / len(first | second) if first and second else 0.0


def remove_similar_facts(facts, accepted, log):
    kept = []
    for fact in facts:
        if any(
            text_similarity(fact["fact"], other["fact"]) >= SIMILAR_FACT_THRESHOLD
            for other in accepted + kept
        ):
            log["dropped"].append(fact["fact"])
            continue
        kept.append(fact)
    return kept


# ---------- 컷 쓰기 ----------


def tidy_lines(lines):
    """대사 정리: (해설: …)는 해설 줄로 꺼내고, 번호 표시와 빈 괄호를 지우고, 줄바꿈을 한 칸 띄어쓰기로 바꾼다."""
    result = []
    for line in lines:
        narrations = hybrid.NARRATION_IN_LINE.findall(line["text"])
        text = hybrid.NARRATION_IN_LINE.sub("", line["text"])
        text = MARK_IN_PARENS.sub("", text)
        text = base.MARK_IN_LINE.sub("", text)
        text = " ".join(EMPTY_PARENS.sub("", text).split())
        if text:
            result.append({"speaker": line["speaker"], "text": text})
        result.extend(
            {"speaker": "해설", "text": " ".join(n.split())} for n in narrations
        )
    return result


def check_panel(panel, seen_lines):
    problems = []
    if len(panel["scene"].strip()) < MIN_SCENE_LENGTH:
        problems.append(
            f"장면 설명이 너무 짧다: \"{panel['scene']}\". 장소, 행동, 표정을 구체적으로 써라."
        )
    for line in panel["lines"]:
        text = hybrid.NARRATION_IN_LINE.sub("", line["text"]).strip()
        if base.MARK_IN_LINE.search(text) or MARK_IN_PARENS.search(text):
            problems.append(f'대사에 번호 표시가 들어 있다: "{text[:60]}"')
        if "학습 만화" in text:
            problems.append("대사에 '학습 만화'라는 말이 들어 있다.")
        if line["speaker"] == "박사님" and any(
            base.POLITE_ENDING.search(s.strip()) for s in SENTENCE_END.findall(text)
        ):
            problems.append(
                f'박사님이 존댓말을 썼다: "{text[:60]}". 박사님은 누리에게 반말을 쓴다.'
            )
        if line["speaker"] == "해설" and "누리야" in text:
            problems.append(
                f'해설이 누리에게 말을 걸었다: "{text[:60]}". 해설은 설명문으로 쓴다.'
            )
        if len(text) >= base.MIN_DUPLICATE_LINE_LENGTH and text in seen_lines:
            problems.append(f'앞 컷과 같은 대사다: "{text[:60]}"')
    return problems


def describe(panel):
    lines = " / ".join(f"{line['speaker']}: {line['text']}" for line in panel["lines"])
    return f"장면: {panel['scene']} / 대사: {lines}"


def write_fact_panel(
    model,
    page_number,
    total_pages,
    page_topics,
    previous_panel,
    fact,
    sentences,
    seen_lines,
):
    quotes = " / ".join(f"[{n}] {sentences[n]['text'][:200]}" for n in fact["evidence"])
    previous = f"바로 앞 컷 — {describe(previous_panel)}\n" if previous_panel else ""
    prompt = PANEL_INSTRUCTIONS.format(
        page=page_number,
        total=total_pages,
        page_topics=page_topics,
        previous=previous,
        fact=fact["fact"],
        quotes=quotes,
    )
    result, problems, attempts = base.ask_with_retry(
        model,
        PANEL_SYSTEM,
        prompt,
        hybrid.panel_schema(),
        lambda r: check_panel(r, seen_lines),
    )
    panel = {
        "scene": " ".join(result["scene"].split()),
        "lines": tidy_lines(result["lines"]),
        "staging": False,
        "fact": fact["fact"],
        "evidence": fact["evidence"],  # 근거는 코드가 붙인다
        "problems": problems,
        "attempts": attempts,
    }
    seen_lines.update(line["text"] for line in panel["lines"])
    return panel


def write_staging_panels(
    model,
    page_number,
    total_pages,
    page_topics,
    previous_panel,
    fact_panels,
    seen_lines,
):
    previous = (
        f"앞 쪽의 마지막 컷 — {describe(previous_panel)}\n" if previous_panel else ""
    )
    prompt = STAGING_INSTRUCTIONS.format(
        page=page_number,
        total=total_pages,
        page_topics=page_topics,
        previous=previous,
        first_panel=describe(fact_panels[0]) if fact_panels else "없음",
        last_panel=describe(fact_panels[-1]) if fact_panels else "없음",
    )
    result, problems, attempts = base.ask_with_retry(
        model,
        PANEL_SYSTEM,
        prompt,
        staging_schema(),
        lambda r: check_panel(r["opening"], seen_lines)
        + check_panel(r["closing"], seen_lines),
    )
    panels = []
    for key in ["opening", "closing"]:
        panels.append(
            {
                "scene": " ".join(result[key]["scene"].split()),
                "lines": tidy_lines(result[key]["lines"]),
                "staging": True,
                "fact": None,
                "evidence": [],
                "problems": [],
                "attempts": [],
            }
        )
    panels[0]["problems"] = problems
    panels[0]["attempts"] = attempts
    return panels


# ---------- 결과 정리 ----------


def render_markdown(models, title_ko, pages, sentences):
    out = [
        f"# {title_ko}",
        f"조각 제목·사실 고르기: `{models['outline']}` / 컷 쓰기: `{models['dialogue']}`",
        "",
        "## 구조도",
        "",
    ]
    for page_number, page in enumerate(pages, start=1):
        for chunk in page["chunks"]:
            out.append(
                f"- {page_number}쪽 · **{chunk['title_ko']}** [{chunk['start']}~{chunk['end']}] — {chunk['summary']}"
            )
    out.append("")
    for page_number, page in enumerate(pages, start=1):
        out.append(
            f"## {page_number}쪽 — {', '.join(chunk['title_ko'] for chunk in page['chunks'])}"
        )
        dropped = [text for log in page["fact_log"] for text in log["dropped"]]
        if dropped:
            out.append(
                "버린 사실(근거가 틀리거나 겹치거나 비슷함): " + " / ".join(dropped)
            )
        out.append("")
        for index, panel in enumerate(page["panels"], start=1):
            tag = " (연출)" if panel["staging"] else ""
            out.append(f"**컷 {index}{tag}** — {panel['scene']}")
            if panel["problems"]:
                out.append("  - 남은 문제: " + " / ".join(panel["problems"]))
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
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "--outline-model", default="qwen3:8b", help="조각 제목과 사실 고르기에 쓸 모델"
    )
    parser.add_argument(
        "--dialogue-model", default="exaone3.5:7.8b", help="컷 쓰기에 쓸 모델"
    )
    parser.add_argument(
        "--dry-run", action="store_true", help="AI 호출 없이 조각 나누기만 확인"
    )
    args = parser.parse_args()
    models = {"outline": args.outline_model, "dialogue": args.dialogue_model}

    paper = json.loads(base.PAPER_JSON.read_text(encoding="utf-8"))
    sections, sentences = base.number_sentences(paper)
    total_pages = 4 if len(sentences) >= base.FOUR_PAGE_MIN_SENTENCES else 3
    chunks = build_chunks(sections, len(sentences), total_pages * CHUNKS_PER_PAGE)
    outline_prompt = build_outline_prompt(paper, chunks, sentences)

    print(
        f"조각 제목·사실 모델: {models['outline']} / 컷 모델: {models['dialogue']} / Ollama 주소: {base.OLLAMA_URL}"
    )
    print(
        f"본문 문장 수: {len(sentences)} (부록 제외), 쪽 수: {total_pages}, 조각 수: {len(chunks)}"
    )
    for index, chunk in enumerate(chunks):
        print(
            f"  {index // CHUNKS_PER_PAGE + 1}쪽 · 조각 {index} [{chunk['start']}~{chunk['end']}] "
            f"{chunk['end'] - chunk['start'] + 1}문장: {', '.join(chunk['sections'])}"
        )
    if args.dry_run:
        print(f"\n조각 제목 프롬프트 글자 수: {len(outline_prompt):,}")
        return

    total_started = time.time()
    stage_seconds = {}

    print(f"\n[① 조각 제목] {models['outline']}")
    started = time.time()
    content, _ = base.call_ollama(
        models["outline"],
        base.OUTLINE_SYSTEM,
        [{"role": "user", "content": outline_prompt}],
        chunk_titles_schema(len(chunks)),
    )
    outline = json.loads(content)
    for chunk, titled in zip(chunks, outline["chunks"]):
        chunk.update(title_ko=titled["title_ko"], summary=titled["summary"])
    stage_seconds["outline"] = round(time.time() - started)
    print(f"  {stage_seconds['outline']}초. 제목: {outline['title_ko']}")
    for chunk in chunks:
        print(f"  [{chunk['start']}~{chunk['end']}] {chunk['title_ko']}")

    print(f"\n[② 사실 고르기] {models['outline']}")
    started = time.time()
    used_evidence = set()
    accepted = []
    pages = []
    for page_index in range(0, len(chunks), CHUNKS_PER_PAGE):
        page_chunks = chunks[page_index : page_index + CHUNKS_PER_PAGE]
        facts = []
        fact_log = []
        for chunk in page_chunks:
            print(f"  [{chunk['start']}~{chunk['end']}] {chunk['title_ko']}")
            chunk_facts, log = hybrid.pick_facts(
                models["outline"], chunk, FACTS_PER_CHUNK, sentences, used_evidence
            )
            chunk_facts = remove_similar_facts(chunk_facts, accepted, log)
            accepted.extend(chunk_facts)
            facts.extend(chunk_facts)
            fact_log.append(log)
        pages.append({"chunks": page_chunks, "facts": facts, "fact_log": fact_log})
    stage_seconds["facts"] = round(time.time() - started)

    print(f"\n[③ 컷 쓰기] {models['dialogue']}")
    started = time.time()
    previous_panel = None
    for page_number, page in enumerate(pages, start=1):
        page_topics = ", ".join(chunk["title_ko"] for chunk in page["chunks"])
        print(
            f"  {page_number}/{len(pages)}쪽 — 사실 컷 {len(page['facts'])}개 + 연출 컷 2개"
        )
        seen_lines = set()
        fact_panels = []
        for fact in page["facts"]:
            panel = write_fact_panel(
                models["dialogue"],
                page_number,
                len(pages),
                page_topics,
                fact_panels[-1] if fact_panels else previous_panel,
                fact,
                sentences,
                seen_lines,
            )
            fact_panels.append(panel)
        opening, closing = write_staging_panels(
            models["dialogue"],
            page_number,
            len(pages),
            page_topics,
            previous_panel,
            fact_panels,
            seen_lines,
        )
        page["panels"] = [opening] + fact_panels + [closing]
        previous_panel = closing
    stage_seconds["panels"] = round(time.time() - started)

    all_panels = [panel for page in pages for panel in page["panels"]]
    summary = {
        "models": models,
        "total_seconds": round(time.time() - total_started),
        "stage_seconds": stage_seconds,
        "chunks": len(chunks),
        "pages": len(pages),
        "panels": len(all_panels),
        "fact_panels": sum(1 for panel in all_panels if not panel["staging"]),
        "staging_panels": sum(1 for panel in all_panels if panel["staging"]),
        "dropped_facts": sum(
            len(log["dropped"]) for page in pages for log in page["fact_log"]
        ),
        "remaining_problems": sum(len(panel["problems"]) for panel in all_panels),
    }
    print("\n===== 요약 =====")
    for key, value in summary.items():
        print(f"{key}: {value}")

    RESULT_DIR.mkdir(exist_ok=True)
    name = f"chunk_{models['outline'].replace(':', '_')}__{models['dialogue'].replace(':', '_')}"
    (RESULT_DIR / f"{name}.json").write_text(
        json.dumps(
            {"summary": summary, "title_ko": outline["title_ko"], "pages": pages},
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    (RESULT_DIR / f"{name}.md").write_text(
        render_markdown(models, outline["title_ko"], pages, sentences), encoding="utf-8"
    )
    print(f"\n결과 저장: {RESULT_DIR / f'{name}.md'}")


if __name__ == "__main__":
    main()
