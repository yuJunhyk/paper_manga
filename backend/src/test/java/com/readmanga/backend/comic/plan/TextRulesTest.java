package com.readmanga.backend.comic.plan;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 시험 단계(Python)에서 정한 규칙과 기준값이 Java에서도 같게 나오는지 확인한다. */
class TextRulesTest {

    @Test
    void 한글_뒤에_영어가_붙은_단어만_섞인_단어로_잡는다() {
        assertThat(TextRules.mixedScriptWords("라tent Diffusion 모델")).isEqualTo("라tent");
        assertThat(TextRules.hasMixedScript("마asking 영역의 채우기")).isTrue();
        assertThat(TextRules.hasMixedScript("LDM은 GAN보다 느리고 UNet에 붙인다")).isFalse();
        assertThat(TextRules.hasMixedScript("1.45B 파라미터, f=8과 같은")).isFalse();
    }

    @Test
    void 수식_기호를_잡고_보통_괄호는_통과시킨다() {
        assertThat(TextRules.hasFormula("조건부 분포 p(z|y)를 모델링한다")).isTrue();
        assertThat(TextRules.hasFormula("데이터 분포 p(x)를 배운다")).isTrue();
        assertThat(TextRules.hasFormula("LDM(ours)은 빠르다")).isFalse();
        assertThat(TextRules.hasFormula("f=4 모델은 손실이 작다")).isFalse();
    }

    @Test
    void 숫자와_기호가_대부분인_표_조각을_잡는다() {
        assertThat(TextRules.isTableFragment("CelebA-HQ 256 × 256 FFHQ 256 × 256 Method FID ↓ Prec.")).isTrue();
        assertThat(TextRules.isTableFragment("Tab. 8.")).isTrue();
        assertThat(TextRules.isTableFragment("On CelebA-HQ, we report a new state-of-the-art FID of 5.11.")).isFalse();
    }

    @Test
    void 부록과_한계_결론_섹션을_알아본다() {
        assertThat(TextRules.isAppendixHeading("A. Changelog")).isTrue();
        assertThat(TextRules.isAppendixHeading("D.6. Super-Resolution")).isTrue();
        assertThat(TextRules.isAppendixHeading("R d×d i , W")).isFalse();
        assertThat(TextRules.isClosingSection("Limitations & Societal Impact")).isTrue();
        assertThat(TextRules.isClosingSection("Conclusion")).isTrue();
        assertThat(TextRules.isConclusionSection("Concluding remarks")).isTrue();
        assertThat(TextRules.isConclusionSection("Limitations and Future Work")).isFalse();
    }

    @Test
    void 결과_숫자는_소수와_백분율만_고른다() {
        assertThat(TextRules.resultValues("WMT 2014에서 2 블루 점수 이상 향상된 28.4 블루 점수, 정확도 12 % 상승"))
                .containsExactlyInAnyOrder("28.4", "12%");
    }

    @Test
    void 비슷한_사실의_유사도가_시험_단계와_같다() {
        // 같은 근거, 다른 내용 → 0.35 미만이라 둘 다 남는다
        assertThat(TextRules.similarity("잠재 확산 모델은 픽셀 기반 모델에 비해 계산 요구가 적다.",
                "잠재 확산 모델은 GAN에 비해 샘플링 속도가 느리다.")).isCloseTo(0.24, org.assertj.core.data.Offset.offset(0.01));
        // 4차 FID 반복 → 0.55 이상이라 버린다
        assertThat(TextRules.similarity("LDM-4는 FID 점수에서 기존 모델보다 우수한 성능을 보여주었다.",
                "LDM-4는 텍스트 기반 생성에서 기존 모델보다 우수한 성능을 보여주었다.")).isCloseTo(0.61, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void 사실_문장의_번호를_지운다() {
        assertThat(TextRules.removeCitations("확산 모델은 뛰어나다 [11].")).isEqualTo("확산 모델은 뛰어나다.");
        assertThat(TextRules.removeCitations("효율을 높인다 [62, 63]")).isEqualTo("효율을 높인다");
    }

    @Test
    void 논문에_없는_영어_용어를_찾는다() {
        Set<String> paper = TextRules.englishWords(List.of(
                "We apply cross-attention based conditioning and convolutional sampling in the latent space of LDM."));

        assertThat(TextRules.unknownEnglishWords("트랜스포머와 Kernel Filtering을 결합한 Latent Space", paper))
                .containsExactly("Kernel", "Filtering");
        assertThat(TextRules.unknownEnglishWords("'Cross-Attention Based Conditioning' 문구와 LDM", paper)).isEmpty();
        assertThat(TextRules.unknownEnglishWords("LDMs와 latent spaces", paper)).as("복수형은 같은 낱말").isEmpty();
    }

    @Test
    void 표_그림_제목만_골라내고_표를_인용하는_본문_문장은_남긴다() {
        assertThat(TextRules.isCaption("Comparison of a class-conditional ImageNet LDM with recent state-of-the-art methods [12].")).isTrue();
        assertThat(TextRules.isCaption("Table 2. Evaluation of text-conditional image synthesis on the 256 × 256-sized MS-COCO dataset")).isTrue();
        assertThat(TextRules.isCaption("Tab. 2. We note that applying classifier-free diffusion guidance greatly boosts sample quality")).isFalse();
        assertThat(TextRules.isCaption("Tab. 8 shows hyperparameters and reconstruction performance of the first stage models")).isFalse();
        assertThat(TextRules.isCaption("Fig. 6 shows sample quality as a function of training progress")).isFalse();
    }

    @Test
    void 사실_끝의_괄호_근거_번호를_찾아_지운다() {
        assertThat(TextRules.hasCitation("잠재 확산 모델은 샘플링 속도가 느리다(239).")).isTrue();
        assertThat(TextRules.removeCitations("잠재 확산 모델은 샘플링 속도가 느리다(239, 240).")).isEqualTo("잠재 확산 모델은 샘플링 속도가 느리다.");
        assertThat(TextRules.removeCitations("FID가 1.6배(1.6x) 개선됐다 (239번)")).isEqualTo("FID가 1.6배(1.6x) 개선됐다");
    }

    @Test
    void 비교와_숫자가_있는_문장을_결과_문장으로_본다() {
        assertThat(TextRules.resultScore("Participants achieved a significantly higher CorrectRate with DC (median = 0.750) than with CV.")).isGreaterThanOrEqualTo(2);
        assertThat(TextRules.resultScore("Participants working with data comics achieved on average 28.1% higher accuracy.")).isGreaterThanOrEqualTo(2);
        assertThat(TextRules.resultScore("Overall, we observe a speed-up of at least 2.7× while improving FID scores.")).isGreaterThanOrEqualTo(2);
        assertThat(TextRules.resultScore("To address RQ1, we compared the effectiveness of data comics and conventional visualisations.")).isLessThan(2);
        assertThat(TextRules.resultScore("We selected the globally relevant topics of climate change and global warming.")).isZero();
    }

    @Test
    void 정한_번역_대신_다른_번역을_쓴_곳을_찾는다() {
        List<ComicPlan.Term> terms = List.of(new ComicPlan.Term("data comics", "데이터 만화", List.of("데이터 코믹스", "만화")));

        assertThat(TextRules.termVariants("데이터 코믹스는 데이터 만화와 같다", terms)).containsExactly("'데이터 코믹스' → '데이터 만화'");
        assertThat(TextRules.termVariants("데이터 만화(data comics)는 쉽다", terms)).as("정한 번역 안의 '만화'는 문제 아님").isEmpty();
    }

    @Test
    void 영어_원문_자리에_한국어만_든_괄호를_찾는다() {
        assertThat(TextRules.koreanParenthesisProblems("1번 사실", "학생들은 윤리적 문제(윤리적 문제)를 가지고 있다")).singleElement().asString()
                .contains("(윤리적 문제)");
        assertThat(TextRules.koreanParenthesisProblems("흐름", "시각 자료 이해 능력(시각 자료 이해력)이 중요하다")).hasSize(1);
        assertThat(TextRules.koreanParenthesisProblems("1번 사실", "잠재 공간(latent space)에서 정답률이 높았다(중앙값 0.750, N=54)")).isEmpty();
    }

    @Test
    void 사실끼리_비교할_때는_괄호_속_영어_원문을_빼고_잰다() {
        // 199번: 똑같이 붙은 '(Latent Diffusion Models)' 때문에 다른 두 사실이 0.40으로 나와 'GAN보다 느리다'가 버려졌다
        String slow = "잠재 공간 확산 모델(Latent Diffusion Models)의 순차적 샘플링 과정은 GAN보다 느리다.";
        String cheap = "잠재 공간 확산 모델(Latent Diffusion Models)은 픽셀 기반 접근 방식에 비해 계산 요구 사항을 크게 줄인다.";

        assertThat(TextRules.similarity(slow, cheap)).isGreaterThanOrEqualTo(0.35);
        assertThat(TextRules.factSimilarity(slow, cheap)).isLessThan(0.35);
    }

    @Test
    void 인용_번호가_주어인_원문_문장은_다른_연구의_문장이다() {
        // 204번: "[66] extend …"를 '이 연구는'으로, "VQ-VAEs [67,101] use …"를 냥박사가 '우리는'으로 말했다
        assertThat(TextRules.isOtherStudySentence("[66] extend this approach to text-to-image generation by learning a joint distribution.")).isTrue();
        assertThat(TextRules.isOtherStudySentence("VQ-VAEs [67,101] use autoregressive models to learn an expressive prior.")).isTrue();
        assertThat(TextRules.isOtherStudySentence("Milesi et al. [6] employed GenAI tools to transform learning analytics data.")).isTrue();
        assertThat(TextRules.isOtherStudySentence("[72] and fix the image degradation to a bicubic interpolation.")).as("이 논문 문장이 쪼개진 것").isFalse();
        assertThat(TextRules.isOtherStudySentence("Following [15], the UNet of this diffusion model uses attention layers.")).isFalse();
        assertThat(TextRules.isOtherStudySentence("We train a 1.45B parameter model on LAION-400M [78].")).isFalse();
    }
}
