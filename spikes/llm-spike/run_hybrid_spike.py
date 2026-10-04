"""단계마다 잘하는 모델을 따로 쓰는 학습 만화 대본 생성 실험 스크립트 (5차 시험용).

4차 시험(run_outline_spike.py)에서 qwen3는 형식을 잘 지키고(목차 균형, 사실 배정), exaone은 글을 잘 썼다(Why책 느낌).
그래서 단계별로 모델을 나누고, 4차에서 나온 문제를 고쳤다.

  ① 구조도      (기본 qwen3:8b)      : 목차 항목을 만든다. 너무 큰 항목은 코드가 섹션 경계에서 나눈다.
  ② 사실 고르기  (기본 qwen3:8b)      : 목차 항목마다 핵심 사실과 근거 번호를 뽑는다.
                                      표 조각 문장은 AI에게 보여 주지 않고, 근거가 겹치는 사실은 코드가 버린다.
  ③ 대사 쓰기   (기본 exaone3.5:7.8b) : 응답 모양을 '사실 컷 n개(순서대로)' + '연출 컷 최대 2개(들어갈 위치)'로 나눠서,
                                      사실을 빼먹거나 순서를 바꾸는 일이 구조상 불가능하게 한다. 근거는 코드가 붙인다.

그래픽카드 메모리(8GB)에 두 모델이 동시에 올라가지 않으므로, ①② 를 모든 쪽에 대해 먼저 끝내고 ③ 을 한꺼번에 한다.
(모델 교체가 한 번만 일어난다.)

사용법:
    python3 spikes/llm-spike/run_hybrid_spike.py --dry-run     # AI 호출 없이 목차 입력과 걸러지는 표 조각 확인
    OLLAMA_URL=http://<주 서버 IP>:11434 python3 spikes/llm-spike/run_hybrid_spike.py
    OLLAMA_URL=http://<주 서버 IP>:11434 python3 spikes/llm-spike/run_hybrid_spike.py --outline-model qwen3:8b --dialogue-model exaone3.5:7.8b

입력: read_manga/results/2026-10-02_grobid-api.json
출력: spikes/llm-spike/results/hybrid_<구조도 모델>__<대사 모델>.json, .md
"""

import argparse
import json
import re
import time
from pathlib import Path

import run_outline_spike as base  # Ollama 호출, 문장 번호 붙이기, 다시 쓰기 같은 공통 부분은 4차 코드를 그대로 쓴다

FACTS_PER_PAGE = 6
MAX_ITEM_SENTENCES = 60
MAX_EXTRA_PANELS = 2
RESULT_DIR = base.RESULT_DIR

NUMERIC_TOKEN = re.compile(r"^[\d.,±×%()\[\]\-–—↑↓/:;]+$")
NARRATION_IN_LINE = re.compile(r"\(\s*해설\s*[:：]\s*(.+?)\s*\)")
CITATION_IN_FACT = re.compile(r"\s*\[\d+(\s*,\s*\d+)*\]")

OUTLINE_INSTRUCTIONS = """아래 논문의 전체 구조도를 만들어라. 이 구조도가 학습 만화의 목차가 된다.

목차 항목은 7~10개로 하고, 항목마다 다음을 정한다.
- title_ko: 독자가 한눈에 알 수 있는 짧은 한국어 소제목
- summary: 이 항목에서 독자가 알게 될 내용을 한국어 한 문장으로
- first_section: 이 항목이 시작되는 섹션 번호 (아래 섹션 목록의 번호)

규칙:
1. 첫 항목의 first_section은 0이다. 뒤 항목일수록 first_section이 커져야 한다. 한 항목은 다음 항목의 first_section 바로 앞 섹션까지를 다룬다.
2. 목차 흐름은 연구가 왜 필요했는지(문제) → 어떻게 해결했는지(방법) → 실험해 보니 어땠는지(결과) → 왜 중요하고 한계는 무엇인지 순서로 한다.
3. 항목마다 다루는 문장 수가 비슷하게 나눈다. 전체 {total}문장이므로 한 항목은 대략 {target}문장 안팎이 되게 하고, 앞부분에만 항목을 몰지 않는다.
4. 실험 결과, 한계, 결론은 서로 다른 항목으로 나눈다.
5. 만화 전체의 한국어 제목(title_ko)도 정한다.

논문 제목: {title}

초록:
{abstract}

섹션 목록 (형식: [섹션 번호] 섹션 제목 (문장 수) — 첫 문장):
{sections}
"""

FACT_INSTRUCTIONS = """학습 만화의 목차 항목: {title} — {summary}

아래 논문 문장에서 이 항목에 맞는 핵심 사실 {count}개를 뽑아라.

규칙:
1. fact는 중학생도 이해할 수 있는 쉬운 한국어 한 문장으로 쓴다. 문장에 적힌 내용만 쓰고, 숫자는 문장에 있는 그대로 쓴다.
2. 사실의 주어는 이 논문의 연구나 방법(예: 이 연구, 잠재 확산 모델, LDM)으로 쓴다. '학습 만화'라는 말은 쓰지 않는다.
3. p(x), x_t 같은 수식 기호나 변수 이름은 쓰지 않는다. 수식의 세부 내용보다 그 수식이 하는 일을 쉬운 말로 쓴다.
4. evidence에는 그 사실이 적힌 문장 번호를 1~3개 넣는다. 반드시 아래 목록에 있는 번호만 쓴다.
5. 사실끼리 내용과 근거 문장이 겹치지 않게 한다. 범위의 앞부분만 보지 말고 전체에서 고르게 고른다.
6. fact 안에 [11] 같은 번호를 쓰지 않는다.

논문 문장 (형식: [문장 번호] (p.페이지) 문장):
{sentences}
"""

DIALOGUE_INSTRUCTIONS = """학습 만화의 {page}쪽(전체 {total}쪽) 대본을 써라.

전체 목차:
{toc}

이번 쪽에서 다루는 목차: {page_items}
{previous}
이번 쪽에서 전할 사실 (순서대로, 괄호 안은 근거가 된 원문):
{facts}

응답 모양:
- fact_panels: 사실 컷 {count}개. 1번째 컷은 F1, 2번째 컷은 F2를 전하는 식으로 순서대로 정확히 {count}개를 쓴다.
- extra_panels: 연출 컷(감탄, 비유, 장면 전환) 1~{max_extra}개. after에는 몇 번째 사실 컷 뒤에 넣을지 쓴다(0이면 맨 앞).

규칙:
1. 사실 컷의 대사는 배정된 사실을 쉽게 전한다. 사실에 없는 숫자나 주장을 덧붙이지 않는다.
2. Why책처럼 누리가 궁금한 점을 묻고, 박사님이 생활 속 비유를 곁들여 설명한다.
3. scene에는 그림 작가가 그릴 수 있게 장소, 인물의 행동과 표정을 구체적으로 쓴다. 컷마다 장면이 달라야 한다.
4. lines에는 대사를 1~3개 넣는다. speaker는 누리, 박사님, 해설 중 하나다. 해설은 대사 안에 괄호로 넣지 말고 speaker를 해설로 한 줄로 쓴다.
5. 박사님은 누리에게 반말을, 누리는 박사님에게 존댓말을 쓴다.
6. 대사 안에 [23], (p.3), F1 같은 번호 표시를 쓰지 않는다. 같은 대사를 반복하지 않는다. '학습 만화'라는 말은 쓰지 않는다.
"""


def outline_schema():
    schema = base.outline_schema()
    schema["properties"]["items"]["minItems"] = 7
    return schema


def panel_schema():
    return {
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
        },
        "required": ["scene", "lines"],
    }


def dialogue_schema(fact_count):
    extra = panel_schema()
    extra["properties"]["after"] = {
        "type": "integer",
        "enum": list(range(fact_count + 1)),
    }
    extra["required"] = ["after", "scene", "lines"]
    return {
        "type": "object",
        "properties": {
            "fact_panels": {
                "type": "array",
                "minItems": fact_count,
                "maxItems": fact_count,
                "items": panel_schema(),
            },
            "extra_panels": {
                "type": "array",
                "minItems": 0,
                "maxItems": MAX_EXTRA_PANELS,
                "items": extra,
            },
        },
        "required": ["fact_panels", "extra_panels"],
    }


# ---------- 논문 준비 ----------


def is_table_fragment(text):
    """'CelebA-HQ 256 × 256 Method FID ↓ Prec.' 처럼 숫자·기호가 대부분인 표 조각 문장인지 판단한다."""
    tokens = text.split()
    if len(tokens) < 4:
        return True
    numeric = sum(1 for token in tokens if NUMERIC_TOKEN.match(token))
    return numeric / len(tokens) > 0.3


# ---------- ① 구조도 ----------


def build_outline_prompt(paper, sections, sentences):
    abstract = " ".join(s["text"] for s in paper["sections"][0]["sentences"])
    lines = [
        f"[{index}] {sec['title']} ({sec['end'] - sec['start'] + 1}) — {sentences[sec['start']]['text'][:150]}"
        for index, sec in enumerate(sections)
    ]
    total = len(sentences)
    return OUTLINE_INSTRUCTIONS.format(
        title=paper["title"],
        abstract=abstract,
        sections="\n".join(lines),
        total=total,
        target=round(total / 8),
    )


def split_large_items(items, sections):
    """MAX_ITEM_SENTENCES보다 큰 목차 항목은 섹션 경계에서 나눈다. 한계·결론이 실험 항목에 묻히지 않게 하기 위함."""
    result = []
    for item in items:
        size = item["end"] - item["start"] + 1
        if size <= MAX_ITEM_SENTENCES or len(item["sections"]) < 2:
            result.append(item)
            continue
        parts = max(2, round(size / MAX_ITEM_SENTENCES))
        chunks = []
        current = []
        for section_index in item["sections"]:
            current.append(section_index)
            current_size = (
                sections[current[-1]]["end"] - sections[current[0]]["start"] + 1
            )
            if current_size >= size / parts and len(chunks) < parts - 1:
                chunks.append(current)
                current = []
        if current:
            chunks.append(current)
        for number, chunk in enumerate(chunks, start=1):
            result.append(
                {
                    **item,
                    "title_ko": (
                        f"{item['title_ko']} ({number}/{len(chunks)})"
                        if len(chunks) > 1
                        else item["title_ko"]
                    ),
                    "first_section": chunk[0],
                    "sections": chunk,
                    "start": sections[chunk[0]]["start"],
                    "end": sections[chunk[-1]]["end"],
                }
            )
    return result


# ---------- ② 사실 고르기 ----------


def pick_facts(model, item, count, sentences, used_evidence):
    numbers = [
        n
        for n in range(item["start"], item["end"] + 1)
        if not is_table_fragment(sentences[n]["text"])
    ]
    allowed = set(numbers)
    prompt = FACT_INSTRUCTIONS.format(
        title=item["title_ko"],
        summary=item["summary"],
        count=count,
        sentences="\n".join(
            f"[{n}] (p.{sentences[n]['page']}) {sentences[n]['text']}" for n in numbers
        ),
    )
    result, problems, attempts = base.ask_with_retry(
        model,
        base.FACT_SYSTEM,
        prompt,
        base.fact_schema(count),
        lambda r: base.check_facts(r, allowed),
    )

    facts = []
    dropped = []
    for fact in result["facts"]:
        text = CITATION_IN_FACT.sub("", fact["fact"]).strip()
        evidence = [
            n
            for n in dict.fromkeys(fact["evidence"])
            if n in allowed and n not in used_evidence
        ]
        if not evidence:
            dropped.append(text)  # 근거가 틀렸거나 앞 사실과 근거가 겹치면 버린다
            continue
        used_evidence.update(evidence)
        facts.append({"fact": text, "evidence": evidence, "item": item["title_ko"]})
    return facts, {
        "item": item["title_ko"],
        "problems": problems,
        "dropped": dropped,
        "attempts": attempts,
    }


# ---------- ③ 대사 쓰기 ----------


def tidy_lines(lines):
    """대사 안의 '(해설: …)'는 해설 줄로 꺼내고, 번호 표시는 지운다."""
    result = []
    for line in lines:
        narrations = NARRATION_IN_LINE.findall(line["text"])
        text = base.MARK_IN_LINE.sub(
            "", NARRATION_IN_LINE.sub("", line["text"])
        ).strip()
        if text:
            result.append({"speaker": line["speaker"], "text": text})
        result.extend(
            {"speaker": "해설", "text": narration.strip()} for narration in narrations
        )
    return result


def check_dialogue(result):
    problems = []
    seen_lines = set()
    panels = result["fact_panels"] + result["extra_panels"]
    for index, panel in enumerate(panels, start=1):
        for line in panel["lines"]:
            text = NARRATION_IN_LINE.sub("", line["text"]).strip()
            if base.MARK_IN_LINE.search(text):
                problems.append(
                    f'{index}번 컷의 대사에 번호 표시가 들어 있다: "{text[:60]}"'
                )
            if line["speaker"] == "박사님" and base.POLITE_ENDING.search(text):
                problems.append(
                    f'{index}번 컷에서 박사님이 존댓말을 썼다: "{text[:60]}". 박사님은 누리에게 반말을 쓴다.'
                )
            if "학습 만화" in text:
                problems.append(
                    f"{index}번 컷의 대사에 '학습 만화'라는 말이 들어 있다."
                )
            if len(text) >= base.MIN_DUPLICATE_LINE_LENGTH and text in seen_lines:
                problems.append(f'{index}번 컷의 대사 "{text[:60]}"가 앞 컷과 똑같다.')
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
        count=len(facts),
        max_extra=MAX_EXTRA_PANELS,
    )
    result, problems, attempts = base.ask_with_retry(
        model, base.DIALOGUE_SYSTEM, prompt, dialogue_schema(len(facts)), check_dialogue
    )

    # 사실 컷은 순서대로, 연출 컷은 지정한 위치에 끼워 넣는다. 근거는 AI가 아니라 코드가 사실에서 붙인다.
    extras = sorted(result["extra_panels"], key=lambda panel: panel["after"])
    panels = []
    for position in range(len(facts) + 1):
        if position > 0:
            fact_panel = result["fact_panels"][position - 1]
            fact = facts[position - 1]
            panels.append(
                {
                    "scene": fact_panel["scene"],
                    "lines": tidy_lines(fact_panel["lines"]),
                    "staging": False,
                    "fact": fact["fact"],
                    "evidence": fact["evidence"],
                }
            )
        for extra in (e for e in extras if e["after"] == position):
            panels.append(
                {
                    "scene": extra["scene"],
                    "lines": tidy_lines(extra["lines"]),
                    "staging": True,
                    "fact": None,
                    "evidence": [],
                }
            )
    return panels, problems, attempts


# ---------- 결과 정리 ----------


def render_markdown(models, title_ko, pages, sentences):
    out = [
        f"# {title_ko}",
        f"구조도·사실 고르기: `{models['outline']}` / 대사 쓰기: `{models['dialogue']}`",
        "",
        "## 구조도",
        "",
    ]
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
        dropped = [text for log in page["fact_log"] for text in log["dropped"]]
        if dropped:
            out.append("버린 사실(근거가 틀리거나 겹침): " + " / ".join(dropped))
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
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "--outline-model", default="qwen3:8b", help="구조도와 사실 고르기에 쓸 모델"
    )
    parser.add_argument(
        "--dialogue-model", default="exaone3.5:7.8b", help="대사 쓰기에 쓸 모델"
    )
    parser.add_argument(
        "--dry-run", action="store_true", help="AI 호출 없이 입력만 확인"
    )
    args = parser.parse_args()
    models = {"outline": args.outline_model, "dialogue": args.dialogue_model}

    paper = json.loads(base.PAPER_JSON.read_text(encoding="utf-8"))
    sections, sentences = base.number_sentences(paper)
    outline_prompt = build_outline_prompt(paper, sections, sentences)
    fragments = [n for n in sentences if is_table_fragment(sentences[n]["text"])]

    print(
        f"구조도·사실 모델: {models['outline']} / 대사 모델: {models['dialogue']} / Ollama 주소: {base.OLLAMA_URL}"
    )
    print(
        f"본문 문장 수: {len(sentences)} (부록 제외), 섹션 수: {len(sections)}, 표 조각으로 빼는 문장: {len(fragments)}개"
    )
    if args.dry_run:
        for n in fragments:
            print(f"  표 조각 [{n}] {sentences[n]['text'][:90]}")
        print(f"\n구조도 프롬프트 글자 수: {len(outline_prompt):,}")
        print(outline_prompt)
        return

    total_started = time.time()
    stage_seconds = {}

    print(f"\n[① 구조도] {models['outline']}")
    started = time.time()
    content, _ = base.call_ollama(
        models["outline"],
        base.OUTLINE_SYSTEM,
        [{"role": "user", "content": outline_prompt}],
        outline_schema(),
    )
    outline = json.loads(content)
    items = split_large_items(
        base.normalize_outline(outline["items"], sections), sections
    )
    page_groups = base.group_pages(items, len(sentences))
    stage_seconds["outline"] = round(time.time() - started)
    print(f"  {stage_seconds['outline']}초. 제목: {outline['title_ko']}")
    for page_number, page_items in enumerate(page_groups, start=1):
        for item in page_items:
            print(
                f"  {page_number}쪽 · {item['title_ko']} [{item['start']}~{item['end']}]"
            )
    toc = "\n".join(
        f"{page_number}쪽: " + ", ".join(item["title_ko"] for item in page_items)
        for page_number, page_items in enumerate(page_groups, start=1)
    )

    print(f"\n[② 사실 고르기] {models['outline']}")
    started = time.time()
    used_evidence = set()
    pages = []
    for page_number, page_items in enumerate(page_groups, start=1):
        facts = []
        fact_log = []
        for item, count in zip(page_items, base.split_fact_counts(page_items)):
            print(f"  {page_number}쪽 · {item['title_ko']} — 사실 {count}개")
            item_facts, log = pick_facts(
                models["outline"], item, count, sentences, used_evidence
            )
            facts.extend(item_facts)
            fact_log.append(log)
        pages.append({"items": page_items, "facts": facts, "fact_log": fact_log})
    stage_seconds["facts"] = round(time.time() - started)

    print(f"\n[③ 대사 쓰기] {models['dialogue']}")
    started = time.time()
    previous = ""
    for page_number, page in enumerate(pages, start=1):
        print(f"  {page_number}/{len(pages)}쪽 (사실 {len(page['facts'])}개)")
        panels, problems, attempts = write_page(
            models["dialogue"],
            page_number,
            len(pages),
            toc,
            page["items"],
            page["facts"],
            previous,
            sentences,
        )
        page.update(
            {"panels": panels, "problems": problems, "dialogue_attempts": attempts}
        )
        print(f"    컷 {len(panels)}개, 남은 문제 {len(problems)}개")
        previous = (
            f"이전 쪽은 이렇게 끝났다: {panels[-1]['scene']}\n"
            "이번 쪽은 이 장면에서 자연스럽게 이어지되, 이전 쪽의 대사를 반복하지 않는다.\n"
        )
    stage_seconds["dialogue"] = round(time.time() - started)

    all_panels = [panel for page in pages for panel in page["panels"]]
    summary = {
        "models": models,
        "total_seconds": round(time.time() - total_started),
        "stage_seconds": stage_seconds,
        "outline_items": len(items),
        "pages": len(pages),
        "panels": len(all_panels),
        "fact_panels": sum(1 for panel in all_panels if not panel["staging"]),
        "staging_panels": sum(1 for panel in all_panels if panel["staging"]),
        "dropped_facts": sum(
            len(log["dropped"]) for page in pages for log in page["fact_log"]
        ),
        "remaining_problems": sum(len(page["problems"]) for page in pages),
    }
    print("\n===== 요약 =====")
    for key, value in summary.items():
        print(f"{key}: {value}")

    RESULT_DIR.mkdir(exist_ok=True)
    name = f"hybrid_{models['outline'].replace(':', '_')}__{models['dialogue'].replace(':', '_')}"
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
