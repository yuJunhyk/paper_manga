# GROBID 추출 결과 (1단계)

GROBID 0.9.1(CRF)로 샘플 논문 2편을 분석한 TEI XML을 두던 곳입니다.
논문 원문이 그대로 들어 있어 저작권 때문에 git에는 올리지 않습니다(`.gitignore`).

다시 만들기 (`compose.yaml`로 GROBID를 띄운 뒤):

```
curl -sS -F "input=@samples/latent-diffusion-2112.10752.pdf" -F segmentSentences=1 \
  -F teiCoordinates=s -F teiCoordinates=head \
  http://127.0.0.1:8070/api/processFulltextDocument > spikes/grobid-spike/latent-diffusion-2112.10752.xml
```

비교 결과와 GROBID로 결정한 이유는 `docs/plan.md` 1단계 항목에 있습니다.
