# Spring 백엔드 실행 결과

Spring 서버 API로 받은 결과를 보관합니다. 파일 이름은 `날짜_무엇_작업번호`입니다.

| 파일 | 날짜 | 받은 API | 내용 |
| --- | --- | --- | --- |
| `2026-10-02_grobid-api.xml` | 10-02 | GROBID 연동 확인 | Spring이 GROBID에서 받은 논문 분석 원본(TEI XML) |
| `2026-10-02_grobid-api.json` | 10-02 | `POST /api/papers` (DB 저장 전) | 위 XML을 제목 → 섹션 → 문장(페이지 포함)으로 바꾼 JSON |
| `2026-10-03_comic-plan_job4.json` | 10-03 | `POST /api/papers/4/comic-plan` | 만화 설계도(조각 제목·사실·근거 번호). Java로 옮긴 핵심 사실 추출의 2차 실행 결과, 조각 제목 12개 모두 맞음 |
| `2026-10-04_comic_job13.json` | 10-04 | `GET /api/papers/13/comic` | **실제 실행 1차**. 업로드부터 만화 저장까지 처음 끝까지 돈 결과(맥북, 36분, 4쪽 33컷) |
| `2026-10-04_comic_job23.json` | 10-04 | `GET /api/papers/23/comic` | **실제 실행 2차**. 장면 칸·한계 컷 검사를 보강한 뒤 다시 돈 결과(맥북, 51분, 4쪽 33컷). 13번과 비교용 |

## 새 결과 저장하기

작업이 `COMPLETED`가 된 뒤에 저장합니다. 처리 중에 받으면 만화 대신 `409 Conflict` 오류가 저장됩니다.

```
curl -s http://127.0.0.1:8080/api/papers/<작업 번호>/comic | python3 -m json.tool --no-ensure-ascii > results/<날짜>_comic_job<작업 번호>.json
```

## 다른 곳에 있는 결과

- Python 시험 1~7차: `spikes/llm-spike/results/` (그 폴더의 README 참고)
- 결정 기록과 진행 상황: `docs/plan.md`
- 1단계 추출 도구 비교: `spikes/pdfbox-spike/*.txt`(PDFBox), `spikes/grobid-spike/*.xml`(GROBID)
