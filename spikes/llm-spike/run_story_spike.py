"""기승전결 이야기로 학습 만화 대본을 만드는 실험 스크립트 (7차 시험용).

6차 시험(run_chunk_spike.py)에서 구조 문제(범위·순서·어긋남·형식)는 해결됐고,
대사가 사실보다 말을 더 하거나 바꾸는 문제와 '마asking' 같은 한글·영어 섞인 단어가 남았다. 고친 점:

  - 기승전결: 4쪽을 기(문제 제기)·승(해결 방법)·전(실험의 반전)·결(의미와 한계)에 맞춘다.
    대사 모델이 먼저 '이야기 뼈대'(누리가 겪는 사건과 쪽별 흐름)를 만들고, 연출 컷과 사실 컷이 그 흐름을 따른다.
  - 해설은 AI가 쓰지 않는다: 사실 컷의 해설 줄에는 코드가 검증된 사실 문장을 그대로 넣는다.
    AI는 누리와 박사님의 대사만 쓴다 (해설이 평가를 덧붙이던 문제 제거).
  - 대사 길이: 한 대사는 한두 문장, 60자 안팎. 80자를 넘으면 다시 쓰게 한다.
  - 앞 컷은 장면만 전달한다: 앞 컷의 대사를 주지 않아 내용을 끌고 오지 못하게 한다.
  - 주어를 분명히: 사실을 뽑을 때 '기존 확산 모델은'과 '이 연구는'을 구분하고, 대사에서 주어를 바꾸지 말라고 지시한다.
  - 사소한 실험 설정(GPU 종류, 표·그림 안내)은 사실로 고르지 않는다.
  - 한 단어 안에 한글 바로 뒤에 영어가 붙는 경우('라tent', '마asking')는 제목·사실·대사 어디서든 문제로 잡는다.

7차 시험 뒤 고친 점(2026-10-03, Spring으로 옮기기 전 마지막 정리):
  - 이야기 뼈대의 쪽별 흐름을 '기', '승', '전', '결' 이름이 붙은 칸으로 받는다 (한 칸씩 밀려 들어간 문제).
  - 근거 문장이 겹쳐도 내용이 다르면(유사도 0.35 미만) 사실을 남긴다 ('GAN보다 느리다' 한계가 버려진 문제).
    한계·결론 조각은 사실을 3개 뽑는다.
  - '했다'보다 '그래서 결과가 어땠다'를 고르게 하고, p(z|y) 같은 수식이 든 사실은 다시 뽑거나 버린다.
  - 컷 다시 쓰기는 2번까지, 대사를 감싼 따옴표는 지우고, 해설이 '~거야'처럼 인물 말투로 끝나면 문제로 잡는다.

사용법:
    python3 spikes/llm-spike/run_story_spike.py --dry-run
    OLLAMA_URL=http://<주 서버 IP>:11434 python3 spikes/llm-spike/run_story_spike.py

입력: read_manga/results/2026-10-02_grobid-api.json
출력: spikes/llm-spike/results/story_<사실 모델>__<대사 모델>.json, .md
"""

import argparse
import json
import re
import time

import run_chunk_spike as chunk  # 조각 나누기, 대사 정리, 비슷한 사실 거르기
import run_hybrid_spike as hybrid  # 표 조각 거르기, 컷 응답 모양
import run_outline_spike as base  # Ollama 호출, 문장 번호 붙이기, 다시 쓰기

MAX_LINE_LENGTH = 80
PANEL_RETRIES = 2  # 컷은 요청 하나가 작아서 다시 쓰기를 2번까지 한다
SHARED_EVIDENCE_SIMILARITY = 0.35  # 근거 문장이 겹치는 사실끼리 이보다 비슷하면 버린다
RESULT_DIR = base.RESULT_DIR

CLOSING_SECTION = re.compile(
    r"limitation|conclusion|discussion|future work", re.IGNORECASE
)
CLOSING_SECTION_FACTS = 3  # 한계·결론 조각은 사실을 하나 더 뽑는다
FORMULA = re.compile(
    r"\b[A-Za-z]\s*\([^()]*[|,][^()]*\)|\b[A-Za-z]\([A-Za-z_]\w*\)"
)  # p(z|y), p(x)
NARRATION_SPEECH_ENDING = re.compile(
    r"(야|지|단다|요|어|거든|네)[.!?~…]*$"
)  # 해설이 인물 말투로 끝남
QUOTE_MARKS = "\"“”'‘’"

MIXED_SCRIPT = re.compile(
    r"[가-힣][A-Za-z]"
)  # '라tent', '마asking'. 'LDM은', 'GAN보다'는 통과

PAGE_ROLES = {
    4: [
        (
            "기",
            "문제 제기: 누리가 겪는 사건으로 시작해, 이 연구가 왜 필요했는지 보여 준다",
        ),
        ("승", "해결 방법: 연구자들이 생각해 낸 방법을 차근차근 이해한다"),
        (
            "전",
            "반전과 절정: 실험해 보니 어떤 놀라운 결과가 나왔는지 긴장감 있게 보여 준다",
        ),
        (
            "결",
            "마무리: 결과의 의미와 한계를 정리하고, 처음 사건이 해결되며 누리가 깨닫는다",
        ),
    ],
    3: [
        (
            "기",
            "문제 제기: 누리가 겪는 사건으로 시작해, 이 연구가 왜 필요했는지 보여 준다",
        ),
        ("승·전", "해결 방법과 놀라운 실험 결과를 차근차근, 긴장감 있게 보여 준다"),
        (
            "결",
            "마무리: 결과의 의미와 한계를 정리하고, 처음 사건이 해결되며 누리가 깨닫는다",
        ),
    ],
}

TITLE_RULE = "영어 용어는 영어 그대로 쓰거나 한글로 옮겨 쓰되, 한 단어 안에서 한글과 영어를 섞지 않는다(예: '라tent'(X), 'Latent' 또는 '잠재'(O))."

FACT_INSTRUCTIONS = (
    """학습 만화의 목차 항목: {title} — {summary}

아래 논문 문장에서 이 항목에 맞는 핵심 사실 {count}개를 뽑아라.

규칙:
1. fact는 중학생도 이해할 수 있는 쉬운 한국어 한 문장으로 쓴다. 문장에 적힌 내용만 쓰고, 숫자는 문장에 있는 그대로 쓴다.
2. 주어를 분명히 쓴다. 이 논문이 제안한 방법이면 '이 연구는' 또는 '잠재 확산 모델은'으로, 기존 방법이나 다른 모델의 이야기면 '기존 확산 모델은', 'GAN은'처럼 쓴다. '학습 만화'라는 말은 쓰지 않는다.
3. p(x), p(z|y), x_t 같은 수식 기호나 변수 이름은 쓰지 않는다. 수식이 들어간 문장은 수식 대신 그 뜻을 쉬운 말로 쓴다.
4. 연구의 문제, 아이디어, 결과, 한계처럼 독자에게 의미 있는 내용을 고른다. GPU 종류, 학습 횟수, 표나 그림을 안내하는 문장 같은 사소한 실험 설정은 고르지 않는다.
   '~을 평가했다', '~을 따랐다', '~로 실험했다'처럼 무엇을 했는지만 말하는 문장보다, 그래서 어떤 결과가 나왔는지(숫자, 비교, 발견)를 말하는 문장을 먼저 고른다.
5. evidence에는 그 사실이 적힌 문장 번호를 1~3개 넣는다. 반드시 아래 목록에 있는 번호만 쓴다.
6. 사실끼리 내용과 근거 문장이 겹치지 않게 한다. fact 안에 [11] 같은 번호를 쓰지 않는다.
7. """
    + TITLE_RULE
    + """

논문 문장 (형식: [문장 번호] (p.페이지) 문장):
{sentences}
"""
)

STORY_SYSTEM = (
    "너는 연구 논문을 Why책 같은 학습 만화로 만드는 작가다.\n" + base.CHARACTERS
)

STORY_INSTRUCTIONS = """아래 논문으로 {total}쪽짜리 학습 만화를 만든다. 쪽마다 다룰 내용은 정해져 있다.
기승전결 이야기 뼈대를 만들어라.

- setting: 이야기의 배경과 누리가 겪는 사건 한두 문장. 누리의 일상에서 이 논문의 문제와 이어지는 사건이어야 한다.
  (예: 누리가 숙제로 고화질 그림을 컴퓨터로 만들려는데 몇 시간이 걸려서 박사님을 찾아간다)
- {role_keys}: 각 칸에 그 쪽의 이야기 흐름을 한 문장으로 쓴다. 칸 이름이 그 쪽의 역할이다. 대사를 따옴표로 넣지 말고 상황만 쓴다.
- ending: 마지막에 처음 사건이 어떻게 해결되고 누리가 무엇을 깨닫는지 한 문장.

규칙: 논문에 없는 숫자나 결과를 지어내지 않는다. 이야기 장치(사건, 장소, 감정)는 자유롭게 만들어도 된다.

논문 제목: {title}

쪽별 역할과 다룰 내용:
{pages}
"""

PANEL_SYSTEM = (
    "너는 연구 논문을 Why책 같은 학습 만화 대본으로 쓰는 작가다. "
    "주어진 사실만 사실로 말하고, 사실에 없는 숫자나 주장은 지어내지 않는다.\n"
    + base.CHARACTERS
    + "\n\n"
    + chunk.STYLE_EXAMPLE
)

PANEL_INSTRUCTIONS = """학습 만화 {page}쪽(전체 {total}쪽, 기승전결의 '{role}')의 사실 컷 하나를 써라.

이야기 배경: {setting}
이번 쪽의 이야기 흐름: {beat}
{previous}
이 컷이 전할 사실: {fact}
(근거 원문: {quotes})

규칙:
1. 이 컷은 위의 사실 하나만 전한다. 사실에 없는 숫자, 예시, 주장을 덧붙이지 않는다.
2. 사실의 주어를 바꾸지 않는다. 예: 기존 확산 모델의 문제를 잠재 확산 모델(이 연구)의 문제처럼 말하지 않는다.
3. 누리가 이야기 흐름에 맞게 묻거나 반응하고, 박사님이 생활 속 비유로 짧게 설명한다. 사실 문장은 해설로 따로 붙으니 대사로 그대로 읽지 않는다.
4. lines에는 대사를 1~2개 넣는다. speaker는 누리 또는 박사님이다. 한 대사는 한두 문장, 60자 안팎으로 짧게 쓴다.
5. scene에는 장소, 인물의 행동과 표정을 구체적으로 쓴다. 바로 앞 컷과 다른 장면으로 한다.
6. 대사 안에 번호 표시와 '학습 만화'라는 말을 쓰지 않는다. {title_rule}
"""

STAGING_INSTRUCTIONS = """학습 만화 {page}쪽(전체 {total}쪽, 기승전결의 '{role}')의 연출 컷 두 개를 써라.

이야기 배경: {setting}
이번 쪽의 이야기 흐름: {beat}
{special}
{previous}
- opening: 이번 쪽을 여는 컷. 이야기 흐름에 맞는 장면 전환이나 사건으로 시작한다.
- closing: 이번 쪽을 닫는 컷. {closing_rule}

규칙:
1. 연출 컷이므로 논문의 숫자나 새로운 주장을 말하지 않는다.
2. scene에는 장소, 인물의 행동과 표정을 구체적으로 쓴다.
3. lines에는 대사를 1~3개 넣는다. speaker는 누리, 박사님, 해설 중 하나다. 해설은 '~이다', '~했다'로 끝나는 설명문으로 쓴다.
4. 한 대사는 한두 문장, 60자 안팎으로 짧게 쓴다. 대사 안에 번호 표시와 '학습 만화'라는 말을 쓰지 않는다. {title_rule}
"""


def chunk_titles_schema(count):
    return chunk.chunk_titles_schema(count)


def story_schema(roles):
    """쪽별 흐름을 순서 있는 목록 대신 '기', '승', '전', '결' 이름이 붙은 칸으로 받는다 (7차에서 한 칸씩 밀린 문제 방지)."""
    properties = {"setting": {"type": "string"}}
    properties.update({role: {"type": "string"} for role, _ in roles})
    properties["ending"] = {"type": "string"}
    return {"type": "object", "properties": properties, "required": list(properties)}


def fact_panel_schema():
    schema = hybrid.panel_schema()
    lines = schema["properties"]["lines"]
    lines["maxItems"] = 2
    lines["items"]["properties"]["speaker"]["enum"] = [
        "누리",
        "박사님",
    ]  # 해설은 코드가 사실 문장으로 넣는다
    return schema


# ---------- 검사 ----------


def mixed_script_problems(label, text):
    words = re.findall(r"\S*[가-힣][A-Za-z]\S*", text)
    return (
        [
            f"{label}에 한글과 영어가 한 단어 안에 섞여 있다: {', '.join(words)}. {TITLE_RULE}"
        ]
        if words
        else []
    )


def check_titles(result):
    problems = mixed_script_problems("만화 제목", result["title_ko"])
    for index, item in enumerate(result["chunks"]):
        problems += mixed_script_problems(
            f"{index}번 조각 제목", item["title_ko"] + " " + item["summary"]
        )
    return problems


def check_lines(panel, seen_lines):
    problems = chunk.check_panel(panel, seen_lines)
    for line in panel["lines"]:
        text = line["text"].strip(QUOTE_MARKS).strip()
        if len(text) > MAX_LINE_LENGTH:
            problems.append(
                f'대사가 너무 길다({len(text)}자): "{text[:40]}…". 60자 안팎으로 줄여라.'
            )
        if line["speaker"] == "해설" and NARRATION_SPEECH_ENDING.search(text):
            problems.append(
                f"해설이 인물 말투로 끝난다: \"{text[-20:]}\". 해설은 '~이다', '~했다'로 끝나는 설명문으로 써라."
            )
        problems += mixed_script_problems("대사", text)
    return problems


def tidy(lines):
    """chunk.tidy_lines 정리에 더해, 대사를 감싼 따옴표를 지운다."""
    result = []
    for line in chunk.tidy_lines(lines):
        text = line["text"].strip(QUOTE_MARKS).strip()
        if text:
            result.append({"speaker": line["speaker"], "text": text})
    return result


# ---------- 사실 고르기 ----------


def pick_facts(model, item, count, sentences, used_evidence):
    numbers = [
        n
        for n in range(item["start"], item["end"] + 1)
        if not hybrid.is_table_fragment(sentences[n]["text"])
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

    def check(result):
        problems = base.check_facts(result, allowed)
        for index, fact in enumerate(result["facts"], start=1):
            problems += mixed_script_problems(f"{index}번 사실", fact["fact"])
            if FORMULA.search(fact["fact"]):
                problems.append(
                    f"{index}번 사실에 수식 기호가 있다. 수식 대신 그 뜻을 쉬운 말로 써라."
                )
        return problems

    result, problems, attempts = base.ask_with_retry(
        model, base.FACT_SYSTEM, prompt, base.fact_schema(count), check
    )
    facts = []
    dropped = []
    for fact in result["facts"]:
        text = hybrid.CITATION_IN_FACT.sub("", fact["fact"]).strip()
        evidence = [n for n in dict.fromkeys(fact["evidence"]) if n in allowed]
        # 근거 문장이 앞 사실과 겹치면, 내용까지 비슷할 때만 버린다.
        # (한 문장에서 '계산이 적다'와 'GAN보다 느리다'처럼 다른 사실 두 개가 나올 수 있어서다)
        repeated = any(
            chunk.text_similarity(text, other) >= SHARED_EVIDENCE_SIMILARITY
            for n in evidence
            for other in used_evidence.get(n, [])
        )
        if (
            not evidence
            or repeated
            or MIXED_SCRIPT.search(text)
            or FORMULA.search(text)
        ):
            dropped.append(
                text
            )  # 근거가 틀리거나, 앞 사실의 반복이거나, 섞인 단어·수식이 남은 사실은 버린다
            continue
        for n in evidence:
            used_evidence.setdefault(n, []).append(text)
        facts.append({"fact": text, "evidence": evidence, "item": item["title_ko"]})
    return facts, {
        "item": item["title_ko"],
        "problems": problems,
        "dropped": dropped,
        "attempts": attempts,
    }


# ---------- 이야기 뼈대와 컷 ----------


def write_story(model, title, pages, roles):
    page_lines = "\n".join(
        f"{index}쪽 ({role}: {meaning}) — "
        + ", ".join(c["title_ko"] for c in page["chunks"])
        for index, (page, (role, meaning)) in enumerate(zip(pages, roles), start=1)
    )
    role_keys = ", ".join(f"'{role}'" for role, _ in roles)
    prompt = STORY_INSTRUCTIONS.format(
        total=len(pages), title=title, pages=page_lines, role_keys=role_keys
    )
    result, problems, attempts = base.ask_with_retry(
        model,
        STORY_SYSTEM,
        prompt,
        story_schema(roles),
        lambda r: mixed_script_problems(
            "이야기 뼈대", " ".join(str(value) for value in r.values())
        ),
    )
    # 이름 붙은 칸에서 쪽 순서대로 꺼낸다. 흐름 안의 따옴표 대사는 지운다.
    result["pages"] = [
        " ".join(result[role].replace("'", "").replace('"', "").split())
        for role, _ in roles
    ]
    return result, problems, attempts


def write_fact_panel(
    model,
    page_number,
    total,
    role,
    story,
    beat,
    previous_scene,
    fact,
    sentences,
    seen_lines,
):
    quotes = " / ".join(f"[{n}] {sentences[n]['text'][:200]}" for n in fact["evidence"])
    previous = f"바로 앞 컷의 장면: {previous_scene}\n" if previous_scene else ""
    prompt = PANEL_INSTRUCTIONS.format(
        page=page_number,
        total=total,
        role=role,
        setting=story["setting"],
        beat=beat,
        previous=previous,
        fact=fact["fact"],
        quotes=quotes,
        title_rule=TITLE_RULE,
    )
    result, problems, attempts = base.ask_with_retry(
        model,
        PANEL_SYSTEM,
        prompt,
        fact_panel_schema(),
        lambda r: check_lines(r, seen_lines),
        max_retries=PANEL_RETRIES,
    )
    lines = tidy(result["lines"]) + [
        {"speaker": "해설", "text": fact["fact"]}
    ]  # 해설은 검증된 사실 그대로
    panel = {
        "scene": " ".join(result["scene"].split()),
        "lines": lines,
        "staging": False,
        "fact": fact["fact"],
        "evidence": fact["evidence"],
        "problems": problems,
        "attempts": attempts,
    }
    seen_lines.update(line["text"] for line in lines)
    return panel


def write_staging_panels(
    model, page_number, total, role, story, beat, previous_scene, seen_lines
):
    special = ""
    if page_number == 1:
        special = "첫 쪽이므로 opening에서 이야기 배경의 사건을 처음 보여 준다."
    closing_rule = (
        "이번 쪽 내용에 대한 누리의 감탄이나 다음 쪽에 대한 궁금증으로 마무리한다."
    )
    if page_number == total:
        closing_rule = f"이야기를 끝맺는다: {story['ending']}"
    previous = f"앞 쪽의 마지막 장면: {previous_scene}\n" if previous_scene else ""
    prompt = STAGING_INSTRUCTIONS.format(
        page=page_number,
        total=total,
        role=role,
        setting=story["setting"],
        beat=beat,
        special=special,
        previous=previous,
        closing_rule=closing_rule,
        title_rule=TITLE_RULE,
    )
    result, problems, attempts = base.ask_with_retry(
        model,
        PANEL_SYSTEM,
        prompt,
        chunk.staging_schema(),
        lambda r: check_lines(r["opening"], seen_lines)
        + check_lines(r["closing"], seen_lines),
        max_retries=PANEL_RETRIES,
    )
    panels = []
    for key in ["opening", "closing"]:
        panels.append(
            {
                "scene": " ".join(result[key]["scene"].split()),
                "lines": tidy(result[key]["lines"]),
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


def render_markdown(models, title_ko, story, pages, roles, sentences):
    out = [
        f"# {title_ko}",
        f"조각 제목·사실 고르기: `{models['outline']}` / 이야기·컷 쓰기: `{models['dialogue']}`",
        "",
    ]
    out += ["## 이야기 뼈대", "", f"- **배경**: {story['setting']}"]
    for index, ((role, _), beat) in enumerate(zip(roles, story["pages"]), start=1):
        out.append(f"- **{index}쪽 ({role})**: {beat}")
    out += [f"- **결말**: {story['ending']}", "", "## 구조도", ""]
    for page_number, page in enumerate(pages, start=1):
        for item in page["chunks"]:
            out.append(
                f"- {page_number}쪽 · **{item['title_ko']}** [{item['start']}~{item['end']}] — {item['summary']}"
            )
    out.append("")
    for page_number, (page, (role, _)) in enumerate(zip(pages, roles), start=1):
        out.append(
            f"## {page_number}쪽 ({role}) — {', '.join(item['title_ko'] for item in page['chunks'])}"
        )
        dropped = [text for log in page["fact_log"] for text in log["dropped"]]
        if dropped:
            out.append("버린 사실: " + " / ".join(dropped))
        out.append("")
        for index, panel in enumerate(page["panels"], start=1):
            tag = " (연출)" if panel["staging"] else ""
            out.append(f"**컷 {index}{tag}** — {panel['scene']}")
            if panel["problems"]:
                out.append("  - 남은 문제: " + " / ".join(panel["problems"]))
            for line in panel["lines"]:
                out.append(f"- **{line['speaker']}**: {line['text']}")
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
        "--dialogue-model",
        default="exaone3.5:7.8b",
        help="이야기 뼈대와 컷 쓰기에 쓸 모델",
    )
    parser.add_argument(
        "--dry-run", action="store_true", help="AI 호출 없이 조각 나누기만 확인"
    )
    args = parser.parse_args()
    models = {"outline": args.outline_model, "dialogue": args.dialogue_model}

    paper = json.loads(base.PAPER_JSON.read_text(encoding="utf-8"))
    sections, sentences = base.number_sentences(paper)
    total_pages = 4 if len(sentences) >= base.FOUR_PAGE_MIN_SENTENCES else 3
    roles = PAGE_ROLES[total_pages]
    chunks = chunk.build_chunks(
        sections, len(sentences), total_pages * chunk.CHUNKS_PER_PAGE
    )
    outline_prompt = (
        chunk.build_outline_prompt(paper, chunks, sentences) + "\n규칙: " + TITLE_RULE
    )

    print(
        f"조각 제목·사실 모델: {models['outline']} / 이야기·컷 모델: {models['dialogue']} / Ollama 주소: {base.OLLAMA_URL}"
    )
    print(
        f"본문 문장 수: {len(sentences)} (부록 제외), 쪽 수: {total_pages}, 조각 수: {len(chunks)}"
    )
    for index, item in enumerate(chunks):
        page = index // chunk.CHUNKS_PER_PAGE
        print(
            f"  {page + 1}쪽({roles[page][0]}) · 조각 {index} [{item['start']}~{item['end']}]: {', '.join(item['sections'])}"
        )
    if args.dry_run:
        return

    total_started = time.time()
    stage_seconds = {}

    print(f"\n[① 조각 제목] {models['outline']}")
    started = time.time()
    outline, title_problems, _ = base.ask_with_retry(
        models["outline"],
        base.OUTLINE_SYSTEM,
        outline_prompt,
        chunk_titles_schema(len(chunks)),
        check_titles,
    )
    for item, titled in zip(chunks, outline["chunks"]):
        item.update(title_ko=titled["title_ko"], summary=titled["summary"])
    stage_seconds["outline"] = round(time.time() - started)
    print(f"  {stage_seconds['outline']}초. 제목: {outline['title_ko']}")

    print(f"\n[② 사실 고르기] {models['outline']}")
    started = time.time()
    used_evidence = {}  # 근거 문장 번호 → 그 문장을 근거로 쓴 사실들
    accepted = []
    pages = []
    for page_index in range(0, len(chunks), chunk.CHUNKS_PER_PAGE):
        page_chunks = chunks[page_index : page_index + chunk.CHUNKS_PER_PAGE]
        facts = []
        fact_log = []
        for item in page_chunks:
            print(f"  [{item['start']}~{item['end']}] {item['title_ko']}")
            count = CLOSING_SECTION_FACTS if any(CLOSING_SECTION.search(t) for t in item["sections"]) else chunk.FACTS_PER_CHUNK
            item_facts, log = pick_facts(models["outline"], item, count, sentences, used_evidence)
            item_facts = chunk.remove_similar_facts(item_facts, accepted, log)
            accepted.extend(item_facts)
            facts.extend(item_facts)
            fact_log.append(log)
        pages.append({"chunks": page_chunks, "facts": facts, "fact_log": fact_log})
    stage_seconds["facts"] = round(time.time() - started)

    print(f"\n[③ 이야기 뼈대] {models['dialogue']}")
    started = time.time()
    story, story_problems, _ = write_story(
        models["dialogue"], outline["title_ko"], pages, roles
    )
    print(f"  배경: {story['setting']}")
    for index, ((role, _), beat) in enumerate(zip(roles, story["pages"]), start=1):
        print(f"  {index}쪽({role}): {beat}")
    print(f"  결말: {story['ending']}")

    print(f"\n[④ 컷 쓰기] {models['dialogue']}")
    previous_scene = None
    for page_number, (page, (role, _), beat) in enumerate(
        zip(pages, roles, story["pages"]), start=1
    ):
        print(
            f"  {page_number}/{len(pages)}쪽({role}) — 사실 컷 {len(page['facts'])}개 + 연출 컷 2개"
        )
        seen_lines = set()
        fact_panels = []
        for fact in page["facts"]:
            scene = fact_panels[-1]["scene"] if fact_panels else previous_scene
            fact_panels.append(
                write_fact_panel(
                    models["dialogue"],
                    page_number,
                    len(pages),
                    role,
                    story,
                    beat,
                    scene,
                    fact,
                    sentences,
                    seen_lines,
                )
            )
        opening, closing = write_staging_panels(
            models["dialogue"],
            page_number,
            len(pages),
            role,
            story,
            beat,
            previous_scene,
            seen_lines,
        )
        page["panels"] = [opening] + fact_panels + [closing]
        previous_scene = closing["scene"]
    stage_seconds["story_and_panels"] = round(time.time() - started)

    all_panels = [panel for page in pages for panel in page["panels"]]
    summary = {
        "models": models,
        "total_seconds": round(time.time() - total_started),
        "stage_seconds": stage_seconds,
        "pages": len(pages),
        "panels": len(all_panels),
        "fact_panels": sum(1 for panel in all_panels if not panel["staging"]),
        "staging_panels": sum(1 for panel in all_panels if panel["staging"]),
        "dropped_facts": sum(
            len(log["dropped"]) for page in pages for log in page["fact_log"]
        ),
        "title_problems": len(title_problems),
        "story_problems": len(story_problems),
        "remaining_problems": sum(len(panel["problems"]) for panel in all_panels),
    }
    print("\n===== 요약 =====")
    for key, value in summary.items():
        print(f"{key}: {value}")

    RESULT_DIR.mkdir(exist_ok=True)
    name = f"story_{models['outline'].replace(':', '_')}__{models['dialogue'].replace(':', '_')}"
    (RESULT_DIR / f"{name}.json").write_text(
        json.dumps(
            {
                "summary": summary,
                "title_ko": outline["title_ko"],
                "story": story,
                "pages": pages,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    (RESULT_DIR / f"{name}.md").write_text(
        render_markdown(models, outline["title_ko"], story, pages, roles, sentences),
        encoding="utf-8",
    )
    print(f"\n결과 저장: {RESULT_DIR / f'{name}.md'}")


if __name__ == "__main__":
    main()
